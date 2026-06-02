package dev.endless.util.dataset;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Один фрейм записи датасета
 */
@Data
@AllArgsConstructor
public class DatasetFrame {
    private long timestamp;           // Время в миллисекундах
    private float yaw;                // Yaw игрока
    private float pitch;              // Pitch игрока
    private float distanceToTarget;   // Дистанция до цели
    private boolean onGround;         // На земле ли игрок
    private boolean attacking;        // Атакует ли в этот момент
    private float targetVelocity;     // Скорость цели
    
    /**
     * Вычисляет схожесть с другим фреймом (0-1, где 1 = идентично)
     */
    public float similarity(float distance, boolean ground, float targetVel) {
        float distDiff = Math.abs(this.distanceToTarget - distance);
        float velDiff = Math.abs(this.targetVelocity - targetVel);
        boolean groundMatch = this.onGround == ground;
        
        // Чем меньше разница - тем выше схожесть
        float distScore = 1.0f - Math.min(distDiff / 3.0f, 1.0f); // Макс разница 3 блока
        float velScore = 1.0f - Math.min(velDiff / 2.0f, 1.0f);   // Макс разница 2 м/с
        float groundScore = groundMatch ? 1.0f : 0.5f;
        
        return (distScore * 0.5f + velScore * 0.3f + groundScore * 0.2f);
    }
    
    /**
     * Конвертирует в CSV строку
     */
    public String toCsv() {
        return String.format("%d,%.2f,%.2f,%.2f,%b,%b,%.2f",
            timestamp, yaw, pitch, distanceToTarget, onGround, attacking, targetVelocity);
    }
    
    /**
     * Создает из CSV строки
     */
    public static DatasetFrame fromCsv(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        String[] parts = line.split(",");
        if (parts.length != 7) {
            System.err.println("[DatasetFrame] Неверное количество полей: " + parts.length + " (ожидается 7)");
            System.err.println("[DatasetFrame] Строка: " + line);
            return null;
        }
        
        try {
            long timestamp = Long.parseLong(parts[0].trim());
            float yaw = Float.parseFloat(parts[1].trim());
            float pitch = Float.parseFloat(parts[2].trim());
            float distance = Float.parseFloat(parts[3].trim());
            boolean onGround = Boolean.parseBoolean(parts[4].trim());
            boolean attacking = Boolean.parseBoolean(parts[5].trim());
            float targetVelocity = Float.parseFloat(parts[6].trim());
            
            return new DatasetFrame(timestamp, yaw, pitch, distance, onGround, attacking, targetVelocity);
        } catch (NumberFormatException e) {
            System.err.println("[DatasetFrame] Ошибка парсинга чисел: " + e.getMessage());
            System.err.println("[DatasetFrame] Строка: " + line);
            return null;
        } catch (Exception e) {
            System.err.println("[DatasetFrame] Неожиданная ошибка: " + e.getMessage());
            System.err.println("[DatasetFrame] Строка: " + line);
            return null;
        }
    }
}
