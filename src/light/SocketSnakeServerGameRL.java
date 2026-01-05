package light;

import java.io.IOException;

/**
 * 使用 SocketSnakeServer + GameState 與 Python DQN 透過 socket 互動的 RL 版遊戲迴圈。
 *
 * 流程：
 *  1. 建立 GameState，啟動 SocketSnakeServer 監聽指定埠。
 *  2. 等待 Python client 連線後，送出 INIT(board_size)。
 *  3. 進入迴圈：
 *     - 若 done：reset()，送 RESET。
 *     - 送 STATE(board, reward, done)。
 *     - 阻塞 readAction()，拿到 0~3 的 action。
 *     - 呼叫 gameState.stepByAction(action)。
 */
public class SocketSnakeServerGameRL {

    /** 要與 Python 一致，例如 agent/socket_eval_play.py 的 PORT。 */
    private static final int PORT = 5000;

    /** 每步之間的延遲，主要讓訓練用時不要太快吃滿 CPU。視情況可調或設成 0。 */
    private static final long STEP_DELAY_MS = 10L;

    public static void main(String[] args) {
        GameState gameState = new GameState();
        int boardSize = gameState.getBoardSize();

        try (SocketSnakeServer server = new SocketSnakeServer(PORT)) {
            System.out.println("[SocketSnakeServerGameRL] 啟動，等待 Python client 連線 (port=" + PORT + ")...");
            server.waitForClient();
            System.out.println("[SocketSnakeServerGameRL] Python client 已連線。");

            // 告訴 Python 棋盤大小
            server.sendInit(boardSize);
            System.out.println("[SocketSnakeServerGameRL] 已送出 INIT, board_size=" + boardSize);

            while (true) {
                // 若一局結束，reset 並告訴 Python
                if (gameState.isDone()) {
                    System.out.println("[SocketSnakeServerGameRL] 一局結束，重置遊戲。");
                    gameState.reset();
                    server.sendReset();
                }

                int[][] board = gameState.getBoard();
                double reward = gameState.getReward(); // GameState 已有 reward 欄位
                boolean done = gameState.isDone();

                // 1) 把目前狀態送給 Python
                try {
                    server.sendState(board, reward, done);
                } catch (IOException e) {
                    System.err.println("[SocketSnakeServerGameRL] 傳送 STATE 給 Python 失敗，結束伺服器。");
                    e.printStackTrace();
                    break;
                }

                // 2) 從 Python 讀取動作；若連線斷掉或資料不對會拋 IOException
                int action;
                try {
                    action = server.readAction();
                } catch (IOException e) {
                    System.err.println("[SocketSnakeServerGameRL] 讀取 ACTION 失敗 (可能斷線)，結束伺服器。");
                    e.printStackTrace();
                    break;
                }

                // 3) 依 action 前進一步
                gameState.stepByAction(action);

                // 4) 控制迴圈速度（可視需要調整或拿掉）
                sleepQuietly(STEP_DELAY_MS);
            }

        } catch (IOException e) {
            System.err.println("[SocketSnakeServerGameRL] 建立或關閉 SocketSnakeServer 時發生 IOException:");
            e.printStackTrace();
        }

        System.out.println("[SocketSnakeServerGameRL] 結束。");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
