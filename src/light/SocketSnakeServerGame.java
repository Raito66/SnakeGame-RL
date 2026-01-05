package light;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;
import java.util.Random; // 新增 Random import

// SocketSnakeServerGame：
// 這個類同時包含 Swing 視窗（檢視與控制）與後台的 socket server 管理。
// 程式流程：
// - 建構子會建立 UI，並在背景執行緒啟動 socket server（等待 Python client 連線）
// - 當 client 連線並收到 INIT 後，會（若使用者已按開始或自動）啟動遊戲循環
// - 遊戲循環使用 Swing Timer，週期性發送 STATE 給 Python，接收 ACTION，並更新畫面
public class SocketSnakeServerGame extends JFrame {

    private static final long serialVersionUID = 1L;
    // 與 Python 溝通的埠號
    private static final int PORT = 5000;

    private static final String AGENT_CONFIG_REL = "agent" + File.separator + "config.json";
    private final Gson gson = new Gson();

    // 遊戲狀態與 UI 元件
    private final GameState gameState;           // 遊戲邏輯物件（含 board, snake, food, reward）
    private final SocketSnakePanel snakePanel;   // 顯示盤面用的自訂 JPanel
    private final JLabel statusLabel;            // 下方狀態列
    private final JTextField episodesField;      // 輸入局數的欄位
    private final JTextField maxStepsField;     // 輸入每局最大步數（0 表示無上限）
    private final JTextField totalTimestepsField; // 可直接指定 total_timesteps（可選）
    private final JButton startButton;           // 開始按鈕
    private final JButton stopButton;            // 停止按鈕（新增）
    // 將下面三個按鈕提升為欄位，以便在 connect 後啟用
    private final JButton restartBtn;
    private final JButton speedUpBtn;
    private final JButton slowDownBtn;

    // Socket 與 Timer
    private SocketSnakeServer socketServer;      // 伺服端物件（負責 accept、send/read）
    private Timer gameLoopTimer;                 // 控制遊戲步進的 Swing Timer

    // 隨機 generator（在 Python 未回應時 fallback 用）
    private final Random rng = new Random();

    // 遊戲控制參數
    private int stepDelayMs = 50;                // 步進延遲 (ms)
    private int maxEpisodes = 1;                 // 要跑的局數
    private int currentEpisode = 0;              // 目前第幾局
    private boolean isRunning = false;           // 是否正在跑遊戲
    private int uiMaxSteps = 0;                 // 由 UI 輸入的每局最大步數 (0 表示無上限)
    private int stepCountInEpisode = 0;         // 本局已執行的步數

    // 每局累積 reward（方便在局結束時列印）
    private double episodeReward = 0.0;

    // 若 UI 要啟動 Python 訓練程式，記錄其 Process
    private Process pythonTrainerProcess = null;

