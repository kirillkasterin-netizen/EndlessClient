package dev.endless.util.neuro.rotation;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import dev.endless.Endless;
import dev.endless.event.list.EventTick;
import dev.endless.module.list.combat.KillAura;
import dev.endless.util.IMinecraft;
import dev.endless.util.math.RotationUtil;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Запись полных паттернов атак: от начала наведения до удара.
 * <p>
 * Основные отличия от v1:
 * - Цель выбирается по eye-to-nearest, а не distanceTo.
 * - Первый фрейм корректно инициализирован (нет мусорной дельты).
 * - Момент удара фиксируется явно через markAttack() из KillAura.
 * - Пишется fovToTarget, скорость цели, режим gliding.
 * - Верхний лимит фреймов 60 вместо 30.
 */
public class PatternRecorder implements IMinecraft {

    private static boolean recording = false;
    private static PatternRecorder instance = null;

    private static final List<AttackPattern> patterns = new ArrayList<>();
    private static AttackPattern currentPattern = null;

    private static float previousYaw = 0f;
    private static float previousPitch = 0f;
    private static long previousTime = 0L;
    private static LivingEntity previousTarget = null;
    private static boolean firstFrameOfPattern = true;

    @Subscribe
    public void onTick(EventTick event) {
        if (!recording || mc.player == null || mc.world == null) return;

        // Пишем только когда KillAura ВЫКЛЮЧЕН — иначе ротация вклинится в данные.
        KillAura killAura = Endless.getInstance().getModuleStorage().get(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            return;
        }

        LivingEntity target = findNearestTarget();

        // Смена цели — закрываем текущий паттерн.
        if (target != previousTarget) {
            saveIfValid();
            currentPattern = null;
            previousTarget = target;
        }

        if (target == null || !target.isAlive()) {
            currentPattern = null;
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d targetMid = target.getBoundingBox().getCenter();
        float distance = (float) eye.distanceTo(targetMid);

        float currentYaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float currentPitch = mc.player.getPitch();
        long currentTime = System.currentTimeMillis();

        // Начало нового паттерна.
        if (currentPattern == null && distance <= 6.0f) {
            currentPattern = new AttackPattern();
            currentPattern.setInitialDistance(distance);
            currentPattern.setTargetWasMoving(target.getVelocity().length() > 0.1);
            currentPattern.setPlayerWasMoving(mc.player.getVelocity().length() > 0.1);
            currentPattern.setTargetWasGliding(target.isGliding());
            currentPattern.setRelativeTargetVelocity(
                    (float) target.getVelocity().subtract(mc.player.getVelocity()).length());

            float[] want = RotationUtil.calculateAngle(targetMid);
            currentPattern.setFovAtStart(angularDistance(currentYaw, currentPitch, want[0], want[1]));

            // Инициализируем previous корректно, иначе первая дельта будет мусорной.
            previousYaw = currentYaw;
            previousPitch = currentPitch;
            previousTime = currentTime;
            firstFrameOfPattern = true;
        }

        if (currentPattern == null) return;

        float deltaYaw = firstFrameOfPattern ? 0f : MathHelper.wrapDegrees(currentYaw - previousYaw);
        float deltaPitch = firstFrameOfPattern ? 0f : (currentPitch - previousPitch);
        firstFrameOfPattern = false;

        float timeDelta = Math.max(0.001f, (currentTime - previousTime) / 1000.0f);
        float mouseSpeed = (float) Math.hypot(deltaYaw, deltaPitch) / timeDelta;

        double bodyHeight = Math.max(0.1, target.getBoundingBox().getLengthY());
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        double aimY = eye.y + lookVec.y * distance;
        float aimPointY = (float) MathHelper.clamp((aimY - target.getY()) / bodyHeight, 0.0, 1.0);

        float[] want = RotationUtil.calculateAngle(targetMid);
        float fovToTarget = angularDistance(currentYaw, currentPitch, want[0], want[1]);

        float cooldown = mc.player.getAttackCooldownProgress(0.0f);
        int ticksUntilHit = (int) ((1.0f - cooldown) * 20);

        AttackPattern.RotationFrame frame = new AttackPattern.RotationFrame();
        frame.setYaw(currentYaw);
        frame.setPitch(currentPitch);
        frame.setDeltaYaw(deltaYaw);
        frame.setDeltaPitch(deltaPitch);
        frame.setDistanceToTarget(distance);
        frame.setAimPointY(aimPointY);
        frame.setTicksUntilHit(ticksUntilHit);
        frame.setMouseSpeed(mouseSpeed);
        frame.setFovToTarget(fovToTarget);

        currentPattern.addFrame(frame);
        currentPattern.setTotalTicks(currentPattern.getFrames().size());
        currentPattern.setFinalDistance(distance);
        currentPattern.setFovAtEnd(fovToTarget);

        // Жёсткий лимит. Если вышли за него — закрываем без маркера атаки.
        if (currentPattern.getFrames().size() > 60) {
            saveIfValid();
            currentPattern = null;
        }

        previousYaw = currentYaw;
        previousPitch = currentPitch;
        previousTime = currentTime;
    }

    /**
     * Вызывается извне в момент реального удара. Закрывает текущий паттерн.
     */
    public static void markAttack() {
        if (!recording || currentPattern == null) return;
        List<AttackPattern.RotationFrame> frames = currentPattern.getFrames();
        if (frames.isEmpty()) return;

        AttackPattern.RotationFrame last = frames.get(frames.size() - 1);
        last.setAttack(true);
        currentPattern.setAttackFrameIndex(frames.size() - 1);

        saveIfValid();
        currentPattern = null;
    }

    private static void saveIfValid() {
        if (currentPattern != null && currentPattern.isValid()) {
            patterns.add(currentPattern);
        }
    }

    private LivingEntity findNearestTarget() {
        if (mc.world == null || mc.player == null) return null;

        LivingEntity nearest = null;
        double nearestDistance = 6.5;
        Vec3d eye = mc.player.getEyePos();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity le)) continue;
            if (le == mc.player || !le.isAlive() || le instanceof ArmorStandEntity) continue;

