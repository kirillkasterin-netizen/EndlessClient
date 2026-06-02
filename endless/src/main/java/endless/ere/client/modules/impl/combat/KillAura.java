package endless.ere.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import com.darkmagician6.eventapi.types.Priority;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import endless.ere.base.events.impl.player.EventMoveInput;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.MultiBooleanSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.client.modules.impl.combat.wraith.WraithCombatUtil;

import java.util.concurrent.ThreadLocalRandom;

/**
 * KillAura, перенесённый из WraithClient максимально близко к оригиналу.
 *
 * Структура повторяет оригинал:
 *   • тик ({@link #onUpdate}) — выбор цели и атака;
 *   • расчёт ротации в тике для всех режимов кроме Spookytime;
 *   • Spookytime — пер-кадровая пружинная интерполяция в {@link #onFrameTick}.
 *
 * Ротация отправляется через silent-систему endless (RotationManager), как в
 * Aura, поэтому ротация валидна для пакетов и не ломает элитру.
 *
 * Из оригинала не перенесены режимы/проверки, завязанные на отсутствующий в
 * endless стек: Neuro (нейросеть), IdealHitUtils-криты и SimulatedPlayer-спринт
 * заменены ванильными эквивалентами; FreeCamera fakePlayer не используется.
 */
@ModuleAnnotation(name = "KillAura", category = Category.COMBAT, description = "Авто-атака по целям")
public final class KillAura extends Module {

    public static final KillAura INSTANCE = new KillAura();

    private final ModeSetting rotation = new ModeSetting("Ротация",
            "Old SlothHW", "Reallyworld", "Spookytime", "Sloth Test", "Grim/Matrix");
    private final ModeSetting rotationBehavior = new ModeSetting("Поведение ротации", "Плавная", "Снапы");
    private final MultiBooleanSetting targets = new MultiBooleanSetting("Таргеты",
            new MultiBooleanSetting.Value("Игроки", true),
            new MultiBooleanSetting.Value("Голые", true),
            new MultiBooleanSetting.Value("Монстры", true),
            new MultiBooleanSetting.Value("Животные", true));

