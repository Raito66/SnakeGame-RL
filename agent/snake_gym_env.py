import gymnasium as gym
from gymnasium import spaces
import numpy as np
import json
import time
import os

class JavaSnakeEnv(gym.Env):
    def __init__(self):
        super().__init__()
        self.board_size = self.detect_board_size()
        self.action_space = spaces.Discrete(4)  # 上下左右
        self.observation_space = spaces.Box(0, 2, shape=(self.board_size*self.board_size,), dtype=np.int32)

    def detect_board_size(self):
        # 自動偵測 board 尺寸（只在初始化時執行一次）
        while not os.path.exists('../game_state.json'):
            time.sleep(0.05)
        while True:
            try:
                with open('../game_state.json', 'r') as f:
                    state = json.load(f)
                board = state['board']
                size = len(board)
                return size
            except Exception:
                time.sleep(0.05)

    def reset(self, seed=None, options=None):
        # 直接等待 Java 自動 newGame
        obs = None
        while True:
            if not os.path.isfile('../game_state.json'):
                time.sleep(0.05)
                continue
            try:
                with open('../game_state.json', 'r') as f:
                    state = json.load(f)
                board = np.array(state['board']).flatten()
                obs = board
                break
            except:
                time.sleep(0.05)
        return obs, {}

    def step(self, action):
        # 寫 action 給 Java
        with open("../action.json", "w") as f:
            json.dump({"action": int(action)}, f)
        # 等新狀態
        while True:
            try:
                with open("../game_state.json") as f:
                    s = json.load(f)
                board = np.array(s['board']).flatten()
                reward = s['reward']
                done = s['done']
                break
            except:
                time.sleep(0.02)
        return board, reward, done, False, {}