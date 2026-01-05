package light;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 統一處理：寫 config.json 並從 Java 啟動 Python 訓練程式。
 * 供 SnakeWindow 等 UI 呼叫。
 */
public class PythonTrainerLauncher {

    // 專案根目錄下的 agent 目錄，相對於執行路徑可視情況調整
    private static final String AGENT_DIR = "D:\\workspace\\SnakeGame-RL\\agent";
    private static final String CONFIG_PATH = AGENT_DIR + File.separator + "config.json";

    /**
     * 寫入 config.json 並啟動 Python 的 train_agent.py。
     * 此方法會阻塞直到 Python 訓練程式結束，回傳是否成功。
     *
     * \param parent   用來顯示錯誤訊息的父視窗，可為 null
     * \param episodes 局數
     * \param maxSteps 每局最大步數
     * \return true\=Python 正常結束且退出碼為 0；false\=發生例外或退出碼非 0
     */
    public static boolean runTrainingWithConfig(JFrame parent, int episodes, int maxSteps) {
        try {
            writeConfigJson(episodes, maxSteps);
        } catch (IOException e) {
            showError(parent, "寫入 `agent/config.json` 失敗:\\n" + e.getMessage());
            e.printStackTrace();
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "cmd.exe", "/c",
                    "cd /d " + AGENT_DIR + " && python train_agent.py"
            );
            pb.inheritIO(); // 讓 Python 輸出直接顯示在同一個 console
            Process process = pb.start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                showError(parent, "Python 訓練程式結束時返回碼非 0，exitCode =" + exitCode);
                return false;
            }
            return true;
        } catch (IOException e) {
            showError(parent, "啟動 Python 訓練程式失敗:\\n" + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            showError(parent, "等待 Python 訓練程式結束時被中斷。");
            return false;
        }
    }

    /**
     * 寫 `agent/config.json`，內容為 episodes / max_steps。
     */
    private static void writeConfigJson(int episodes, int maxSteps) throws IOException {
        File configFile = new File(CONFIG_PATH);
        File parentDir = configFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("無法建立目錄: " + parentDir.getAbsolutePath());
            }
        }

        String json = "{"
                + "\"episodes\":" + episodes + ","
                + "\"max_steps\":" + maxSteps
                + "}";

        try (FileWriter fw = new FileWriter(configFile)) {
            fw.write(json);
        }
    }

    private static void showError(JFrame parent, String message) {
        if (parent != null) {
            JOptionPane.showMessageDialog(parent, message, "錯誤", JOptionPane.ERROR_MESSAGE);
        } else {
            System.err.println(message);
        }
    }
}
