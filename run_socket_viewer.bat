@echo off
REM 檔案: run_socket_viewer.bat
REM 放在 D:\workspace\SnakeGame-RL

chcp 65001 >nul
cd /d D:\workspace\SnakeGame-RL

REM === 刪除舊的 .class ===
if exist src\light\*.class del /q src\light\*.class

REM === 編譯 src\light 底下所有 .java，加入 gson.jar ===
for %%f in (src\light\*.java) do (
    echo ===== 編譯 %%f =====
    javac -encoding UTF-8 -cp "src;lib\gson-2.10.1.jar" "%%f"
)

echo.
echo ===== 編譯結果 =====
dir src\light\*.class
echo.

REM === 檢查必要的 class 是否存在 ===
if not exist src\light\SocketSnakeServerGame.class (
    echo [錯誤] 找不到 SocketSnakeServerGame.class
    pause
    goto :eof
)

REM === 視窗1：啟動 Java socket server（含畫面） ===
start "Java Socket Server" cmd /k cd /d D:\workspace\SnakeGame-RL ^&^& java -cp "src;lib\gson-2.10.1.jar" light.SocketSnakeServerGame

REM 稍等 2 秒，讓 server 起來
timeout /t 2 /nobreak >nul

REM === 視窗2：啟動 Python socket_eval_play.py（RL agent） ===
start "Python Socket Agent" cmd /k cd /d D:\workspace\SnakeGame-RL\agent ^&^& python socket_eval_play.py

echo.
echo 已啟動：Java Socket 伺服器 + Python Socket Agent。
echo 訓練/自動玩期間請不要再啟動 SocketSnakeViewer，以免搶走連線。
pause