    private final NumberSetting distance = new NumberSetting("Дистанция", 3f, 2f, 6f, 0.1f);
    private final NumberSetting preRotation = new NumberSetting("Пре дистанция", 1.5f, 0f, 3f, 0.1f);
    private final BooleanSetting raycastCheck = new BooleanSetting("Проверка на наведение", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Бить через стены", false);
    private final BooleanSetting smartAim = new BooleanSetting("Умное наведение", true);
    private final BooleanSetting predictate = new BooleanSetting("Предикт", true);
    private final NumberSetting predictValue = new NumberSetting("Предикт значение", 3f, 1f, 5f, 0.1f);
    private final ModeSetting moveFix = new ModeSetting("Коррекция движения", "Нет", "Таргетированная");
    private final BooleanSetting clientLook = new BooleanSetting("Клиент лук", true);
    private final BooleanSetting dontHitWhileEating = new BooleanSetting("Не бить когда ешь", false);

    private LivingEntity target;
    public static LivingEntity lastTarget;
    private int ticksToAttack;
    private float lastYaw, lastPitch;

    private final StopWatch stopWatch = new StopWatch();
    private final StopWatch attackTimer = new StopWatch();
    private long nextAttackDelay = 0;

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();

    // ── Old SlothHW spring state ────────────────────────────────────────────
    private LivingEntity slothTrackedTarget;

    // ── Spookytime per-frame state ──────────────────────────────────────────
    private LivingEntity testLastTarget;
    private long testFreezeUntil;
    private long testNextRefreshAt;
    private Vec3d testAimPoint = Vec3d.ZERO;
    private float testYaw, testPitch;
    private float testYawVel, testPitchVel;
    private long testLastFrameNanos;

    // ── Grim/Matrix spooky-pattern state ────────────────────────────────────
    private LivingEntity spookyLastTarget;
    private long spookyTargetSeenAt;
    private long spookyReactionMs;
    private float spookySpeedBias;
    private long spookySpeedBiasUntil;
    private Vec3d spookyFocusPoint = Vec3d.ZERO;
    private int spookyPatternStep;
    private int spookyPatternLength;
    private long spookyPatternStepUntil;
    private int spookyStutterTicks;

    // ── Disable transition (плавный доворот камеры после выключения) ─────────
    private boolean disableTransitionActive;
    private LivingEntity disableTransitionTarget;
    private long disableTransitionStartMs;
    private float disableTransitionStartYaw, disableTransitionStartPitch;
    private static final long DISABLE_TRANSITION_MS = 350L;

    private KillAura() {
    }

    @Override
    public void onEnable() {
        target = null;
        slothTrackedTarget = null;
        testLastTarget = null;
        testFreezeUntil = 0L;
        testNextRefreshAt = 0L;
        testLastFrameNanos = 0L;
        testYawVel = testPitchVel = 0f;
        if (mc.player != null) {
            lastYaw = testYaw = mc.player.getYaw();
            lastPitch = testPitch = mc.player.getPitch();
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        LivingEntity carryOver = target != null ? target : lastTarget;
        if (carryOver != null && carryOver.isAlive() && mc.player != null && !disableTransitionActive) {
            disableTransitionTarget = carryOver;
            disableTransitionStartMs = System.currentTimeMillis();
            disableTransitionStartYaw = mc.player.getYaw();
            disableTransitionStartPitch = mc.player.getPitch();
            disableTransitionActive = true;
            target = null;
            // Остаёмся подписанными пока доворот не доиграет — финализация в onFrameTick.
            return;
        }
        target = null;
        ticksToAttack = 0;
        slothTrackedTarget = null;
        spookyLastTarget = null;
        disableTransitionActive = false;
        super.onDisable();
    }

    // ── Тик: цель, ротация (кроме Spookytime), атака ─────────────────────────

    @EventTarget(Priority.HIGH)
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) return;

        if (ticksToAttack > 0) ticksToAttack--;

        updateTarget();

        if (target == null) {
            spookyLastTarget = null;
            spookyPatternLength = 0;
            spookyPatternStep = 0;
            return;
        }
        lastTarget = target;

        // Снапы: ждём готовности к удару прежде чем доводиться.
        if (rotationBehavior.is("Снапы")) {
            boolean ready = mc.player.getAttackCooldownProgress(1.0f) >= 0.95f && ticksToAttack <= 1;
            if (!ready) return;
        }

        // Spookytime крутится в onFrameTick (каждый кадр).
        switch (rotation.get()) {
            case "Old SlothHW" -> slothRotation(target);
            case "Sloth Test" -> slothTestRotation(target);
            case "Reallyworld" -> reallyWorldRotation(target);
            case "Grim/Matrix" -> spookyRotation(target);
            default -> {
            }
        }

        if (canAttack()) {
            if (rotation.is("Reallyworld")) {
                if (attackTimer.isReached(nextAttackDelay)) {
                    performAttack();
                    nextAttackDelay = 450 + (long) (Math.random() * 200);
                    attackTimer.reset();
                }
            } else {
                performAttack();
            }
        }
    }

    // ── Кадр: Spookytime + плавный доворот после disable ─────────────────────

    @EventTarget
    public void onFrameTick(EventRender3D event) {
        if (isEnabled() && rotation.is("Spookytime") && target != null) {
            updateTestRotationFrame(target);
        }
        tickDisableTransition();
        // Доворот доиграл, а модуль уже выключен — теперь действительно отписываемся.
        if (!isEnabled() && !disableTransitionActive) {
            slothTrackedTarget = null;
            spookyLastTarget = null;
            super.onDisable();
        }
    }

    // ── Коррекция движения ───────────────────────────────────────────────────

