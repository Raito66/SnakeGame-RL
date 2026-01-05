import socket
import json
import os
from typing import Any, Dict, Optional, Tuple

import gymnasium as gym
from gymnasium import spaces
import numpy as np
from stable_baselines3 import DQN
import collections
import random as _rand
import time

HOST = "127.0.0.1"
PORT = 5000

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
# use the shared model filename (dqn_snake_model.zip) so evaluation matches trained model
MODEL_PATH = os.path.join(os.path.dirname(__file__), "dqn_snake_model")

# Debug / test flags
# If True, ignore the model and use fully random actions (useful to test environment)
DEBUG_FORCE_RANDOM = False
# If True, print action distribution every ACTION_LOG_INTERVAL steps
ACTION_LOG_INTERVAL = 200

# exploration during live run (only used if DEBUG_FORCE_RANDOM is False)
USE_EPSILON = False
EPSILON = 0.05
# If True, pass deterministic=True to model.predict (useful for evaluation)
DETERMINISTIC_PREDICT = True

# internal counters
_action_counter = collections.Counter()
_step_counter = 0


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


def send_msg(sock: socket.socket, msg_type: str, payload: Optional[Dict[str, Any]] = None, max_retries: int = 3) -> None:
    """送出一行 `{ "type": .., "payload": .. }` JSON + '\n'，包含重試機制。"""
    if payload is None:
        payload = {}
    msg = {"type": msg_type, "payload": payload}
    data = (json.dumps(msg) + "\n").encode("utf-8")
    last_exc = None
    for i in range(max_retries):
        try:
            sock.sendall(data)
            return
        except Exception as e:
            last_exc = e
            time.sleep(0.05)
    # 若重試仍失敗，丟出最後一個例外
    raise last_exc


def load_model() -> Optional[DQN]:
    """載入 DQN 模型；若找不到則回傳 None（上層會退回隨機策略）。"""
    if (not os.path.exists(MODEL_PATH + ".zip")) and (not os.path.isdir(MODEL_PATH)):
        print(f"[WARN] 找不到模型 `{MODEL_PATH}`，socket agent 將以隨機策略暫代（可訓練後再載入模型）。")
        return None
    print(f"[INFO] 從 `{MODEL_PATH}` 載入 DQN 模型...")
    try:
        model = DQN.load(MODEL_PATH)
        print("[INFO] DQN 模型載入完成。")
        return model
    except Exception as e:
        print(f"[ERROR] 載入模型失敗: {e}\n將以隨機策略繼續運作。")
        return None


def board_to_obs(board: Any) -> np.ndarray:
    """二維 board(list of list) -> 一維 np.ndarray[int32]。"""
    arr = np.array(board, dtype=np.int32)
    return arr.flatten()


def handle_init(msg: Dict[str, Any]) -> int:
    """處理 INIT，回傳 board_size。"""
    payload = msg.get("payload", {}) or {}
    board_size = int(payload.get("board_size", 0))
    print(f"[INIT] 收到棋盤大小 board_size={board_size}")
    if board_size <= 0:
        raise ValueError("INIT payload 中的 board_size 不合法")
    return board_size


