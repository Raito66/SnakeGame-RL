
package light;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class GamePanel extends JPanel implements ActionListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	// Constants for game configuration
	private final int TILE_SIZE = 20;
	private int GAME_WIDTH = 30;
	private int GAME_HEIGHT = 30;
	private int DELAY = 100; // Update interval in milliseconds

	// Game state variables
	private LinkedList<Point> snake = new LinkedList<>(); // Snake body parts
	private Point food; // Food position
	private char direction = 'R'; // Initial direction is right
	private boolean running = false; // Is the game running
	private boolean isGameOver = false; // Is it game over
	private Timer timer; // Timer to update the game
	private UserManager userManager; // Reference to UserManager

	// Add a new variable to track the current level
	private int level = 1;
	private ArrayList<Point> obstacles = new ArrayList<>();

	/**
	 * Constructor for GamePanel.
	 * 
	 * @param userManager Reference to the UserManager.
	 */
	public GamePanel(UserManager userManager) {
		this.userManager = userManager;
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(GAME_WIDTH * TILE_SIZE, GAME_HEIGHT * TILE_SIZE));
		setBackground(Color.BLACK);
		setFocusable(true); // Ensure the panel can receive keyboard input
		addKeyListener(new SnakeKeyListener());

		// Create and add the menu panel
		JPanel menuPanel = new JPanel();
		menuPanel.setPreferredSize(new Dimension(GAME_WIDTH * TILE_SIZE, 0));
		menuPanel.setBackground(Color.BLACK); // Set the background color of the menu
		add(menuPanel, BorderLayout.NORTH);

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				Dimension newSize = getSize();
				GAME_WIDTH = newSize.width / TILE_SIZE;
				GAME_HEIGHT = (newSize.height) / TILE_SIZE;
				repaint();
			}
		});
	}

	/**
	 * Starts the game by initializing the snake and food positions, and starting
	 * the timer.
	 */

	public void startGame() {
		// Show a dialog to select speed
		String[] options = { "Slow", "Medium", "Fast" };
		int choice = JOptionPane.showOptionDialog(this, "Select Speed", "Speed Selection", JOptionPane.DEFAULT_OPTION,
				JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);

		// Set delay based on user choice
		switch (choice) {
		case 0 -> DELAY = 200; // Slow
		case 1 -> DELAY = 100; // Medium
		case 2 -> DELAY = 50; // Fast
		default -> DELAY = 100; // Default to Medium if no choice
		}

		// Initialize snake position
		snake.clear();
		snake.add(new Point(5, 5)); // Initial snake head position
		snake.add(new Point(4, 5)); // Initial snake body
		snake.add(new Point(3, 5)); // Initial snake body

		spawnFood(); // Generate food
		direction = 'R'; // Initial direction is right
		running = true;
		isGameOver = false;
		level = 1; // Reset level to 1
		obstacles.clear(); // Clear obstacles

		// Start timer to update the game every DELAY milliseconds
		if (timer == null) {
			timer = new Timer(DELAY, this);
			timer.start();
		} else {
			timer.setDelay(DELAY);
			timer.restart();
		}

		requestFocusInWindow(); // Ensure focus remains on the panel after starting the game
	}

	private void spawnObstacles() {
		Random rand = new Random();
		obstacles.clear();
		for (int i = 0; i < 5; i++) {
			int x = rand.nextInt(GAME_WIDTH);
			int y = rand.nextInt(GAME_HEIGHT);
			obstacles.add(new Point(x, y));
		}
	}

	/**
	 * Generates a new food position randomly within the game area.
	 */
	private void spawnFood() {
		Random rand = new Random();
		int x = rand.nextInt(GAME_WIDTH); // Ensure food does not spawn outside the border
		int y = rand.nextInt(GAME_HEIGHT); // Ensure food does not spawn outside the border
		food = new Point(x, y);
	}

	private void randomizeObstacles() {
		Random rand = new Random();
		for (int i = 0; i < obstacles.size(); i++) {
			int x = rand.nextInt(GAME_WIDTH);
			int y = rand.nextInt(GAME_HEIGHT);
			obstacles.set(i, new Point(x, y));
		}
	}

	/**
	 * Moves the snake in the current direction and checks for collisions and food
	 * consumption.
	 */
	private void move() {
		Point head = snake.getFirst();
		Point newHead = new Point(head);

		// Change snake head position based on direction
		switch (direction) {
		case 'U' -> newHead.y--;
		case 'D' -> newHead.y++;
		case 'L' -> newHead.x--;
		case 'R' -> newHead.x++;
		}

		// Check if snake hits the border
		if (newHead.x < 0 || newHead.x >= GAME_WIDTH || newHead.y < 0 || newHead.y >= GAME_HEIGHT + 1) {
			running = false;
			isGameOver = true;
			userManager.updateUserScore(snake.size());
			return;
		}

		// Check if snake eats food
		if (newHead.equals(food)) {
			snake.addFirst(newHead); // Grow snake head
			spawnFood(); // Generate new food
			if (level == 2) {
				randomizeObstacles(); // Randomize obstacles in level 2
			}
		} else {
			snake.addFirst(newHead); // Move snake head
			snake.removeLast(); // Remove snake tail
		}

		// Check if snake reaches length 15 to enter level 2
		if (level == 1 && snake.size() >= 15) {
			level = 2;
			spawnObstacles();
		}

		// Check if snake hits an obstacle
		if (level == 2) {
			for (Point obstacle : obstacles) {
				if (newHead.equals(obstacle)) {
					running = false;
					isGameOver = true;
					userManager.updateUserScore(snake.size());
					return;
				}
			}
		}
	}

	/**
	 * Checks for collisions with the wall or the snake itself.
	 *
	 * @return true if a collision is detected, false otherwise.
	 */
	private boolean checkCollision() {
		Point head = snake.getFirst();

		// Wall collision
		if (head.x < 0 || head.x >= GAME_WIDTH || head.y < 0 || head.y >= GAME_HEIGHT + 1) {
			running = false;
			isGameOver = true;
			userManager.updateUserScore(snake.size());
			return true;
		}

		// Self-collision
		for (int i = 1; i < snake.size(); i++) {
			if (head.equals(snake.get(i))) {
				running = false;
				isGameOver = true;
				userManager.updateUserScore(snake.size());
				return true;
			}
		}

		return false;
	}

	/**
	 * Paints the game components (snake, food, and messages) on the panel.
	 * 
	 * @param g Graphics object used for drawing.
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (running) {
			// Draw food
			g.setColor(Color.BLUE);
			g.fillRect(food.x * TILE_SIZE, food.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);

			// Draw snake
			Graphics2D g2d = (Graphics2D) g;
			for (int i = 0; i < snake.size(); i++) {
				Point p = snake.get(i);
				if (i == 0) {
					// Draw the head as a more realistic snake head
					g2d.setColor(Color.BLUE); // Snake head color
					int[] xPoints = new int[4];
					int[] yPoints = new int[4];
					switch (direction) {
					case 'U' -> {
						xPoints = new int[] { p.x * TILE_SIZE + TILE_SIZE / 2, p.x * TILE_SIZE,
								p.x * TILE_SIZE + TILE_SIZE, p.x * TILE_SIZE + TILE_SIZE / 2 };
						yPoints = new int[] { p.y * TILE_SIZE, p.y * TILE_SIZE + TILE_SIZE / 2,
								p.y * TILE_SIZE + TILE_SIZE / 2, p.y * TILE_SIZE };
					}
					case 'D' -> {
						xPoints = new int[] { p.x * TILE_SIZE + TILE_SIZE / 2, p.x * TILE_SIZE,
								p.x * TILE_SIZE + TILE_SIZE, p.x * TILE_SIZE + TILE_SIZE / 2 };
						yPoints = new int[] { p.y * TILE_SIZE + TILE_SIZE, p.y * TILE_SIZE + TILE_SIZE / 2,
								p.y * TILE_SIZE + TILE_SIZE / 2, p.y * TILE_SIZE + TILE_SIZE };
					}
					case 'L' -> {
						xPoints = new int[] { p.x * TILE_SIZE, p.x * TILE_SIZE + TILE_SIZE / 2,
								p.x * TILE_SIZE + TILE_SIZE / 2, p.x * TILE_SIZE };
						yPoints = new int[] { p.y * TILE_SIZE + TILE_SIZE / 2, p.y * TILE_SIZE,
								p.y * TILE_SIZE + TILE_SIZE, p.y * TILE_SIZE + TILE_SIZE / 2 };
					}
					case 'R' -> {
						xPoints = new int[] { p.x * TILE_SIZE + TILE_SIZE, p.x * TILE_SIZE + TILE_SIZE / 2,
								p.x * TILE_SIZE + TILE_SIZE / 2, p.x * TILE_SIZE + TILE_SIZE };
						yPoints = new int[] { p.y * TILE_SIZE + TILE_SIZE / 2, p.y * TILE_SIZE,
								p.y * TILE_SIZE + TILE_SIZE, p.y * TILE_SIZE + TILE_SIZE / 2 };
					}
					}
					g2d.fillPolygon(xPoints, yPoints, 4);
				} else {
					g2d.setColor(Color.GREEN); // Snake body color
					g2d.fillRect(p.x * TILE_SIZE, p.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
				}
			}

			// Draw obstacles if level 2
			if (level == 2) {
				g.setColor(Color.RED);
				for (Point obstacle : obstacles) {
					g.fillRect(obstacle.x * TILE_SIZE, obstacle.y * TILE_SIZE, TILE_SIZE, TILE_SIZE);
				}
			}
		} else {
			String message;
			if (isGameOver) {
				message = "Game Over!";
				g.setColor(Color.WHITE);
				g.setFont(new Font("Helvetica", Font.BOLD, 36));
				FontMetrics metrics = getFontMetrics(g.getFont());
				g.drawString(message, (getWidth() - metrics.stringWidth(message)) / 2, getHeight() / 2);

				// Draw leaderboard
				g.setFont(new Font("Helvetica", Font.PLAIN, 18));
				final int[] y = { getHeight() / 2 + 50 };
				int x = (getWidth() - metrics.stringWidth("Leaderboard:")) / 2;
				g.drawString("Leaderboard:", x, y[0]);
				y[0] += g.getFontMetrics().getHeight();

				Map<String, Integer> scores = userManager.getUserScores();
				final int[] rank = { 1 };
				scores.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
						.forEach(entry -> {
							String scoreEntry = rank[0] + ". " + entry.getKey() + ": " + entry.getValue();
							g.drawString(scoreEntry, x, y[0]);
							y[0] += g.getFontMetrics().getHeight();
							rank[0]++;
						});
			} else {
				message = "Press 'Start Game' to begin the game.";
				g.setColor(Color.WHITE);
				g.setFont(new Font("Helvetica", Font.BOLD, 36));
				FontMetrics metrics = getFontMetrics(g.getFont());
				g.drawString(message, (getWidth() - metrics.stringWidth(message)) / 2, getHeight() / 2);

				// Add creator's name below the main message
				String creatorName = "Created by LIGHT HUNG";
				g.setFont(new Font("Helvetica", Font.PLAIN, 18));
				FontMetrics creatorMetrics = getFontMetrics(g.getFont());
				g.drawString(creatorName, (getWidth() - creatorMetrics.stringWidth(creatorName)) / 2,
						getHeight() / 2 + 40);
			}
		}
	}

	/**
	 * Handles the action events triggered by the timer.
	 * 
	 * @param e ActionEvent object.
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		if (running) {
			move();
			if (checkCollision()) {
				running = false;
			}
			repaint();
		} else {
			repaint();
		}
	}

	/**
	 * Inner class to handle keyboard inputs for controlling the snake.
	 */
	private class SnakeKeyListener extends KeyAdapter {
		@Override
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();

			// Control snake direction
			if (key == KeyEvent.VK_UP && direction != 'D') {
				direction = 'U';
			} else if (key == KeyEvent.VK_DOWN && direction != 'U') {
				direction = 'D';
			} else if (key == KeyEvent.VK_LEFT && direction != 'R') {
				direction = 'L';
			} else if (key == KeyEvent.VK_RIGHT && direction != 'L') {
				direction = 'R';
			}

			// Press 'R' to restart the game
			if (key == KeyEvent.VK_R && !running) {
				startGame();
				requestFocusInWindow(); // Ensure focus remains on the panel after restart
			}
		}
	}
}
