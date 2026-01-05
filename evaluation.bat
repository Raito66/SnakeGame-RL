@echo off
REM ================================
REM 切到專案根目錄
REM ================================
cd /d D:\workspace\SnakeGame-RL

echo [INFO] 目前目錄：%cd%
echo.

REM ================================
REM 檢查模型檔是否存在
REM ================================
if not exist agent\dqn_snake_model.zip if not exist agent\dqn_snake_model (
    echo [錯誤] 找不到模型檔 dqn_snake_model，請先執行 `agent\train_agent.py` 訓練。
    pause
    goto :EOF
)

REM ================================
REM 啟動 Python 評估腳本 eval_agent.py
REM ================================
cd /d D:\workspace\SnakeGame-RL\agent
echo [INFO] 啟動 Python 評估腳本 eval_agent.py ...
start "Python Eval" cmd /k python eval_agent.py
echo [INFO] 已在新視窗啟動評估。
echo.

pause
