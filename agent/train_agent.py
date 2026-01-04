from stable_baselines3 import DQN
from snake_gym_env import JavaSnakeEnv

env = JavaSnakeEnv()
model = DQN("MlpPolicy", env, verbose=1)
model.learn(total_timesteps=100_000)    # 可自行調整訓練步數
model.save("dqn_snake_model")
print("訓練完畢，模型已儲存。")