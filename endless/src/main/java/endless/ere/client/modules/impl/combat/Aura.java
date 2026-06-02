package endless.ere.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import com.darkmagician6.eventapi.types.Priority;
import lombok.Getter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import endless.ere.Endless;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.rotation.RotationTarget;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.MultiBooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.game.player.rotation.Rotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Порт Nuclear AttackAura + SmartCrit с режимом Spooky.
 * Ротация через silent RotationManager (НЕ через setYaw - чтобы не ломать элитру через fixElytra mixin).
 */
@ModuleAnnotation(name = "Aura", category = Category.COMBAT, description = "AttackAura + SmartCrit (Spooky)")
public final class Aura extends Module {

    public static final Aura INSTANCE = new Aura();
    private Aura() {}

    @Getter
    private static volatile LivingEntity target;
    private static volatile float[] rotate = new float[]{0f, 0f};

    public final ModeSetting sortingMode = new ModeSetting("Сортировка", "Поле зрения", "Дистанция", "Здоровье");
    public final ModeSetting attackMode = new ModeSetting("Режим атаки", "УмныеКриты", "Обычный", "ТолькоКриты");
    public final ModeSetting rotationMode = new ModeSetting("Ротация",
            "Snap", "Spooky", "SpookyRelease", "SpookyTime", "FunTime", "ReallyWorld", "HolyWorld");

    public final NumberSetting attackDistance = new NumberSetting("Дистанция", 3.0f, 2.5f, 6.0f, 0.1f);
    public final NumberSetting rotationSpeed = new NumberSetting("Скорость ротации", 30f, 5f, 40f, 1f);

    public final MultiBooleanSetting targetFilters = new MultiBooleanSetting("Цели",
            new MultiBooleanSetting.Value("Игроки", true),
            new MultiBooleanSetting.Value("Мобы", true),
            new MultiBooleanSetting.Value("Животные", false),
            new MultiBooleanSetting.Value("Невидимки", true));

    public final MultiBooleanSetting featureSettings = new MultiBooleanSetting("Настройки",
            new MultiBooleanSetting.Value("Водная коррекция", false),
            new MultiBooleanSetting.Value("Сквозь стены", false),
            new MultiBooleanSetting.Value("Не бить при еде", true));

    private static final int GROUND_DELAY_BASE = 5;
    private static final int GROUND_DELAY_EXTRA = 4;
    private static final int POST_ATTACK_LOCK_TICKS = 1;
    private static final int TARGET_LOCK_TICKS = 8;
    private static final double TP_DETECT_DISTANCE = 8.0;
    private static final float GAUSSIAN_CLAMP = 2.0F;

    private final Random rng = new Random();
    private int groundAttackDelay = 0;
    private boolean critHitThisCycle = false;
    private int postAttackLock = 0;
    private int targetLockTicks = 0;
    private LivingEntity lockedTarget = null;
    private int extraAttackDelay = 0;

    private float currentCritFallDist = 0.12F;
    private float currentCooldownThreshold = 0.98F;

    private double lastPosX, lastPosY, lastPosZ;
    private boolean positionInit = false;

    // Lock после отпускания ПКМ - предотвращает PacketOrderI
    private int postUseLock = 0;
    private boolean wasUsingLastTick = false;

    // Spooky pattern state
    private LivingEntity spookyLastTarget;
    private long spookyTargetSeenAt;
    private long spookyReactionMs;
    private float spookySpeedBias;
    private long spookySpeedBiasUntil;
    private int spookyPatternStep;
    private int spookyPatternLength;
    private long spookyPatternStepUntil;
    private Vec3d spookyFocusPoint = Vec3d.ZERO;

    @Override
    public void onEnable() {
        // Сброс shake-стейта на случай re-enable во время затухающего качания после предыдущего disable
        shakingActive = false;
        shakeTicks = 0;
        resetState();
        positionInit = false;
        if (mc.player != null) {
            rotate[0] = mc.player.getYaw();
            rotate[1] = mc.player.getPitch();
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Запускаем "качание головы" на 1 сек после disable - имитирует естественное
        // движение игрока после удара. Aura остаётся зарегистрированным пока transition не закончится.
        if (mc.player != null && !shakingActive) {
            shakingActive = true;
            shakeTicks = 0;
            shakeBaseYaw = rotate[0];
            shakeBasePitch = rotate[1];
            target = null;
            lockedTarget = null;
            return;
        }
        // финализация
        Endless.getInstance().getRotationManager().forceResetToVanilla();
        resetState();
        shakingActive = false;
        positionInit = false;
        super.onDisable();
    }

    // Постакт shake state
    private boolean shakingActive = false;
    private int shakeTicks = 0;
    private float shakeBaseYaw, shakeBasePitch;
    private static final int SHAKE_TICKS = 20;

    @EventTarget(Priority.HIGH)
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) return;

