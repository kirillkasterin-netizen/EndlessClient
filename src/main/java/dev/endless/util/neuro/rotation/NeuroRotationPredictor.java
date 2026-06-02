package dev.endless.util.neuro.rotation;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Предсказатель ротации на основе нейросети
 * Использует обученную LSTM модель для генерации человекоподобных движений камеры
 */
public class NeuroRotationPredictor {
    
    private static final int SEQUENCE_LENGTH = 10;
    private static final int INPUT_SIZE = 19; // Увеличено с 11 до 19 (добавлено 8 новых параметров)
    
    @Getter
    private static boolean modelLoaded = false;
    
    private static ZooModel<float[][], float[]> model;
    private static Predictor<float[][], float[]> predictor;
    private static final Queue<float[]> sequenceBuffer = new LinkedList<>();
    
    /**
     * Загружает модель из файла
     * @param modelPath путь к файлу модели (.pt)
     * @return true если загрузка успешна
     */
    public static boolean loadModel(String modelPath) {
        try {
            Path path = Paths.get(modelPath);
            
            if (!path.toFile().exists()) {
                System.err.println("[NeuroPredictor] Файл модели не найден: " + modelPath);
                System.err.println("[NeuroPredictor] Сначала обучите модель используя python/train_rotation_model.py");
                return false;
            }
            
            // Создаем критерии для загрузки PyTorch модели
            Criteria<float[][], float[]> criteria = Criteria.builder()
                    .setTypes(float[][].class, float[].class)
                    .optModelPath(path.getParent())
                    .optModelName(path.getFileName().toString().replace(".pt", ""))
                    .optTranslator(new RotationTranslator())
                    .build();
            
            model = criteria.loadModel();
            predictor = model.newPredictor();
            modelLoaded = true;
            
            System.out.println("[NeuroPredictor] Модель успешно загружена из " + modelPath);
            return true;
            
        } catch (ModelNotFoundException | MalformedModelException | IOException e) {
            System.err.println("[NeuroPredictor] Ошибка загрузки модели: " + e.getMessage());
            e.printStackTrace();
            modelLoaded = false;
            return false;
        }
    }
    
    /**
     * Предсказывает следующее движение камеры
     * @param data текущие данные ротации
     * @return массив [deltaYaw, deltaPitch] или null если модель не загружена
     */
    public static float[] predict(NeuroRotationData data) {
        if (!modelLoaded || predictor == null) {
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
            
            // Конвертируем буфер в 2D массив
            float[][] sequence = new float[SEQUENCE_LENGTH][INPUT_SIZE];
            int i = 0;
            for (float[] frame : sequenceBuffer) {
                sequence[i++] = frame;
            }
            
            // Предсказываем
            float[] prediction = predictor.predict(sequence);
            
            // Применяем ограничения для безопасности
            prediction[0] = clamp(prediction[0], -30.0f, 30.0f); // deltaYaw
            prediction[1] = clamp(prediction[1], -20.0f, 20.0f); // deltaPitch
            
            return prediction;
            
        } catch (Exception e) {
            System.err.println("[NeuroPredictor] Ошибка предсказания: " + e.getMessage());
            e.printStackTrace();
            return new float[]{0.0f, 0.0f};
        }
    }
    
    /**
     * Сбрасывает буфер последовательности
     */
    public static void resetSequence() {
        sequenceBuffer.clear();
    }
    
    /**
     * Закрывает модель и освобождает ресурсы
     */
    public static void close() {
        if (predictor != null) {
            predictor.close();
        }
        if (model != null) {
            model.close();
        }
        modelLoaded = false;
        sequenceBuffer.clear();
        System.out.println("[NeuroPredictor] Модель закрыта");
    }
    
    /**
     * Ограничивает значение в диапазоне
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Translator для конвертации данных между Java и DJL
     */
    private static class RotationTranslator implements Translator<float[][], float[]> {
        
        @Override
        public NDList processInput(TranslatorContext ctx, float[][] input) {
            NDManager manager = ctx.getNDManager();
            
            // Конвертируем float[][] в NDArray с shape [1, SEQUENCE_LENGTH, INPUT_SIZE]
            NDArray array = manager.create(input);
            array = array.expandDims(0); // Добавляем batch dimension
            
            return new NDList(array);
        }
        
        @Override
        public float[] processOutput(TranslatorContext ctx, NDList list) {
            // Получаем выход модели
            NDArray output = list.singletonOrThrow();
            
            // Конвертируем в float[]
            return output.toFloatArray();
        }
        
        @Override
        public Batchifier getBatchifier() {
            return Batchifier.STACK;
        }
    }
}
