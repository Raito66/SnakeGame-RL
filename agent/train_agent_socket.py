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

        # --- Feature extraction wrapper: 将稀疏的盘面展平转为紧凑特征向量 ---
        class FeatureExtractWrapper(gym.ObservationWrapper):
            def __init__(self, env):
                super().__init__(env)
                # we will set observation_space dynamically after first observation
                self._set_space = False

            def observation(self, obs):
                import numpy as _np
                arr = _np.asarray(obs)
                # assume board part is first n*n when extras not provided
                # try to detect board_size by sqrt
                total = arr.size
                # if extras exist, we try to find n by searching for perfect square <= total
                n = int(_np.sqrt(total))
                if n * n != total:
                    # try to use known board_size via env if available
                    try:
                        n = env.board_size
                    except Exception:
                        n = int(_np.sqrt(total))
                board = arr[: n * n].reshape((n, n))
                # find head (first 1) and food (first 2)
                ones = _np.argwhere(board == 1)
                twos = _np.argwhere(board == 2)
                if ones.shape[0] > 0:
                    head_y, head_x = ones[0]
                else:
                    head_x = head_y = n // 2
                if twos.shape[0] > 0:
                    food_y, food_x = twos[0]
                else:
                    food_x = food_y = n // 2
                # normalized relative vector from head to food in [-1,1]
                dx = (food_x - head_x) / float(n)
                dy = (food_y - head_y) / float(n)
                snake_len = float(ones.shape[0]) / float(n * n)
                # also include distance normalized
                manhattan = abs(food_x - head_x) + abs(food_y - head_y)
                maxd = 2 * n
                dnorm = manhattan / float(maxd)

                feat = _np.array([dx, dy, snake_len, dnorm], dtype=_np.float32)
                if not self._set_space:
                    from gymnasium import spaces as _spaces
                    self.observation_space = _spaces.Box(low=-1.0, high=1.0, shape=feat.shape, dtype=_np.float32)
                    self._set_space = True
                return feat

        env = FeatureExtractWrapper(env)

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
                    if self.step_counter % max(1, self.log_interval) == 0:
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
            def __init__(self, env, log_interval=100):
                super().__init__(env)
                self.step_counter = 0
                self.action_counts = [0, 0, 0, 0]
                self.log_interval = log_interval

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
                if self.step_counter % max(1, self.log_interval) == 0:
                    print(f"[ActionLoggingWrapper] step={self.step_counter}, action_counts={self.action_counts}")
                return super().step(action)

        # Wrap order: first observation logger, then action logger, then Monitor will be applied by make_env
        env = ObservationLoggingWrapper(env, log_interval=10)
        env = ActionLoggingWrapper(env, log_interval=10)
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
    policy_kwargs = dict(net_arch=[512, 256])
    # 更激進但更注重探索的設定（臨時，用於快速觀察是否能學會轉彎）
    # - learning_rate 較高
    # - buffer_size 較小，讓新經驗影響更快
    # - learning_starts 設為 100，較早開始學習
    # - train_freq=1 每步學習
    # - exploration: epsilon 從 1.0 衰減到 0.3（保留大量隨機性以避免過早收斂）
    model = DQN(
        "MlpPolicy",
        env,
        learning_rate=5e-4,
        buffer_size=8000,
        learning_starts=100,
        batch_size=64,
        gamma=0.97,
        train_freq=1,
        target_update_interval=200,
        exploration_fraction=0.3,
        exploration_initial_eps=1.0,
        exploration_final_eps=0.05,
        policy_kwargs=policy_kwargs,
        verbose=1,
        tensorboard_log=TB_LOG_DIR,
    )

    print("[train_agent_socket] DQN hyperparams:")
    print(f"  learning_rate=5e-4, buffer_size=8000, learning_starts=100, batch_size=64")
    print(f"  exploration: {1.0} -> {0.05} over fraction {0.3}")

    checkpoint_callback = CheckpointCallback(
        save_freq=max(1, (total_timesteps // 10) if total_timesteps>0 else 1000),
        save_path=os.path.join(base_dir, "checkpoints_socket"),
        name_prefix="dqn_snake_socket",
    )

    # Optional: eval callback to save best model (requires a separate eval env)
    try:
        from stable_baselines3.common.callbacks import EvalCallback
        eval_env = None
        # We can't create a reliable eval env before Java server is running; skip if fails
        try:
            eval_env = JavaSnakeSocketEnv()
            eval_env = Monitor(eval_env)
        except Exception:
            eval_env = None
        if eval_env is not None:
            eval_cb = EvalCallback(eval_env, best_model_save_path=os.path.join(base_dir, 'best_model_socket'),
                                   log_path=os.path.join(base_dir, 'eval_logs_socket'), eval_freq=max(1000, total_timesteps//20),
                                   deterministic=True, render=False)
        else:
            eval_cb = None
    except Exception:
        eval_cb = None

    print("開始訓練 DQN (socket 版)... 請確認 Java Socket Server 已啟動且在等待 Python 連線")
    # Combine callbacks
    callbacks = [checkpoint_callback]
    if 'eval_cb' in locals() and eval_cb is not None:
        callbacks.append(eval_cb)

    model.learn(total_timesteps=total_timesteps, callback=callbacks)
    print("訓練結束，儲存模型中...")

    model.save(model_path)
    print(f"模型已儲存到 `{model_path}`")


if __name__ == "__main__":
    main()
