import socket
import json
from typing import Any, Dict, Tuple, Optional

import numpy as np
import gymnasium as gym
from gymnasium import spaces

HOST = "127.0.0.1"
PORT = 5000


def recv_msg(sock: socket.socket) -> Dict[str, Any]:
    """從 socket 讀取一行以 '\n' 結尾的 JSON，解析成 dict。"""
    buffer = b""
    while True:
        chunk = sock.recv(4096)
        if not chunk:
            raise ConnectionError("socket 已關閉")
        buffer += chunk
        if b"\n" in buffer:
            line, buffer = buffer.split(b"\n", 1)
            line_str = line.decode("utf-8").strip()
            if not line_str:
                continue
            try:
                return json.loads(line_str)
            except json.JSONDecodeError as e:
                print(f"[WARN] JSON 解析失敗: {e}, line={line_str}")
                # 略過錯誤行，繼續讀
                continue


def send_msg(sock: socket.socket, msg_type: str, payload: Optional[Dict[str, Any]] = None) -> None:
    """送出一行 `{ \"type\": .., \"payload\": .. }` JSON + '\n'."""
    if payload is None:
        payload = {}
    msg = {"type": msg_type, "payload": payload}
    data = (json.dumps(msg) + "\n").encode("utf-8")
    sock.sendall(data)