        // Shake transition после disable - 1 сек качания головы
        if (shakingActive) {
            tickShake();
            return;
        }
        // Отслеживаем отпускание ПКМ - на 3 тика блокируем атаки чтобы не было PacketOrderI
        boolean usingNow = mc.player.isUsingItem() || mc.options.useKey.isPressed();
        if (wasUsingLastTick && !usingNow) {
            postUseLock = 3;
        }
        wasUsingLastTick = usingNow;

        if (detectTeleport()) {
            critHitThisCycle = false;
            postAttackLock = 0;
            targetLockTicks = 0;
            lockedTarget = null;
        }

        if (mc.player.fallDistance == 0.0F && mc.player.isOnGround()) {
            critHitThisCycle = false;
        }

        if (postAttackLock > 0) postAttackLock--;
        if (targetLockTicks > 0) targetLockTicks--;

        LivingEntity newTarget = selectTarget();
        if (newTarget == null) {
            resetState();
            return;
        }

        if (lockedTarget != newTarget) {
            lockedTarget = newTarget;
            critHitThisCycle = false;
            groundAttackDelay = 0;
            spookyLastTarget = null;
        }
        target = newTarget;

        applySnapRotation(target);
        handleAttack(target);
    }

    private LivingEntity selectTarget() {
        if (targetLockTicks > 0 && target != null && isStillValid(target)) {
            return target;
        }
        LivingEntity fresh = findBestTarget();
        if (fresh != null) targetLockTicks = TARGET_LOCK_TICKS;
        return fresh;
    }

    private boolean isStillValid(LivingEntity entity) {
        if (entity == null || mc.player == null) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0) return false;
        return mc.player.distanceTo(entity) <= attackDistance.getCurrent() + 0.5;
    }

    private boolean detectTeleport() {
        if (mc.player == null) return false;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (!positionInit) {
            lastPosX = x; lastPosY = y; lastPosZ = z;
            positionInit = true;
            return false;
        }
        double dx = x - lastPosX, dy = y - lastPosY, dz = z - lastPosZ;
        double delta = Math.sqrt(dx * dx + dy * dy + dz * dz);
        lastPosX = x; lastPosY = y; lastPosZ = z;
        return delta > TP_DETECT_DISTANCE;
    }

    // ── Targeting ─────────────────────────────────────────────────────────

    private LivingEntity findBestTarget() {
        if (mc.world == null || mc.player == null) return null;
        double range = attackDistance.getCurrent();
        List<LivingEntity> candidates = new ArrayList<>();
        try {
            for (Entity ent : mc.world.getEntities()) {
                if (ent instanceof LivingEntity le && isValidTarget(le, range)) {
                    candidates.add(le);
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (candidates.isEmpty()) return null;
        sortTargets(candidates);
        return candidates.get(0);
    }

    private void sortTargets(List<LivingEntity> targets) {
        if (sortingMode.is("Здоровье")) {
            targets.sort(Comparator.comparingDouble(LivingEntity::getHealth));
        } else if (sortingMode.is("Поле зрения")) {
            targets.sort(Comparator.comparingDouble(this::getFovDiff));
        } else {
            targets.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        }
    }

    private boolean isValidTarget(LivingEntity living, double range) {
        if (living == null || mc.player == null) return false;
        if (living == mc.player) return false;
        if (living == mc.player.getVehicle()) return false;
        if (!living.isAlive() || living.getHealth() <= 0) return false;
        if (mc.player.distanceTo(living) > range) return false;
        if (living instanceof ArmorStandEntity) return false;

        if (!targetFilters.isEnable("Невидимки") && living.isInvisible()) return false;
        if (!featureSettings.isEnable("Сквозь стены") && !mc.player.canSee(living)) return false;

        if (living instanceof PlayerEntity p) {
            return !isFriend(p) && targetFilters.isEnable("Игроки");
        }
        if (living instanceof HostileEntity) return targetFilters.isEnable("Мобы");
        if (living instanceof AnimalEntity) return targetFilters.isEnable("Животные");
        if (living instanceof MobEntity) return targetFilters.isEnable("Мобы");
        return false;
    }

    private boolean isFriend(PlayerEntity p) {
        try {
            return Endless.getInstance().getFriendManager().isFriend(p.getGameProfile().getName());
        } catch (Exception e) {
            return false;
        }
    }

    private double getFovDiff(LivingEntity living) {
        if (living == null || mc.player == null) return Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = living.getBoundingBox().getCenter();
        double dx = to.x - eye.x;
        double dy = to.y - eye.y;
        double dz = to.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = MathHelper.clamp((float) (-Math.toDegrees(Math.atan2(dy, dist))), -90F, 90F);
        return Math.abs(MathHelper.wrapDegrees(yaw - rotate[0]))
                + Math.abs(pitch - rotate[1]) * 0.35;
    }

    // ── Rotation - точно как в Nuclear AttackAura.applySnapRotation ─────────

    private void applySnapRotation(LivingEntity entity) {
        if (mc.player == null || entity == null) return;

        // Akven-style режимы: SpookyTime / FunTime / ReallyWorld / HolyWorld
        // имеют свою формулу расчёта next yaw/pitch.
        // Snap / Spooky / SpookyRelease - наша логика.
        switch (rotationMode.get()) {
            case "SpookyTime" -> rotateSpookyTime(entity);
            case "FunTime" -> rotateFunTime(entity);
            case "ReallyWorld" -> rotateReallyWorld(entity);
            case "HolyWorld" -> rotateHolyWorld(entity);
            default -> rotateDefault(entity);
        }
    }

    private void rotateDefault(LivingEntity entity) {
        float curYaw = rotate[0];
        float curPitch = rotate[1];

        Box aabb = entity.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();

        Vec3d aimPoint;
        if (rotationMode.is("Spooky") || rotationMode.is("SpookyRelease")) {
            aimPoint = computeSpookyPoint(entity);
        } else {
            double clX = MathHelper.clamp(eye.x, aabb.minX, aabb.maxX);
            double clY = MathHelper.clamp(eye.y, aabb.minY + 0.1, aabb.maxY - 0.1);
            double clZ = MathHelper.clamp(eye.z, aabb.minZ, aabb.maxZ);
            aimPoint = new Vec3d(clX, clY, clZ);
        }

        double dx = aimPoint.x - eye.x;
        double dy = aimPoint.y - eye.y;
        double dz = aimPoint.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float tYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float tPitch = MathHelper.clamp((float) (-Math.toDegrees(Math.atan2(dy, hDist))), -90F, 90F);

        float dYaw = MathHelper.wrapDegrees(tYaw - curYaw);
        float dPitch = tPitch - curPitch;

        float maxYawPerTick = 90.0f;
        if (Math.abs(dYaw) > maxYawPerTick) dYaw = Math.signum(dYaw) * maxYawPerTick;

        float snap = MathHelper.clamp(rotationSpeed.getCurrent() / 40.0f, 0.3f, 1.0f);
        float smoothFactor = MathHelper.clamp(snap * (0.75f + (float) Math.abs(clampedGaussian()) * 0.15f),
                0.4f, 1.0f);
        if (rotationMode.is("Spooky")) smoothFactor = 1.0f;

        float nextYaw = curYaw + dYaw * smoothFactor;
        float nextPitch = curPitch + dPitch * smoothFactor;

        if (rotationMode.is("Spooky")) {
            int phase = spookyPatternStep % 5;
            float sigmaYaw = phase == 1 ? 0.02f : 0.05f;
            float sigmaPitch = phase == 1 ? 0.015f : 0.03f;
            nextYaw += (float) clampedGaussian() * sigmaYaw;
            nextPitch += (float) clampedGaussian() * sigmaPitch;
        } else if (rotationMode.is("SpookyRelease")) {
            int phase = spookyPatternStep % 5;
            float sigmaYaw = phase == 1 ? 0.04f : 0.10f;
            float sigmaPitch = phase == 1 ? 0.025f : 0.06f;
            nextYaw += (float) clampedGaussian() * sigmaYaw;
            nextPitch += (float) clampedGaussian() * sigmaPitch;
        }

        applyGCD(nextYaw, nextPitch);
        sendRotation();
    }

    // ──────────── Akven SpookyTime ────────────
    private void rotateSpookyTime(LivingEntity entity) {
        Vec3d targetPos = entity.getPos();
        Vec3d eye = mc.player.getEyePos();
        double yClamp = MathHelper.clamp(eye.y - entity.getY(), 0.0,
                entity.getHeight() - 1.0 * (eye.distanceTo(entity.getEyePos()) / attackDistance.getCurrent()));
        Vec3d aimPos = targetPos.add(0, yClamp, 0);
        Vec3d vec = aimPos.subtract(eye);

        float yawToTarget = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0);
        float pitchToTarget = (float) (-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))));

        float yawDelta = MathHelper.wrapDegrees(yawToTarget - rotate[0]);
        float pitchDelta = MathHelper.wrapDegrees(pitchToTarget - rotate[1]);
        float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.5f), 70.8f);
        float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.0f), 12.3f);
        float targetYaw = rotate[0] + (yawDelta > 1.0f ? clampedYaw : -clampedYaw);
        float targetPitch = rotate[1] + (pitchDelta > 0.5f ? clampedPitch : -clampedPitch);

        float lerpFactor = 0.687f;
        float yaw = rotate[0] + (targetYaw - rotate[0]) * lerpFactor;
        float pitch = rotate[1] + (targetPitch - rotate[1]) * lerpFactor;

        float time = (System.currentTimeMillis() % 10000L) / 720.0f;
        yaw += (float) Math.sin(time * 2.0f * Math.PI * 2.7) * 7.8f;
        pitch += (float) Math.sin(time * 2.0f * Math.PI * 2.9) * 2.6f;
        pitch = MathHelper.clamp(pitch, -89.0f, 89.0f);

        applyGCD(yaw, pitch);
        sendRotation();
    }

    // ──────────── Akven FunTime ────────────
    private void rotateFunTime(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = entity.getPos().add(0, MathHelper.clamp(1.1, 0.3, entity.getHeight()), 0);
        Vec3d vec = aim.subtract(eye);

        float yawToTarget = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0);
        float pitchToTarget = (float) (-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))));
        float yawDelta = MathHelper.wrapDegrees(yawToTarget - rotate[0]);
        float pitchDelta = MathHelper.wrapDegrees(pitchToTarget - rotate[1]);

        float clampedYaw = Math.min(Math.max(Math.abs(yawDelta), 0.5f), 180.0f) / 0.525f;
        float clampedPitch = Math.min(Math.max(Math.abs(pitchDelta), 0.5f), 90.0f) / 0.425f;

        if (Math.abs(clampedYaw - lastYawFt) <= 0.35f) {
            clampedYaw = lastYawFt + 0.45f;
        }
        float randomFactor = (float) (Math.random() * 0.15 - 0.075);

        float smoothYaw = MathHelper.lerp(0.3f, rotate[0],
                rotate[0] + (yawDelta > 0.0f ? clampedYaw : -clampedYaw) + randomFactor);
        float smoothPitch = MathHelper.lerp(0.3f, rotate[1], MathHelper.clamp(
                rotate[1] + (pitchDelta > 0.0f ? clampedPitch : -clampedPitch) + randomFactor, -80.0f, 80.0f));

        applyGCD(smoothYaw, smoothPitch);
        sendRotation();
        lastYawFt = clampedYaw;
    }

    // ──────────── Akven ReallyWorld ────────────
    private void rotateReallyWorld(LivingEntity entity) {
        double heightOffset = entity.getHeight() * 0.9;
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = entity.getPos().add(0, MathHelper.clamp(eye.y - entity.getY(), 0.0, heightOffset), 0);
        Vec3d vec = aim.subtract(eye);

        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0);
        float targetPitch = MathHelper.clamp((float) (-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z)))), -89f, 89f);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - rotate[0]);
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - rotate[1]);

        float baseSpeed = 95.0f;
        float accelFactor = 3.5f;
        float snapSpeed = baseSpeed * (1.0f + Math.min(Math.abs(yawDelta) / 90.0f, 1.0f) * (accelFactor - 1.0f));
        float snapFactor = Math.min(1.0f, snapSpeed / Math.max(1.0f, Math.abs(yawDelta)));
        float snapYaw = rotate[0] + yawDelta * snapFactor;

        snapSpeed = baseSpeed * (1.0f + Math.min(Math.abs(pitchDelta) / 90.0f, 1.0f) * (accelFactor - 1.0f));
        snapFactor = Math.min(1.0f, snapSpeed / Math.max(1.0f, Math.abs(pitchDelta)));
        float snapPitch = rotate[1] + pitchDelta * snapFactor;

        if (Math.abs(MathHelper.wrapDegrees(targetYaw - snapYaw)) < 0.1f) snapYaw = targetYaw;
        if (Math.abs(MathHelper.wrapDegrees(targetPitch - snapPitch)) < 0.1f) snapPitch = targetPitch;

        applyGCD(snapYaw, snapPitch);
        sendRotation();
    }

    // ──────────── Akven HolyWorld ────────────
    private void rotateHolyWorld(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = entity.getPos().add(0, MathHelper.clamp(1.1, 0.3, entity.getHeight()), 0);
        Vec3d vec = aim.subtract(eye);

        float yawToTarget = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(vec.z, vec.x)) - 90.0);
        float pitchToTarget = (float) (-Math.toDegrees(Math.atan2(vec.y, Math.hypot(vec.x, vec.z))));
        float yawDelta = MathHelper.wrapDegrees(yawToTarget - rotate[0]);
        float pitchDelta = MathHelper.wrapDegrees(pitchToTarget - rotate[1]);

        float yawSpeed = Math.min(Math.max(Math.abs(yawDelta), 1.0f), 160.0f);
        float pitchSpeed = Math.min(Math.max(Math.abs(pitchDelta), 1.0f), 70.0f);

        float yaw = rotate[0] + (yawDelta > 0 ? yawSpeed : -yawSpeed);
        float pitch = MathHelper.clamp(rotate[1] + (pitchDelta > 0 ? pitchSpeed : -pitchSpeed), -89f, 89f);

        // twitch
        if (mc.player.age % 2 == 0) {
            yaw += (float) (Math.random() - 0.5) * 0.15f;
            pitch += (float) (Math.random() - 0.5) * 0.15f;
        }
        yaw += (float) (Math.random() - 0.5) * 0.03f;
        pitch += (float) (Math.random() - 0.5) * 0.03f;

        // max change clamp
        float maxYawChange = 40.0f;
        float maxPitchChange = 35.0f;
        yaw = rotate[0] + MathHelper.clamp(yaw - rotate[0], -maxYawChange, maxYawChange);
        pitch = MathHelper.clamp(rotate[1] + MathHelper.clamp(pitch - rotate[1], -maxPitchChange, maxPitchChange), -89f, 89f);

        applyGCD(yaw, pitch);
        sendRotation();
    }

    private float lastYawFt = 0f;

    private void applyGCD(float yaw, float pitch) {
        // GCD от ванильного yaw игрока - точно как в Nuclear
        float f = (float) (mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2);
        float gcd = f * f * f * 1.2F;
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();
        float deltaYaw = MathHelper.wrapDegrees(yaw - playerYaw);
        float deltaPitch = pitch - playerPitch;
        float fixedYaw = playerYaw + Math.round(deltaYaw / gcd) * gcd;
        float fixedPitch = playerPitch + Math.round(deltaPitch / gcd) * gcd;
        // Нормализация yaw чтобы не было AimModulo 360
        rotate[0] = MathHelper.wrapDegrees(fixedYaw);
        rotate[1] = MathHelper.clamp(fixedPitch, -90F, 90F);
    }

    private void sendRotation() {
        Rotation rot = new Rotation(rotate[0], rotate[1]);
        var rm = Endless.getInstance().getRotationManager();
        var aim = rm.getAimManager();
        rm.setRotation(
                new RotationTarget(rot,
                        () -> aim.rotate(aim.getInstantSetup(), rot),
                        aim.getInstantSetup()),
                3, this);
    }

    // ── Spooky pattern point ──────────────────────────────────────────────

    private Vec3d computeSpookyPoint(LivingEntity entity) {
        long now = System.currentTimeMillis();
        if (spookyLastTarget != entity) {
            spookyLastTarget = entity;
            spookyTargetSeenAt = now;
            spookyReactionMs = 120L + rng.nextInt(160);
            spookyPatternStep = 0;
            spookyPatternLength = 0;
            spookyPatternStepUntil = 0L;
            spookySpeedBiasUntil = 0L;
        }

        if (spookyPatternLength == 0 || spookyPatternStep >= spookyPatternLength
                || now >= spookyPatternStepUntil) {
            advanceSpookyPattern(entity, now);
        }

        if (now >= spookySpeedBiasUntil) {
            spookySpeedBias = 0.78f + rng.nextFloat() * 0.19f;
            spookySpeedBiasUntil = now + 320L + rng.nextInt(540);
        }

        spookyPatternStep++;
        return spookyFocusPoint;
    }

    private void advanceSpookyPattern(LivingEntity entity, long now) {
        if (spookyPatternStep == 0 || spookyPatternStep >= spookyPatternLength) {
            spookyPatternStep = 0;
            spookyPatternLength = 3 + rng.nextInt(3);
        }
        Box box = entity.getBoundingBox();
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double halfX = (box.maxX - box.minX) * 0.5;
        double halfZ = (box.maxZ - box.minZ) * 0.5;
        double height = box.maxY - box.minY;

        double yLegs = box.minY + height * 0.08;
        double yKnees = box.minY + height * 0.25;
        double yHips = box.minY + height * 0.48;
        double yChest = box.minY + height * 0.62;
        double ySholder = box.minY + height * 0.78;
        double yNeck = box.minY + height * 0.86;
        double yTemple = box.minY + height * 0.93;

        double sx = rng.nextDouble() < 0.5 ? -1.0 : 1.0;
        double sz = rng.nextDouble() < 0.5 ? -1.0 : 1.0;
        double cornerX = cx + sx * halfX * (0.55 + rng.nextDouble() * 0.3);
        double cornerZ = cz + sz * halfZ * (0.55 + rng.nextDouble() * 0.3);
        double jitterX = (rng.nextDouble() - 0.5) * halfX * 0.35;
        double jitterZ = (rng.nextDouble() - 0.5) * halfZ * 0.35;

        int phase = spookyPatternStep % 5;
        double y = switch (phase) {
            case 0 -> rollY(yChest, ySholder, yHips, 0.6);
            case 1 -> rollY(yTemple, yNeck, ySholder, 0.65);
            case 2 -> rollY(yChest, yHips, ySholder, 0.6);
            case 3 -> rollY(yHips, yKnees, yLegs, 0.5);
            default -> rollY(yChest, ySholder, yTemple, 0.55);
        };
        y += (rng.nextDouble() - 0.5) * 0.18;

        spookyFocusPoint = new Vec3d(cornerX + jitterX, y, cornerZ + jitterZ);

        // Новая Spooky - быстрее меняет фазы (меньше задержки = быстрее наводится и бьёт)
        // SpookyRelease использует старые длительности для "legit" поведения.
        long base;
        if (rotationMode.is("SpookyRelease")) {
            base = switch (phase) {
                case 0 -> 220L + rng.nextInt(200);
                case 1 -> 110L + rng.nextInt(130);
                case 2 -> 180L + rng.nextInt(180);
                case 3 -> 230L + rng.nextInt(240);
                default -> 260L + rng.nextInt(320);
            };
        } else {
            // Spooky (новая) - быстрее
            base = switch (phase) {
                case 0 -> 100L + rng.nextInt(100);
                case 1 -> 60L + rng.nextInt(60);
                case 2 -> 90L + rng.nextInt(80);
                case 3 -> 110L + rng.nextInt(120);
                default -> 130L + rng.nextInt(160);
            };
        }
        spookyPatternStepUntil = now + base;
    }

    private double rollY(double primary, double a, double b, double primaryChance) {
        double r = rng.nextDouble();
        if (r < primaryChance) return primary;
        return r < primaryChance + (1.0 - primaryChance) * 0.5 ? a : b;
    }

    // ── Attack (точно как в Nuclear AttackAura.handleAttack) ──────────────

    private void handleAttack(LivingEntity entity) {
        if (postAttackLock > 0) return;
        if (mc.currentScreen != null) return;
        if (mc.interactionManager == null) return;
        if (mc.player.networkHandler == null) return;
        if (mc.player.isUsingItem() && featureSettings.isEnable("Не бить при еде")) return;

        // PacketOrderI fix: не бить пока активен ПКМ или только что отпустили его.
        // Grim палит attack одновременно с rightClicking=true или releasing=true.
        if (mc.options.useKey.isPressed()) return;
        if (postUseLock > 0) {
            postUseLock--;
            return;
        }

        // Hitbox-фикс: атакуем только если СЕРВЕРНАЯ ротация уже смотрит в хитбокс цели.
        // Если атаковать раньше - Grim ловит Hitboxes (rotation в attack пакете не совпадает с хитбоксом).
        if (!serverRotationLooksAtTarget(entity, attackDistance.getCurrent())) return;

        // Grim reach check: дистанция от глаза до ближайшей точки хитбокса < 3.0
        // (vanilla creative reach 6.0, survival 3.0). Иначе урон режется.
        Vec3d eye = mc.player.getEyePos();
        Box hb = entity.getBoundingBox();
        double clampedX = MathHelper.clamp(eye.x, hb.minX, hb.maxX);
        double clampedY = MathHelper.clamp(eye.y, hb.minY, hb.maxY);
        double clampedZ = MathHelper.clamp(eye.z, hb.minZ, hb.maxZ);
        double dToBox = eye.distanceTo(new Vec3d(clampedX, clampedY, clampedZ));
        if (dToBox > 3.0) return;

        if (attackMode.is("УмныеКриты")) {
            handleSmartCrit(entity);
        } else if (attackMode.is("ТолькоКриты")) {
            handleOnlyCrit(entity);
        } else {
            if (canAttackBase(entity) && getCooldown() >= currentCooldownThreshold) {
                performAttack(entity);
            }
        }
    }

    private boolean serverRotationLooksAtTarget(LivingEntity entity, double range) {
        Rotation serverRot = Endless.getInstance().getRotationManager().getCurrentRotation();
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = Vec3d.fromPolar(serverRot.getPitch(), serverRot.getYaw());
        Vec3d end = eye.add(look.multiply(range));
        return entity.getBoundingBox().raycast(eye, end).isPresent();
    }

    private float getCooldown() {
        return mc.player.getAttackCooldownProgress(1.0F);
    }

    private boolean hasBlindness() {
        return mc.player != null && mc.player.hasStatusEffect(StatusEffects.BLINDNESS);
    }

    private void handleOnlyCrit(LivingEntity entity) {
        if (mc.player.isOnGround() || critHitThisCycle) return;
        if (!canAttackBase(entity)) return;
        if (getCooldown() < currentCooldownThreshold) return;
        if (hasBlindness()) return;

        double motionY = mc.player.getVelocity().y;
        if (motionY >= 0.0 || motionY < -3.0) return;
        if (mc.player.fallDistance >= currentCritFallDist
                && !mc.player.isTouchingWater() && !mc.player.isInLava()
                && !mc.player.isClimbing() && !mc.player.hasVehicle()) {
            performAttack(entity);
            critHitThisCycle = true;
        }
    }

    private void handleSmartCrit(LivingEntity entity) {
        if (!canAttackBase(entity)) return;
        if (hasBlindness()) {
            if (mc.player.isOnGround()) attackOnGround(entity);
            return;
        }
        if (mc.player.isOnGround()) {
            attackOnGround(entity);
        } else if (featureSettings.isEnable("Водная коррекция")
                && (mc.player.isTouchingWater() || mc.player.isInLava())) {
            if (getCooldown() >= currentCooldownThreshold) performAttack(entity);
        } else {
            attackInAir(entity);
        }
    }

    private void attackOnGround(LivingEntity entity) {
        if (getCooldown() < currentCooldownThreshold) return;
        if (--groundAttackDelay > 0) return;
        if (extraAttackDelay > 0) { extraAttackDelay--; return; }

        // Human-like пропуск только для SpookyRelease (legit поведение).
        // Spooky - быстрый, без пропусков.
        if (rotationMode.is("SpookyRelease") && rng.nextInt(20) == 0) {
            groundAttackDelay = 2;
            return;
        }

        performAttack(entity);
        // Spooky - 1 тик базовой задержки = максимально быстрая атака
        int baseDelay = rotationMode.is("Spooky") ? 1 : GROUND_DELAY_BASE;
        int delay = baseDelay + (int) Math.abs(clampedGaussian() * (rotationMode.is("Spooky") ? 0.8 : 2.0));
        groundAttackDelay = MathHelper.clamp(delay, 1, baseDelay + GROUND_DELAY_EXTRA);
        extraAttackDelay = rotationMode.is("Spooky") ? 0 : (rng.nextInt(5) == 0 ? 1 + rng.nextInt(2) : 0);
    }

    private void attackInAir(LivingEntity entity) {
        if (critHitThisCycle) return;
        if (getCooldown() < currentCooldownThreshold) return;

        double motionY = mc.player.getVelocity().y;
        boolean falling = motionY < 0.0 && motionY > -3.0;
        boolean fallOk = mc.player.fallDistance >= currentCritFallDist;
        boolean stable = !mc.player.isClimbing() && !mc.player.hasVehicle()
                && !mc.player.isTouchingWater() && !mc.player.isInLava();
        if (falling && fallOk && stable) {
            performAttack(entity);
            critHitThisCycle = true;
        }
    }

    private void performAttack(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity.getHealth() <= 0) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
        // Breath pause только для SpookyRelease (Spooky без пауз - максимум скорости)
        if (rotationMode.is("SpookyRelease")) {
            attackCounter++;
            if (attackCounter >= breathInterval) {
                postAttackLock = POST_ATTACK_LOCK_TICKS + 2 + rng.nextInt(3);
                attackCounter = 0;
                breathInterval = 7 + rng.nextInt(6);
            } else {
                postAttackLock = POST_ATTACK_LOCK_TICKS;
            }
        } else {
            postAttackLock = POST_ATTACK_LOCK_TICKS;
        }
        randomizeThresholds();
    }

    private int attackCounter = 0;
    private int breathInterval = 8;

    private void randomizeThresholds() {
        currentCritFallDist = 0.10F + rng.nextFloat() * 0.12F;
        // Кулдаун от 0.94 до 1.00 - добавляет естественную вариативность атак
        // (некоторые удары "до полного" кулдауна, некоторые при 94%)
        currentCooldownThreshold = 0.94F + rng.nextFloat() * 0.06F;
    }

    private boolean canAttackBase(LivingEntity entity) {
        if (entity == null || mc.player == null) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0) return false;
        if (entity == mc.player.getVehicle()) return false;
        if (mc.player.distanceTo(entity) > attackDistance.getCurrent()) return false;
        if (featureSettings.isEnable("Не бить при еде") && mc.player.isUsingItem()) return false;
        if (!featureSettings.isEnable("Сквозь стены") && !hasLineOfSight(entity)) return false;
        return true;
    }

    private boolean hasLineOfSight(LivingEntity entity) {
        if (mc.world == null || mc.player == null) return false;
        try {
            Vec3d eye = mc.player.getEyePos();
            Box aabb = entity.getBoundingBox();
            double cx = MathHelper.clamp(eye.x, aabb.minX, aabb.maxX);
            double cy = MathHelper.clamp(eye.y, aabb.minY + 0.1, aabb.maxY - 0.1);
            double cz = MathHelper.clamp(eye.z, aabb.minZ, aabb.maxZ);
            Vec3d tgt = new Vec3d(cx, cy, cz);
            return mc.world.raycast(new RaycastContext(eye, tgt,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
        } catch (Exception e) {
            return true;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private double clampedGaussian() {
        double g = rng.nextGaussian();
        if (g > GAUSSIAN_CLAMP) g = GAUSSIAN_CLAMP;
        if (g < -GAUSSIAN_CLAMP) g = -GAUSSIAN_CLAMP;
        return g;
    }

    private void resetState() {
        target = null;
        lockedTarget = null;
        groundAttackDelay = 0;
        critHitThisCycle = false;
        extraAttackDelay = 0;
        postAttackLock = 0;
        targetLockTicks = 0;
        spookyLastTarget = null;
        spookyPatternLength = 0;
        spookyPatternStep = 0;
        if (mc.player != null) {
            rotate[0] = mc.player.getYaw();
            rotate[1] = mc.player.getPitch();
        }
        randomizeThresholds();
    }

    private void tickShake() {
        if (mc.player == null) {
            shakingActive = false;
            super.onDisable();
            return;
        }
        shakeTicks++;
        // Затухающая sin-волна вокруг shakeBaseYaw/Pitch
        // Амплитуда падает от 8° до 0° за 20 тиков
        float t = shakeTicks / (float) SHAKE_TICKS;
        float amplitude = (1f - t) * 8f;
        float phase = shakeTicks * 0.55f;
        float yawShake = (float) Math.sin(phase) * amplitude;
        float pitchShake = (float) Math.cos(phase * 1.3) * amplitude * 0.4f;

        // Базу плавно сводим к ванильному yaw/pitch игрока
        float playerYaw = mc.player.getYaw();
        float playerPitch = mc.player.getPitch();
        float dYaw = MathHelper.wrapDegrees(playerYaw - shakeBaseYaw);
        float dPitch = playerPitch - shakeBasePitch;
        shakeBaseYaw += dYaw * 0.15f;
        shakeBasePitch += dPitch * 0.15f;

        rotate[0] = shakeBaseYaw + yawShake;
        rotate[1] = MathHelper.clamp(shakeBasePitch + pitchShake, -90f, 90f);
        sendRotation();

        if (shakeTicks >= SHAKE_TICKS) {
            shakingActive = false;
            Endless.getInstance().getRotationManager().forceResetToVanilla();
            resetState();
            positionInit = false;
            super.onDisable();
        }
    }
}
