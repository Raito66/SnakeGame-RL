import os
import json

import gymnasium as gym
from gymnasium import spaces
from stable_baselines3 import DQN
from stable_baselines3.common.callbacks import CheckpointCallback
from stable_baselines3.common.monitor import Monitor
from stable_baselines3.common.vec_env import DummyVecEnv

from snake_socket_env import JavaSnakeSocketEnv

BASE_DIR = os.path.dirname(__file__)
TB_LOG_DIR = os.path.join(BASE_DIR, "tb_logs_socket")
DEFAULT_PER_EPISODE = 200  # 用於估算每局步數（當 max_steps<=0 時）


def load_config() -> dict:
    base_dir = os.path.dirname(__file__)
    config_path = os.path.join(base_dir, "config.json")

    episodes = 100
    max_steps = 200
    total_timesteps = None

    if os.path.exists(config_path):
        with open(config_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        episodes = int(data.get("episodes", episodes))
        max_steps = int(data.get("max_steps", max_steps))
        if "total_timesteps" in data:
            try:
                total_timesteps = int(data.get("total_timesteps"))
            except Exception:
                total_timesteps = None

    return {"episodes": episodes, "max_steps": max_steps, "total_timesteps": total_timesteps}


def make_env():
    # create a SB3-compatible vectorized env wrapping the socket env
    def _init():
        env = JavaSnakeSocketEnv()

        # --- Observation logging wrapper: 檢查輸入給模型的 obs 是否有資訊 ---
        class ObservationLoggingWrapper(gym.ObservationWrapper):
            def __init__(self, env, log_interval=100):
                super().__init__(env)
                self.step_counter = 0
                self.log_interval = log_interval

            def observation(self, obs):
                # obs 可能是 numpy array
                try:
                    import numpy as _np
                    arr = _np.asarray(obs)
                    self.step_counter += 1
                    if self.step_counter % self.log_interval == 0:
                        # 打印基本統計量與一個簡單 hash
                        vmin = float(arr.min())
                        vmax = float(arr.max())
                        mean = float(arr.mean())
                        # 計算前 10 個 unique values
                        unique_vals = _np.unique(arr).tolist()
                        flat = arr.flatten()
                        # quick hash
                        hv = hex(int(abs(float(flat.sum())) * 1000000) & 0xFFFFFFFF)
                        print(f"[ObsLog] step={self.step_counter}, shape={arr.shape}, min={vmin}, max={vmax}, mean={mean:.4f}, uniques={unique_vals[:10]}, hash={hv}")
                except Exception as ex:
                    print(f"[ObsLog] 無法處理 obs 統計: {ex}")
                return obs

        # --- Action logging wrapper: 記錄訓練期間 action 分布，幫助診斷 agent 是否總是輸出同一動作 ---
        class ActionLoggingWrapper(gym.Wrapper):
            def __init__(self, env):
                super().__init__(env)
                self.step_counter = 0
                self.action_counts = [0, 0, 0, 0]

            def step(self, action):
                # action 有可能是 numpy array (vec env)，嘗試取第一個
                try:
                    a = int(action)
                except Exception:
                    import numpy as _np
                    if isinstance(action, (list, tuple)):
                        a = int(action[0])
                    elif isinstance(action, _np.ndarray):
                        a = int(action.flatten()[0])
                    else:
                        a = int(action)
                if 0 <= a < 4:
                    self.action_counts[a] += 1
                self.step_counter += 1
                if self.step_counter % 100 == 0:
                    print(f"[ActionLoggingWrapper] step={self.step_counter}, action_counts={self.action_counts}")
                return super().step(action)

        # Wrap order: first observation logger, then action logger, then Monitor will be applied by make_env
        env = ObservationLoggingWrapper(env)
        env = ActionLoggingWrapper(env)
        return Monitor(env)
    return DummyVecEnv([_init])


def main():
    base_dir = os.path.dirname(__file__)
    model_path = os.path.join(base_dir, "dqn_snake_model_socket")

    # ensure tensorboard log dir exists
    os.makedirs(TB_LOG_DIR, exist_ok=True)

    params = load_config()
    episodes = params["episodes"]
    max_steps = params["max_steps"]
    cfg_total = params.get("total_timesteps")

    # safety: if max_steps is extremely large (e.g. INT_MAX), warn and cap for calculation
    if max_steps > 10_000_000:
        print(f"警告: config max_steps={max_steps} 非常大，將在計算 total_timesteps 時使用 1000000 作為單局估計值以避免過大。")
        estimated_per_episode = 1_000_000
    else:
        # Interpret max_steps == 0 as "no per-episode cap" (由環境決定).
        # For total_timesteps estimation we fall back to DEFAULT_PER_EPISODE.
        if max_steps <= 0:
            estimated_per_episode = DEFAULT_PER_EPISODE
            print(f"注意: max_steps={max_steps} 被視為無上限；在估算 total_timesteps 時會使用預設每局 {DEFAULT_PER_EPISODE} 步作為估計。"
                  + " 若要精確控制總訓練步數，請在 config.json 設定 'total_timesteps' 或使用 CLI/ENV 覆寫。")
        else:
            estimated_per_episode = max(DEFAULT_PER_EPISODE, max_steps)

    if cfg_total is not None:
        total_timesteps = cfg_total
    else:
        total_timesteps = episodes * estimated_per_episode

    print(f"episodes={episodes}, max_steps={max_steps}, total_timesteps={total_timesteps}")

    # Build env (Java server must be running and accepting connection before env connects)
    env = make_env()

    # DQN hyperparameters tuned for faster learning in small grid-world
    # 增加網路容量與調整超參數以改進學習
    policy_kwargs = dict(net_arch=[256, 256])
    # 設置更強的 epsilon-greedy 探索策略，讓 agent 在訓練早期能更多隨機行動
    model = DQN(
        "MlpPolicy",
        env,
        learning_rate=5e-4,
        buffer_size=50000,
        learning_starts=1,  # 盡早開始學習（診斷用）
        batch_size=32,
        gamma=0.99,
        train_freq=1,  # 每步都 update
        target_update_interval=500,
        exploration_fraction=1.0,  # 將 epsilon 衰減期拉長到整個訓練期
        exploration_initial_eps=1.0,
        exploration_final_eps=1.0,  # 保留較高隨機性，促進探索
        policy_kwargs=policy_kwargs,
        verbose=1,
        tensorboard_log=TB_LOG_DIR,
    )

    checkpoint_callback = CheckpointCallback(
        save_freq=max(1, (total_timesteps // 10) if total_timesteps>0 else 1000),
        save_path=os.path.join(base_dir, "checkpoints_socket"),
        name_prefix="dqn_snake_socket",
    )

    print("開始訓練 DQN (socket 版)... 請確認 Java Socket Server 已啟動且在等待 Python 連線")
    model.learn(total_timesteps=total_timesteps, callback=checkpoint_callback)
    print("訓練結束，儲存模型中...")

    model.save(model_path)
    print(f"模型已儲存到 `{model_path}`")


if __name__ == "__main__":
    main()
