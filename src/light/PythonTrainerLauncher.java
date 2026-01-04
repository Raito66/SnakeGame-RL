package light;

import javax.swing.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 統一負責：
 * 1\) 寫入 `agent/config.json`
 * 2\) 從 Java 呼叫 Python `train_agent.py`，同步等待結束
 */
public class PythonTrainerLauncher {

    // 依實際專案根目錄調整
    private static final String PROJECT_ROOT = "D:\\workspace\\SnakeGame-RL";
    private static final String AGENT_DIR = PROJECT_ROOT + "\\agent";
    private static final String CONFIG_PATH = AGENT_DIR + "\\config.json";

    /**
     * 將 episodes/max\_steps 寫入 `config.json`，並在同一個視窗中啟動 `python train_agent.py`。
     * 若訓練成功完成回傳 true，否則 false。
     */
    public static boolean runTrainingWithConfig(JFrame parent, int episodes, int maxSteps) {
        try {
            // 1\) 寫 config.json
            writeConfigJson(episodes, maxSteps);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    parent,
                    "寫入 config.json 失敗： " + e.getMessage(),
                    "錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }

        // 2\) 啟動 Python 訓練並同步等待結束
        return startPythonTrainerBlocking(parent);
    }

    /** 寫 `agent/config.json` */
    private static void writeConfigJson(int episodes, int maxSteps) throws IOException {
        File configFile = new File(CONFIG_PATH);
        File dir = configFile.getParentFile();
        if (dir != null && !dir.exists()) {
            if (!dir.mkdirs()) {
                throw new IOException("建立 agent 目錄失敗: " + dir.getAbsolutePath());
            }
        }

        // 與 `RunTrainer` 一樣的 JSON 結構
        String json = "{"
                + "\"episodes\":" + episodes + ","
                + "\"max_steps\":" + maxSteps
                + "}";

        try (FileWriter fw = new FileWriter(configFile, false)) {
            fw.write(json);
        }
    }

    /**
     * 在 `AGENT_DIR` 下執行：`python train_agent.py`，並等待程式結束。
     */
    private static boolean startPythonTrainerBlocking(JFrame parent) {
        ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c",
                "cd /d " + AGENT_DIR + " && python train_agent.py"
        );
        pb.directory(new File(AGENT_DIR));
        // 把 Python 的 stdout/stderr 直接印在目前啟動 Java 的 console
        pb.inheritIO();

        try {
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode != 0) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Python 訓練程式結束，但 exit code\=" + exitCode,
                        "訓練失敗",
                        JOptionPane.ERROR_MESSAGE
                );
                return false;
            }
            JOptionPane.showMessageDialog(
                    parent,
                    "Python 訓練完成！（exit code\=0）",
                    "訓練完成",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    parent,
                    "啟動 Python 訓練程式失敗： " + e.getMessage(),
                    "錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            JOptionPane.showMessageDialog(
                    parent,
                    "等待 Python 訓練程式時被中斷。",
                    "錯誤",
                    JOptionPane.ERROR_MESSAGE
            );
            return false;
        }
    }
}
