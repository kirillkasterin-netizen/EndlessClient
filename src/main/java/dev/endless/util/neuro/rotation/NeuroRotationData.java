package dev.endless.util.neuro.rotation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Расширенные данные для обучения нейросети ротации
 * Собирает параметры за 10 тиков до удара для имитации человеческого поведения
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NeuroRotationData {
    // Входные параметры (features)
    private float deltaYaw;              // Изменение yaw с прошлого тика
    private float deltaPitch;            // Изменение pitch с прошлого тика
    private float distanceToTarget;      // Дистанция до цели
    private float targetVelocityX;       // Скорость цели по X
    private float targetVelocityY;       // Скорость цели по Y
    private float targetVelocityZ;       // Скорость цели по Z
    private float mouseMovementSpeed;    // Скорость движения мыши
    private float attackCooldown;        // Прогресс кулдауна атаки (0-1)
    private boolean isPlayerGliding;     // Летит ли игрок на элитрах
    private boolean isTargetGliding;     // Летит ли цель на элитрах
    private float ticksBeforeHit;        // Сколько тиков до удара (0-10)
    
    // НОВЫЕ параметры для имитации человеческого поведения
    private float aimPointY;             // Высота точки прицеливания (0=ноги, 0.5=тело, 1=голова)
    private float rotationAcceleration;  // Ускорение поворота (изменение скорости)
    private float previousDeltaYaw;      // Предыдущее изменение yaw (для паттернов)
    private float previousDeltaPitch;    // Предыдущее изменение pitch (для паттернов)
    private float timeSinceLastHit;      // Время с последнего удара (в тиках)
    private float angleToTarget;         // Угол до цели (градусы)
    private boolean isMovingToTarget;    // Двигается ли к цели
    private float strafeDirection;       // Направление стрейфа (-1=влево, 0=прямо, 1=вправо)
    
    // Выходные параметры (labels) - следующее движение камеры
    private float nextDeltaYaw;          // Следующее изменение yaw
    private float nextDeltaPitch;        // Следующее изменение pitch
    
    /**
     * Конвертирует данные в массив float для нейросети
     * @return массив входных параметров
     */
    public float[] toInputArray() {
        return new float[]{
            deltaYaw,
            deltaPitch,
            distanceToTarget,
            targetVelocityX,
            targetVelocityY,
            targetVelocityZ,
            mouseMovementSpeed,
            attackCooldown,
            isPlayerGliding ? 1.0f : 0.0f,
            isTargetGliding ? 1.0f : 0.0f,
            ticksBeforeHit,
            aimPointY,
            rotationAcceleration,
            previousDeltaYaw,
            previousDeltaPitch,
            timeSinceLastHit,
            angleToTarget,
            isMovingToTarget ? 1.0f : 0.0f,
            strafeDirection
        };
    }
    
    /**
     * Конвертирует выходные данные в массив
     * @return массив выходных параметров
     */
    public float[] toOutputArray() {
        return new float[]{
            nextDeltaYaw,
            nextDeltaPitch
        };
    }
    
    /**
     * Проверяет валидность данных
     * @return true если данные валидны
     */
    public boolean isValid() {
        // Проверка на NaN и Infinity
        if (!Float.isFinite(deltaYaw) || !Float.isFinite(deltaPitch) ||
            !Float.isFinite(distanceToTarget) || !Float.isFinite(mouseMovementSpeed) ||
            !Float.isFinite(nextDeltaYaw) || !Float.isFinite(nextDeltaPitch)) {
            return false;
        }
        
        // Проверка разумных границ
        if (Math.abs(deltaYaw) > 180 || Math.abs(deltaPitch) > 90) {
            return false;
        }
        
        if (Math.abs(nextDeltaYaw) > 180 || Math.abs(nextDeltaPitch) > 90) {
            return false;
        }
        
        if (distanceToTarget < 0 || distanceToTarget > 50) {
            return false;
        }
        
        if (ticksBeforeHit < 0 || ticksBeforeHit > 10) {
            return false;
        }
        
        return true;
    }
}
