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

        this.startRLPlayButton = new JButton("開始訓練+自動玩(RL)");
        this.rule0PlayButton = new JButton("規0 自動玩(隨機)");

        topPanel.add(new JLabel("局數(訓練+重播):"));
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

    /** 按「開始訓練+自動玩(RL)」 */
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
                    "請輸入正確的整數（局數與每局最大步數）。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (episodes <= 0 || maxSteps <= 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "局數與每局最大步數必須大於 0。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // 先依 Viewer 的輸入寫 config.json 並啟動 Python 訓練（同步等待）
        statusLabel.setText("正在訓練 DQN，請稍候...");
        startRLPlayButton.setEnabled(false);
        rule0PlayButton.setEnabled(false);

        // 避免卡住 EDT，用背景執行緒做訓練
        new Thread(() -> {
            boolean ok = PythonTrainerLauncher.runTrainingWithConfig(this, episodes, maxSteps);

            SwingUtilities.invokeLater(() -> {
                startRLPlayButton.setEnabled(true);
                rule0PlayButton.setEnabled(true);

                if (!ok) {
                    statusLabel.setText("訓練失敗或被中斷，請查看 console。");
                    return;
                }

                // 訓練成功後，設定 Viewer 自己的播放參數
                this.maxEpisodes = episodes;
                this.maxStepsPerEpisode = maxSteps;
                this.currentEpisode = 1;
                this.stepCountInEpisode = 0;
                this.currentDelayMs = baseDelayMs;

                gameState.reset();
                statusLabel.setText("RL 自動玩中，第 1 局 / " + maxEpisodes
                        + "，每局最多 " + maxStepsPerEpisode + " 步。");

                // 這裡假設你已經另外啟動 `agent/eval_play.py`，
                // 它會一直寫 `action.json`，GameState.stepFromActionFileOrRandom() 會讀。
                startGameLoopTimer(true);
            });
        }).start();
    }

    /** 按「規0 自動玩(隨機)」──不觸發訓練，只用 Java 亂數走 */
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
                    "請輸入正確的整數（局數與每局最大步數）。",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        if (episodes <= 0 || maxSteps <= 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "局數與每局最大步數必須大於 0。",
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
                + "，每局最多 " + maxStepsPerEpisode + " 步。");

        startGameLoopTimer(false);
    }

    /**
     * 啟動遊戲循環 Timer；
     * \- `useRL\=true`：每步呼叫 `stepFromActionFileOrRandom()`（若有 `action.json` 則用 RL 動作）
     * \- `useRL\=false`：每步呼叫 `stepRandom()`
     */
    private void startGameLoopTimer(boolean useRL) {
        if (gameLoopTimer != null) {
            gameLoopTimer.stop();
        }

        gameLoopTimer = new Timer(currentDelayMs, e -> {
            if (currentEpisode > maxEpisodes) {
                gameLoopTimer.stop();
                statusLabel.setText("全部 " + maxEpisodes + " 局已結束。");
                return;
            }

            if (stepCountInEpisode >= maxStepsPerEpisode || gameState.isDone()) {
                // 本局結束，進入下一局
                currentEpisode++;
                if (currentEpisode > maxEpisodes) {
                    gameLoopTimer.stop();
                    statusLabel.setText("全部 " + maxEpisodes + " 局已結束。");
                    return;
                }
                stepCountInEpisode = 0;
                gameState.reset();
                statusLabel.setText((useRL ? "RL 自動玩中" : "規0 自動玩(隨機)中")
                        + "，第 " + currentEpisode + " 局 / " + maxEpisodes
                        + "，每局最多 " + maxStepsPerEpisode + " 步。");
            } else {
                // 本局尚未結束，走一步
                if (useRL) {
                    // 優先依 `action.json`（eval_play.py 寫的）走一步，沒有檔案就隨機
                    gameState.stepFromActionFileOrRandom();
                } else {
                    gameState.stepRandom();
                }
                stepCountInEpisode++;
            }

            snakePanel.repaint();
        });

        gameLoopTimer.setInitialDelay(0);
        gameLoopTimer.start();
    }
}
