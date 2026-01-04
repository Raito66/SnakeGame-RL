package light;

import javax.swing.*;
import java.awt.*;

public class SnakePanel extends JPanel {

    private final GameState state;
    private final int cellSize = 20;

    public SnakePanel(GameState state) {
        this.state = state;
        int w = state.board.length * cellSize;
        int h = state.board[0].length * cellSize;
        setPreferredSize(new Dimension(w, h));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 背景
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        // 食物
        g.setColor(Color.RED);
        g.fillRect(state.foodX * cellSize, state.foodY * cellSize, cellSize, cellSize);

        // 蛇身
        g.setColor(Color.GREEN);
        if (state.snakeBody != null) {
            for (int[] body : state.snakeBody) {
                g.fillRect(body[0] * cellSize, body[1] * cellSize, cellSize, cellSize);
            }
        }

        // 蛇頭（黃色）
        g.setColor(Color.YELLOW);
        g.fillRect(state.snakeX * cellSize, state.snakeY * cellSize, cellSize, cellSize);
    }
}