    // 建構子：建立 UI 元件並立即在背景啟動 socket server 等待連線
    public SocketSnakeServerGame() {
        super("Socket Snake Server Game");

        this.gameState = new GameState();
        this.snakePanel = new SocketSnakePanel();
        this.statusLabel = new JLabel("請輸入局數並按開始。");

        // 控制列（局數輸入、開始、重新開始、加速/減速）
        // 提升 controlPanel 為欄位，方便計算最終 frame 大小
        JPanel controlPanel = new JPanel();
        controlPanel.add(new JLabel("局數:"));
        episodesField = new JTextField("10", 5);
        controlPanel.add(episodesField);
        controlPanel.add(new JLabel("每局最大步數:"));
        maxStepsField = new JTextField("0", 6); // 0 = 無上限
        controlPanel.add(maxStepsField);
        controlPanel.add(new JLabel("總步數(total_timesteps, 可選):"));
        totalTimestepsField = new JTextField("", 8);
        controlPanel.add(totalTimestepsField);
        startButton = new JButton("開始");
        controlPanel.add(startButton);

        // 新增停止按鈕
        stopButton = new JButton("停止");
        stopButton.setEnabled(false);
        controlPanel.add(stopButton);

         // 把按鈕提升為欄位，並預設為不可點按，直到 socket 連線成功
         restartBtn = new JButton("重新開始一局");
         restartBtn.setEnabled(false);
         speedUpBtn = new JButton("加速");
         speedUpBtn.setEnabled(false);
         slowDownBtn = new JButton("減速");
         slowDownBtn.setEnabled(false);

        restartBtn.addActionListener(e -> resetEpisode());
        speedUpBtn.addActionListener(e -> changeSpeed(-10));
        slowDownBtn.addActionListener(e -> changeSpeed(10));
        controlPanel.add(restartBtn);
        controlPanel.add(speedUpBtn);
        controlPanel.add(slowDownBtn);

        setLayout(new BorderLayout());
        add(controlPanel, BorderLayout.NORTH);
        add(snakePanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);


        // 初次 pack() 讓 Swing 使用預設偏好尺寸計算整體佈局
        pack();
        // 確保整個內容視窗背景為黑，避免 frame 的預設背景色在邊緣顯示灰框
        getContentPane().setBackground(Color.BLACK);

        // 計算 content area 可用的寬高，並調整 snakePanel 的偏好大小，
        // 讓棋盤完全填滿中間區域（避免左右出現黑邊）。
        Insets insets = getInsets();
        int contentW = getWidth() - insets.left - insets.right;
        int contentH = getHeight() - insets.top - insets.bottom;

        // controlPanel 在 NORTH、status 在 SOUTH，中心為 snakePanel。
        int controlH = controlPanel.getPreferredSize().height;
        int statusH = statusLabel.getPreferredSize().height;

        int desiredPanelW = contentW;
        int desiredPanelH = Math.max(1, contentH - controlH - statusH);

        // 設定 snakePanel 的偏好尺寸並重新 pack
        snakePanel.setPreferredSize(new Dimension(desiredPanelW, desiredPanelH));
        pack();

        // 禁止使用者改變視窗大小以維持固定顯示比例
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 開始按鈕：設定局數並啟動（若 server 已連線則馬上啟動循環）
        // 預設開始按鈕不可用，直到 socket 連線
        startButton.setEnabled(false);
        startButton.addActionListener(e -> onStart());
        // 停止按鈕：停止遊戲循環
        stopButton.addActionListener(e -> onStop());

        // 預設不啟動遊戲，但立即在背景啟動 socket server，等待 Python client 連線。
        System.out.println("[SocketSnakeServerGame] 啟動 UI，將在背景啟動 socket server 等待連線...");
        new Thread(this::initSocketServerOnly, "SocketSnakeServerGame-ServerThread").start();

        // 顯示視窗
        setVisible(true);
    }

