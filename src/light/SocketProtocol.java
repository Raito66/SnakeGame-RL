package light;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 定義 Java ↔ Python 之間的 Socket 通訊協定。
 *
 * 封包格式統一為單行 JSON：
 * {
 *   "type": "STATE" | "ACTION" | "INIT" | "RESET" | "PING",
 *   "payload": { ... } // 可為空物件
 * }
 */
public final class SocketProtocol {

    private static final Gson GSON = new Gson();

    private SocketProtocol() {
        // 工具類不允許實例化
    }

    /** 訊息種類。 */
    public enum MessageType {
        STATE,   // 傳送棋盤狀態 (board / reward / done)
        ACTION,  // 傳送動作 (action)
        RESET,   // 重置一局
        INIT,    // 初始資訊 (例如 boardSize)
        PING     // 心跳
    }

    /**
     * 單一封包資料結構。
     * 內部欄位設為 private final，外部一律用 getter 取值。
     */
    public static final class SocketMessage {
        private final MessageType type;
        private final JsonObject payload;

        public SocketMessage(MessageType type, JsonObject payload) {
            this.type = type;
            this.payload = payload == null ? new JsonObject() : payload;
        }

        public MessageType getType() {
            return type;
        }

        public JsonObject getPayload() {
            return payload;
        }
    }

    // ================== 編碼 / 解碼 ==================

    /** 將 SocketMessage 編碼成單行 JSON 字串，結尾自動加上 '\n'。 */
    public static String encode(SocketMessage msg) {
        JsonObject root = new JsonObject();
        root.addProperty("type", msg.getType().name());
        root.add("payload", msg.getPayload());
        // `SocketSnakeServer` 使用 BufferedWriter.write(line) 之後對方用 readLine(),
        // 所以這裡加上 '\n'.
        return GSON.toJson(root) + "\n";
    }

    /** 將一行 JSON 字串解碼成 SocketMessage。 */
    public static SocketMessage decode(String line) {
        JsonObject root = JsonParser.parseString(line).getAsJsonObject();

        if (!root.has("type")) {
            throw new IllegalArgumentException("缺少欄位 `type`");
        }

        String typeStr = root.get("type").getAsString();
        MessageType type = MessageType.valueOf(typeStr);

        JsonObject payload = new JsonObject();
        if (root.has("payload") && root.get("payload").isJsonObject()) {
            payload = root.getAsJsonObject("payload");
        }

        return new SocketMessage(type, payload);
    }

    // ================== 工廠方法：給 SocketSnakeServer 用 ==================

    /**
     * 建立 STATE 訊息。
     *
     * payload:
     * {
     *   "board": [[...], ...],
     *   "reward": double,
     *   "done": boolean,
     *   "head_x": int,
     *   "head_y": int,
     *   "snake_len": int,
     *   "food_x": int,
     *   "food_y": int,
     *   "direction": int  // 新增：目前方向 0=up,1=down,2=left,3=right
     * }
     */
    public static SocketMessage createStateMessage(int[][] board, double reward, boolean done,
                                                   int headX, int headY, int snakeLen,
                                                   int foodX, int foodY, int direction) {
        JsonObject payload = new JsonObject();
        // 用 Gson 直接把 int[][] 轉成 JsonElement
        payload.add("board", GSON.toJsonTree(board));
        payload.addProperty("reward", reward);
        payload.addProperty("done", done);
        payload.addProperty("head_x", headX);
        payload.addProperty("head_y", headY);
        payload.addProperty("snake_len", snakeLen);
        payload.addProperty("food_x", foodX);
        payload.addProperty("food_y", foodY);
        payload.addProperty("direction", direction);
        return new SocketMessage(MessageType.STATE, payload);
    }

    /**
     * 建立 INIT 訊息。
     *
     * payload:
     * {
     *   "boardSize": int
     * }
     */
    public static SocketMessage createInitMessage(int boardSize) {
        JsonObject payload = new JsonObject();
        payload.addProperty("board_size", boardSize);
        return new SocketMessage(MessageType.INIT, payload);
    }

    /**
     * 建立 RESET 訊息，payload 留空物件。
     */
    public static SocketMessage createResetMessage() {
        JsonObject payload = new JsonObject();
        return new SocketMessage(MessageType.RESET, payload);
    }

    /**
     * 建立 PING 訊息，可帶時間戳記或保持空 payload。
     */
    public static SocketMessage createPingMessage() {
        JsonObject payload = new JsonObject();
        // 如需時間戳可打開下一行：
        // payload.addProperty("ts", System.currentTimeMillis());
        return new SocketMessage(MessageType.PING, payload);
    }

    /**
     * 建立 ACTION 訊息（若之後 Python 也要回傳 ACTION 給 Java 也可用）。
     *
     * payload:
     * {
     *   "action": int
     * }
     */
    public static SocketMessage createActionMessage(int action) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        return new SocketMessage(MessageType.ACTION, payload);
    }
}