            double d = eye.distanceTo(le.getBoundingBox().getCenter());
            if (d < nearestDistance) {
                nearest = le;
                nearestDistance = d;
            }
        }

        return nearest;
    }

    private static float angularDistance(float yaw, float pitch, float targetYaw, float targetPitch) {
        float dy = MathHelper.wrapDegrees(targetYaw - yaw);
        float dp = targetPitch - pitch;
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    public static void startRecording() {
        if (recording) return;

        recording = true;
        patterns.clear();
        currentPattern = null;
        previousYaw = 0f;
        previousPitch = 0f;
        previousTime = System.currentTimeMillis();
        previousTarget = null;
        firstFrameOfPattern = true;

        if (instance == null) {
            instance = new PatternRecorder();
        }
        Endless.getInstance().getEventBus().register(instance);
    }

    public static int stopRecording() {
        if (!recording) return 0;

        recording = false;
        saveIfValid();
        currentPattern = null;

        if (instance != null) {
            Endless.getInstance().getEventBus().unregister(instance);
        }

        int count = patterns.size();
        if (count > 0) {
            saveToJson();
        }
        return count;
    }

    private static void saveToJson() {
        try {
            var dataDir = Paths.get("endless/patterns");
            if (!Files.exists(dataDir)) Files.createDirectories(dataDir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String filename = "endless/patterns/attack_patterns_" + timestamp + ".json";

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(filename)) {
                gson.toJson(patterns, writer);
            }
        } catch (Exception e) {
            System.err.println("[PatternRecorder] Ошибка сохранения: " + e.getMessage());
        }
    }

    public static List<AttackPattern> getPatterns() {
        return new ArrayList<>(patterns);
    }

    public static boolean isRecording() {
        return recording;
    }
}
