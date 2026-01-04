
package light;

import javax.swing.*;
import java.awt.*;

public class SnakeGame {
    // Main frame of the game
    private static JFrame frame;
    // Panel where the game is played
    private static GamePanel gamePanel;
    // Panel for the menu
    private static MenuPanel menuPanel;
    // Manager for user-related operations
    private static UserManager userManager;

    /**
     * Main method to start the Snake game.
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        // Initialize the main frame
        frame = new JFrame("Snake Game");
        // Initialize the user manager
        userManager = new UserManager();
		// Initialize the game panel with the user manager
		gamePanel = new GamePanel(userManager);
		// Initialize the menu panel with the user manager
		menuPanel = new MenuPanel(userManager);

		// Set default close operation for the frame
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// Set the size of the frame
		frame.setSize(800, 800);
		// Center the frame on the screen
		frame.setLocationRelativeTo(null);
		// Make the frame visible
		frame.setVisible(true);

		// Show the menu panel
		showMenu();
	}

	/**
	 * Displays the menu panel in the frame.
	 */
	public static void showMenu() {
		// Remove all components from the frame's content pane
		frame.getContentPane().removeAll();
		// Set the layout of the frame to BorderLayout
		frame.setLayout(new BorderLayout());
		// Add the menu panel to the north region of the frame
		frame.add(menuPanel, BorderLayout.NORTH);
		// Add the game panel to the center region of the frame
		frame.add(gamePanel, BorderLayout.CENTER);
		// Revalidate the frame to apply the changes
		frame.revalidate();
		// Repaint the frame to update the display
		frame.repaint();
	}

	/**
	 * Starts the game by calling the startGame method of the game panel.
	 */
	public static void startGame() {
		gamePanel.startGame();
	}

	/**
	 * Quits the game by exiting the application.
	 */
	public static void quitGame() {
		System.exit(0);
	}
}
