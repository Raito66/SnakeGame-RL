import os
import json
from typing import List

import matplotlib.pyplot as plt
from stable_baselines3 import DQN

from snake_gym_env import JavaSnakeEnv


def evaluate(model_path: str, episodes: int = 20, max_steps: int = 200) -> List[float]:
    """
    跑多局評估，回傳每一局的總 reward list，並在 console 印資訊。
    """
    env = JavaSnakeEnv()
    model = DQN.load(model_path, env=env)
    total_reward = 0.0
    rewards: List[float] = []

    for ep in range(episodes):
        reset_result = env.reset()
        if isinstance(reset_result, tuple):
            obs, _info = reset_result
        else:
            obs = reset_result

        done = False
        ep_reward = 0.0

        for _step in range(max_steps):
            action, _ = model.predict(obs, deterministic=True)
            step_result = env.step(action)

            # 兼容 4 或 5 個回傳值
            if len(step_result) == 5:
                obs, reward, done, _trunc, _info = step_result
            else:
                obs, reward, done, _info = step_result

            ep_reward += reward
            if done:
                break

        print(f"Eval episode {ep + 1}/{episodes} reward={ep_reward:.2f}")
        total_reward += ep_reward
        rewards.append(float(ep_reward))

    avg_reward = total_reward / episodes
    print(f"Average reward over {episodes} episodes: {avg_reward:.2f}")
    env.close()
    return rewards


def plot_learning_curve(base_dir: str):
    """
    優先從 eval_rewards.json 讀取 reward 資料畫學習曲線。
    若檔案不存在，就直接 evaluate 一次再畫。
    """
    rewards_file = os.path.join(base_dir, "eval_rewards.json")
    model_path = os.path.join(base_dir, "dqn_snake_model")

    if os.path.exists(rewards_file):
        print("從 `eval_rewards.json` 載入 reward 資料畫圖...")
        with open(rewards_file, "r", encoding="utf-8") as f:
            data = json.load(f)
        rewards = data.get("rewards", [])
        if not rewards:
            print("eval_rewards.json 中沒有 rewards 欄位或為空，改為即時 evaluate。")
            rewards = evaluate(model_path, episodes=20, max_steps=data.get("max_steps", 200))
    else:
        print("找不到 `eval_rewards.json`，改為即時 evaluate 模型...")
        rewards = evaluate(model_path, episodes=20, max_steps=200)

    if not rewards:
        print("沒有 reward 資料可畫圖。")
        return

    episodes_idx = list(range(1, len(rewards) + 1))

    plt.figure(figsize=(8, 4))
    plt.plot(episodes_idx, rewards, marker="o", label="Episode Reward")

    # 簡單 moving average 平滑一下
    if len(rewards) >= 5:
        window = 5
        ma = []
        for i in range(len(rewards)):
            start = max(0, i - window + 1)
            window_vals = rewards[start:i + 1]
            ma.append(sum(window_vals) / len(window_vals))
        plt.plot(episodes_idx, ma, label=f"Moving Avg (window={window})")

    plt.xlabel("Episode")
    plt.ylabel("Total Reward")
    plt.title("Learning Curve")
    plt.grid(True)
    plt.legend()
    plt.tight_layout()
    plt.show()


if __name__ == "__main__":
    base_dir = os.path.dirname(__file__)
    plot_learning_curve(base_dir)
