package light;

import com.google.gson.JsonObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 使用 {@link SocketProtocol} 進行 Java ↔ Python 溝通的簡單 TCP 伺服端。
 *
 * 一般流程：
 * 1. Java 端建立 SocketSnakeServer，指定監聽 port。
 * 2. 呼叫 waitForClient()，阻塞等待 Python 連線。
 * 3. 之後用 sendState(...) / readAction() 反覆交換資料。
 *
 * 封包格式由 SocketProtocol 負責定義與編解碼。
 */
public class SocketSnakeServer implements Closeable {

    private final int port;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private BufferedReader reader;
    private BufferedWriter writer;

    /**
     * 建立一個監聽指定埠號的 Socket 伺服端。
     *
     * @param port 例如 5000, 6000 ...
     */
    public SocketSnakeServer(int port) {
        this.port = port;
    }

    /**
     * 啟動 ServerSocket 並阻塞等待一個 Python client 連進來。
     * 只接受一次連線，若要多 client 需自行擴充。
     */
    public synchronized void waitForClient() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }

        serverSocket = new ServerSocket(port);
        System.out.println("[SocketSnakeServer] Listening on port " + port + " ...");
        clientSocket = serverSocket.accept();
        System.out.println("[SocketSnakeServer] Client connected from " + clientSocket.getRemoteSocketAddress());

        reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(
                new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** 確保已經有 client 連線，否則拋出 IOException。 */
    private void ensureConnected() throws IOException {
        if (clientSocket == null || clientSocket.isClosed()
                || reader == null || writer == null) {
            throw new IOException("尚未有有效的 client 連線，請先呼叫 waitForClient()");
        }
    }

    /** 發送一個通用 SocketMessage。會自動 encode 並加上換行。 */
    public synchronized void sendMessage(SocketProtocol.SocketMessage msg) throws IOException {
        ensureConnected();
        String line = SocketProtocol.encode(msg);
        writer.write(line);
        writer.flush();
    }

    /**
     * 阻塞讀取一行 JSON 並 decode 成 SocketMessage。
     *
     * @return 讀到的封包；若對方關閉連線，回傳 null。
     */
    public synchronized SocketProtocol.SocketMessage readMessage() throws IOException {
        ensureConnected();
        String line = reader.readLine();
        if (line == null) {
            // 對方斷線
            return null;
        }
        return SocketProtocol.decode(line);
    }

    // ======== 專用 helper：State / Action / Init / Reset / Ping ========

    /**
     * Java → Python：傳送當前棋盤狀態（舊版，direction 預設為 -1）。
     *
     * @param board  N x N 的棋盤 (0空,1蛇,2食物)
     * @param reward 此步的 reward
     * @param done   是否結束一局
     */
    public void sendState(int[][] board, double reward, boolean done) throws IOException {
        // 相容性保留：direction 設為 -1
        SocketProtocol.SocketMessage msg =
                SocketProtocol.createStateMessage(board, reward, done, -1, -1, 0, -1, -1, -1);
        sendMessage(msg);
    }

    /**
     * Java → Python：傳送當前棋盤狀態（包含 head/food/len/direction）
     *
     * @param board  N x N 的棋盤 (0空,1蛇,2食物)
     * @param reward 此步的 reward
     * @param done   是否結束一局
     * @param headX  蛇頭 X
     * @param headY  蛇頭 Y
     * @param snakeLen 蛇長
     * @param foodX  食物 X
     * @param foodY  食物 Y
     * @param direction 目前方向 (0=up,1=down,2=left,3=right)
     */
    public void sendState(int[][] board, double reward, boolean done,
                          int headX, int headY, int snakeLen,
                          int foodX, int foodY, int direction) throws IOException {
        SocketProtocol.SocketMessage msg =
                SocketProtocol.createStateMessage(board, reward, done, headX, headY, snakeLen, foodX, foodY, direction);
        sendMessage(msg);
    }

    /**
     * Java → Python：傳送初始資訊，例如棋盤大小。
     */
    public void sendInit(int boardSize) throws IOException {
        SocketProtocol.SocketMessage msg =
                SocketProtocol.createInitMessage(boardSize);
        sendMessage(msg);
    }

    /**
     * Java → Python：送出 RESET，告訴對方要重開一局。
     */
    public void sendReset() throws IOException {
        sendMessage(SocketProtocol.createResetMessage());
    }

    /**
     * Java → Python：送出 PING。
     */
    public void sendPing() throws IOException {
        sendMessage(SocketProtocol.createPingMessage());
    }

    /**
     * Python → Java：讀取一個 ACTION 封包並回傳其中的 action 整數。
     *
     * @return 動作 0~3
     * @throws IOException 若連線中斷或封包不是 ACTION
     */
    public int readAction() throws IOException {
        // 連續讀取，直到收到 ACTION；若收到 RESET 或 PING 則記錄並繼續等待。
        while (true) {
            SocketProtocol.SocketMessage msg = readMessage();
            if (msg == null) {
                throw new IOException("連線已關閉，讀不到 ACTION。");
            }
            SocketProtocol.MessageType type = msg.getType();
            if (type == SocketProtocol.MessageType.ACTION) {
                JsonObject payload = msg.getPayload();
                if (!payload.has("action")) {
                    throw new IOException("ACTION 封包缺少 `action` 欄位。");
                }
                return payload.get("action").getAsInt();
            } else if (type == SocketProtocol.MessageType.RESET) {
                System.out.println("[SocketSnakeServer] 收到 RESET，暫時忽略 ACTION 讀取，繼續等待 ACTION。");
                // continue 等待下一個訊息
                continue;
            } else if (type == SocketProtocol.MessageType.PING) {
                // 對 PING 可以回 PING 或忽略
                System.out.println("[SocketSnakeServer] 收到 PING，回應 PING。");
                try {
                    sendPing();
                } catch (IOException ignored) {
                }
                continue;
            } else {
                System.out.println("[SocketSnakeServer] 收到非 ACTION 訊息 type=" + type + "，忽略。");
                continue;
            }
        }
    }

    /**
     * Read action with timeout. If a valid ACTION message is received within the timeout, return it.
     * If timeout occurs, return -1 to indicate no action received.
     */
    public int readActionWithTimeout(int timeoutMs) throws IOException {
        ensureConnected();
        int originalTimeout = 0;
        try {
            originalTimeout = clientSocket.getSoTimeout();
        } catch (Exception ignored) {
            originalTimeout = 0;
        }
        try {
            clientSocket.setSoTimeout(Math.max(1, timeoutMs));
            SocketProtocol.SocketMessage msg = readMessage();
            if (msg == null) {
                throw new IOException("連線已關閉，讀不到 ACTION。");
            }
            if (msg.getType() == SocketProtocol.MessageType.ACTION) {
                JsonObject payload = msg.getPayload();
                if (!payload.has("action")) {
                    throw new IOException("ACTION 封包缺少 `action` 欄位。");
                }
                return payload.get("action").getAsInt();
            } else {
                // 如果收到 RESET/INIT/PING 等非 ACTION 訊息，回傳 -1 表示逾時/無動作
                return -1;
            }
        } catch (IOException ioe) {
            if (ioe instanceof java.net.SocketTimeoutException) {
                return -1; // timeout
            }
            throw ioe;
        } finally {
            try {
                clientSocket.setSoTimeout(originalTimeout);
            } catch (Exception ignored) {
            }
        }
    }

    // ======== 資源釋放 ========

    @Override
    public synchronized void close() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
