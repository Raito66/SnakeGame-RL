package light;

import javax.swing.SwingUtilities;

public class SnakeGameAI {

    public static void main(String[] args) {
        GameState state = new GameState();
        SwingUtilities.invokeLater(() -> new SnakeWindow(state));
    }
}
