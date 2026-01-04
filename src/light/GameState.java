package light;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

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

    // JSON 檔路徑（相對於 Python agent）
    private static final String GAME_STATE_PATH = "../game_state.json";
    private static final String ACTION_PATH = "../action.json";

    private static final Gson GSON = new GsonBuilder().create();

    public GameState() {
        reset();
    }

    /** 重新開始一局，並寫出初始 JSON */
    public void reset() {
        board = new int[size][size];
        snakeBody = new LinkedList<>();

        // 蛇從中間開始
        int startX = size / 2;
        int startY = size / 2;
        snakeBody.clear();
        snakeBody.add(new int[]{startX, startY});
        direction = 1; // 一開始往右
        done = false;
        reward = 0.0;

        // 隨機放一顆食物
        spawnFood();

        // 更新棋盤陣列
        updateBoardFromState();

        // 寫出初始狀態，給 Python detect_board_size / reset 用
        writeToJson(GAME_STATE_PATH);
    }

    /** 是否 GameOver */
    public boolean isDone() {
        return done;
    }

    /**
     * 優先依照 Python 寫的 `../action.json` 走一步，
     * 若檔案不存在或解析失敗則改用隨機步。
     */
    public void stepFromActionFileOrRandom() {
        if (done) {
            return;
        }

        boolean usedAction = false;
        File actionFile = new File(ACTION_PATH);

        if (actionFile.isFile()) {
            try (FileReader fr = new FileReader(actionFile)) {
                JsonObject obj = GSON.fromJson(fr, JsonObject.class);
                if (obj != null && obj.has("action")) {
                    int action = obj.get("action").getAsInt();
                    stepByAction(action);
                    usedAction = true;
                }
            } catch (Exception e) {
                // 若讀檔失敗，之後改用隨機
            }
        }

        if (!usedAction) {
            stepRandom();
        }
    }

    /** 隨機一個方向走一步，並寫出 JSON（給純 UI 測試時用） */
    public void stepRandom() {
        if (done) {
            return;
        }
        direction = random.nextInt(4);
        stepByDirection();
        writeToJson(GAME_STATE_PATH);
    }

    /** 依外部 action 0\~3 走一步，並寫出 JSON（給 Python 用） */
    public void stepByAction(int action) {
        if (done) {
            return;
        }
        if (action < 0 || action > 3) {
            action = 0;
        }
        this.direction = action;
        stepByDirection();
        writeToJson(GAME_STATE_PATH);
    }

    /** 依目前 direction 走一步（核心蛇邏輯） */
    private void stepByDirection() {
        if (done) {
            return;
        }

        int[] head = snakeBody.getFirst();
        int x = head[0];
        int y = head[1];

        switch (direction) {
            case 0: y -= 1; break; // 上
            case 1: x += 1; break; // 右
            case 2: y += 1; break; // 下
            case 3: x -= 1; break; // 左
            default: break;
        }

        reward = 0.0;

        // 撞牆：game over + 負獎勵
        if (x < 0 || x >= size || y < 0 || y >= size) {
            done = true;
            reward = -1.0;
            return;
        }

        // 撞到自己：game over + 負獎勵
        for (int[] body : snakeBody) {
            if (body[0] == x && body[1] == y) {
                done = true;
                reward = -1.0;
                return;
            }
        }

        // 新頭位置
        int[] newHead = new int[]{x, y};
        snakeBody.addFirst(newHead);

        // 吃到食物：加長、不移除尾，重生食物 + 正獎勵
        if (x == foodX && y == foodY) {
            reward = 1.0;
            spawnFood();
        } else {
            // 一般移動：移除尾巴，長度不變
            reward = 0.0;
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

    /** 寫出當前狀態到 JSON，給 Python `JavaSnakeEnv` 用 */
    public void writeToJson(String path) {
        JsonObject root = new JsonObject();

        // board 深拷貝
        int[][] b = this.board;
        int n = b.length;
        int[][] copy = new int[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(b[i], 0, copy[i], 0, n);
        }
        root.add("board", GSON.toJsonTree(copy));

        // reward / done
        root.addProperty("reward", this.reward);
        root.addProperty("done", this.done);

        try (FileWriter fw = new FileWriter(path)) {
            GSON.toJson(root, fw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
