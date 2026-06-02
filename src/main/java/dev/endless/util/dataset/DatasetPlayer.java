package dev.endless.util.dataset;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Воспроизводит датасет - находит похожие фреймы и применяет ротацию
 */
public class DatasetPlayer {
    
    private static List<DatasetFrame> dataset = new ArrayList<>();
    private static String currentDatasetName = null;
    
    /**
     * Загружает датасет из CSV файла
     */
    public static boolean loadDataset(String filename) {
        try {
            Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                System.err.println("[DatasetPlayer] Файл не найден: " + filename);
                return false;
            }
            
            dataset.clear();
            int lineNumber = 0;
            int loadedFrames = 0;
            
            try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
                String line;
                boolean firstLine = true;
                
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    
                    if (firstLine) {
                        firstLine = false;
                        continue; // Пропускаем заголовок
                    }
                    
                    if (line.trim().isEmpty()) {
                        continue; // Пропускаем пустые строки
                    }
                    
                    try {
                        DatasetFrame frame = DatasetFrame.fromCsv(line);
                        if (frame != null) {
                            dataset.add(frame);
                            loadedFrames++;
                        } else {
                            System.err.println("[DatasetPlayer] Ошибка парсинга строки " + lineNumber + ": " + line);
                        }
                    } catch (Exception e) {
                        System.err.println("[DatasetPlayer] Ошибка в строке " + lineNumber + ": " + e.getMessage());
                    }
                }
            }
            
            if (dataset.isEmpty()) {
                System.err.println("[DatasetPlayer] Датасет пустой после загрузки!");
                return false;
            }
            
            currentDatasetName = filename;
            System.out.println("[DatasetPlayer] Загружено фреймов: " + loadedFrames + " из " + (lineNumber - 1));
            return true;
            
        } catch (Exception e) {
            System.err.println("[DatasetPlayer] Ошибка загрузки: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Находит наиболее подходящий фрейм из датасета
     */
    public static DatasetFrame findBestFrame(LivingEntity target, boolean playerOnGround) {
        if (dataset.isEmpty()) {
            return null;
        }
        
        // Вычисляем параметры текущей ситуации
        Vec3d targetVel = new Vec3d(
            target.getX() - target.prevX,
            target.getY() - target.prevY,
            target.getZ() - target.prevZ
        );
        float targetVelocity = (float) targetVel.length();
        
        // Ищем наиболее похожий фрейм
        DatasetFrame bestFrame = null;
        float bestSimilarity = 0;
        
        for (DatasetFrame frame : dataset) {
            float similarity = frame.similarity(0, playerOnGround, targetVelocity);
            
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestFrame = frame;
            }
        }
        
        return bestFrame;
    }
    
    /**
     * Находит фрейм по конкретным условиям
     */
    public static DatasetFrame findFrameByConditions(float distance, boolean onGround, float targetVelocity) {
        if (dataset.isEmpty()) {
            return null;
        }
        
        DatasetFrame bestFrame = null;
        float bestSimilarity = 0;
        
        for (DatasetFrame frame : dataset) {
            float similarity = frame.similarity(distance, onGround, targetVelocity);
            
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestFrame = frame;
            }
        }
        
        return bestFrame;
    }
    
    /**
     * Получает ротацию из фрейма
     */
    public static float[] getRotation(DatasetFrame frame) {
        if (frame == null) {
            return null;
        }
        
        return new float[]{frame.getYaw(), frame.getPitch()};
    }
    
    public static boolean hasDataset() {
        return !dataset.isEmpty();
    }
    
    public static int getFrameCount() {
        return dataset.size();
    }
    
    public static String getCurrentDatasetName() {
        return currentDatasetName;
    }
    
    public static void clearDataset() {
        dataset.clear();
        currentDatasetName = null;
    }
}
