package light;

import javax.swing.*;
import java.awt.*;

public class SnakePanel extends JPanel {

    private final GameState gameState;
    private static final int CELL_SIZE = 20; // 每一格像素

    public SnakePanel(GameState state) {
        this.gameState = state;
        int size = gameState.getBoardSize();
        setPreferredSize(new Dimension(size * CELL_SIZE, size * CELL_SIZE));
        setBackground(Color.BLACK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int size = gameState.getBoardSize();
        int[][] board = gameState.getBoard();

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int cell = board[y][x];
                switch (cell) {
                    case 0:
                        g.setColor(Color.BLACK);   // 空
                        break;
                    case 1:
                        g.setColor(Color.GREEN);   // 蛇
                        break;
                    case 2:
                        g.setColor(Color.RED);     // 食物
                        break;
                    default:
                        g.setColor(Color.DARK_GRAY);
                }
                g.fillRect(x * CELL_SIZE, y * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }
        }
    }
}
