package light;

import java.util.LinkedList;
import java.util.Random;

public class SnakeGameAI {

    public static void main(String[] args) {

        GameState state = new GameState();
        state.board = new int[20][20];
        state.snakeX = 10;
        state.snakeY = 10;
        state.foodX = 5;
        state.foodY = 5;

        // 型別要跟 GameState 宣告的一樣
        state.snakeBody = new LinkedList<>();
        state.snakeBody.add(new int[]{state.snakeX, state.snakeY});
        state.done = false;

        // 如果 GameState 有 initSnakeBody()，可直接用
        state.initSnakeBody();

        SnakeWindow window = new SnakeWindow(state);
        Random random = new Random();

        while (!state.done) {
            // 隨機選方向：0\=上,1\=右,2\=下,3\=左
            state.direction = random.nextInt(4);

            // 依目前 direction 前進
            state.stepByDirection();

            // 重畫畫面
            window.repaintBoard();

            // 控制速度
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("Game over.");
        System.exit(0);
    }
}
