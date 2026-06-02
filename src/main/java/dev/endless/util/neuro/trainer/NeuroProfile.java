package dev.endless.util.neuro.trainer;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Профиль нейросети ротации с записью полных траекторий движения.
 * Хранит:
 *  • Trajectory'ы — массив кадров (dt_ms, dyaw, dpitch) от старта до клика
 *  • Тайминги ударов — ISI между кликами в мс
 *  • Агрегированные метрики (скорости, реакция и т.д.) для UI/fallback
 */
public class NeuroProfile {

    public String name = "default";

    @SerializedName("schemaVersion")
    public int schemaVersion = 3;

    public float trainSeconds = 0f;
    public int targetsHit = 0;
    public float avgClickAccuracy = 0f;

    // ─────────── Агрегированные метрики ───────────
    public float reactionTimeMs = 250f;
    public float aimTimeMs = 500f;
    public float avgSpeed = 200f;
    public float horizontalSpeed = 200f;
    public float horizontalPeakSpeed = 400f;
    public float verticalSpeed = 150f;
    public float verticalPeakSpeed = 300f;
    public float speedStdDev = 80f;
    public float horizontalSpeedStdDev = 80f;
    public float verticalSpeedStdDev = 60f;
    public float startAccel = 1500f;
    public float endDecel = 2000f;
    public float overshootFactor = 0.15f;
    public float jitter = 0.3f;
    public float curvature = 0.4f;

    // ─────────── Трассы (полные траектории до клика) ───────────

    /** Каждая Trajectory = один полный путь от спавна цели до клика. */
    public List<Trajectory> trajectories = new ArrayList<>();

    /** Тайминги между кликами (ISI = inter-stimulus interval), мс. */
    public List<Integer> clickIntervals = new ArrayList<>();

    /** Сэмплы движений (для kNN, опц. — для совместимости). */
    public List<MovementSample> samples = new ArrayList<>();

    public static class Trajectory {
        /** Стартовая угловая дистанция до цели (град — для kNN-поиска). */
        public float startDistance;
        /** Полное время траектории (мс). */
        public int totalMs;
        /** Реакция перед началом движения (мс). */
        public int reactionMs;
        /** Прямой вектор от старта до цели в градусах: targetYaw-startYaw, targetPitch-startPitch. */
        public float dirYaw;
        public float dirPitch;
        /** Кадры (delta-time + delta-yaw + delta-pitch в градусах). */
        public List<TrajectoryFrame> frames = new ArrayList<>();
    }

    public static class TrajectoryFrame {
        public int dt;       // мс с предыдущего кадра
        public float dYaw;   // приращение yaw в градусах
        public float dPitch; // приращение pitch в градусах

        public TrajectoryFrame() {}
        public TrajectoryFrame(int dt, float dYaw, float dPitch) {
            this.dt = dt;
            this.dYaw = dYaw;
            this.dPitch = dPitch;
        }
    }

    public static class MovementSample {
        public float distYaw;
        public float distPitch;
        public float progress;
        public float dYaw;
        public float dPitch;
        public MovementSample() {}
        public MovementSample(float distYaw, float distPitch, float progress, float dYaw, float dPitch) {
            this.distYaw = distYaw;
            this.distPitch = distPitch;
            this.progress = progress;
            this.dYaw = dYaw;
            this.dPitch = dPitch;
        }
    }
}
