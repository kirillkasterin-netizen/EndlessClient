package dev.endless.util.neuro.rotation;

import ai.catboost.CatBoostModel;
import ai.catboost.CatBoostPredictions;
import lombok.Getter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Предсказатель ротации на основе CatBoost
 * Использует обученную CatBoost модель для генерации человекоподобных движений камеры
 */
public class CatBoostRotationPredictor {
    
    private static final int SEQUENCE_LENGTH = 5;
    private static final int INPUT_SIZE = 19;
    
    @Getter
    private static boolean modelLoaded = false;
    
    private static CatBoostModel modelYaw;
    private static CatBoostModel modelPitch;
    private static final Queue<float[]> sequenceBuffer = new LinkedList<>();
    
    /**
     * Загружает модели из файлов
     * @param yawModelPath путь к модели Yaw (.cbm)
     * @param pitchModelPath путь к модели Pitch (.cbm)
     * @return true если загрузка успешна
     */
    public static boolean loadModels(String yawModelPath, String pitchModelPath) {
        try {
            Path yawPath = Paths.get(yawModelPath);
            Path pitchPath = Paths.get(pitchModelPath);
            
            if (!yawPath.toFile().exists()) {
                System.err.println("[CatBoostPredictor] Файл модели Yaw не найден: " + yawModelPath);
                System.err.println("[CatBoostPredictor] Сначала обучите модель используя python/train_catboost_rotation.py");
                return false;
            }
            
            if (!pitchPath.toFile().exists()) {
                System.err.println("[CatBoostPredictor] Файл модели Pitch не найден: " + pitchModelPath);
                System.err.println("[CatBoostPredictor] Сначала обучите модель используя python/train_catboost_rotation.py");
                return false;
            }
            
            // Загружаем модели CatBoost
            modelYaw = CatBoostModel.loadModel(yawModelPath);
            modelPitch = CatBoostModel.loadModel(pitchModelPath);
            
            modelLoaded = true;
            
            System.out.println("[CatBoostPredictor] Модели успешно загружены:");
            System.out.println("[CatBoostPredictor]   Yaw: " + yawModelPath);
            System.out.println("[CatBoostPredictor]   Pitch: " + pitchModelPath);
            return true;
            
        } catch (Exception e) {
            System.err.println("[CatBoostPredictor] Ошибка загрузки моделей: " + e.getMessage());
            e.printStackTrace();
            modelLoaded = false;
            return false;
        }
    }
    
    /**
     * Загружает модели из стандартной директории
     * @return true если загрузка успешна
     */
    public static boolean loadModels() {
        return loadModels(
            "endless/models/catboost_rotation_yaw.cbm",
            "endless/models/catboost_rotation_pitch.cbm"
        );
    }
    
    /**
     * Предсказывает следующее движение камеры
     * @param data текущие данные ротации
     * @return массив [deltaYaw, deltaPitch] или null если модель не загружена
     */
    public static float[] predict(NeuroRotationData data) {
        if (!modelLoaded || modelYaw == null || modelPitch == null) {
            return null;
        }
        
        try {
            // Добавляем текущие данные в буфер
            sequenceBuffer.offer(data.toInputArray());
            
            // Поддерживаем размер буфера
            while (sequenceBuffer.size() > SEQUENCE_LENGTH) {
                sequenceBuffer.poll();
            }
            
            // Если буфер еще не заполнен, возвращаем нулевые значения
            if (sequenceBuffer.size() < SEQUENCE_LENGTH) {
                return new float[]{0.0f, 0.0f};
            }
            
            // Создаем массив фичей для CatBoost
            float[] features = createFeatures();
            
            // CatBoost требует String[] для категориальных фичей (у нас их нет)
            String[] catFeatures = new String[0];
            
            // Создаем объекты для предсказаний
            CatBoostPredictions predictionsYaw = new CatBoostPredictions(1, 1);
            CatBoostPredictions predictionsPitch = new CatBoostPredictions(1, 1);
            
            // Предсказываем (используем правильный API: float[] + String[])
            modelYaw.predict(features, catFeatures, predictionsYaw);
            modelPitch.predict(features, catFeatures, predictionsPitch);
            
            // Получаем результаты
            double predYaw = predictionsYaw.get(0, 0);
            double predPitch = predictionsPitch.get(0, 0);
            
            // Применяем ограничения для безопасности
            float deltaYaw = clamp((float) predYaw, -30.0f, 30.0f);
            float deltaPitch = clamp((float) predPitch, -20.0f, 20.0f);
            
            return new float[]{deltaYaw, deltaPitch};
            
        } catch (Exception e) {
            System.err.println("[CatBoostPredictor] Ошибка предсказания: " + e.getMessage());
            e.printStackTrace();
            return new float[]{0.0f, 0.0f};
        }
    }
    
