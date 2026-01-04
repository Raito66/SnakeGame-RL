
package light;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MenuPanel extends JPanel {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    // Reference to the UserManager
    private UserManager userManager;
    // Menu items
    private JMenuItem logoutItem;
    private JMenuItem loginItem;
    // Label to display login status
    private JLabel loginStatusLabel;

    /**
     * Constructor for MenuPanel.
     * 
     * @param userManager Reference to the UserManager.
     */
    public MenuPanel(UserManager userManager) {
        this.userManager = userManager;
        setLayout(new BorderLayout());

        // Set the background color of the panel to gray
        setBackground(Color.lightGray);

        // Button to open the menu
        JButton menuButton = new JButton("Menu");
        menuButton.setPreferredSize(new Dimension(125, 30));

        // Popup menu with various options
        JPopupMenu menu = new JPopupMenu();
        JMenuItem startGameItem = new JMenuItem("Start Game");
        JMenuItem quitGameItem = new JMenuItem("Quit Game");
        loginItem = new JMenuItem("Login");
        JMenuItem registerItem = new JMenuItem("Register");
        logoutItem = new JMenuItem("Logout");
        loginStatusLabel = new JLabel();

        // Set the size of menu items
        Dimension menuItemSize = new Dimension(menuButton.getPreferredSize().width - 2, menuButton.getPreferredSize().height);
        startGameItem.setPreferredSize(menuItemSize);
        quitGameItem.setPreferredSize(menuItemSize);
        loginItem.setPreferredSize(menuItemSize);
        registerItem.setPreferredSize(menuItemSize);
        logoutItem.setPreferredSize(menuItemSize);

        // Add action listeners to menu items
        startGameItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (userManager.isLoggedIn()) {
                    SnakeGame.startGame();
                } else {
                    JOptionPane.showMessageDialog(null, "Please log in to start the game.");
                }
            }
        });

        quitGameItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SnakeGame.quitGame();
            }
        });

        loginItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showLoginDialog();
            }
        });

        registerItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showRegisterDialog();
            }
        });

        logoutItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                userManager.logoutUser();
                logoutItem.setVisible(false);
                loginItem.setVisible(true);
                loginStatusLabel.setText("");
                JOptionPane.showMessageDialog(null, "Logged out successfully.");
            }
        });

        // Add menu items to the popup menu
        menu.add(startGameItem);
        menu.add(quitGameItem);
        menu.add(loginItem);
        menu.add(registerItem);
        menu.add(logoutItem);

        // Initially hide the logout item
        logoutItem.setVisible(false);

        // Show the popup menu when the menu button is clicked
        menuButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menu.show(menuButton, 0, menuButton.getHeight());
            }
        });

        // Add components to the panel
        add(menuButton, BorderLayout.WEST);
        add(loginStatusLabel, BorderLayout.EAST);
    }

    /**
     * Displays a dialog for user login.
     */
    private void showLoginDialog() {
        JTextField usernameField = new JTextField(10);
        JPasswordField passwordField = new JPasswordField(10);

        JPanel panel = new JPanel();
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(Box.createHorizontalStrut(15)); // a spacer
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Login", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            if (userManager.loginUser(username, password)) {
                logoutItem.setVisible(true);
                loginItem.setVisible(false);
                loginStatusLabel.setText("Login: " + username);
                JOptionPane.showMessageDialog(null, "Logged in as " + username);
            } else {
                JOptionPane.showMessageDialog(null, "Invalid username or password.");
            }
        }
    }

    /**
     * Displays a dialog for user registration.
     */
    private void showRegisterDialog() {
        JTextField usernameField = new JTextField(10);
        JPasswordField passwordField = new JPasswordField(10);

        JPanel panel = new JPanel();
        panel.add(new JLabel("Username:"));
        panel.add(usernameField);
        panel.add(Box.createHorizontalStrut(15)); // a spacer
        panel.add(new JLabel("Password:"));
        panel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Register", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            if (userManager.registerUser(username, password)) {
                JOptionPane.showMessageDialog(null, "Registered as " + username);
            } else {
                JOptionPane.showMessageDialog(null, "Username already exists.");
            }
        }
    }
}
