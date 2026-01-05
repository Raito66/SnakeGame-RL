package light;

import java.util.LinkedList;
import java.util.Random;

public class GameState {

    // 0 = 空, 1 = 蛇, 2 = 食物
    private int[][] board;
    private LinkedList<int[]> snakeBody; // 每個元素: [x, y]
    private int direction;               // 0=上, 1=下, 2=左, 3=右  (changed mapping per user request)
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

        // 蛇從中間開始，預設長度 3。頭在中心，身體要延伸到與 direction 相反的方向，
        // 以避免當 direction 被隨機為非右時產生立刻自撞的情況。
        int startX = size / 2;
        int startY = size / 2;

        // 初始方向隨機，避免每局都往同一方向（降低每次剛好 10 步到牆的情況）
        direction = random.nextInt(4);
        lastDirection = direction;

        int dx = 0, dy = 0; // 用於延伸蛇身的方向（tail 相對於 head 的偏移）
        switch (direction) {
            case 0: // up -> body should extend downward
                dx = 0; dy = 1; break;
            case 1: // down -> body should extend upward
                dx = 0; dy = -1; break;
            case 2: // left -> body should extend to the right
                dx = 1; dy = 0; break;
            case 3: // right -> body should extend to the left
                dx = -1; dy = 0; break;
        }

        // 建立長度為 3 的蛇身：i==0 為頭
        for (int i = 0; i < 3; i++) {
            int x = startX + dx * i;
            int y = startY + dy * i;
            snakeBody.add(new int[]{x, y});
        }

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
        // 禁止 180 度回轉：若 action 與目前方向相反，忽略該 action
        if (isReverseDirection(this.direction, action)) {
            // 記錄一次 debug 訊息並保留原方向
            System.out.println(String.format("[GameState] 忽略 180 度回轉請求：current=%d, requested=%d", this.direction, action));
            action = this.direction;
        }
        // 保留上一個方向以判斷是否有轉彎
        this.lastDirection = this.direction;
        this.direction = action;
        stepByDirection();
    }

    // 檢查兩個方向是否互為相反方向
    public boolean isReverseDirection(int dirA, int dirB) {
        // mapping: 0=up,1=down,2=left,3=right
        if (dirA == 0 && dirB == 1) return true;
        if (dirA == 1 && dirB == 0) return true;
        if (dirA == 2 && dirB == 3) return true;
        if (dirA == 3 && dirB == 2) return true;
        return false;
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
        // mapping: 0=up,1=down,2=left,3=right
        switch (direction) {
            case 0: newY = curY - 1; break; // 上
            case 1: newY = curY + 1; break; // 下
            case 2: newX = curX - 1; break; // 左
            case 3: newX = curX + 1; break; // 右
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
        double rawDistDelta = (double)(oldDist - newDist);
        double distBonus = 0.2 * rawDistDelta; // 每格差值最多帶來 +/-0.2
        if (distBonus > 0.5) distBonus = 0.5;
        if (distBonus < -0.5) distBonus = -0.5;

        boolean isTurn = (this.lastDirection >= 0 && this.lastDirection != this.direction);
        double turnBonus = 0.0;
        if (isTurn && newDist < oldDist) {
            turnBonus = 0.6; // 增加到 0.6
        }

        boolean willEat = false;
        if (!hitWall) {
            willEat = (newX == foodX && newY == foodY);
        }

        System.out.println(String.format("[GameState] calc head=(%d,%d) food=(%d,%d) willEat=%b oldDist=%d newDist=%d", newX, newY, foodX, foodY, willEat, oldDist, newDist));

        if (hitWall) {
            done = true;
            reward = -10.0; // 死亡較重懲罰
            updateBoardFromState();
            System.out.println(String.format("[GameState] DONE triggered: hitWall=%b selfCollision=%b newHead=(%d,%d)", hitWall, false, newX, newY));
            return;
        }

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

        int[] newHead = new int[]{newX, newY};
        snakeBody.addFirst(newHead);

        if (selfCollision) {
            done = true;
            reward = -10.0;
            updateBoardFromState();
            System.out.println(String.format("[GameState] DONE triggered: hitWall=%b selfCollision=%b newHead=(%d,%d)", false, selfCollision, newX, newY));
            return;
        }

        if (willEat) {
            reward = 12.0; // 吃到食物給大正分
            spawnFood(); // 產生下一個食物
        } else {
            reward = stepReward + distBonus + turnBonus; // 微懲罰加上接近食物的獎勵與轉彎獎勵
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

    /** 取得目前方向 */
    public int getDirection() {
        return direction;
    }

    /**
     * 檢查若採取給定 action，是否會立即導致死亡（撞牆或撞到自己）。
     * 不會改變內部狀態。
     */
    public boolean wouldCollide(int action) {
        if (action < 0 || action > 3) return true;
        int[] head = snakeBody.getFirst();
        int curX = head[0];
        int curY = head[1];
        int newX = curX;
        int newY = curY;
        switch (action) {
            case 0: newY = curY - 1; break; // up
            case 1: newY = curY + 1; break; // down
            case 2: newX = curX - 1; break; // left
            case 3: newX = curX + 1; break; // right
            default: break;
        }
        // 撞牆
        if (!wrapWalls) {
            if (newX < 0 || newX >= size || newY < 0 || newY >= size) return true;
        }
        boolean willEat = (newX == foodX && newY == foodY);
        int tailIndex = snakeBody.size() - 1;
        for (int i = 0; i < snakeBody.size(); i++) {
            int[] b = snakeBody.get(i);
            if (b[0] == newX && b[1] == newY) {
                if (i != tailIndex || willEat) {
                    return true;
                }
            }
        }
        return false;
    }
}
