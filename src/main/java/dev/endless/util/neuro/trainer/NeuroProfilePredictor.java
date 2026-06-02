package dev.endless.util.neuro.trainer;

import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Предиктор движений на основе записанных траекторий.
 *
 * <p>При смене таргета:
 *  1. Считаем стартовую угловую дистанцию и направление.
 *  2. Ищем самую похожую траекторию (по startDistance + direction).
 *  3. Масштабируем её под реальную дистанцию (если игрок дальше/ближе — растягиваем).
 *  4. Воспроизводим кадр за кадром по реальному времени (dt в каждом кадре).
 *  5. На завершении — сигнал что готов «бить».
 *
 * <p>Тайминги между ударами (ISI) тоже воспроизводятся — KillAura может
 * запросить {@link #shouldAttack()} чтобы решить можно ли бить.
 */
public class NeuroProfilePredictor {

    /** 1px в тренажёре ≈ столько градусов. */
    private static final float PX_TO_DEG = 0.13f;

    private final NeuroProfile profile;
    private final List<NormalizedSample> normSamples;
    private final List<NeuroProfile.Trajectory> trajectories;
    private final Random random = new Random();

    private float lastYaw, lastPitch;
    private float startYaw, startPitch;
    private float targetYaw, targetPitch;
    private float startDistance;

    /** Активная траектория для воспроизведения. */
    private NeuroProfile.Trajectory active;
    /** Коэффициент растяжения dYaw / dPitch (под реальную дистанцию). */
    private float scaleY = 1f, scaleP = 1f;
    /** Текущий индекс кадра. */
    private int frameIdx = 0;
    /** Время начала проигрывания. */
    private long playbackStart = 0;
    /** Время реакции прошло? */
    private boolean reactionDone = false;

    /** Для тайминга ударов. */
    private long lastAttackMs = 0;
    private int currentISI = 0;
    private int isiIndex = 0;

    public NeuroProfilePredictor(NeuroProfile profile) {
        this.profile = profile;
        this.normSamples = new ArrayList<>();
        if (profile.samples != null) {
            for (NeuroProfile.MovementSample s : profile.samples) {
                normSamples.add(new NormalizedSample(
                        s.distYaw * PX_TO_DEG,
                        s.distPitch * PX_TO_DEG,
                        s.progress,
                        s.dYaw * PX_TO_DEG,
                        s.dPitch * PX_TO_DEG
                ));
            }
        }
        this.trajectories = profile.trajectories != null
                ? profile.trajectories
                : new ArrayList<>();
        // Начальный ISI — средний если есть
        if (profile.clickIntervals != null && !profile.clickIntervals.isEmpty()) {
            currentISI = pickISI();
        }
    }

    public NeuroProfile getProfile() { return profile; }
    public boolean hasTrajectories() { return !trajectories.isEmpty(); }
    public boolean hasSamples() { return !normSamples.isEmpty(); }

    public void newTarget(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        this.lastYaw = currentYaw;
        this.lastPitch = currentPitch;
        this.startYaw = currentYaw;
        this.startPitch = currentPitch;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        float dy = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float dp = targetPitch - currentPitch;
        this.startDistance = (float) Math.hypot(dy, dp);

        // Ищем подходящую траекторию
        active = findBestTrajectory(dy, dp);
        if (active != null) {
            // Масштабируем по dirYaw/dirPitch
            scaleY = Math.abs(active.dirYaw) > 0.01f ? dy / active.dirYaw : 1f;
            scaleP = Math.abs(active.dirPitch) > 0.01f ? dp / active.dirPitch : 1f;
            // Защита от знаковых перевёртышей и слишком больших коэффициентов
            scaleY = MathHelper.clamp(scaleY, -3f, 3f);
            scaleP = MathHelper.clamp(scaleP, -3f, 3f);
            frameIdx = 0;
            playbackStart = System.currentTimeMillis();
            reactionDone = active.reactionMs <= 0;
        }
    }

    public void updateTarget(float targetYaw, float targetPitch) {
        // Если цель сдвинулась значительно — пере-выбираем траекторию
        float dy = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float dp = targetPitch - lastPitch;
        float dist = (float) Math.hypot(dy, dp);
        if (active != null && dist > 8f && frameIdx < active.frames.size() / 2) {
            // не перевыбираем, продолжим текущую
        }
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
    }

    /**
     * Один шаг к цели. Если есть траектория — играет её, иначе fallback.
     */
    public float[] stepTowards(float targetYaw, float targetPitch) {
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;

        float dy = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float dp = targetPitch - lastPitch;
        float distance = (float) Math.hypot(dy, dp);

        if (distance < 0.001f) {
            return new float[]{targetYaw, targetPitch};
        }

        // 1) Воспроизведение траектории
        if (active != null && frameIdx < active.frames.size()) {
            return stepTrajectory(dy, dp, distance);
        }
        // 2) kNN по сэмплам
        if (!normSamples.isEmpty()) {
            return stepKNN(dy, dp, distance);
        }
        // 3) Fallback по средней скорости
        return stepFallback(dy, dp, distance);
    }

    private float[] stepTrajectory(float dyRemaining, float dpRemaining, float distance) {
        long now = System.currentTimeMillis();
        long elapsed = now - playbackStart;

        if (!reactionDone && elapsed < active.reactionMs) {
            return new float[]{lastYaw, lastPitch};
        }
        reactionDone = true;

        // Воспроизводим столько кадров сколько прошло времени
        // По нашему «timeline»: суммируем dt из frames до тех пор пока сумма < elapsed
        long timeline = active.reactionMs;
        while (frameIdx < active.frames.size()) {
            NeuroProfile.TrajectoryFrame f = active.frames.get(frameIdx);
            timeline += f.dt;
            if (timeline > elapsed) break;
            // Применяем frame
            float dy = f.dYaw * scaleY;
            float dp = f.dPitch * scaleP;
            // Защита: если шаг превысил оставшееся — обрезаем
            if (Math.signum(dy) == Math.signum(dyRemaining)) {
                dy = Math.signum(dy) * Math.min(Math.abs(dy), Math.abs(dyRemaining));
            }
            if (Math.signum(dp) == Math.signum(dpRemaining)) {
                dp = Math.signum(dp) * Math.min(Math.abs(dp), Math.abs(dpRemaining));
            }
            lastYaw += dy;
            lastPitch += dp;
            dyRemaining -= dy;
            dpRemaining -= dp;
            frameIdx++;
        }
        lastPitch = MathHelper.clamp(lastPitch, -89f, 89f);

        // Если траектория закончилась — лёгкое доведение к цели чтобы не зависнуть
        if (frameIdx >= active.frames.size() && Math.hypot(dyRemaining, dpRemaining) > 0.5f) {
            float dt = 0.05f;
            float yawSpeed = profile.horizontalSpeed * 0.6f;
            float pitchSpeed = profile.verticalSpeed * 0.6f;
            float ys = Math.min(yawSpeed * dt, Math.abs(dyRemaining));
            float ps = Math.min(pitchSpeed * dt, Math.abs(dpRemaining));
            lastYaw += Math.signum(dyRemaining) * ys;
            lastPitch += Math.signum(dpRemaining) * ps;
        }

        return new float[]{lastYaw, lastPitch};
    }

    /** Ищет самую близкую траекторию по дистанции и направлению. */
    private NeuroProfile.Trajectory findBestTrajectory(float dy, float dp) {
        if (trajectories.isEmpty()) return null;
        float dist = (float) Math.hypot(dy, dp);
        // Собираем топ-3 кандидата по близости startDistance,
        // затем фильтруем по сходству направления.
        NeuroProfile.Trajectory best = null;
        float bestScore = Float.MAX_VALUE;
        for (NeuroProfile.Trajectory t : trajectories) {
            if (t.frames == null || t.frames.isEmpty()) continue;
            float distDiff = Math.abs(t.startDistance - dist);
            // Нормализуем направления
            float magT = (float) Math.hypot(t.dirYaw, t.dirPitch);
            float magC = (float) Math.hypot(dy, dp);
            float dirScore = 0;
            if (magT > 0.01f && magC > 0.01f) {
                float dotProd = (t.dirYaw * dy + t.dirPitch * dp) / (magT * magC);
                dirScore = (1f - dotProd) * 30f; // полное совпадение = 0, противоположное = 60
            }
            float score = distDiff + dirScore;
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    // ─── kNN fallback ───
    private float[] stepKNN(float dy, float dp, float distance) {
        float progress = startDistance > 0.01f
                ? Math.max(0f, Math.min(1f, 1f - distance / startDistance))
                : 1f;
        List<Scored> scored = new ArrayList<>(normSamples.size());
        for (NormalizedSample s : normSamples) {
            float ddy = s.distYaw - dy;
            float ddp = s.distPitch - dp;
            float dpr = (s.progress - progress) * 30f;
            scored.add(new Scored(ddy * ddy + ddp * ddp + dpr * dpr, s));
        }
        scored.sort(Comparator.comparingDouble(o -> o.distSq));
        int k = Math.min(5, scored.size());
        float sumDY = 0, sumDP = 0, sumW = 0;
        for (int i = 0; i < k; i++) {
            float w = 1f / (1f + scored.get(i).distSq);
            sumDY += scored.get(i).sample.dYaw * w;
            sumDP += scored.get(i).sample.dPitch * w;
            sumW += w;
        }
        if (sumW < 1e-6f) return stepFallback(dy, dp, distance);
        float predDY = sumDY / sumW;
        float predDP = sumDP / sumW;
        float dirY = dy > 0 ? 1f : -1f;
        float dirP = dp > 0 ? 1f : -1f;
        float magY = Math.min(Math.abs(predDY), Math.abs(dy));
        float magP = Math.min(Math.abs(predDP), Math.abs(dp));
        lastYaw += dirY * magY;
        lastPitch = MathHelper.clamp(lastPitch + dirP * magP, -89f, 89f);
        return new float[]{lastYaw, lastPitch};
    }

    private float[] stepFallback(float dy, float dp, float distance) {
        float dt = 0.05f;
        float ys = Math.min(profile.horizontalSpeed * dt, Math.abs(dy));
        float ps = Math.min(profile.verticalSpeed * dt, Math.abs(dp));
        if (dy < 0) ys = -ys;
        if (dp < 0) ps = -ps;
        lastYaw += ys;
        lastPitch = MathHelper.clamp(lastPitch + ps, -89f, 89f);
        return new float[]{lastYaw, lastPitch};
    }

    public float[] step() {
        return new float[]{lastYaw, lastPitch};
    }

    // ─── Тайминг ударов ───

    /**
     * Можно ли сейчас бить? Соблюдает ISI из обучения.
     */
    public boolean shouldAttack() {
        if (profile.clickIntervals == null || profile.clickIntervals.isEmpty()) return true;
        long now = System.currentTimeMillis();
        if (lastAttackMs == 0) {
            // Первый удар — сразу можно
            return true;
        }
        return (now - lastAttackMs) >= currentISI;
    }

    /** Должно вызываться после реального удара — выбирает следующий ISI. */
    public void onAttack() {
        lastAttackMs = System.currentTimeMillis();
        currentISI = pickISI();
    }

    private int pickISI() {
        if (profile.clickIntervals == null || profile.clickIntervals.isEmpty()) return 100;
        return profile.clickIntervals.get(random.nextInt(profile.clickIntervals.size()));
    }

    // ─── Внутренние ───

    private static class NormalizedSample {
        final float distYaw, distPitch, progress, dYaw, dPitch;
        NormalizedSample(float distYaw, float distPitch, float progress, float dYaw, float dPitch) {
            this.distYaw = distYaw; this.distPitch = distPitch;
            this.progress = progress; this.dYaw = dYaw; this.dPitch = dPitch;
        }
    }

    private static class Scored {
        final float distSq; final NormalizedSample sample;
        Scored(float distSq, NormalizedSample sample) { this.distSq = distSq; this.sample = sample; }
    }
}
