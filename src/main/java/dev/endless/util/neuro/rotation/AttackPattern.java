package dev.endless.util.neuro.rotation;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Паттерн атаки — последовательность движений от начала наведения до удара.
 * <p>
 * schemaVersion=1 — оригинальный формат (yaw/pitch/delta/distance/aimY/ticksUntilHit/mouseSpeed).
 * schemaVersion=2 — добавлены fovAtStart/fovAtEnd, relativeTargetVelocity, targetWasGliding,
 * attackFrameIndex (индекс кадра, на котором случился удар).
 */
@Data
public class AttackPattern {
    private int schemaVersion = 2;
    private List<RotationFrame> frames = new ArrayList<>();

    private float initialDistance;
    private float finalDistance;
    private boolean targetWasMoving;
    private boolean playerWasMoving;
    private int totalTicks;

    // v2
    private float fovAtStart;
    private float fovAtEnd;
    private float relativeTargetVelocity;
    private boolean targetWasGliding;
    private int attackFrameIndex = -1;

    @Data
    public static class RotationFrame {
        private float yaw;
        private float pitch;
        private float deltaYaw;
        private float deltaPitch;
        private float distanceToTarget;
        private float aimPointY;      // 0=ноги, 0.5=тело, 1=голова
        private int ticksUntilHit;
        private float mouseSpeed;

        // v2
        private float fovToTarget;
        private boolean isAttack;
    }

    public void addFrame(RotationFrame frame) {
        frames.add(frame);
    }

    /** Валидный паттерн имеет разумное число кадров. Верхняя граница повышена с 30 до 60. */
    public boolean isValid() {
        return frames.size() >= 5 && frames.size() <= 60;
    }
}
