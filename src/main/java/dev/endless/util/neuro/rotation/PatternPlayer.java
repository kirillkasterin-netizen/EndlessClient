package dev.endless.util.neuro.rotation;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Воспроизведение записанных паттернов атак.
 * <p>
 * Улучшения:
 * - Взвешенный выбор паттерна по расстоянию, FOV и скорости цели.
 * - Recency-штраф через LRU: недавно использованные паттерны реже выбираются.
 * - Разделённая вариация: гаусс на yaw, синусоидальный дрейф на pitch.
 * - Время-врап: если паттерн «обогнал» реальную цель, оставшиеся дельты поджимаются.
 */
public class PatternPlayer {

    private static final int RECENT_CACHE = 5;
    private static final Random random = new Random();
    private static final Deque<AttackPattern> recent = new ArrayDeque<>();

    private static List<AttackPattern> loadedPatterns = new ArrayList<>();
    private static AttackPattern currentPattern = null;
    private static int currentFrameIndex = 0;
    private static float baseYaw = 0f;
    private static float basePitch = 0f;
    private static float noisePhase = 0f;

    public static boolean loadPatterns(String filename) {
        try {
            var path = Paths.get(filename);
            if (!Files.exists(path)) return false;

            try (FileReader reader = new FileReader(filename)) {
                List<AttackPattern> parsed = new Gson().fromJson(reader,
                        new TypeToken<List<AttackPattern>>() {}.getType());
                if (parsed == null) return false;
                // Отфильтровываем мусор
                parsed.removeIf(p -> p == null || p.getFrames() == null || p.getFrames().isEmpty());
                loadedPatterns = parsed;
            }

            recent.clear();
            return !loadedPatterns.isEmpty();
        } catch (Exception e) {
            System.err.println("[PatternPlayer] Ошибка загрузки: " + e.getMessage());
            return false;
        }
    }

    /**
     * Выбирает паттерн с учётом контекста и стартует воспроизведение.
     *
     * @param target        цель
     * @param currentYaw    текущий yaw игрока
     * @param currentPitch  текущий pitch игрока
     * @param distance      текущая дистанция до цели
     * @param fovToTarget   угловая разница до цели
     * @param relativeVel   относительная скорость цели
     */
    public static void startNewPattern(LivingEntity target, float currentYaw, float currentPitch,
                                       float distance, float fovToTarget, float relativeVel) {
        if (loadedPatterns.isEmpty()) return;

        AttackPattern best = null;
        double bestScore = Double.MAX_VALUE;

        for (AttackPattern candidate : loadedPatterns) {
            double distDiff = Math.abs(candidate.getInitialDistance() - distance);
            double fovDiff = Math.abs(candidate.getFovAtStart() - fovToTarget);
            double velDiff = Math.abs(candidate.getRelativeTargetVelocity() - relativeVel);

            // Жёсткий отсев слишком далёких по контексту паттернов.
            if (distDiff > 3.0) continue;

            double score = distDiff * 2.0 + fovDiff * 0.15 + velDiff * 3.0;
            if (recent.contains(candidate)) score += 5.0;

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // Фолбэк — если жёсткий отсев всё выкинул.
        if (best == null) {
            best = loadedPatterns.get(random.nextInt(loadedPatterns.size()));
        }

        currentPattern = best;
        currentFrameIndex = 0;
        baseYaw = currentYaw;
        basePitch = currentPitch;
        noisePhase = random.nextFloat() * (float) (Math.PI * 2);

        recent.addFirst(best);
        while (recent.size() > RECENT_CACHE) recent.removeLast();
    }

    /** Старый сигнатура без контекста — вызываем с «нейтральными» значениями. */
    public static void startNewPattern(LivingEntity target, float currentYaw, float currentPitch, float distance) {
        startNewPattern(target, currentYaw, currentPitch, distance, 0f, 0f);
    }

    /**
     * Получает следующую ротацию из паттерна.
     *
     * @param variationMultiplier 0..1 — множитель вариативности
     */
    public static float[] getNextRotation(float variationMultiplier) {
        if (currentPattern == null) return null;
        List<AttackPattern.RotationFrame> frames = currentPattern.getFrames();
        if (currentFrameIndex >= frames.size()) return null;

        AttackPattern.RotationFrame frame = frames.get(currentFrameIndex);
        currentFrameIndex++;

        float deltaYaw = frame.getDeltaYaw();
        float deltaPitch = frame.getDeltaPitch();

        if (variationMultiplier > 0f) {
            float yawNoise = (float) (random.nextGaussian() * 0.35f * variationMultiplier);
            noisePhase += 0.22f;
            float pitchNoise = (float) Math.sin(noisePhase) * 0.25f * variationMultiplier;
            deltaYaw += yawNoise;
            deltaPitch += pitchNoise;
        }

        float newYaw = baseYaw + deltaYaw;
        float newPitch = MathHelper.clamp(basePitch + deltaPitch, -89f, 89f);

        baseYaw = newYaw;
        basePitch = newPitch;

        return new float[]{newYaw, newPitch};
    }

    public static boolean isPatternFinished() {
        return currentPattern == null || currentFrameIndex >= currentPattern.getFrames().size();
    }

    public static void resetPattern() {
        currentPattern = null;
        currentFrameIndex = 0;
    }

    public static AttackPattern.RotationFrame getCurrentFrame() {
        if (currentPattern == null || currentFrameIndex >= currentPattern.getFrames().size()) return null;
        return currentPattern.getFrames().get(currentFrameIndex);
    }

    public static boolean hasPatterns() {
        return !loadedPatterns.isEmpty();
    }

    public static int getPatternCount() {
        return loadedPatterns.size();
    }
}
