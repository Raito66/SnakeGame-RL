@echo off
chcp 65001 >nul

REM ===============================
REM 進入專案目錄
REM ===============================
cd /d D:\workspace\SnakeGame-RL

REM ===============================
REM 刪除舊的 .class 檔案
REM ===============================
if exist src\light\*.class (
    del /q src\light\*.class
)

REM ===============================
REM 編譯 src\light 下所有 .java 檔案（含 gson）
REM ===============================
set HAS_JAVA_FILES=false
for %%f in (src\light\*.java) do (
    set HAS_JAVA_FILES=true
    echo ===== 編譯 %%f =====
    javac -encoding UTF-8 -cp "src;lib\gson-2.10.1.jar" "%%f"
)

REM ===============================
REM 列出編譯結果
REM ===============================
echo.
echo ===== 編譯結果 =====
dir src\light\*.class
echo.

REM ===============================
REM 檢查 class 是否存在
REM ===============================
if not exist src\light\SocketSnakeServerGame.class (
    echo [錯誤] 找不到 src\light\SocketSnakeServerGame.class
    echo 請檢查是否有語法錯誤、或 Java 原始碼檔名/路徑錯誤。
    pause
    goto :eof
)

REM ===============================
REM 啟動 Java Socket Server
REM ===============================
start "Java Socket Server" cmd /k ^
    cd /d D:\workspace\SnakeGame-RL ^&^& ^
    java -cp "src;lib\gson-2.10.1.jar" light.SocketSnakeServerGame

REM ===============================
REM 等待 2 秒，讓 server 啟動
REM ===============================
timeout /t 2 /nobreak >nul

REM ===============================
REM 啟動 Python 訓練代理人 (train_agent_socket.py)
REM ===============================
start "Python Train Agent" cmd /k ^
    cd /d D:\workspace\SnakeGame-RL\agent ^&^& ^
    python train_agent_socket.py

echo.
echo 已啟動：Java Socket 伺服器 + Python 訓練 agent（在新視窗）。
echo 若你只想觀察，不要同時啟動其他會 send ACTION 的 client（如 random_play_socket.py），以免搶連線。
pause