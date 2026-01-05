package light;

import java.util.LinkedList;
import java.util.Random;

public class GameState {

    // 0 = 空, 1 = 蛇, 2 = 食物
    private int[][] board;
    private LinkedList<int[]> snakeBody; // 每個元素: [x, y]
    private int direction;               // 0上 1右 2下 3左
    private int foodX;
    private int foodY;
    private boolean done;

    // 給 RL 用的獎勵
    private double reward;

    private final Random random = new Random();
    private final int size = 20; // 棋盤邊長

    // 明確：不啟用穿牆（wrap）行為；撞到格子邊界即視為撞牆
    private boolean wrapWalls = false;
    // 上一步方向（用於偵測是否轉彎）
    private int lastDirection = -1;

    public GameState() {
        reset();
    }

    /** 重新開始一局 */
    public void reset() {
        board = new int[size][size];
        snakeBody = new LinkedList<>();

        // 蛇從中間開始，預設長度 3。頭在中心，身體往左延伸
        int startX = size / 2;
        int startY = size / 2;
        snakeBody.clear();
        for (int i = 0; i < 3; i++) {
            snakeBody.add(new int[]{startX - i, startY});
        }
        // 初始方向隨機，避免每局都往同一方向（降低每次剛好 10 步到牆的情況）
        direction = random.nextInt(4);
        lastDirection = direction;
        done = false;
        reward = 0.0;

        // 隨機放一顆食物
        spawnFood();

        // 更新棋盤陣列
        updateBoardFromState();
    }

    /** 是否 GameOver */
    public boolean isDone() {
        return done;
    }

    /** 隨機一個方向走一步 */
    public void stepRandom() {
        if (done) {
            return;
        }
        lastDirection = direction;
        direction = random.nextInt(4);
        stepByDirection();
    }

    /** 依外部 action 0~3 走一步 */
    public void stepByAction(int action) {
        if (done) {
            return;
        }
        if (action < 0 || action > 3) {
            action = 0;
        }
        // 保留上一個方向以判斷是否有轉彎
        this.lastDirection = this.direction;
        this.direction = action;
        stepByDirection();
    }

    private int manhattan(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }

