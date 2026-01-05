package light;

import javax.swing.*;
import java.awt.*;

public class SocketSnakePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // 預設面板大小（與 SnakePanel 保持一致）
    private static final int DEFAULT_PIXELS = 600;

    // 目前的棋盤資料，由 SocketSnakeViewer 更新
    private int[][] board = null;

    public SocketSnakePanel() {
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(DEFAULT_PIXELS, DEFAULT_PIXELS));
    }

    /**
     * 更新棋盤資料，供 SocketSnakeViewer 呼叫：
     * snakePanel.updateBoard(board);
     */
    public void updateBoard(int[][] newBoard) {
        this.board = newBoard;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;

        if (board == null || board.length == 0 || board[0].length == 0) {
            return;
        }

        int h = board.length;
        int w = board[0].length;

        int panelW = getWidth();
        int panelH = getHeight();

        int baseW = panelW / w;
        int remW = panelW % w;
        int[] xPos = new int[w + 1];
        xPos[0] = 0;
        for (int i = 0; i < w; i++) {
            int colw = baseW + (i < remW ? 1 : 0);
            xPos[i + 1] = xPos[i] + colw;
        }

        int baseH = panelH / h;
        int remH = panelH % h;
        int[] yPos = new int[h + 1];
        yPos[0] = 0;
        for (int i = 0; i < h; i++) {
            int rowh = baseH + (i < remH ? 1 : 0);
            yPos[i + 1] = yPos[i] + rowh;
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cell = board[y][x];
                switch (cell) {
                    case 0 -> g.setColor(Color.BLACK);
                    case 1 -> g.setColor(Color.GREEN);
                    case 2 -> g.setColor(Color.RED);
                    default -> g.setColor(Color.DARK_GRAY);
                }
                int px = xPos[x];
                int py = yPos[y];
                int pw = xPos[x + 1] - px;
                int ph = yPos[y + 1] - py;
                g.fillRect(px, py, pw, ph);
            }
        }

        // Diagnostic border
        g.setColor(Color.MAGENTA);
        int gridW = xPos[w] - xPos[0];
        int gridH = yPos[h] - yPos[0];
        g.drawRect(xPos[0], yPos[0], Math.max(0, gridW - 1), Math.max(0, gridH - 1));

        // 畫格線
        g.setColor(new Color(0x44, 0x44, 0x44, 0x80));
        for (int i = 0; i <= w; i++) {
            int x = xPos[i];
            g.drawLine(x, 0, x, panelH);
        }
        for (int i = 0; i <= h; i++) {
            int y = yPos[i];
            g.drawLine(0, y, panelW, y);
        }
    }
}
