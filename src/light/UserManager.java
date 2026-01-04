
package light;

import java.util.*;

public class UserManager {
    // Set to store registered users with their passwords
    private Set<String> registeredUsers = new HashSet<>();
    // Map to store user scores
    private Map<String, Integer> userScores = new HashMap<>();
    // Currently logged-in user
    private String loggedInUser = null;

    /**
     * Registers a new user with a username and password.
     * 
     * @param username The username of the new user.
     * @param password The password of the new user.
     * @return true if the user was successfully registered, false if the user
     *         already exists.
     */
    public boolean registerUser(String username, String password) {
        return registeredUsers.add(username + ":" + password);
    }

    /**
     * Logs in a user with a username and password.
     * 
     * @param username The username of the user.
     * @param password The password of the user.
     * @return true if the login was successful, false otherwise.
     */
    public boolean loginUser(String username, String password) {
        if (registeredUsers.contains(username + ":" + password)) {
            loggedInUser = username;
            return true;
        }
        return false;
    }

    /**
     * Logs out the currently logged-in user.
     */
    public void logoutUser() {
        loggedInUser = null;
    }

    /**
     * Checks if a user is currently logged in.
     * 
     * @return true if a user is logged in, false otherwise.
     */
    public boolean isLoggedIn() {
        return loggedInUser != null;
    }

    /**
     * Gets the username of the currently logged-in user.
     * 
     * @return The username of the logged-in user, or null if no user is logged in.
     */
    public String getLoggedInUser() {
        return loggedInUser;
    }

    /**
     * Updates the score of the currently logged-in user.
     * 
     * @param score The new score to be updated.
     */
    public void updateUserScore(int score) {
        if (loggedInUser != null) {
            userScores.put(loggedInUser, score);
        }
    }

    /**
     * Gets the scores of all users, sorted in descending order, limited to the top 10.
     * 
     * @return A map of usernames to their scores.
     */
    public Map<String, Integer> getUserScores() {
        List<Map.Entry<String, Integer>> sortedScores = new ArrayList<>(userScores.entrySet());
        sortedScores.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        Map<String, Integer> topScores = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(10, sortedScores.size()); i++) {
            Map.Entry<String, Integer> entry = sortedScores.get(i);
            topScores.put(entry.getKey(), entry.getValue());
        }
        return topScores;
    }
}