    /** 依目前 direction 走一步（核心蛇邏輯） */
    private void stepByDirection() {
        if (done) {
            return;
        }

        int[] head = snakeBody.getFirst();
        int curX = head[0];
        int curY = head[1];

        int newX = curX;
        int newY = curY;
        switch (direction) {
            case 0: newY = curY - 1; break; // 上
            case 1: newX = curX + 1; break; // 右
            case 2: newY = curY + 1; break; // 下
            case 3: newX = curX - 1; break; // 左
            default: break;
        }

        // Reward shaping: base step penalty
        double stepReward = -0.01; // 每步微小懲罰，鼓勵短路徑

        // 檢查是否撞牆（grid 邊界）
        boolean hitWall = false;
        if (!wrapWalls) {
            if (newX < 0 || newX >= size || newY < 0 || newY >= size) {
                hitWall = true;
            }
        } else {
            // 若啟用 wrap（目前預設 false），則環繞
            newX = (newX % size + size) % size;
            newY = (newY % size + size) % size;
        }

        // 計算距離變化（舊距離與新距離）
        int oldDist = manhattan(curX, curY, foodX, foodY);
        int newDist = (hitWall ? oldDist : manhattan(newX, newY, foodX, foodY));
        // 接近食物給正獎勵，但 scale 很小，避免單步負獎懲過大影響學習
        // 原本 1.0 * delta 導致走遠一格就是 -1.0，會過度懲罰，改成按距離差乘 0.2，再 clamp
        double rawDistDelta = (double)(oldDist - newDist);
        double distBonus = 0.2 * rawDistDelta; // 每格差值最多帶來 +/-0.2
        // clamp distBonus 到 [-0.5, 0.5]
        if (distBonus > 0.5) distBonus = 0.5;
        if (distBonus < -0.5) distBonus = -0.5;

        // 轉彎獎勵：當 agent 改變方向且該變動使距離縮短，給較高的額外獎勵
        boolean isTurn = (this.lastDirection >= 0 && this.lastDirection != this.direction);
        double turnBonus = 0.0;
        if (isTurn && newDist < oldDist) {
            turnBonus = 0.6; // 增加到 0.6
        }

        // 先判斷是否會吃食物（用 newX/newY）
        boolean willEat = false;
        if (!hitWall) {
            willEat = (newX == foodX && newY == foodY);
        }

        // DEBUG: 印出本步計算的座標與狀態
        System.out.println(String.format("[GameState] calc head=(%d,%d) food=(%d,%d) willEat=%b oldDist=%d newDist=%d", newX, newY, foodX, foodY, willEat, oldDist, newDist));

        // 若撞牆則直接結束（不要 addFirst 重複 head）
        if (hitWall) {
            done = true;
            reward = -10.0; // 死亡較重懲罰
            updateBoardFromState();
            System.out.println(String.format("[GameState] DONE triggered: hitWall=%b selfCollision=%b newHead=(%d,%d)", hitWall, false, newX, newY));
            return;
        }

        // 判斷是否撞到自己：允許移入尾巴的位置（當且僅當本步不會吃食物，因為吃食物會令尾巴不移除）
        boolean selfCollision = false;
        int tailIndex = snakeBody.size() - 1;
        for (int i = 0; i < snakeBody.size(); i++) {
            int[] b = snakeBody.get(i);
            if (b[0] == newX && b[1] == newY) {
                if (i != tailIndex || willEat) {
                    selfCollision = true;
                    break;
                }
            }
        }

        // 新頭
        int[] newHead = new int[]{newX, newY};
        snakeBody.addFirst(newHead);

        // 若撞自己，標示為結束並給負分；仍更新 board 讓 UI 顯示最後位置
        if (selfCollision) {
            done = true;
            reward = -10.0;
            updateBoardFromState();
            System.out.println(String.format("[GameState] DONE triggered: hitWall=%b selfCollision=%b newHead=(%d,%d)", false, selfCollision, newX, newY));
            return;
        }

        // 吃到食物：加長、不移除尾，重生食物 + 正獎勵
        if (willEat) {
            reward = 12.0; // 吃到食物給大正分
            spawnFood(); // 產生下一個食物
        } else {
            // 一般移動：移除尾巴，長度不變
            reward = stepReward + distBonus + turnBonus; // 微懲罰加上接近食物的獎勵與轉彎獎勵
            // clamp 單步 reward 範圍，避免極端值影響 Q 更新
            if (reward > 1.0) reward = 1.0;
            if (reward < -1.0) reward = -1.0;
            snakeBody.removeLast();
        }

        updateBoardFromState();
    }

    /** 隨機產生一顆新食物（不與身體重疊） */
    private void spawnFood() {
        while (true) {
            int fx = random.nextInt(size);
            int fy = random.nextInt(size);
            boolean conflict = false;
            for (int[] body : snakeBody) {
                if (body[0] == fx && body[1] == fy) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) {
                foodX = fx;
                foodY = fy;
                break;
            }
        }
    }

    /** 依蛇與食物位置更新 board 陣列 */
    private void updateBoardFromState() {
        // 全清為 0
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                board[i][j] = 0;
            }
        }
        // 畫蛇
        for (int[] body : snakeBody) {
            int bx = body[0];
            int by = body[1];
            if (bx >= 0 && bx < size && by >= 0 && by < size) {
                board[by][bx] = 1;
            }
        }
        // 畫食物
        if (foodX >= 0 && foodX < size && foodY >= 0 && foodY < size) {
            board[foodY][foodX] = 2;
        }
    }

    /** 給 UI 使用的棋盤大小 */
    public int getBoardSize() {
        return size;
    }

    /** 給 UI 使用的棋盤內容 */
    public int[][] getBoard() {
        return board;
    }

    /** 給 RL 用的 reward */
    public double getReward() {
        return reward;
    }

    /** 取得蛇頭 X */
    public int getHeadX() {
        if (snakeBody == null || snakeBody.isEmpty()) return -1;
        return snakeBody.getFirst()[0];
    }

    /** 取得蛇頭 Y */
    public int getHeadY() {
        if (snakeBody == null || snakeBody.isEmpty()) return -1;
        return snakeBody.getFirst()[1];
    }

    /** 取得蛇長 */
    public int getSnakeLength() {
        if (snakeBody == null) return 0;
        return snakeBody.size();
    }

    /** 取得食物 X */
    public int getFoodX() {
        return foodX;
    }

    /** 取得食物 Y */
    public int getFoodY() {
        return foodY;
    }
}
