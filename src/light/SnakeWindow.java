package light;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class SnakeWindow extends JFrame {

    private final GameState gameState;
    private final SnakePanel snakePanel;
    private final JTextField episodesField;
    private final JButton trainButton;
    private final JTextArea reportArea;

    // JSON 設定檔路徑
    private static final String CONFIG_PATH = "agent/config.json";

    public SnakeWindow(GameState state) {
        this.gameState = state;

        setTitle("Snake RL Viewer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        // 中間：棋盤畫面
        snakePanel = new SnakePanel(gameState);
        add(snakePanel, BorderLayout.CENTER);

        // 上方：訓練控制列
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("訓練局數(episodes):"));
        episodesField = new JTextField("100", 6);
        topPanel.add(episodesField);

        trainButton = new JButton("開始訓練");
        trainButton.addActionListener(e -> onStartTrain());
        topPanel.add(trainButton);

        add(topPanel, BorderLayout.NORTH);

        // 下方：學習報告輸出
        reportArea = new JTextArea(5, 40);
        reportArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(reportArea);
        add(scroll, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);

        // 啟動固定刷新遊戲的 Timer
        startGameLoopTimer();
    }

    /** 使用 Swing Timer，每隔固定時間讓蛇走一步。 */
    private void startGameLoopTimer() {
        int delayMs = 80; // 每 80ms 一步
        new Timer(delayMs, e -> {
            if (gameState.isDone()) {
                gameState.reset();
            } else {
                // 優先吃 Python 的 action，沒有就隨機
                gameState.stepFromActionFileOrRandom();
            }
            snakePanel.repaint();
        }).start();
    }

    private void onStartTrain() {
        String episodesStr = episodesField.getText().trim();
        int episodes = 100;
        try {
            if (!episodesStr.isEmpty()) {
                episodes = Integer.parseInt(episodesStr);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "請輸入正確的整數 episodes",
                    "輸入錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        // 1\) 寫入 config.json 給 Python 使用
        writeConfigJson(episodes);

        // 2\) 背景執行 Python 訓練
        runPythonTrainAsync();

        // 3\) 在報告區顯示提示
        appendReport("開始訓練：episodes=" + episodes + "\n");
    }

    private void writeConfigJson(int episodes) {
        String json = "{\n" +
                "  \"episodes\": " + episodes + ",\n" +
                "  \"max_steps\": 200\n" +
                "}\n";
        try (FileWriter fw = new FileWriter(CONFIG_PATH)) {
            fw.write(json);
        } catch (IOException e) {
            appendReport("寫入 `agent/config.json` 失敗：" + e.getMessage() + "\n");
        }
    }

    private void runPythonTrainAsync() {
        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("python", "agent/train_agent.py");
                pb.redirectErrorStream(true);
                Process p = pb.start();

                try (BufferedReader br =
                             new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String logLine = line;
                        SwingUtilities.invokeLater(() -> appendReport(logLine + "\n"));
                    }
                }

                int exitCode = p.waitFor();
                SwingUtilities.invokeLater(
                        () -> appendReport("訓練結束，exitCode=" + exitCode + "\n")
                );
            } catch (Exception ex) {
                SwingUtilities.invokeLater(
                        () -> appendReport("執行 `agent/train_agent.py` 失敗：" + ex.getMessage() + "\n")
                );
            }
        }).start();
    }

    private void appendReport(String text) {
        reportArea.append(text);
        reportArea.setCaretPosition(reportArea.getDocument().getLength());
    }

    public void repaintBoard() {
        snakePanel.repaint();
    }
}