    /**
     * Создает массив фичей из буфера последовательности
     */
    private static float[] createFeatures() {
        // Конвертируем буфер в список
        float[][] sequence = sequenceBuffer.toArray(new float[0][]);
        
        // Создаем массив фичей
        // SEQUENCE_LENGTH * INPUT_SIZE + 6 агрегированных фичей
        int totalFeatures = SEQUENCE_LENGTH * INPUT_SIZE + 6;
        float[] features = new float[totalFeatures];
        
        int idx = 0;
        
        // Добавляем все фичи из последовательности
        for (float[] frame : sequence) {
            System.arraycopy(frame, 0, features, idx, INPUT_SIZE);
            idx += INPUT_SIZE;
        }
        
        // Добавляем агрегированные фичи
        // Средние, стандартные отклонения и максимумы для deltaYaw и deltaPitch
        float[] deltasYaw = new float[SEQUENCE_LENGTH];
        float[] deltasPitch = new float[SEQUENCE_LENGTH];
        
        for (int i = 0; i < SEQUENCE_LENGTH; i++) {
            deltasYaw[i] = sequence[i][0]; // deltaYaw - первая фича
            deltasPitch[i] = sequence[i][1]; // deltaPitch - вторая фича
        }
        
        features[idx++] = mean(deltasYaw);
        features[idx++] = std(deltasYaw);
        features[idx++] = maxAbs(deltasYaw);
        features[idx++] = mean(deltasPitch);
        features[idx++] = std(deltasPitch);
        features[idx] = maxAbs(deltasPitch);
        
        return features;
    }
    
    /**
     * Вычисляет среднее значение
     */
    private static float mean(float[] values) {
        float sum = 0;
        for (float v : values) {
            sum += v;
        }
        return sum / values.length;
    }
    
    /**
     * Вычисляет стандартное отклонение
     */
    private static float std(float[] values) {
        float m = mean(values);
        float sum = 0;
        for (float v : values) {
            float diff = v - m;
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum / values.length);
    }
    
    /**
     * Находит максимальное абсолютное значение
     */
    private static float maxAbs(float[] values) {
        float max = 0;
        for (float v : values) {
            float abs = Math.abs(v);
            if (abs > max) {
                max = abs;
            }
        }
        return max;
    }
    
    /**
     * Сбрасывает буфер последовательности
     */
    public static void resetSequence() {
        sequenceBuffer.clear();
    }
    
    /**
     * Закрывает модели и освобождает ресурсы
     */
    public static void close() {
        try {
            if (modelYaw != null) {
                modelYaw.close();
            }
            if (modelPitch != null) {
                modelPitch.close();
            }
            modelLoaded = false;
            sequenceBuffer.clear();
            System.out.println("[CatBoostPredictor] Модели закрыты");
        } catch (Exception e) {
            System.err.println("[CatBoostPredictor] Ошибка при закрытии моделей: " + e.getMessage());
        }
    }
    
    /**
     * Ограничивает значение в диапазоне
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