def handle_state(
    sock: socket.socket,
    model: Optional[DQN],
    msg: Dict[str, Any],
    board_size: int,
) -> Tuple[bool, float]:
    """
    處理 STATE:
    - 解析 board/reward/done
    - 用模型預測 action
    - 經由 sock 回傳 ACTION
    回傳 (done, reward)
    """
    global _step_counter
    payload = msg.get("payload", {}) or {}

    board = payload.get("board")
    reward = float(payload.get("reward", 0.0))
    done = bool(payload.get("done", False))

    if board is None:
        raise ValueError("STATE payload 缺少 board 欄位")

    obs = board_to_obs(board)
    if obs.size != board_size * board_size:
        raise ValueError(f"obs size {obs.size} 與 board_size^2 {board_size * board_size} 不符")

    # 解析 board 簡要資訊：蛇長度、食物位置（第一個找到的）、head 猜測
    arr = np.array(board, dtype=np.int32)
    ones = np.argwhere(arr == 1)
    twos = np.argwhere(arr == 2)
    snake_len = len(ones)
    head_guess = (int(ones[0][1]), int(ones[0][0])) if snake_len > 0 else None
    food_pos = (int(twos[0][1]), int(twos[0][0])) if len(twos) > 0 else None
    # obs hash 用於快速判斷前後狀態是否一樣
    import hashlib

    obs_hash = hashlib.md5(obs.tobytes()).hexdigest()[:8]

    # Decide action: debug override -> epsilon-greedy -> model.predict
    _step_counter += 1

    if DEBUG_FORCE_RANDOM or model is None:
        action_int = int(_rand.randint(0, 3))
    else:
        # epsilon-greedy
        if USE_EPSILON and _rand.random() < EPSILON:
            action_int = int(_rand.randint(0, 3))
        else:
            # SB3 expects the same dtype as training data; use float32
            obs_for_pred = obs.astype(np.float32)
            try:
                # try direct predict
                action_pred, _ = model.predict(obs_for_pred, deterministic=DETERMINISTIC_PREDICT)
            except Exception:
                # try reshaped batch
                action_pred, _ = model.predict(obs_for_pred.reshape(1, -1), deterministic=DETERMINISTIC_PREDICT)
            # normalize returned action
            if isinstance(action_pred, (list, tuple, np.ndarray)):
                action_int = int(np.array(action_pred).reshape(-1)[0])
            else:
                action_int = int(action_pred)

    # record action distribution for debug
    _action_counter[action_int] += 1
    if _step_counter % ACTION_LOG_INTERVAL == 0:
        print(f"[ACTION_STATS] steps={_step_counter} counts={dict(_action_counter)}")

    # 這裡用真正的 sock 送出 ACTION 封包（含重試）
    try:
        send_msg(sock, "ACTION", {"action": action_int})
    except Exception as e:
        print(f"[ERROR] 傳送 ACTION 失敗: {e}")
        raise

    # 更詳細的除錯輸出
    print(
        f"[STATE] reward={reward:.3f}, done={done}, action={action_int}, "
        f"snake_len={snake_len}, head={head_guess}, food={food_pos}, obs_hash={obs_hash}"
    )

    return done, reward


def main() -> None:
    print(f"[INFO] BASE_DIR={BASE_DIR}")
    print(f"[INFO] 嘗試連線到 {HOST}:{PORT} ...")

    # 先載入模型
    model = load_model()

    with socket.create_connection((HOST, PORT)) as sock:
        print("[INFO] 已連線到 Java Server。等待 INIT...")

        board_size: Optional[int] = None
        episode_idx = 0
        ep_reward = 0.0
        in_episode = False

        while True:
            msg = recv_msg(sock)
            msg_type = msg.get("type")

            if msg_type == "INIT":
                board_size = handle_init(msg)
                # INIT 表示 environment 已準備好，視為第一局開始
                episode_idx = 1
                ep_reward = 0.0
                in_episode = True
                print(f"[INFO] 進入主迴圈，開始第 {episode_idx} 局，等待 STATE / RESET / PING ...")

            elif msg_type == "STATE":
                if board_size is None:
                    raise RuntimeError("尚未收到 INIT，卻收到了 STATE")
                done, r = handle_state(sock, model, msg, board_size)
                ep_reward += r
                if done:
                    # 當收到 done=True 時，印出該局結束資訊
                    print(f"[EPISODE END] 第 {episode_idx} 局結束，總 reward={ep_reward:.3f}")
                    in_episode = False
                    ep_reward = 0.0

            elif msg_type == "RESET":
                # Java 發出 RESET，表示新的局要開始
                episode_idx += 1
                in_episode = True
                ep_reward = 0.0
                print(f"[RESET] Java 發出 RESET，開始第 {episode_idx} 局")

            elif msg_type == "PING":
                send_msg(sock, "PONG", {})
                print("[PING] 收到 PING，已回 PONG。")

            else:
                print(f"[WARN] 收到未知 type: {msg_type}, msg={msg}")


if __name__ == "__main__":
    main()
