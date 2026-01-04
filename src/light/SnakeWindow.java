package light;

import javax.swing.*;

public class SnakeWindow {

    private final JFrame frame;
    private final SnakePanel panel;

    public SnakeWindow(GameState state) {
        frame = new JFrame("Snake RL Viewer");
        panel = new SnakePanel(state);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void repaintBoard() {
        panel.repaint();
    }
}