class JavaSnakeSocketEnv(gym.Env):
    """
    使用 socket 與 Java `SocketSnakeServerGame` 溝通的 Gym Environment。

    This env probes the first STATE after INIT to detect extra fields (head_x, head_y,
    food_x, food_y, snake_len, direction). If present, observations returned are:
        flat_board (n*n) + [head_x_norm, head_y_norm, food_x_norm, food_y_norm, snake_len_norm, direction_onehot(4)]
    Otherwise obs is just flat_board.
    """

    metadata = {"render.modes": ["human"]}

    def __init__(self) -> None:
        super().__init__()

        self.sock: Optional[socket.socket] = None
        self.board_size: Optional[int] = None

        # dynamic: extras detected and ordering
        self._extras_keys = []  # e.g. ['head_x','head_y','food_x','food_y','snake_len','direction']
        self._extras_count = 0

        # 這裡維持與 Java 的編碼一致：action 0~3
        self.action_space = spaces.Discrete(4)
        # observation_space will be set after probing first STATE
        self.observation_space: Optional[spaces.Box] = None

        # store initial observation read during connect, so reset() can return it immediately
        self._initial_obs: Optional[np.ndarray] = None

        # 連線並拿 INIT + probe one STATE
        self._connect_and_init()

        # 內部狀態緩存
        self._last_obs: Optional[np.ndarray] = None
        self._last_done: bool = False

    def _connect_and_init(self) -> None:
        print(f"[JavaSnakeEnv] 連線到 {HOST}:{PORT} ...")
        self.sock = socket.create_connection((HOST, PORT))
        # use raw recv functions (recv_msg uses sock.recv)

        # 等 INIT
        while True:
            msg = recv_msg(self.sock)
            msg_type = msg.get("type")
            if msg_type == "INIT":
                payload = msg.get("payload", {}) or {}
                board_size = int(payload.get("board_size", 0))
                if board_size <= 0:
                    raise ValueError(f"INIT.board_size 不合法: {board_size}")
                self.board_size = board_size
                print(f"[JavaSnakeEnv] 收到 INIT, board_size={board_size}")
                break
            else:
                print(f"[JavaSnakeEnv] 忽略非 INIT 封包: {msg_type}")
                continue

        # Probe: read messages until we observe a STATE so we can detect extras and set observation_space
        probe_timeout = 10.0
        import time
        start = time.time()
        while True:
            if time.time() - start > probe_timeout:
                # fallback: set obs to flat board only
                n = self.board_size
                self._extras_keys = []
                self._extras_count = 0
                self.observation_space = spaces.Box(low=0, high=2, shape=(n * n,), dtype=np.int32)
                print(f"[JavaSnakeEnv] probe timeout: assume no extras, obs shape={n*n}")
                return
            try:
                msg = recv_msg(self.sock)
            except Exception:
                time.sleep(0.05)
                continue
            t = msg.get("type")
            payload = msg.get("payload", {}) or {}
            if t == "STATE":
                # detect extras keys
                extras = []
                for k in ("head_x", "head_y", "food_x", "food_y", "snake_len", "direction"):
                    if k in payload:
                        extras.append(k)
                self._extras_keys = extras
                # compute extras count: if 'direction' present we will encode as one-hot 4
                extras_count = 0
                for k in extras:
                    if k == "direction":
                        extras_count += 4
                    else:
                        extras_count += 1
                self._extras_count = extras_count
                n = self.board_size
                obs_len = n * n + self._extras_count
                # set observation_space
                self.observation_space = spaces.Box(low=-1.0, high=2.0, shape=(obs_len,), dtype=np.float32)
                # store initial obs to return on reset
                obs_arr, reward, done = self._parse_state(msg)
                self._initial_obs = obs_arr
                self._last_obs = obs_arr
                self._last_done = bool(done)
                print(f"[JavaSnakeEnv] probe STATE found, extras={self._extras_keys}, obs_len={obs_len}")
                return
            else:
                # ignore other messages
                continue

    # ====== gym API ======

    def reset(self, *, seed: Optional[int] = None, options: Optional[Dict[str, Any]] = None):
        # Gymnasium reset: return (obs, info)
        super().reset(seed=seed)

        if self.sock is None:
            self._connect_and_init()

        # If we already probed and stored an initial obs, return it
        if self._initial_obs is not None:
            obs = self._initial_obs
            # clear initial so next reset waits for a new STATE
            self._initial_obs = None
            return obs, {}

        # Otherwise wait for a STATE
        while True:
            msg = recv_msg(self.sock)
            msg_type = msg.get("type")
            if msg_type == "STATE":
                obs, reward, done = self._parse_state(msg)
                self._last_obs = obs
                self._last_done = done
                return obs, {}
            elif msg_type == "INIT":
                payload = msg.get("payload", {}) or {}
                board_size = int(payload.get("board_size", self.board_size or 0))
                self.board_size = board_size
                print(f"[JavaSnakeEnv] reset 時收到 INIT, board_size={board_size}")
            else:
                print(f"[JavaSnakeEnv] reset() 忽略封包 type={msg_type}")

    def step(self, action: int):
        # Gymnasium step: return (obs, reward, terminated, truncated, info)
        if self.sock is None:
            raise RuntimeError("socket 尚未連線")

        # 將 action 送給 Java
        action_int = int(action)
        send_msg(self.sock, "ACTION", {"action": action_int})

        # 等下一個 STATE
        while True:
            msg = recv_msg(self.sock)
            msg_type = msg.get("type")
            if msg_type == "STATE":
                obs, reward, done = self._parse_state(msg)
                self._last_obs = obs
                self._last_done = done
                info: Dict[str, Any] = {}
                # In Gymnasium, return terminated, truncated. We treat 'done' as terminated and truncated=False
                return obs, reward, bool(done), False, info
            elif msg_type == "RESET":
                # Java 主動 reset，視為 terminated=True for gym
                print("[JavaSnakeEnv] 收到 RESET，下一輪請呼叫 env.reset()")
                obs = self._last_obs if self._last_obs is not None else self._empty_obs()
                return obs, 0.0, True, False, {}
            elif msg_type == "INIT":
                payload = msg.get("payload", {}) or {}
                board_size = int(payload.get("board_size", self.board_size or 0))
                self.board_size = board_size
                print(f"[JavaSnakeEnv] step 時收到 INIT, board_size={board_size}")
            else:
                print(f"[JavaSnakeEnv] step() 忽略封包 type={msg_type}")

    def _empty_obs(self) -> np.ndarray:
        if self.board_size is None:
            return np.zeros((0,), dtype=np.float32)
        n = self.board_size
        return np.zeros((n * n + self._extras_count,), dtype=np.float32)

    def _parse_state(self, msg: Dict[str, Any]) -> Tuple[np.ndarray, float, bool]:
        payload = msg.get("payload", {}) or {}
        board = payload.get("board")
        reward = float(payload.get("reward", 0.0))
        done = bool(payload.get("done", False))

        if board is None:
            raise ValueError("STATE payload 缺少 board 欄位")

        arr = np.array(board, dtype=np.float32)
        flat = arr.flatten()

        n = self.board_size
        if n is not None and flat.size != n * n:
            raise ValueError(f"obs size {flat.size} != board_size^2 {n*n}")

        extras_values = []
        # if extras were detected earlier, try to extract them; otherwise attempt best-effort
        keys = self._extras_keys if self._extras_keys else [k for k in ("head_x", "head_y", "food_x", "food_y", "snake_len", "direction") if k in payload]
        for k in keys:
            if k == "direction":
                # encode as one-hot length 4
                try:
                    d = int(payload.get("direction", 0))
                except Exception:
                    d = 0
                onehot = [0.0, 0.0, 0.0, 0.0]
                if 0 <= d < 4:
                    onehot[d] = 1.0
                extras_values.extend(onehot)
            else:
                try:
                    v = float(payload.get(k, 0.0))
                except Exception:
                    v = 0.0
                # normalize positions by board size for stability
                if k in ("head_x", "food_x"):
                    extras_values.append(v / float(n))
                elif k in ("head_y", "food_y"):
                    extras_values.append(v / float(n))
                elif k == "snake_len":
                    extras_values.append(v / float(n * n))
                else:
                    extras_values.append(v)

        if extras_values:
            obs = np.concatenate([flat.astype(np.float32), np.array(extras_values, dtype=np.float32)])
        else:
            obs = flat.astype(np.float32)

        return obs, reward, done

    def render(self, mode: str = "human"):
        # 已經有 Java Swing 畫面，這裡通常不需要再 render
        pass

    def close(self):
        if self.sock is not None:
            try:
                self.sock.close()
            except OSError:
                pass
            self.sock = None
