package light;

import javax.swing.*;
import java.awt.*;

/**
 * 繪製貪吃蛇棋盤：視覺上按面板大小縮放，但遊戲邏輯仍以 grid index 判定。
 */
public class SnakePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final GameState gameState;
    private static final int DEFAULT_PIXELS = 600; // 視窗預設像素大小（固定）

    public SnakePanel(GameState gameState) {
        this.gameState = gameState;
        // 固定面板偏好大小為 600x600（保持 UI 尺寸穩定，不影響訓練數據）
        setPreferredSize(new Dimension(DEFAULT_PIXELS, DEFAULT_PIXELS));
        setBackground(Color.BLACK);
    }

    public int[][] getBoard() {
        return gameState.getBoard();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        int[][] board = getBoard();
        if (board == null) return;
        int rows = board.length;
        if (rows == 0) return;
        int cols = board[0].length;

        int panelW = getWidth();
        int panelH = getHeight();

        // Integer division + remainder distribution to ensure exact coverage
        int baseW = panelW / cols;
        int remW = panelW % cols;
        int[] xPos = new int[cols + 1];
        xPos[0] = 0;
        for (int i = 0; i < cols; i++) {
            int w = baseW + (i < remW ? 1 : 0);
            xPos[i + 1] = xPos[i] + w;
        }

        int baseH = panelH / rows;
        int remH = panelH % rows;
        int[] yPos = new int[rows + 1];
        yPos[0] = 0;
        for (int i = 0; i < rows; i++) {
            int h = baseH + (i < remH ? 1 : 0);
            yPos[i + 1] = yPos[i] + h;
        }

        // fill cells
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int v = board[y][x];
                switch (v) {
                    case 1:
                        g.setColor(Color.GREEN);
                        break;
                    case 2:
                        g.setColor(Color.RED);
                        break;
                    case 0:
                    default:
                        g.setColor(Color.DARK_GRAY);
                        break;
                }
                int px = xPos[x];
                int py = yPos[y];
                int pw = xPos[x + 1] - px;
                int ph = yPos[y + 1] - py;
                g.fillRect(px, py, pw, ph);
            }
        }

        // Diagnostic: draw magenta border around grid extents to confirm coverage
        g.setColor(Color.MAGENTA);
        int gridX = xPos[0];
        int gridY = yPos[0];
        int gridW = xPos[cols] - gridX;
        int gridH = yPos[rows] - gridY;
        g.drawRect(gridX, gridY, Math.max(0, gridW - 1), Math.max(0, gridH - 1));

        // 畫格線
        g.setColor(new Color(0x44, 0x44, 0x44, 0x80));
        for (int i = 0; i <= cols; i++) {
            int x = xPos[i];
            g.drawLine(x, 0, x, panelH);
        }
        for (int i = 0; i <= rows; i++) {
            int y = yPos[i];
            g.drawLine(0, y, panelW, y);
        }
    }
}
