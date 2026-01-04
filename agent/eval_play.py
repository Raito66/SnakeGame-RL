import os
import time
import json
from typing import Optional

import numpy as np
from stable_baselines3 import DQN
from snake_gym_env import JavaSnakeEnv

GAME_STATE_PATH = "../game_state.json"
ACTION_PATH = "../action.json"
MODEL_PATH = "dqn_snake_model"  # `train_agent.py` 儲存的模型名稱


def wait_for_game_state(timeout: Optional[float] = None) -> None:
    """等待 `../game_state.json` 出現，Java SnakeWindow 一開就會寫第一個。"""
    print(f"等待 Java 產生 `{GAME_STATE_PATH}` ...")
    start = time.time()
    while not os.path.exists(GAME_STATE_PATH):
        time.sleep(0.05)
        if timeout is not None and (time.time() - start) > timeout:
            raise TimeoutError("等候 game_state.json 超時，請確認 Snake RL Viewer 已啟動。")
    print("偵測到 game_state.json。")


def read_state(board_size: int) -> np.ndarray:
    """讀取 `game_state.json`，回傳攤平成一維的 obs。"""
    while True:
        try:
            with open(GAME_STATE_PATH, "r", encoding="utf-8") as f:
                state = json.load(f)
            board = state["board"]  # 2D list
            arr = np.array(board, dtype=np.int32).reshape(board_size * board_size)
            return arr
        except Exception:
            # 可能正在寫檔，稍等再讀
            time.sleep(0.02)


def write_action(action: int) -> None:
    """把模型選好的 action 寫到 `action.json`，給 Java 讀。"""
    tmp_path = ACTION_PATH + ".tmp"
    data = {"action": int(action)}
    with open(tmp_path, "w", encoding="utf-8") as f:
        json.dump(data, f)
    os.replace(tmp_path, ACTION_PATH)


def main() -> None:
    print("載入模型中...")
    # 用 JavaSnakeEnv 偵測 board_size，保持跟訓練一致
    env = JavaSnakeEnv()
    board_size = env.board_size
    model = DQN.load(MODEL_PATH, env=env)
    print(f"模型載入完成，偵測到棋盤大小：{board_size}x{board_size}")

    # 確保有 game_state.json 可讀（SnakeWindow 開啟後 GameState.reset() 會寫一次）
    wait_for_game_state()

    print("開始自動玩（eval\_play），請在 Snake RL Viewer 按「開始自動玩(RL)」...")
    try:
        while True:
            obs = read_state(board_size)
            action, _ = model.predict(obs, deterministic=True)
            write_action(int(action))
            time.sleep(0.02)  # 避免讀寫過於頻繁
    except KeyboardInterrupt:
        print("收到中斷，結束 eval\_play。")
    finally:
        env.close()


if __name__ == "__main__":
    main()