    @EventTarget
    public void onMoveInput(EventMoveInput event) {
        if (mc.player == null || target == null) return;
        if (!moveFix.is("Таргетированная")) return;

        if (mc.player.isGliding()) {
            event.setForward(0);
            event.setStrafe(0);
            return;
        }
        if (event.getForward() == 0 && event.getStrafe() == 0) return;

        float yaw = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        float bestForward = 0, bestStrafe = 0;
        float smallestDiff = Float.MAX_VALUE;
        for (float f = -1f; f <= 1f; f++) {
            for (float s = -1f; s <= 1f; s++) {
                if (f == 0 && s == 0) continue;
                double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(direction(yaw, f, s)));
                float diff = (float) Math.abs(MathHelper.wrapDegrees((float) (targetAngle - predictedAngle)));
                if (diff < smallestDiff) {
                    smallestDiff = diff;
                    bestForward = f;
                    bestStrafe = s;
                }
            }
        }
        event.setForward(bestForward);
        event.setStrafe(bestStrafe);
    }

    private static double direction(float rotationYaw, float moveForward, float moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

    // ── Targeting ────────────────────────────────────────────────────────────

    private void updateTarget() {
        LivingEntity best = null;
        double bestFovDot = -1;
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F);

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidEntity(living)) continue;
            Vec3d targetVec = WraithCombatUtil.getNearestPoint(entity).subtract(eyePos).normalize();
            double dot = lookVec.dotProduct(targetVec);
            if (dot > bestFovDot) {
                bestFovDot = dot;
                best = living;
            }
        }
        if (target == null || !isValidEntity(target)) {
            target = best;
        }
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (!(entity instanceof LivingEntity)) return false;

        if (entity instanceof PlayerEntity p && p.getArmor() != 0 && !targets.isEnable("Игроки")) return false;
        if (entity instanceof PlayerEntity p && p.getArmor() == 0 && !targets.isEnable("Голые")) return false;
        if ((entity instanceof HostileEntity || entity instanceof AmbientEntity) && !targets.isEnable("Монстры")) return false;
        if ((entity instanceof PassiveEntity || entity instanceof FishEntity) && !targets.isEnable("Животные")) return false;
        if (entity instanceof PlayerEntity p && WraithCombatUtil.isFriend(p)) return false;

        double maxRange = mc.player.isGliding() ? 50 : distance.getCurrent() + preRotation.getCurrent();
        if (mc.player.getEyePos().distanceTo(WraithCombatUtil.getNearestPoint(entity)) > maxRange) return false;

        if (!throughWalls.isEnabled()) {
            Vec3d eyePos = mc.player.getEyePos();
            Vec3d targetPos = entity.getBoundingBox().getCenter();
            HitResult result = mc.world.raycast(new RaycastContext(eyePos, targetPos,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (result.getType() == HitResult.Type.BLOCK) return false;
        }
        return true;
    }

    // ── Attack ──────────────────────────────────────────────────────────────

    private boolean canAttack() {
        if (target == null) return false;
        if (mc.currentScreen != null || mc.interactionManager == null) return false;
        if (dontHitWhileEating.isEnabled() && mc.player.isUsingItem()) return false;
        if (mc.player.getAttackCooldownProgress(1.0f) < 0.95f) return false;
        if (ticksToAttack > 0) return false;

        if (target.isGliding()) {
            double distToPredict = mc.player.getEyePos().distanceTo(WraithCombatUtil.predict(target, predictValue.getCurrent()));
            if (distToPredict > 4f) return false;
        } else {
            if (raycastCheck.isEnabled()
                    && !WraithCombatUtil.rayTrace(mc.player.getRotationVector(), distance.getCurrent(), target.getBoundingBox())) {
                return false;
            }
            if (mc.player.getEyePos().distanceTo(WraithCombatUtil.getNearestPoint(target)) > (distance.getCurrent() - 0.2f)) {
                return false;
            }
        }

        // Spookytime: серверная ротация должна смотреть в (расширенный) хитбокс.
        if (rotation.is("Spookytime")) {
            return isTestRotationLookingAtTarget();
        }
        return true;
    }

    private boolean isTestRotationLookingAtTarget() {
        if (target == null) return false;
        float yawRad = (float) Math.toRadians(testYaw);
        float pitchRad = (float) Math.toRadians(testPitch);
        float xz = (float) Math.cos(pitchRad);
        Vec3d look = new Vec3d(-Math.sin(yawRad) * xz, -Math.sin(pitchRad), Math.cos(yawRad) * xz);
        return WraithCombatUtil.rayTrace(look, distance.getCurrent(), target.getBoundingBox().expand(0.05));
    }

    private void performAttack() {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        ticksToAttack = 10;
        if (rotation.is("Spookytime")) {
            testFreezeUntil = System.currentTimeMillis() + 100L + rng.nextLong(200L);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void send(float yaw, float pitch) {
        lastYaw = yaw;
        lastPitch = MathHelper.clamp(pitch, -90f, 90f);
        WraithCombatUtil.updateRotation(lastYaw, lastPitch, 1, this);
    }

    private Vec3d resolveMultipoint(LivingEntity entity, Vec3d point) {
        if (!smartAim.isEnabled()) return point;
        return WraithCombatUtil.getNearestVisiblePoint(entity, point, distance.getCurrent());
    }

    // ── Old SlothHW (порт slothTest) ─────────────────────────────────────────

    private void slothRotation(LivingEntity entity) {
        Vec3d point = resolveMultipoint(entity, WraithCombatUtil.getPoint2(entity));
        if (entity.isGliding() && predictate.isEnabled()) {
            point = WraithCombatUtil.predict(entity, predictValue.getCurrent());
        }

        boolean isLooking = WraithCombatUtil.rayTrace(mc.player.getRotationVector(), 6, entity.getBoundingBox().expand(0, -1, 0));
        float[] ideal = WraithCombatUtil.calculateAngle(point);
        float targetYaw = ideal[0];
        float targetPitch = ideal[1];
        float randomFactor = (float) Math.random();

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;

        float dist = mc.player.distanceTo(entity) / 30;
        if (!isLooking && mc.player.getAttackCooldownProgress(1) >= 0.7f) {
            dist += 0.03f / 1.5f;
            stopWatch.reset();
        }
        if (!isLooking) {
            dist += 0.0075f / 1.5f;
            stopWatch.reset();
        } else {
            dist *= 0.15f + (randomFactor * 0.2f);
        }
        float smooth = Math.min(Math.max(dist, 0), 0.12f);

        float newYaw = lastYaw + deltaYaw * smooth;
        float newPitch = lastPitch + (deltaPitch * 0.5f) * smooth;

        float gcd = WraithCombatUtil.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;
        send(newYaw, MathHelper.clamp(newPitch, -90f, 90f));
    }

    // ── Sloth Test (порт slothTestNew: тот же + живой Y) ─────────────────────

    private void slothTestRotation(LivingEntity entity) {
        Vec3d point = resolveMultipoint(entity, WraithCombatUtil.getPoint2(entity));
        if (entity.isGliding() && predictate.isEnabled()) {
            point = WraithCombatUtil.predict(entity, predictValue.getCurrent());
        }

        Box bb = entity.getBoundingBox();
        double bbHeight = bb.maxY - bb.minY;
        double yAmp = Math.min(0.18, bbHeight * 0.18);
        double yPhase = (System.currentTimeMillis() % 2200L) / 2200.0 * Math.PI * 2.0;
        double yOffset = Math.sin(yPhase) * yAmp + Math.sin(yPhase * 2.13 + 0.5) * yAmp * 0.35;
        point = point.add(0, yOffset, 0);

        boolean isLooking = WraithCombatUtil.rayTrace(mc.player.getRotationVector(), 6, entity.getBoundingBox().expand(0, -1, 0));
        float[] ideal = WraithCombatUtil.calculateAngle(point);
        float targetYaw = ideal[0];
        float targetPitch = ideal[1];
        float randomFactor = (float) Math.random();

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;

        float dist = mc.player.distanceTo(entity) / 30;
        if (!isLooking && mc.player.getAttackCooldownProgress(1) >= 0.7f) {
            dist += 0.03f / 1.5f;
            stopWatch.reset();
        }
        if (!isLooking) {
            dist += 0.0075f / 1.5f;
            stopWatch.reset();
        } else {
            dist *= 0.15f + (randomFactor * 0.2f);
        }
        float smooth = Math.min(Math.max(dist, 0), 0.12f);

        float newYaw = lastYaw + deltaYaw * smooth;
        float pitchJitter = (float) (Math.sin(yPhase * 1.7 + 0.9) * 0.15 + (Math.random() - 0.5) * 0.08);
        float newPitch = lastPitch + (deltaPitch * 0.5f) * smooth + pitchJitter * smooth;

        float gcd = WraithCombatUtil.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;
        send(newYaw, MathHelper.clamp(newPitch, -90f, 90f));
    }

    // ── Reallyworld (порт updateHwTestRotation: шаги + рандом-офсет) ──────────

    private Vec3d hwTestOffset = Vec3d.ZERO;
    private final StopWatch hwTestOffsetTimer = new StopWatch();

    private void reallyWorldRotation(LivingEntity entity) {
        if (hwTestOffsetTimer.isReached(250)) {
            hwTestOffset = new Vec3d(
                    (Math.random() - 0.5) * entity.getWidth() * 0.8,
                    Math.random() * entity.getHeight() * 0.5,
                    (Math.random() - 0.5) * entity.getWidth() * 0.8);
            hwTestOffsetTimer.reset();
        }

        Vec3d targetPoint = entity.getPos().add(0, entity.getEyeHeight(entity.getPose()) * 0.8, 0).add(hwTestOffset);
        if (predictate.isEnabled()) {
            targetPoint = WraithCombatUtil.predict(entity, predictValue.getCurrent());
        }

        float[] angles = WraithCombatUtil.calculateAngle(targetPoint);
        float yawDiff = MathHelper.wrapDegrees(angles[0] - lastYaw);
        float pitchDiff = angles[1] - lastPitch;

        float stepSize = 15.0f + (float) Math.random() * 10.0f;
        float stepYaw = MathHelper.clamp(yawDiff, -stepSize, stepSize);
        float stepPitch = MathHelper.clamp(pitchDiff, -stepSize, stepSize);

        float newYaw = lastYaw + stepYaw;
        float newPitch = lastPitch + stepPitch;
        float gcd = WraithCombatUtil.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;
        send(newYaw, MathHelper.clamp(newPitch, -90f, 90f));
    }

    // ── Grim/Matrix (порт updateSpookyRotation) ──────────────────────────────

    private void spookyRotation(LivingEntity entity) {
        long now = System.currentTimeMillis();
        if (spookyLastTarget != entity) {
            spookyLastTarget = entity;
            spookyTargetSeenAt = now;
            spookyReactionMs = 120L + rng.nextLong(160L);
            spookyPatternStep = 0;
            spookyPatternLength = 0;
            spookyPatternStepUntil = 0L;
            spookySpeedBiasUntil = 0L;
            spookyStutterTicks = 0;
        }
        if (now - spookyTargetSeenAt < spookyReactionMs) {
            spookyBreath();
            return;
        }
        if (spookyPatternLength == 0 || spookyPatternStep >= spookyPatternLength || now >= spookyPatternStepUntil) {
            advanceSpookyPattern(entity, now);
        }
        if (spookyStutterTicks > 0) {
            spookyStutterTicks--;
            spookyBreath();
            return;
        }
        if (rng.nextDouble() < 0.05) {
            spookyStutterTicks = 1 + rng.nextInt(3);
            spookyBreath();
            return;
        }

        Vec3d aim = (predictate.isEnabled() && entity.isGliding())
                ? WraithCombatUtil.predict(entity, predictValue.getCurrent()) : spookyFocusPoint;
        float[] ideal = WraithCombatUtil.calculateAngle(aim);
        float deltaYaw = MathHelper.wrapDegrees(ideal[0] - lastYaw);
        float deltaPitch = ideal[1] - lastPitch;

        if (now >= spookySpeedBiasUntil) {
            spookySpeedBias = 0.78f + (float) rng.nextDouble() * 0.19f;
            spookySpeedBiasUntil = now + 320L + rng.nextLong(540L);
        }

        double hDist = Math.hypot(entity.getX() - mc.player.getX(), entity.getZ() - mc.player.getZ());
        boolean walkingThrough = hDist < 1.3 && Math.abs(deltaYaw) > 80.0f;

        float phaseStrength = switch (spookyPatternStep % 5) {
            case 0 -> 0.040f;
            case 1 -> 0.165f;
            case 2 -> 0.060f;
            case 3 -> 0.080f;
            default -> 0.032f;
        };
        float diff = (float) Math.hypot(deltaYaw, deltaPitch);
        float smooth = MathHelper.clamp(phaseStrength * spookySpeedBias + Math.min(0.045f, diff * 0.0014f), 0.012f, 0.24f);
        if (walkingThrough) smooth *= 0.15f;

        float yawStep = deltaYaw * smooth;
        float pitchStep = deltaPitch * (smooth * 0.55f);
        float maxStep = walkingThrough ? 3.0f : (spookyPatternStep % 5 == 1 ? 16.0f : 7.0f);
        yawStep = MathHelper.clamp(yawStep, -maxStep, maxStep);
        pitchStep = MathHelper.clamp(pitchStep, -maxStep * 0.7f, maxStep * 0.7f);

        float newYaw = lastYaw + yawStep;
        float newPitch = lastPitch + pitchStep;
        if (!walkingThrough) {
            float sigmaYaw = spookyPatternStep % 5 == 1 ? 0.04f : 0.10f;
            float sigmaPitch = spookyPatternStep % 5 == 1 ? 0.025f : 0.06f;
            newYaw += (float) rng.nextGaussian() * sigmaYaw;
            newPitch += (float) rng.nextGaussian() * sigmaPitch;
        }
        float gcd = WraithCombatUtil.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;
        send(newYaw, MathHelper.clamp(newPitch, -89.5f, 89.5f));
        spookyPatternStep++;
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
        double cornerX = cx + sx * halfX * (0.55 + rng.nextDouble() * 0.30);
        double cornerZ = cz + sz * halfZ * (0.55 + rng.nextDouble() * 0.30);
        double jitterX = (rng.nextDouble() - 0.5) * halfX * 0.35;
        double jitterZ = (rng.nextDouble() - 0.5) * halfZ * 0.35;

        int phase = spookyPatternStep % 5;
        double y = switch (phase) {
            case 0 -> roll(yChest, ySholder, yHips, 0.60);
            case 1 -> roll(yTemple, yNeck, ySholder, 0.65);
            case 2 -> roll(yChest, yHips, ySholder, 0.60);
            case 3 -> roll(yHips, yKnees, yLegs, 0.50);
            default -> roll(yChest, ySholder, yTemple, 0.55);
        };
        y += (rng.nextDouble() - 0.5) * 0.18;

        spookyFocusPoint = new Vec3d(cornerX + jitterX, y, cornerZ + jitterZ);
        long base = switch (phase) {
            case 0 -> 220L + rng.nextLong(200L);
            case 1 -> 110L + rng.nextLong(130L);
            case 2 -> 180L + rng.nextLong(180L);
            case 3 -> 230L + rng.nextLong(240L);
            default -> 260L + rng.nextLong(320L);
        };
        spookyPatternStepUntil = now + base;
    }

    private double roll(double primary, double a, double b, double primaryChance) {
        double r = rng.nextDouble();
        if (r < primaryChance) return primary;
        return r < primaryChance + (1.0 - primaryChance) * 0.5 ? a : b;
    }

    private void spookyBreath() {
        float dyaw = (float) rng.nextGaussian() * 0.07f;
        float dpitch = (float) rng.nextGaussian() * 0.05f;
        float newYaw = lastYaw + dyaw;
        float newPitch = MathHelper.clamp(lastPitch + dpitch, -89.5f, 89.5f);
        float gcd = WraithCombatUtil.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;
        send(newYaw, newPitch);
    }

    // ── Spookytime per-frame spring (порт updateTestRotationFrame) ───────────

    private void updateTestRotationFrame(LivingEntity entity) {
        if (entity == null || mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        long nowNs = System.nanoTime();

        if (testLastTarget != entity) {
            testLastTarget = entity;
            testYaw = mc.player.getYaw();
            testPitch = mc.player.getPitch();
            testYawVel = 0f;
            testPitchVel = 0f;
            testNextRefreshAt = 0L;
            testLastFrameNanos = nowNs;
        }

        float dt;
        if (testLastFrameNanos == 0L) dt = 1.0f / 60.0f;
        else dt = (nowNs - testLastFrameNanos) / 1_000_000_000.0f;
        testLastFrameNanos = nowNs;
        if (dt > 0.05f) dt = 0.05f;

        if (now < testFreezeUntil) {
            testYawVel *= 0.85f;
            testPitchVel *= 0.85f;
            testYaw += (float) rng.nextGaussian() * 0.05f;
            testPitch = MathHelper.clamp(testPitch + (float) rng.nextGaussian() * 0.04f, -89.5f, 89.5f);
            applyTestRotation();
            return;
        }

        if (now >= testNextRefreshAt) {
            Vec3d candidate = WraithCombatUtil.getMultipoint(entity, distance.getCurrent());
            if (smartAim.isEnabled()) {
                candidate = WraithCombatUtil.getNearestVisiblePoint(entity, candidate, distance.getCurrent());
            }
            if (predictate.isEnabled() && entity.isGliding()) {
                candidate = WraithCombatUtil.predict(entity, predictValue.getCurrent());
            }
            testAimPoint = candidate;
            testNextRefreshAt = now + 80L + rng.nextLong(140L);
        }

        float[] ideal = WraithCombatUtil.calculateAngle(testAimPoint);
        float targetYaw = ideal[0];
        float targetPitch = MathHelper.clamp(ideal[1], -89.5f, 89.5f);

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - testYaw);
        float deltaPitch = targetPitch - testPitch;
        float angDiff = (float) Math.hypot(deltaYaw, deltaPitch);

        float stiffness;
        if (angDiff > 60f) stiffness = 240f;
        else if (angDiff > 25f) stiffness = 192f;
        else if (angDiff > 8f) stiffness = 140f;
        else stiffness = 88f;
        stiffness *= 0.85f + (float) rng.nextDouble() * 0.30f;

        float damping = 13f;
        float yawAccel = deltaYaw * stiffness - testYawVel * damping;
        float pitchAccel = deltaPitch * stiffness - testPitchVel * damping;

        testYawVel += yawAccel * dt;
        testPitchVel += pitchAccel * dt;

        float maxVel = angDiff > 40f ? 1440f : 960f;
        testYawVel = MathHelper.clamp(testYawVel, -maxVel, maxVel);
        testPitchVel = MathHelper.clamp(testPitchVel, -maxVel * 0.7f, maxVel * 0.7f);

        if (Float.isNaN(testYawVel) || Float.isInfinite(testYawVel)) testYawVel = 0f;
        if (Float.isNaN(testPitchVel) || Float.isInfinite(testPitchVel)) testPitchVel = 0f;

        float yawStep = testYawVel * dt;
        float pitchStep = testPitchVel * dt;

        float noiseScale = MathHelper.clamp(angDiff / 30f, 0.10f, 0.6f);
        yawStep += (float) rng.nextGaussian() * 0.06f * noiseScale;
        pitchStep += (float) rng.nextGaussian() * 0.04f * noiseScale;

        testYaw += yawStep;
        testPitch = MathHelper.clamp(testPitch + pitchStep, -89.5f, 89.5f);

        applyTestRotation();
    }

    private void applyTestRotation() {
        float outYaw = testYaw;
        float outPitch = MathHelper.clamp(testPitch, -89.5f, 89.5f);
        if (Float.isNaN(outYaw) || Float.isInfinite(outYaw)) outYaw = lastYaw;
        if (Float.isNaN(outPitch) || Float.isInfinite(outPitch)) outPitch = lastPitch;
        send(outYaw, outPitch);
    }

    // ── Disable transition ────────────────────────────────────────────────────

    private void tickDisableTransition() {
        if (!disableTransitionActive) return;
        if (mc.player == null || mc.world == null || disableTransitionTarget == null || !disableTransitionTarget.isAlive()) {
            disableTransitionActive = false;
            return;
        }

        long elapsed = System.currentTimeMillis() - disableTransitionStartMs;
        float t = MathHelper.clamp(elapsed / (float) DISABLE_TRANSITION_MS, 0f, 1f);
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);

        Vec3d aim = disableTransitionTarget.getBoundingBox().getCenter();
        if (predictate.isEnabled() && disableTransitionTarget.isGliding()) {
            aim = WraithCombatUtil.predict(disableTransitionTarget, predictValue.getCurrent());
        }
        float[] ideal = WraithCombatUtil.calculateAngle(aim);
        float targetYaw = ideal[0];
        float targetPitch = MathHelper.clamp(ideal[1], -89.5f, 89.5f);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - disableTransitionStartYaw);
        float pitchDelta = targetPitch - disableTransitionStartPitch;

        float newYaw = disableTransitionStartYaw + yawDelta * eased;
        float newPitch = MathHelper.clamp(disableTransitionStartPitch + pitchDelta * eased, -89.5f, 89.5f);

        // Модуль уже выключен — двигаем реальную камеру напрямую.
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        if (t >= 1.0f) {
            disableTransitionActive = false;
            disableTransitionTarget = null;
        }
    }

    /** Лёгкий секундомер (порт ru.wraith StopWatch). */
    private static final class StopWatch {
        private long lastMs = System.currentTimeMillis();

        void reset() {
            lastMs = System.currentTimeMillis();
        }

        boolean isReached(long time) {
            return System.currentTimeMillis() - lastMs > time;
        }
    }
}
