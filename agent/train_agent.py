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


def main():
    # 讀取設定
    config = load_config()
    EPISODES = config.get("episodes", 100)
    MAX_STEPS = config.get("max_steps", 200)

    print(f"使用設定：episodes={EPISODES}, max_steps={MAX_STEPS}")

    # 用 DummyVecEnv 包一層，這是 SB3 建議的用法
    env = DummyVecEnv([make_env()])

    # 建立 DQN 模型（可依需要調參）
    model = DQN(
        "MlpPolicy",
        env,
        verbose=1,
        buffer_size=50_000,
        learning_starts=1_000,   # 先隨機玩一陣子再開始學
        batch_size=32,
        gamma=0.99,
        train_freq=4,
        target_update_interval=1_000,
    )

    # 讓 SB3 自己控制「隨機探索＋學習」
    total_timesteps = EPISODES * MAX_STEPS
    model.learn(total_timesteps=total_timesteps)

    # 儲存模型
    model_path = os.path.join(os.path.dirname(__file__), "dqn_snake_model")
    model.save(model_path)
    print(f"訓練完畢，模型已儲存：{model_path}")

    env.close()


if __name__ == "__main__":
    main()
