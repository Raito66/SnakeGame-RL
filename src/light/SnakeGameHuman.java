package light;

public class SnakeGameHuman {

    public static void main(String[] args) {
        // 建構子裡已經初始化棋盤、蛇身、食物
        GameState state = new GameState();

        SnakeWindow window = new SnakeWindow(state);

        while (!state.done) {
            // 人類版：依目前 direction 走一步（方向由 SnakeWindow 處理鍵盤事件改變）
            state.stepByDirection();

            window.repaintBoard();

            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.exit(0);
    }
}
