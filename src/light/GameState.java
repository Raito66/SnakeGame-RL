package light;

import java.util.LinkedList;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Random;

public class GameState {

    public int[][] board;
    public int snakeX;
    public int snakeY;
    public int foodX;
    public int foodY;
    public boolean done = false;

    // 0=上,1=右,2=下,3=左
    public int direction = 1;

    public LinkedList<int[]> snakeBody = new LinkedList<>();

    // 強化學習用 reward
    public double reward = 0.0;

    // 建構子：初始化棋盤、蛇、食物
    public GameState() {
        // 預設棋盤大小 20x20
        this.board = new int[20][20];

        // 初始蛇頭位置
        this.snakeX = 10;
        this.snakeY = 10;

        // 初始化蛇身
        initSnakeBody();

        // 產生一個食物
        spawnFood();

        // 畫到棋盤
        refreshBoard();
    }

    // 初始化蛇身，預設長度 3，頭在 snakeX,snakeY，往左延伸
    public void initSnakeBody() {
        snakeBody.clear();
        for (int i = 0; i < 3; i++) {
            int x = snakeX - i;
            int y = snakeY;
            snakeBody.addFirst(new int[]{x, y});
        }
    }

    // 將目前狀態寫成簡單 JSON 給 Python
    public void writeToJson(String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"snakeX\":").append(snakeX).append(",");
        sb.append("\"snakeY\":").append(snakeY).append(",");
        sb.append("\"foodX\":").append(foodX).append(",");
        sb.append("\"foodY\":").append(foodY).append(",");
        sb.append("\"done\":").append(done ? "true" : "false").append(",");
        sb.append("\"direction\":").append(direction).append(",");
        sb.append("\"reward\":").append(reward).append(",");

        // \`board\`：給 Python 當 observation
        sb.append("\"board\":[");
        for (int y = 0; y < board.length; y++) {
            sb.append("[");
            for (int x = 0; x < board[0].length; x++) {
                sb.append(board[y][x]);
                if (x < board[0].length - 1) sb.append(",");
            }
            sb.append("]");
            if (y < board.length - 1) sb.append(",");
        }
        sb.append("],");

        // \`snakeBody\`
        sb.append("\"snakeBody\":[");
        for (int i = 0; i < snakeBody.size(); i++) {
            int[] p = snakeBody.get(i);
            sb.append("[").append(p[0]).append(",").append(p[1]).append("]");
            if (i < snakeBody.size() - 1) sb.append(",");
        }
        sb.append("]");

        sb.append("}");

        try (FileWriter fw = new FileWriter(path)) {
            fw.write(sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // 從 action.json 讀取動作: { "action": 0~3 }
    public static int readActionFromJson(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            if (line == null) return 0;
            int idx = line.indexOf("\"action\"");
            if (idx == -1) return 0;
            int colon = line.indexOf(":", idx);
            if (colon == -1) return 0;
            int start = colon + 1;
            while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < line.length()
                    && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '-')) {
                end++;
            }
            String numStr = line.substring(start, end);
            return Integer.parseInt(numStr);
        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 強化學習版：根據 action 做一步
    public void step(int action) {
        if (done) {
            reward = 0.0;
            return;
        }

        // action 對應到 direction
        if (action >= 0 && action <= 3) {
            direction = action;
        }

        int headX = snakeX;
        int headY = snakeY;

        switch (direction) {
            case 0: headY -= 1; break; // 上
            case 1: headX += 1; break; // 右
            case 2: headY += 1; break; // 下
            case 3: headX -= 1; break; // 左
            default: break;
        }

        // 預設小懲罰，避免無限亂走
        reward = -0.01;

        int rows = board.length;
        int cols = board[0].length;

        // 撞牆
        if (headX < 0 || headX >= cols || headY < 0 || headY >= rows) {
            done = true;
            reward = -1.0;
            return;
        }

        // 撞自己
        for (int[] p : snakeBody) {
            if (p[0] == headX && p[1] == headY) {
                done = true;
                reward = -1.0;
                return;
            }
        }

        // 新頭
        snakeBody.addFirst(new int[]{headX, headY});
        snakeX = headX;
        snakeY = headY;

        // 吃到食物
        if (headX == foodX && headY == foodY) {
            reward = 1.0;
            spawnFood(); // 產生下一個食物
        } else {
            // 沒吃到食物：移除尾巴
            snakeBody.removeLast();
        }

        // 更新棋盤
        refreshBoard();
    }

    // 人類操控用：依照目前 direction 走一步
    public void stepByDirection() {
        step(direction);
    }

    // 重新依 snakeBody / food 更新 board
    private void refreshBoard() {
        if (board == null || board.length == 0 || board[0].length == 0) return;

        int rows = board.length;
        int cols = board[0].length;

        // 清空棋盤
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                board[y][x] = 0;
            }
        }

        // 食物標記為 2
        if (foodX >= 0 && foodX < cols && foodY >= 0 && foodY < rows) {
            board[foodY][foodX] = 2;
        }

        // 蛇身標記為 1
        for (int[] p : snakeBody) {
            int x = p[0];
            int y = p[1];
            if (x >= 0 && x < cols && y >= 0 && y < rows) {
                board[y][x] = 1;
            }
        }
    }

    // 產生不在蛇身上的新食物
    private void spawnFood() {
        if (board == null || board.length == 0 || board[0].length == 0) return;

        int rows = board.length;
        int cols = board[0].length;
        Random rand = new Random();

        while (true) {
            int x = rand.nextInt(cols);
            int y = rand.nextInt(rows);

            boolean onSnake = false;
            for (int[] p : snakeBody) {
                if (p[0] == x && p[1] == y) {
                    onSnake = true;
                    break;
                }
            }

            if (!onSnake) {
                foodX = x;
                foodY = y;
                break;
            }
        }

        // 食物改變後，更新棋盤
        refreshBoard();
    }
}
