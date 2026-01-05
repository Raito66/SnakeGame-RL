import json
import socket
from typing import Any, Dict, Optional, Tuple

import gymnasium as gym
from gymnasium import spaces


class SocketSnakeEnv(gym.Env):
    """
    透過 SocketProtocol 與 Java Snake 互動的 Gym Env。
    動作空間：0~3 (上、右、下、左)
    觀測空間：長度 N*N 的一維 int32 向量 (0空,1蛇,2食物)
    """

    metadata = {"render.modes": ["human"]}

    def __init__(self, host: str = "127.0.0.1", port: int = 5000):
        super().__init__()
        self.host = host
        self.port = port
        self.sock: Optional[socket.socket] = None
        self.sock_file_r = None
        self.sock_file_w = None
        self.board_size: Optional[int] = None

        self._connect_and_wait_init()

        n = self.board_size * self.board_size  # type: ignore[arg-type]
        self.action_space = spaces.Discrete(4)
        self.observation_space = spaces.Box(
            low=0, high=2, shape=(n,), dtype=np.int32
        )

    # === Gym API ===

    def reset(
        self,
        *,
        seed: Optional[int] = None,
        options: Optional[Dict[str, Any]] = None,
    ) -> Tuple[np.ndarray, Dict[str, Any]]:
        super().reset(seed=seed)
        # reset 由 Java 控制：Java 在 done 時會 reset 並發 RESET + 下一個 STATE
        # 這裡只需等下一個 STATE 當作起始 obs
        obs, _r, _done, info = self._recv_state_blocking()
        return obs, info

    def step(self, action: int):
        # 1) 送 ACTION 給 Java
        self._send_msg(
            {
                "type": "ACTION",
                "payload": {"action": int(action)},
            }
        )
        # 2) 等 Java 下一個 STATE
        obs, reward, done, info = self._recv_state_blocking()
        truncated = False
        return obs, reward, done, truncated, info

    def render(self, mode: str = "human"):
        # Java GUI 自己畫畫面，Python 不做事
        return None

    def close(self):
        if self.sock_file_r is not None:
            try:
                self.sock_file_r.close()
            except Exception:
                pass
        if self.sock_file_w is not None:
            try:
                self.sock_file_w.close()
            except Exception:
                pass
        if self.sock is not None:
            try:
                self.sock.close()
            except Exception:
                pass

    # === 內部工具 ===

    def _connect_and_wait_init(self) -> None:
        """建立 TCP 連線並等待 INIT 封包以取得 board_size。"""
        self.sock = socket.create_connection((self.host, self.port))
        self.sock_file_r = self.sock.makefile("r", encoding="utf-8")
        self.sock_file_w = self.sock.makefile("w", encoding="utf-8")

        # 等 INIT
        while True:
            msg = self._recv_msg()
            if msg.get("type") == "INIT":
                payload = msg.get("payload") or {}
                # 與 Java SocketProtocol.createInitMessage 對齊：board_size
                bs = int(payload.get("board_size", 0))
                if bs <= 0:
                    raise ValueError(f"INIT 中的 board_size 非法: {bs}")
                self.board_size = bs
                print(f"[SocketSnakeEnv] 收到 INIT, board_size={bs}")
                break
            else:
                print(f"[SocketSnakeEnv] 忽略非 INIT 訊息: {msg.get('type')}")

    def _recv_state_blocking(
        self,
    ) -> Tuple[np.ndarray, float, bool, Dict[str, Any]]:
        """阻塞讀取下一個 type==STATE 的封包。"""
        n = self.board_size * self.board_size  # type: ignore[operator]
        while True:
            msg = self._recv_msg()
            msg_type = msg.get("type")
            if msg_type != "STATE":
                # RESET / PING 等可以視需要處理，這裡簡單略過
                if msg_type == "RESET":
                    # RESET 表示新一局開始，但照樣等下一個 STATE 即可
                    print("[SocketSnakeEnv] 收到 RESET，等待下一個 STATE 做為新起點。")
                    continue
                if msg_type == "PING":
                    self._send_msg({"type": "PONG", "payload": {}})
                    continue
                print(f"[SocketSnakeEnv] 忽略非 STATE 訊息: {msg_type}")
                continue

            payload = msg.get("payload") or {}
            board = payload.get("board")
            reward = float(payload.get("reward", 0.0))
            done = bool(payload.get("done", False))

            if board is None:
                raise ValueError("STATE payload 缺少 board 欄位")

            arr = np.array(board, dtype=np.int32).flatten()
            if arr.size != n:
                raise ValueError(
                    f"obs size {arr.size} 與 board_size^2 {n} 不符"
                )

            info = {"raw_state": payload}
            return arr, reward, done, info

    def _recv_msg(self) -> Dict[str, Any]:
        """讀一行 JSON -> dict。"""
        assert self.sock_file_r is not None
        line = self.sock_file_r.readline()
        if not line:
            raise ConnectionError("socket 已關閉")
        line = line.strip()
        if not line:
            return self._recv_msg()
        return json.loads(line)

    def _send_msg(self, msg: Dict[str, Any]) -> None:
        """dict -> JSON 一行，寫 socket。"""
        assert self.sock_file_w is not None
        data = json.dumps(msg, ensure_ascii=False) + "\n"
        self.sock_file_w.write(data)
        self.sock_file_w.flush()
