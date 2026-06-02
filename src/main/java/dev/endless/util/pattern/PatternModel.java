package dev.endless.util.pattern;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PatternModel {
    private String name;
    private long createdAt;
    private List<PatternSequence> patterns = new ArrayList<>();

    @Data
    public static class PatternSequence {
        private List<PatternFrame> frames = new ArrayList<>();
        private float initialDistance;
        private float finalDistance;
        private boolean targetWasMoving;
        private boolean playerWasMoving;
        private int totalTicks;

        public void addFrame(PatternFrame frame) {
            frames.add(frame);
        }

        public boolean isValid() {
            return frames.size() >= 5 && frames.size() <= 40;
        }
    }

    @Data
    public static class PatternFrame {
        private float yaw;
        private float pitch;
        private float deltaYaw;
        private float deltaPitch;
        private float distanceToTarget;
        private float aimPointY;
        private int ticksUntilHit;
        private float mouseSpeed;
    }
}
