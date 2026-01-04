import os
import json

from stable_baselines3 import DQN
from stable_baselines3.common.monitor import Monitor
from stable_baselines3.common.vec_env import DummyVecEnv

from snake_gym_env import JavaSnakeEnv


def load_config():
    """
    從同一目錄下的 config.json 讀訓練設定。
    若檔案不存在，回傳預設值：
    { "episodes": 100, "max_steps": 200 }
    """
    config_path = os.path.join(os.path.dirname(__file__), "config.json")
    if not os.path.exists(config_path):
        return {"episodes": 100, "max_steps": 200}

    with open(config_path, "r", encoding="utf-8") as f:
        return json.load(f)


def make_env():
    def _init():
        env = JavaSnakeEnv()
        env = Monitor(env)
        return env
    return _init


def evaluate_model(model, episodes=20, max_steps=200):
    """
    用訓練好的 model 跑多局，回傳每局總 reward list。
    """
    env = JavaSnakeEnv()
    rewards = []

    for ep in range(episodes):
        reset_result = env.reset()
        # gymnasium 0.26+ reset 回傳 (obs, info)
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

        rewards.append(float(ep_reward))
        print(f"[Train後評估] Episode {ep + 1}/{episodes} reward={ep_reward:.2f}")

    env.close()
    return rewards


def main():
    base_dir = os.path.dirname(__file__)

    # 讀取設定
    config = load_config()
    EPISODES = config.get("episodes", 100)
    MAX_STEPS = config.get("max_steps", 200)

    print(f"使用設定：episodes={EPISODES}, max_steps={MAX_STEPS}")

    # 建立向量化環境
    env = DummyVecEnv([make_env()])

    # 建立 DQN 模型
    model = DQN(
        "MlpPolicy",
        env,
        verbose=1,
        buffer_size=50_000,
        learning_starts=1_000,
        batch_size=32,
        gamma=0.99,
        train_freq=4,
        target_update_interval=1_000,
    )

    # 總步數 = episodes * max_steps
    total_timesteps = EPISODES * MAX_STEPS
    model.learn(total_timesteps=total_timesteps)

    # 儲存模型
    model_path = os.path.join(base_dir, "dqn_snake_model")
    model.save(model_path)
    print(f"訓練完畢，模型已儲存：{model_path}")

    # === 重要：訓練後評估幾局並儲存 reward，給 eval_agent 畫學習曲線 ===
    eval_episodes = min(EPISODES, 50)  # 最多50點，避免太擠
    print(f"開始訓練後評估，共 {eval_episodes} 局...")
    rewards = evaluate_model(model, episodes=eval_episodes, max_steps=MAX_STEPS)

    rewards_path = os.path.join(base_dir, "eval_rewards.json")
    with open(rewards_path, "w", encoding="utf-8") as f:
        json.dump(
            {
                "episodes": eval_episodes,
                "max_steps": MAX_STEPS,
                "rewards": rewards,
            },
            f,
            ensure_ascii=False,
            indent=2,
        )
    print(f"評估結果已儲存：{rewards_path}")

    env.close()


if __name__ == "__main__":
    main()
