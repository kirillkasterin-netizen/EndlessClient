package dev.endless.util.neuro;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Автоматический запуск обучения модели из игры
 * Запускает отдельный процесс Python для обучения
 */
public class ModelTrainer {
    
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static Process trainingProcess = null;
    private static boolean isTraining = false;
    
    /**
     * Запускает обучение модели в отдельном процессе
     * @return CompletableFuture с результатом обучения
     */
    public static CompletableFuture<Boolean> startTraining() {
        if (isTraining) {
            sendMessage("§eОбучение уже запущено!");
            return CompletableFuture.completedFuture(false);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Проверяем наличие данных
                Path dataDir = Paths.get("endless/neuro_data");
                if (!Files.exists(dataDir) || !Files.isDirectory(dataDir)) {
                    sendMessage("§cДиректория с данными не найдена!");
                    sendMessage("§7Сначала соберите данные: .neuro record start");
                    return false;
                }
                
                // Проверяем наличие JSON файлов
                File[] dataFiles = dataDir.toFile().listFiles((dir, name) -> 
                    name.startsWith("rotation_data_") && name.endsWith(".json"));
                
                if (dataFiles == null || dataFiles.length == 0) {
                    sendMessage("§cДанные для обучения не найдены!");
                    sendMessage("§7Сначала соберите данные: .neuro record start");
                    return false;
                }
                
                sendMessage("§aНайдено §e" + dataFiles.length + " §aфайлов данных");
                sendMessage("§7Запуск обучения...");
                
                // Определяем команду Python
                String pythonCmd = findPythonCommand();
                if (pythonCmd == null) {
                    sendMessage("§cPython не найден!");
                    sendMessage("§7Установите Python 3.8+ с https://www.python.org/");
                    return false;
                }
                
                sendMessage("§7Используется: " + pythonCmd);
                
                // Проверяем зависимости
                if (!checkDependencies(pythonCmd)) {
                    sendMessage("§7Установка зависимостей...");
                    if (!installDependencies(pythonCmd)) {
                        sendMessage("§cОшибка установки зависимостей!");
                        return false;
                    }
                }
                
                // Запускаем обучение
                isTraining = true;
                sendMessage("§a=== Начато обучение модели ===");
                sendMessage("§7Это займет 5-10 минут на CPU");
                sendMessage("§7Вы можете продолжать играть");
                
                boolean success = runTraining(pythonCmd);
                
                if (success) {
                    sendMessage("§a=== Обучение завершено успешно! ===");
                    
                    // Копируем модель
                    if (copyModelToGame()) {
                        sendMessage("§aМодель скопирована в игру");
                        sendMessage("§7Используйте: KillAura -> Ротация -> Neuro");
                    } else {
                        sendMessage("§eМодель обучена, но не скопирована");
                        sendMessage("§7Скопируйте вручную: python/rotation_model.pt -> wraith/models/");
                    }
                } else {
                    sendMessage("§cОшибка обучения!");
                    sendMessage("§7Проверьте логи в консоли");
                }
                
                isTraining = false;
                return success;
                
            } catch (Exception e) {
                sendMessage("§cОшибка: " + e.getMessage());
                e.printStackTrace();
                isTraining = false;
                return false;
            }
        });
    }
    
    /**
     * Останавливает процесс обучения
     */
    public static void stopTraining() {
        if (trainingProcess != null && trainingProcess.isAlive()) {
            trainingProcess.destroy();
            sendMessage("§eОбучение остановлено");
        }
        isTraining = false;
    }
    
    /**
     * Проверяет статус обучения
     */
    public static boolean isTraining() {
        return isTraining;
    }
    
    /**
     * Находит команду Python в системе
     */
    private static String findPythonCommand() {
        String[] commands = {"python3", "python", "py"};
        
        for (String cmd : commands) {
            try {
                Process process = new ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start();
                
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                
                int exitCode = process.waitFor();
                if (exitCode == 0 && version != null && version.contains("Python 3")) {
                    return cmd;
                }
            } catch (Exception ignored) {
            }
        }
        
        return null;
    }
    
    /**
     * Проверяет установлены ли зависимости
     */
    private static boolean checkDependencies(String pythonCmd) {
        try {
            Process process = new ProcessBuilder(pythonCmd, "-c", 
                "import torch; import numpy")
                .redirectErrorStream(true)
                .start();
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Устанавливает зависимости
     */
    private static boolean installDependencies(String pythonCmd) {
        try {
            String pipCmd = pythonCmd.equals("python3") ? "pip3" : "pip";
            
            ProcessBuilder pb = new ProcessBuilder(
                pipCmd, "install", "-r", "python/requirements.txt"
            );
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            // Читаем вывод
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[PIP] " + line);
            }
            
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Запускает обучение модели
     */
    private static boolean runTraining(String pythonCmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                pythonCmd, "python/train_rotation_model.py"
            );
            pb.redirectErrorStream(true);
            
            trainingProcess = pb.start();
            
            // Читаем вывод в реальном времени
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(trainingProcess.getInputStream()));
            
            String line;
            int epochCount = 0;
            
            while ((line = reader.readLine()) != null) {
                System.out.println("[TRAINING] " + line);
                
                // Отправляем важные сообщения в чат
                if (line.contains("Загружено") && line.contains("сэмплов")) {
                    sendMessage("§7" + line);
                } else if (line.contains("Создано") && line.contains("последовательностей")) {
                    sendMessage("§7" + line);
                } else if (line.contains("Epoch [")) {
                    epochCount++;
                    if (epochCount % 10 == 0) {
                        sendMessage("§7Эпоха " + epochCount + "/50...");
                    }
                } else if (line.contains("Сохранена лучшая модель")) {
                    sendMessage("§a" + line);
                } else if (line.contains("Модель экспортирована")) {
                    sendMessage("§a" + line);
                } else if (line.contains("Обучение завершено")) {
                    sendMessage("§a" + line);
                }
            }
            
            int exitCode = trainingProcess.waitFor();
            trainingProcess = null;
            
            return exitCode == 0;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Копирует обученную модель в игру
     */
    private static boolean copyModelToGame() {
        try {
            Path source = Paths.get("python/rotation_model.pt");
            Path targetDir = Paths.get("endless/models");
            Path target = targetDir.resolve("rotation_model.pt");
            
            if (!Files.exists(source)) {
                return false;
            }
            
            // Создаем директорию если не существует
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            
            // Копируем файл
            Files.copy(source, target, 
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            
            return true;
            
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Отправляет сообщение в чат
     */
    private static void sendMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§8[§5Neuro§8] §r" + message), false);
        }
        System.out.println("[Neuro] " + message);
    }
}
