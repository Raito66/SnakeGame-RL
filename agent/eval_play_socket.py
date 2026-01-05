import json
import socket
import os
from typing import Any, Dict, Tuple

import numpy as np
from stable_baselines3 import DQN


HOST = "127.0.0.1"
PORT = 50007  # 和 Java 端要對齊


def parse_state(line: str) -> Tuple[np.ndarray, float, bool, Dict[str, Any]]:
    """把 Java 傳來的一行 JSON 轉成 obs / reward / done / info。"""
    state = json.loads(line)
    board = np.array(state["board"], dtype=np.int32)
    flat = board.flatten()
    reward = float(state.get("reward", 0.0))
    done = bool(state.get("done", False))
    info = {"raw_state": state}
    return flat, reward, done, info


def main() -> None:
    base_dir = os.path.dirname(__file__)
    model_path = os.path.join(base_dir, "dqn_snake_model")
    print(f"從 `{model_path}` 載入 DQN 模型...")
    model: DQN = DQN.load(model_path)

    # 建立 TCP 連線到 Java
    with socket.create_connection((HOST, PORT)) as sock:
        # 用 file-like 介面方便逐行讀寫
        sock_file_r = sock.makefile("r", encoding="utf-8")
        sock_file_w = sock.makefile("w", encoding="utf-8")

        print("已連線到 Java snake socket server，開始 RL 控制循環...")

        while True:
            # 讀一行 state JSON，若 Java 關連線，readline() 會回 "" 跳出
            line = sock_file_r.readline()
            if not line:
                print("Java 端關閉連線，結束。")
                break

            line = line.strip()
            if not line:
                continue

            # 解析狀態
            try:
                obs, reward, done, _info = parse_state(line)
            except Exception as e:
                print(f"解析 state JSON 失敗: {e}, line={line!r}")
                continue

            # 若本局 done，通常等下一局的 state 就好，仍需回傳一個動作避免堵塞
            if done:
                # 你可以固定回 0 或直接略過，視 Java 實作而定
                action = 0
            else:
                # 模型預測動作
                action_int, _ = model.predict(obs, deterministic=True)
                action = int(action_int)

            # 回傳一行 action JSON
            msg = json.dumps({"action": action}, ensure_ascii=False)
            sock_file_w.write(msg + "\n")
            sock_file_w.flush()


if __name__ == "__main__":
    main()