    // onStart：使用者按開始後的處理
    // - 驗證輸入的局數
    // - 設定狀態為 running
    // - 若 server 已經連線，立即啟動遊戲循環
    private void onStart() {
        int uiEpisodes;
        int uiMaxSteps = 0;
        Long uiTotal = null;
        try {
            uiEpisodes = Integer.parseInt(episodesField.getText().trim());
            if (uiEpisodes <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "請輸入正整數的局數。", "輸入錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            String maxs = maxStepsField.getText().trim();
            if (!maxs.isEmpty()) uiMaxSteps = Integer.parseInt(maxs);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "請輸入有效的每局最大步數（整數）。", "輸入錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            String tot = totalTimestepsField.getText().trim();
            if (!tot.isEmpty()) uiTotal = Long.parseLong(tot);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "請輸入有效的總步數（整數）。", "輸入錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 如果使用者未提供 total_timesteps，則以 episodes * estimate 作為預設（但不強制 env）
        if (uiTotal == null) {
            int perEpisode = (uiMaxSteps > 0) ? uiMaxSteps : 200; // 200 為預設估算
            uiTotal = (long) uiEpisodes * perEpisode;
        }

        // 寫入 config.json（Start 按下即寫入）
        try {
            writeAgentConfig(uiEpisodes, uiMaxSteps, uiTotal);
            System.out.println("[SocketSnakeServerGame] Start: 已將設定寫入 agent/config.json");
        } catch (IOException ioEx) {
            ioEx.printStackTrace();
            JOptionPane.showMessageDialog(this, "無法寫入 agent/config.json：\n" + ioEx.getMessage(), "檔案錯誤", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 啟動遊戲循環狀態
        maxEpisodes = uiEpisodes;
        this.uiMaxSteps = uiMaxSteps; // store UI max steps for per-episode truncation
        startButton.setEnabled(false);
        episodesField.setEnabled(false);
        stopButton.setEnabled(true);
        isRunning = true;
        currentEpisode = 1;
        stepCountInEpisode = 0; // reset step counter for first episode
        statusLabel.setText("等待 Python 連線到埠 " + PORT + " ...");

        System.out.println("[SocketSnakeServerGame] 使用者按下開始，已寫入設定並開始遊戲循環。當前目標局數 = " + maxEpisodes);

        // 嘗試啟動 Python 訓練程式（在獨立 cmd 視窗），會讀取 agent/config.json
        boolean trainerStarted = startPythonTrainer();
        if (trainerStarted) {
            System.out.println("[SocketSnakeServerGame] 已嘗試啟動 Python 訓練程式 (agent/train_agent_socket.py)。請觀察新開的 cmd 視窗或 agent 資料夾的 log。");
        } else {
            System.out.println("[SocketSnakeServerGame] 無法啟動 Python 訓練程式（python 可能不在 PATH 或檔案不存在）。");
        }

        // 如果 server 已連線，直接在 EDT 啟動遊戲循環
        if (socketServer != null) {
            SwingUtilities.invokeLater(() -> startGameLoopTimer());
        }
    }

    /**
     * 在新的 cmd 視窗啟動 Python 訓練程式（非阻塞）。
     * 返回 true 表示命令已發出（不代表內部訓練成功啟動）。
     */
    private boolean startPythonTrainer() {
        try {
            // 確認訓練腳本存在
            File script = new File(System.getProperty("user.dir"), "agent" + File.separator + "train_agent_socket.py");
            if (!script.exists()) {
                System.err.println("[SocketSnakeServerGame] 找不到 agent/train_agent_socket.py，無法啟動訓練。");
                return false;
            }
            String agentDir = script.getParentFile().getAbsolutePath();

            // 用 cmd start 在新視窗執行，保持視窗開啟以便查看輸出
            String cmd = "cmd.exe /c start \"Python Train\" cmd /k \"cd /d " + agentDir + " && python train_agent_socket.py\"";
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "Python Train", "cmd", "/k", "cd /d " + agentDir + " && python train_agent_socket.py");
            pb.directory(new File(agentDir));
            pb.redirectErrorStream(true);
            pythonTrainerProcess = pb.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 寫 agent/config.json：讀取現有檔案（若有），更新 episodes & max_steps & total_timesteps
    private void writeAgentConfig(int episodes, int maxSteps, Long totalTimesteps) throws IOException {
        File cfgFile = new File(System.getProperty("user.dir"), AGENT_CONFIG_REL);
        JsonObject root = new JsonObject();

        // 如果已有 config，先讀取並保留欄位
        if (cfgFile.exists()) {
            try (FileReader fr = new FileReader(cfgFile)) {
                JsonElement el = JsonParser.parseReader(fr);
                if (el != null && el.isJsonObject()) {
                    root = el.getAsJsonObject();
                }
            } catch (Exception e) {
                // 若讀檔失敗，繼續用新的 root
                root = new JsonObject();
            }
        }

        // 更新 episodes 與 max_steps
        root.addProperty("episodes", episodes);
        root.addProperty("max_steps", maxSteps);
        if (totalTimesteps != null) root.addProperty("total_timesteps", totalTimesteps.longValue());

        // 寫回檔案（UTF-8）
        File parent = cfgFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileWriter fw = new FileWriter(cfgFile, false)) {
            fw.write(gson.toJson(root));
        }
        System.out.println("[SocketSnakeServerGame] 已將訓練參數寫入 " + cfgFile.getAbsolutePath());
    }

    // onStop：使用者按停止後的處理，停止 Timer 並切換按鈕狀態
    private void onStop() {
        isRunning = false;
        if (gameLoopTimer != null && gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
        }
        startButton.setEnabled(true);
        episodesField.setEnabled(true);
        stopButton.setEnabled(false);
        statusLabel.setText("已停止。按「開始」可繼續");
        System.out.println("[SocketSnakeServerGame] 使用者按下停止，遊戲循環已停止。");

        // 嘗試終止剛才啟動的 Python 訓練程式 (best-effort)
        if (pythonTrainerProcess != null && pythonTrainerProcess.isAlive()) {
            try {
                pythonTrainerProcess.destroy();
                System.out.println("[SocketSnakeServerGame] 已嘗試終止 Python 訓練程式 (process.destroy)。");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // initSocketServerOnly：背景執行緒負責建立 ServerSocket、accept client、傳 INIT
    // 當收到連線時：
    // - 傳 INIT 給 client（告知棋盤大小）
    // - 若使用者尚未按開始，預設啟動遊戲循環（以便 agent 能立即拿到 STATE）
    private void initSocketServerOnly() {
        try {
            System.out.println("[SocketSnakeServerGame] 建立 SocketSnakeServer on port " + PORT + "...");
            socketServer = new SocketSnakeServer(PORT);

            System.out.println("[SocketSnakeServerGame] 呼叫 waitForClient()，阻塞等待 client 連線...");
            socketServer.waitForClient();

            System.out.println("[SocketSnakeServerGame] client 已連線。準備傳送 INIT...");
            int boardSize = gameState.getBoardSize();
            socketServer.sendInit(boardSize);
            System.out.println("[SocketSnakeServerGame] 已送 INIT(board_size=" + boardSize + ") 给 client。等待使用者按開始以啟動遊戲。" );

            // 在 EDT 更新 UI，並啟用先前被鎖的按鈕
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Python 已連線。棋盤大小: " + boardSize + "x" + boardSize + "。請按開始啟動遊戲。");
                snakePanel.updateBoard(gameState.getBoard());
                // 不要在連線時更改視窗大小，保持預設 600x600
                episodeReward = 0.0; // reset accumulator
                // 啟用控制按鈕
                startButton.setEnabled(true);
                restartBtn.setEnabled(true);
                speedUpBtn.setEnabled(true);
                slowDownBtn.setEnabled(true);
                // 如果使用者之前已按 Start（isRunning==true），則開始循環
                if (isRunning) {
                    startButton.setEnabled(false);
                    episodesField.setEnabled(false);
                    stopButton.setEnabled(true);
                    startGameLoopTimer();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("[SocketSnakeServerGame] 啟動 socket server 或等待 client 失敗: " + e.getMessage());
            SwingUtilities.invokeLater(() ->
                    statusLabel.setText("啟動 socket server 或等待 client 失敗: " + e.getMessage())
            );
        }
    }

    // startGameLoopTimer：每個 Timer tick 會
    // 1) 送出 STATE 給 Python
    // 2) 等待並讀取 ACTION
    // 3) 根據 ACTION 呼叫 gameState.stepByAction 並更新畫面
    private void startGameLoopTimer() {
        if (gameLoopTimer != null) {
            gameLoopTimer.stop();
        }

        gameLoopTimer = new Timer(stepDelayMs, (ActionEvent e) -> {
            if (!isRunning) return;

            // 若本局已結束（撞牆或撞自己），處理局結束流程
            if (gameState.isDone()) {
                // 在局結束時印出本局累積 reward
                System.out.println("[SocketSnakeServerGame] 第 " + currentEpisode + " 局結束，上一局總 reward=" + String.format("%.3f", episodeReward));
                statusLabel.setText("第 " + currentEpisode + " 局結束，蛇死了。上一局總 reward=" + String.format("%.3f", episodeReward));
                snakePanel.updateBoard(gameState.getBoard());
                // 暫停 1 秒，然後自動開始下一局或結束整個任務
                gameLoopTimer.stop();
                new Timer(1000, evt -> {
                    ((Timer) evt.getSource()).stop();
                    if (currentEpisode < maxEpisodes) {
                        currentEpisode++;
                        System.out.println("[SocketSnakeServerGame] 開始第 " + currentEpisode + " 局");
                        // reset accumulator for new episode
                        episodeReward = 0.0;
                        resetEpisode();
                        statusLabel.setText("第 " + currentEpisode + " 局 / 共 " + maxEpisodes + " 局");
                        gameLoopTimer.start();
                    } else {
                        statusLabel.setText("全部 " + maxEpisodes + " 局已結束。最後一局總 reward=" + String.format("%.3f", episodeReward));
                        isRunning = false;
                        startButton.setEnabled(true);
                        episodesField.setEnabled(true);
                    }
                }).start();
                return;
            }

            if (socketServer == null) {
                statusLabel.setText("尚未有 Python 連線...");
                return;
            }

            try {
                int[][] board = gameState.getBoard();
                double reward = gameState.getReward();
                boolean done = gameState.isDone();

                // 累積本局 reward（這個 reward 是上一步的結果）
                episodeReward += reward;

                // 診斷輸出：印在 console 上，方便除錯
                System.out.println("[SocketSnakeServerGame] sendState: reward=" + reward + ", done=" + done + ", episodeReward=" + String.format("%.3f", episodeReward));

                // 傳 STATE 給 Python（先做防護，避免任何欄位為 null / 非法）
                int headX = gameState.getHeadX();
                int headY = gameState.getHeadY();
                int snakeLen = gameState.getSnakeLength();
                int foodX = gameState.getFoodX();
                int foodY = gameState.getFoodY();
                int direction = gameState.getDirection();

                // 驗證座標範圍，若不合法則設為 -1（Python 端可檢測 -1 表示 unknown）
                int boardN = gameState.getBoardSize();
                if (boardN <= 0) boardN = 20;
                if (headX < 0 || headX >= boardN) headX = -1;
                if (headY < 0 || headY >= boardN) headY = -1;
                if (foodX < 0 || foodX >= boardN) foodX = -1;
                if (foodY < 0 || foodY >= boardN) foodY = -1;
                if (snakeLen < 0) snakeLen = 0;
                if (direction < 0 || direction > 3) direction = -1;

                // 記錄要送出的 payload（方便 debug）
                System.out.println(String.format("[SocketSnakeServerGame] sendState payload: head=(%d,%d), snake_len=%d, food=(%d,%d), dir=%d",
                        headX, headY, snakeLen, foodX, foodY, direction));

                try {
                    // 優先使用新簽章（含 head/food/len/dir）
                    socketServer.sendState(board, reward, done, headX, headY, snakeLen, foodX, foodY, direction);
                } catch (NoSuchMethodError nsme) {
                    // 若 socketServer 沒有新簽章（向後相容），改用舊簽章
                    System.err.println("[SocketSnakeServerGame] sendState: 新簽章不可用，使用舊簽章。" + nsme.getMessage());
                    try {
                        socketServer.sendState(board, reward, done);
                    } catch (IOException ioe2) {
                        System.err.println("[SocketSnakeServerGame] fallback sendState 失敗: " + ioe2.getMessage());
                    }
                } catch (IOException ioe) {
                    // 若傳送失敗，嘗試用較小的兼容 payload（舊版 sendState）避免斷線
                    System.err.println("[SocketSnakeServerGame] sendState 發生 IOException，嘗試用最小 payload 傳送並忽略細節: " + ioe.getMessage());
                    try {
                        socketServer.sendState(board, reward, done);
                    } catch (IOException ex2) {
                        System.err.println("[SocketSnakeServerGame] fallback sendState 也失敗，略過本步驟: " + ex2.getMessage());
                    }
                }


                // 等待 Python 傳回 ACTION
                System.out.println("[SocketSnakeServerGame] 等待 client 回傳 ACTION (timeout=" + stepDelayMs + "ms)...");
                int action = socketServer.readActionWithTimeout(stepDelayMs);
                if (action == -1) {
                    // Python 未在 timeout 內回應，改由 Java 端隨機動作（探索）
                    action = rng.nextInt(4);
                    System.out.println("[SocketSnakeServerGame] Python 未回應或回傳非 ACTION，fallback 隨機 action=" + action);
                } else {
                    System.out.println("[SocketSnakeServerGame] 收到 ACTION=" + action);
                }


                // 根據動作推進遊戲一步，並更新畫面
                gameState.stepByAction(action);
                 // 每執行一步，計數 +1
                 stepCountInEpisode++;
                 // DEBUG: 印出步數計數，方便追蹤為何每局只有 10 步
                 System.out.println("[SocketSnakeServerGame] stepCountInEpisode=" + stepCountInEpisode + ", uiMaxSteps=" + uiMaxSteps);
                 // 如果 UI 指定了每局最大步數，且已達到上限，當成本局結束 (truncated)
                 if (uiMaxSteps > 0 && stepCountInEpisode >= uiMaxSteps) {
                     System.out.println("[SocketSnakeServerGame] 已達每局最大步數上限 (" + uiMaxSteps + ")，將結束本局。");
                     // 停止 timer 以處理局結束流程（與撞牆邏輯一致）
                     gameLoopTimer.stop();
                     // 印出本局 reward
                     System.out.println("[SocketSnakeServerGame] 第 " + currentEpisode + " 局達到步數上限，上一局總 reward=" + String.format("%.3f", episodeReward));
                     // 等 1 秒再開始下一局或結束
                     new Timer(1000, evt -> {
                         ((Timer) evt.getSource()).stop();
                         if (currentEpisode < maxEpisodes) {
                             currentEpisode++;
                             System.out.println("[SocketSnakeServerGame] 開始第 " + currentEpisode + " 局 (由步數上限觸發)");
                             // reset accumulator for new episode
                             episodeReward = 0.0;
                             stepCountInEpisode = 0;
                             resetEpisode();
                             statusLabel.setText("第 " + currentEpisode + " 局 / 共 " + maxEpisodes + " 局");
                             gameLoopTimer.start();
                         } else {
                             statusLabel.setText("全部 " + maxEpisodes + " 局已結束 (由步數上限)。最後一局總 reward=" + String.format("%.3f", episodeReward));
                             isRunning = false;
                             startButton.setEnabled(true);
                             episodesField.setEnabled(true);
                         }
                     }).start();
                     return;
                  }
                  snakePanel.updateBoard(gameState.getBoard());
                  statusLabel.setText("第 " + currentEpisode + " 局 / 共 " + maxEpisodes + " 局，最近動作: " + action);
            } catch (IOException ex) {
                ex.printStackTrace();
                System.err.println("[SocketSnakeServerGame] 與 Python 通訊失敗: " + ex.getMessage());
                statusLabel.setText("與 Python 通訊失敗: " + ex.getMessage());
                // 出錯時停止迴圈，避免狂刷例外
                gameLoopTimer.stop();
            }
        });

        gameLoopTimer.setInitialDelay(0);
        gameLoopTimer.start();
    }

    // resetEpisode：重新初始化當前局的 game state，並通知 Python（若連線）
    private void resetEpisode() {
        gameState.reset();
        snakePanel.updateBoard(gameState.getBoard());
        // 保持視窗大小不變以免干擾學習數據
        statusLabel.setText("已重新開始一局。第 " + currentEpisode + " 局 / 共 " + maxEpisodes + " 局");
        // 重置本局步數計數器與累積 reward
        stepCountInEpisode = 0;
        episodeReward = 0.0;
        if (socketServer != null) {
            try {
                // 通知 Python 並包含 head/food/len (reset 後立即發一次 state 會由 timer 做)
                socketServer.sendReset();
            } catch (IOException e) {
                e.printStackTrace();
                statusLabel.setText("送 RESET 給 Python 失敗: " + e.getMessage());
            }
        }
        if (gameLoopTimer != null && !gameLoopTimer.isRunning() && isRunning) {
            gameLoopTimer.start();
        }
    }

    // changeSpeed：調整 Timer delay（控制遊戲速度）
    private void changeSpeed(int delta) {
        int newDelay = stepDelayMs + delta;
        if (newDelay < 5) newDelay = 5;
        if (newDelay > 1000) newDelay = 1000;
        stepDelayMs = newDelay;
        if (gameLoopTimer != null) {
            gameLoopTimer.setDelay(stepDelayMs);
        }
        statusLabel.setText("目前步進延遲: " + stepDelayMs + " ms");
    }

    @Override
    public void dispose() {
        super.dispose();
        if (gameLoopTimer != null) {
            gameLoopTimer.stop();
        }
        if (socketServer != null) {
            socketServer.close();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SocketSnakeServerGame::new);
    }
}
