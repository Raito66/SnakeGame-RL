package light;

import javax.swing.*;
import java.awt.*;

public class SnakeWindow extends JFrame {

    private static final long serialVersionUID = 1L;

    private final GameState gameState;
    private final SnakePanel snakePanel;

    // 控制列元件
    private final JTextField episodesField;
    private final JTextField maxStepsField;
    private final JButton startRLPlayButton;
    private final JButton rule0PlayButton;
    private final JLabel statusLabel;

    // 遊戲循環
    private Timer gameLoopTimer;
    private int currentEpisode = 0;
    private int maxEpisodes = 0;
    private int maxStepsPerEpisode = 0;
    private int stepCountInEpisode = 0;

    // 速度控制
    private final int baseDelayMs = 80;
    private int currentDelayMs = baseDelayMs;
    private final int minDelayMs = 20;

    public SnakeWindow(GameState state) {
        super("Snake RL Viewer");
        this.gameState = state;

        // 中央棋盤
        this.snakePanel = new SnakePanel(gameState);

        // 上方控制列
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        this.episodesField = new JTextField("20", 5);
        this.maxStepsField = new JTextField("200", 5);

        this.startRLPlayButton = new JButton("開始自動玩(RL)");
        this.rule0PlayButton = new JButton("規0 自動玩(隨機)");

        topPanel.add(new JLabel("局數:"));
        topPanel.add(episodesField);
        topPanel.add(new JLabel("每局最大步數:"));
        topPanel.add(maxStepsField);
        topPanel.add(startRLPlayButton);
        topPanel.add(rule0PlayButton);

        this.statusLabel = new JLabel("請先輸入局數與步數，再選擇模式開始。");

        // 事件綁定
        startRLPlayButton.addActionListener(e -> onStartRLPlay());
        rule0PlayButton.addActionListener(e -> onStartRule0Play());

        // 視窗 layout
        setLayout(new BorderLayout());
        add(topPanel, BorderLayout.NORTH);
        add(snakePanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }

    // 按「開始自動玩(RL)」
    private void onStartRLPlay() {
        // 若已有 Timer 在跑，先停
        if (gameLoopTimer != null && gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
        }

        int episodes;
        int maxSteps;
        try {
            episodes = Integer.parseInt(episodesField.getText().trim());
            maxSteps = Integer.parseInt(maxStepsField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "請在「局數」與「每局最大步數」輸入正整數。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (episodes <= 0 || maxSteps <= 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "局數與每局最大步數都必須大於 0。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        this.maxEpisodes = episodes;
        this.maxStepsPerEpisode = maxSteps;
        this.currentEpisode = 1;
        this.stepCountInEpisode = 0;
        this.currentDelayMs = baseDelayMs;

        gameState.reset();
        statusLabel.setText("RL 自動玩中，第 1 局 / " + maxEpisodes
                + "，速度：" + currentDelayMs + "ms");

        startGameLoopTimer(true);
    }

    // 按「規0 自動玩(隨機)」
    private void onStartRule0Play() {
        if (gameLoopTimer != null && gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
        }

        int episodes;
        int maxSteps;
        try {
            episodes = Integer.parseInt(episodesField.getText().trim());
            maxSteps = Integer.parseInt(maxStepsField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "請在「局數」與「每局最大步數」輸入正整數。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (episodes <= 0 || maxSteps <= 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "局數與每局最大步數都必須大於 0。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        this.maxEpisodes = episodes;
        this.maxStepsPerEpisode = maxSteps;
        this.currentEpisode = 1;
        this.stepCountInEpisode = 0;
        this.currentDelayMs = baseDelayMs;

        gameState.reset();
        statusLabel.setText("規0 自動玩(隨機)中，第 1 局 / " + maxEpisodes
                + "，速度：" + currentDelayMs + "ms");

        startGameLoopTimer(false);
    }

    // 啟動遊戲循環 Timer；useRL\=true 表示使用 Python action.json
    private void startGameLoopTimer(boolean useRL) {
        if (gameLoopTimer != null) {
            gameLoopTimer.stop();
        }

        gameLoopTimer = new Timer(currentDelayMs, e -> {
            // 1\. 走一步
            if (useRL) {
                gameState.stepFromActionFileOrRandom();
            } else {
                gameState.stepRandom();
            }
            stepCountInEpisode++;

            // 2\. 重畫
            snakePanel.repaint();

            // 3\. 檢查一局是否結束
            if (gameState.isDone() || stepCountInEpisode >= maxStepsPerEpisode) {
                currentEpisode++;

                // 每完成 5 局加速一次
                if ((currentEpisode - 1) % 5 == 0) {
                    currentDelayMs = Math.max(minDelayMs, currentDelayMs - 10);
                }

                if (currentEpisode > maxEpisodes) {
                    gameLoopTimer.stop();
                    String modeText = useRL ? "RL 自動玩" : "規0 自動玩(隨機)";
                    statusLabel.setText(modeText + " 結束，共 " + maxEpisodes
                            + " 局，最終速度：" + currentDelayMs + "ms");
                    return;
                }

                // 新的一局
                stepCountInEpisode = 0;
                gameState.reset();
                gameLoopTimer.setDelay(currentDelayMs);

                String modeText = useRL ? "RL 自動玩" : "規0 自動玩(隨機)";
                statusLabel.setText(modeText + "中，第 " + currentEpisode + " 局 / "
                        + maxEpisodes + "，速度：" + currentDelayMs + "ms");
            }
        });

        gameLoopTimer.setInitialDelay(0);
        gameLoopTimer.start();
    }
}
