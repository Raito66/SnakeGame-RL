import os
import json
import argparse
from typing import List

import matplotlib
import matplotlib.pyplot as plt
import numpy as np
from stable_baselines3 import DQN

from mem_snake_env import MemSnakeEnv

# 嘗試設定中文字型（Windows 常用）
matplotlib.rcParams['font.sans-serif'] = ['Microsoft JhengHei', 'SimHei', 'Arial']
matplotlib.rcParams['axes.unicode_minus'] = False

BASE_DIR = os.path.dirname(__file__)
MODEL_PATH = os.path.join(BASE_DIR, "dqn_snake_model")
EVAL_JSON = os.path.join(BASE_DIR, "eval_rewards.json")
PLOT_PNG = os.path.join(BASE_DIR, "learning_curve.png")


def evaluate(model_path: str, episodes: int = 100, max_steps: int = 200) -> List[float]:
    env = MemSnakeEnv(size=20, max_steps=max_steps)

    # load model without passing env to avoid spaces-check issues
    model = DQN.load(model_path)

    rewards = []
    total_reward = 0.0

    print(f"開始評估，總共要跑 {episodes} 局，每局最多 {max_steps} 步")

    for ep in range(episodes):
        # gymnasium reset returns (obs, info)
        reset_result = env.reset()
        if isinstance(reset_result, tuple):
            obs, _info = reset_result
        else:
            obs = reset_result
        ep_reward = 0.0
        for _ in range(max_steps):
            action, _ = model.predict(obs, deterministic=True)
            res = env.step(int(action))
            # support both gym and gymnasium-style returns
            if len(res) == 5:
                obs, reward, terminated, truncated, _info = res
                done = bool(terminated or truncated)
            else:
                obs, reward, done, _info = res
            ep_reward += float(reward)
            if done:
                break
        rewards.append(float(ep_reward))
        total_reward += ep_reward
        # 中文輸出每局結果
        print(f"評估：第 {ep+1}/{episodes} 局，總獎勵 = {ep_reward:.2f}")

    avg = total_reward / episodes if episodes > 0 else 0.0
    print(f"評估結束，{episodes} 局平均總獎勵 = {avg:.2f}")
    env.close()
    return rewards


def plot_rewards(rewards: List[float], out_png: str):
    episodes_idx = list(range(1, len(rewards) + 1))
    plt.figure(figsize=(10, 5))
    plt.plot(episodes_idx, rewards, marker="o", label="每局總獎勵")
    if len(rewards) >= 5:
        window = 5
        ma = []
        for i in range(len(rewards)):
            start = max(0, i - window + 1)
            window_vals = rewards[start:i + 1]
            ma.append(sum(window_vals) / len(window_vals))
        plt.plot(episodes_idx, ma, label=f"移動平均 (window={window})")
    plt.xlabel("局數")
    plt.ylabel("總獎勵")
    plt.title("學習曲線（評估）")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.savefig(out_png)
    print(f"已將學習曲線圖存成：{out_png}")
    try:
        plt.show()
    except Exception:
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--episodes", type=str, default="100",
                        help="要評估的局數，傳 0 或 'all' 代表使用 agent/config.json 中的 episodes 值")
    parser.add_argument("--max_steps", type=int, default=200)
    args = parser.parse_args()

    # episodes may be a string 'all' or '0' to mean read from config.json
    episodes_arg = args.episodes
    if isinstance(episodes_arg, str) and (episodes_arg.lower() == 'all' or episodes_arg == '0'):
        # try to read agent/config.json
        cfg_path = os.path.join(BASE_DIR, 'config.json')
        try:
            with open(cfg_path, 'r', encoding='utf-8') as f:
                cfg = json.load(f)
            episodes = int(cfg.get('episodes', 100))
            print(f"從 {cfg_path} 讀取 episodes={episodes}")
        except Exception:
            episodes = 100
            print(f"無法讀取 {cfg_path}，改用預設 episodes={episodes}")
    else:
        episodes = int(episodes_arg)
    max_steps = args.max_steps

    if not (os.path.isdir(MODEL_PATH) or os.path.exists(MODEL_PATH + ".zip")):
        print(f"找不到模型：{MODEL_PATH}；請先訓練或放入模型。")
        return

    # Warn when running a very large evaluation
    if episodes > 10000:
        print(f"警告：你要評估 {episodes} 局，這可能需要很長時間（數小時視設定與硬體而定）。")
    print(f"開始評估 {episodes} 局，每局最多 {max_steps} 步...")

    rewards = evaluate(MODEL_PATH, episodes=episodes, max_steps=max_steps)

    # 以中文表格列印全部局的獎勵，並存成 JSON
    data = {"episodes": episodes, "max_steps": max_steps, "rewards": rewards}
    with open(EVAL_JSON, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"已將評估結果寫入：{EVAL_JSON}")

    # 印出表格（中文）
    print("\n評估結果表：")
    print("局數\t總獎勵")
    for i, r in enumerate(rewards, start=1):
        print(f"{i}\t{r:.2f}")

    plot_rewards(rewards, PLOT_PNG)


if __name__ == "__main__":
    main()
