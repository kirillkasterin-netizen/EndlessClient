package dev.endless.module.list.combat;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
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
import org.joml.Matrix4f;
import dev.endless.Endless;
import dev.endless.event.EventGameUpdate;
import dev.endless.event.list.EventChangeSprint;
import dev.endless.event.list.EventTick;
import dev.endless.event.list.MoveInputEvent;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.player.FreeCamera;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ModeListSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.friend.FriendRepository;
import dev.endless.util.math.BestPoint;
import dev.endless.util.math.RotationUtil;
import dev.endless.util.math.StopWatch;
import dev.endless.util.player.combat.PredictUtils;
import dev.endless.util.player.combat.RaytraceUtil;
import dev.endless.util.player.simulate.SimulatedPlayer;
import dev.endless.util.render.math.GCDFixer;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.rotation.Rotation;
import dev.endless.util.rotation.RotationComponent;
import dev.endless.util.text.ValueUnit;

@ModuleInformation(moduleName = "KillAura", moduleCategory = ModuleCategory.COMBAT)
public class KillAura extends Module {

    public final ModeSetting rotation = new ModeSetting("Ротация", "Old SlothHW", "Old SlothHW", "Neuro", "Funtime");
    public final ModeSetting rotationBehavior = new ModeSetting("Поведение ротации", "Плавная", "Плавная", "Снапы");
    private final ModeListSetting targets = new ModeListSetting("Таргеты",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Монстры", true),
            new BooleanSetting("Животные", true)
    );

    public final SliderSetting distance = new SliderSetting("Дистанция", ValueUnit.countable("блок", "блока", "блоков"), 3, 2, 6, 0.1f);
    private final SliderSetting preRotation = new SliderSetting("Пре дистанция", ValueUnit.countable("блок", "блока", "блоков"), 1.5f, 0, 3, 0.1f);
    public final BooleanSetting raycastCheck = new BooleanSetting("Проверка на наведение", true);
    public final BooleanSetting throughWalls = new BooleanSetting("Бить через стены", false);
    public final BooleanSetting smartAim = new BooleanSetting("Умное наведение", true);
    public final BooleanSetting predictate = new BooleanSetting("Предикт", true);
    public final SliderSetting predictValue = new SliderSetting("Предикт значение", 3, 1, 5, 0.1f);

    public final ModeSetting moveFix = new ModeSetting("Коррекция движения", "Сфокусированная", "Нет", "Сфокусированная", "Таргетированная");

    public final BooleanSetting onlySpace = new BooleanSetting("Только с пробелом", true);
    public final BooleanSetting clientLook = new BooleanSetting("Клиент лук", true);
    public final BooleanSetting dontHitWhileEating = new BooleanSetting("Не бить когда ешь", false);
    public final BooleanSetting showPredictPoint = new BooleanSetting("Показать предикт точку", true);

    public boolean isResolving = false;
    public Vec3d resolverPoint = null;
    private final StopWatch resolverTimer = new StopWatch();

    private float RANDOM_STRENGTH = 0.75f;
    public static boolean isSlowdownActive = false;
    private static StopWatch stopWatch = new StopWatch();

    /** Funtime ротация: время начала тряски при потере цели (для затухания). */
    private long funtimeShakeStartMs = -1L;

    // ── Funtime per-frame interpolation ────────────────────────────────────
    /** Сторона держания прицела (+1 справа от цели, -1 слева). Чередуется после каждого удара. */
    private int funtimeSide = 1;
    /** Текущие интерполируемые углы (обновляются каждый кадр). */
    private float funtimeCurrentYaw = 0f;
    private float funtimeCurrentPitch = 0f;
    /** Целевые углы (обновляются каждый кадр через осциллятор). */
    private float funtimeTargetYaw = 0f;
    private float funtimeTargetPitch = 0f;
    /** Прямой угол на центр цели (без offset'а), обновляется в onTick. */
    private float funtimePureYaw = 0f;
    private float funtimePurePitch = 0f;
    /** Фаза маятника (рад). Растёт со временем для непрерывного движения головы. */
    private float funtimeOscPhase = 0f;
    /** Прошлая цель для определения "новая цель появилась" → быстрый первый snap. */
    private LivingEntity funtimePrevTarget = null;
    /** Сколько тиков активен "быстрый snap" режим (lerpSpeed повышен). */
    private int funtimeFastSnapTicks = 0;
    /** Активна ли система Funtime (была ли цель в прошлом тике). */
    private boolean funtimeActive = false;
    /** Прошлый кадр system time — для расчёта delta-time в frame-step. */
    private long funtimeLastFrameNanos = 0L;
    @Getter
    private LivingEntity target;
    public static LivingEntity lastTarget;
    public int ticksToAttack;

    private int razvorotikTicks;

    private boolean back;
    public float speedAcceleration;
    public float obhod;
    public static long lastPhysicalMoveTime;

    public float lastYaw;
    public float lastPitch;

    private boolean renderListenerRegistered = false;
    private final WorldRenderEvents.Last renderListener = context -> {
        if (isEnabled() && showPredictPoint.getValue()) {
            renderPredictPoint(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true));
        }
        // Per-frame обновление Funtime ротации — плавное перемещение к целевому углу.
        if (isEnabled() && rotation.is("Funtime") && funtimeActive) {
            updateFuntimePerFrame();
        }
        // Лёгкая «доводка» камеры на последнюю цель после выключения KillAura.
        tickDisableTransition();
    };

    // Disable transition (camera follow-through to last target)
    private boolean disableTransitionActive;
    private LivingEntity disableTransitionTarget;
    private long disableTransitionStartMs;
    private float disableTransitionStartYaw;
    private float disableTransitionStartPitch;
    private static final long DISABLE_TRANSITION_MS = 350L;

    private void findResolverPoint() {
        if (mc.player == null || mc.world == null) return;
        Vec3d eye = mc.player.getEyePos();


        float oppositeYaw = mc.player.getYaw() + 180f;

        float searchPitch = -50f;


        int[] yawOffsets = {0, 30, -30, 45, -45, 60, -60, 90, -90};

        for (int offset : yawOffsets) {
            float testYaw = oppositeYaw + offset;

            float radYaw = (float) Math.toRadians(testYaw);
            float radPitch = (float) Math.toRadians(searchPitch);

            double x = -Math.sin(radYaw) * Math.cos(radPitch);
            double y = -Math.sin(radPitch);
            double z = Math.cos(radYaw) * Math.cos(radPitch);

            Vec3d checkVec = new Vec3d(x, y, z).normalize().multiply(8.0);
            Vec3d endPoint = eye.add(checkVec);

            if (mc.world.raycast(new RaycastContext(eye, endPoint, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS) {
                resolverPoint = endPoint;
                return;
            }
        }
        resolverPoint = null;
    }

    @Subscribe
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null || target == null) return;

        Endless.getInstance().getModuleStorage().setRandomness(1);

        if (isResolving) {
            if (resolverTimer.isReached(300)) {
                isResolving = false;
            } else if (resolverPoint != null) {
                var rot = new Rotation(RotationUtil.calculate(resolverPoint));
                RotationComponent.update(rot, 360, 360, 360, 360, 0, 1, clientLook.getValue());
                lastYaw = rot.getYaw();
                lastPitch = rot.getPitch();
                return;
            }
        }


        if (rotationBehavior.is("Снапы")) {
            boolean isReadyToAttack = mc.player.getAttackCooldownProgress(1.0f) >= 0.95f && ticksToAttack <= 1;
            if (!isReadyToAttack) {
                return;
            }
        }

        switch (rotation.getValue()) {
            case "Old SlothHW" -> slothTest(target);
            case "Neuro" -> updateNeuroRotation(target);
            case "Funtime" -> updateFuntimeRotation(target);
        }
    }

    @Subscribe
    private void onChangeSprint(EventChangeSprint e) {
        if (canStopSprinting()) e.setSprinting(false);
    }

    @Subscribe
    private void onMoveInput(MoveInputEvent event) {
        if (mc.player == null) return;
        if (!moveFix.is("Таргетированная")) return;
        if (target == null) return;

        if (mc.player.isGliding()) {
            event.forward = 0;
            event.strafe = 0;
            return;
        }

        if (event.forward == 0 && event.strafe == 0) return;

        float yaw = MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw());

        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double targetAngle = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        float bestForward = 0, bestStrafe = 0;
        float smallestDiff = Float.MAX_VALUE;
        for (float f = -1f; f <= 1f; f++) {
            for (float s = -1f; s <= 1f; s++) {
                if (f == 0 && s == 0) continue;
                double predictedAngle = MathHelper.wrapDegrees(Math.toDegrees(RotationComponent.direction(yaw, f, s)));
                float diff = (float) Math.abs(MathHelper.wrapDegrees((float)(targetAngle - predictedAngle)));
                if (diff < smallestDiff) {
                    smallestDiff = diff;
                    bestForward = f;
                    bestStrafe = s;
                }
            }
        }

        event.forward = bestForward;
        event.strafe = bestStrafe;
    }


    @Subscribe
    private void onUpdate(final EventTick ignored) {
        if (mc.player == null || mc.world == null) return;

        if (ticksToAttack > 0) ticksToAttack--;
        if (razvorotikTicks > 0) razvorotikTicks--;

        updateTarget();

        if (target != null) {
            lastTarget = target;
            isSlowdownActive = false;
            
            if (canStopSprinting()) mc.player.setSprinting(false);

            if (canAttack()) {
                if (rotation.is("Neuro") && neuroPredictor != null
                        && neuroPredictor.getProfile().clickIntervals != null
                        && !neuroPredictor.getProfile().clickIntervals.isEmpty()) {
                    // Тайминги ударов из обученного профиля
                    if (neuroPredictor.shouldAttack()) {
                        performAttack();
                        neuroPredictor.onAttack();
                    }
                } else {
                    performAttack();
                }
            }
        } else {
            speedAcceleration = 0;
            razvorotikTicks = 0;
            // Funtime: цель потеряна, отключаем per-frame обновление.
            funtimeActive = false;
            funtimePrevTarget = null;
        }
    }

    private void performAttack() {
        // Grim PacketOrderB на 1.9+ требует порядок:
        //   INTERACT_ENTITY(ATTACK) → ANIMATION (swing)
        // Иначе flag "pre-attack" (swing не было до атаки) или "post-attack".
        // Также это совпадает с ванильным MinecraftClient.doAttack.
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        ticksToAttack = 10;

        // Funtime: после удара чередуем сторону держания прицела.
        // Голова была "слева" — после удара должна моментально пойти "вправо",
        // и наоборот. Это даёт эффект: "идёт из левого угла → snap по цели → удар → идёт в правый угол".
        if (rotation.is("Funtime")) {
            funtimeSide = -funtimeSide;
        }
    }

    private boolean isValidEntity(Entity entity) {
        if (!entity.isAlive()) return false;
        PlayerEntity player = Endless.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ? Endless.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;
        if (entity == Endless.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer) return false;
        if (entity instanceof ClientPlayerEntity) return false;
        if (entity instanceof ArmorStandEntity) return false;
        if (entity instanceof PlayerEntity p && p.getArmor() != 0 && !targets.isEnabled("Игроки")) return false;
        if (entity instanceof PlayerEntity p && p.getArmor() == 0 && !targets.isEnabled("Голые")) return false;
        if ((entity instanceof HostileEntity || entity instanceof AmbientEntity) && !targets.isEnabled("Монстры"))
            return false;
        if ((entity instanceof PassiveEntity || entity instanceof FishEntity) && !targets.isEnabled("Животные"))
            return false;
        if (entity instanceof PlayerEntity p) {
            if (!FriendRepository.shouldAttack(p)) return false;
        }
        if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(entity)) > (player.isGliding() ? 50 : distance.getValue() + preRotation.getValue()))
            return false;
        
        // Проверка видимости через стены
        if (!throughWalls.getValue()) {
            Vec3d eyePos = player.getEyePos();
            Vec3d targetPos = entity.getBoundingBox().getCenter();
            HitResult result = mc.world.raycast(new RaycastContext(
                eyePos, 
                targetPos, 
                RaycastContext.ShapeType.COLLIDER, 
                RaycastContext.FluidHandling.NONE, 
                player
            ));
            if (result.getType() == HitResult.Type.BLOCK) {
                return false;
            }
        }
        
        return true;
    }

    public boolean canAttack() {
        if (target == null) return false;

        PlayerEntity player = Endless.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer != null ?
                Endless.getInstance().getModuleStorage().get(FreeCamera.class).fakePlayer : mc.player;

        // Проверка на еду
        if (dontHitWhileEating.getValue() && mc.player.isUsingItem()) {
            return false;
        }



        if (!Endless.getInstance().getIdealHitUtils().cooldownIsReached(false)) return false;
        if (ticksToAttack > 0) return false;

        if (target.isGliding()) {
            double distToPredict = player.getEyePos().distanceTo(PredictUtils.predict(target, predictValue.getValue()));
            if (distToPredict > 4f) return false;
        } else {
            if (!RaytraceUtil.rayTrace(player.getRotationVector(), distance.getValue(), target.getBoundingBox()) && raycastCheck.getValue())
                return false;

            if (player.getEyePos().distanceTo(BestPoint.getNearestPoint(target)) > (distance.getValue() - 0.2f))
                return false;
        }

        // Spookytime ротация: дополнительная строгая проверка — наш текущий
        // отправляемый угол должен пересекать хитбокс цели (немного расширенный).
        // Иначе атака уйдёт в момент когда ротация ещё не довелась → kick.
        if (!Endless.getInstance().getIdealHitUtils().canCritical()) return false;

        // Funtime: строгая проверка попадания в хитбокс по нашему отправленному углу.
        // В движении прицел может отставать от цели, и без этого чека удары уходили
        // бы в воздух. Используем funtimeCurrentYaw/Pitch (то что реально отправили
        // на сервер) и расширенный хитбокс цели (+0.1) для запаса.
        if (rotation.is("Funtime")) {
            float yawRad = (float) Math.toRadians(funtimeCurrentYaw);
            float pitchRad = (float) Math.toRadians(funtimeCurrentPitch);
            float xz = (float) Math.cos(pitchRad);
            Vec3d look = new Vec3d(
                    -Math.sin(yawRad) * xz,
                    -Math.sin(pitchRad),
                    Math.cos(yawRad) * xz
            );
            Box hitbox = target.getBoundingBox().expand(0.1);
            if (!RaytraceUtil.rayTrace(look, distance.getValue(), hitbox)) {
                return false;
            }
        }

        return true;
    }

    public boolean canStopSprinting() {
        if (target == null) return false;
        if (!Endless.getInstance().getIdealHitUtils().cooldownIsReached(true)) return false;
        if (ticksToAttack > 1) return false;
        if (SimulatedPlayer.simulateLocalPlayer(1).fallDistance == 0) return false;
        return true;
    }

    private void updateTarget() {
        LivingEntity best = null;
        double bestFovDot = -1;

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0F);

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof LivingEntity) {
                if (!isValidEntity(entity)) continue;

                Vec3d targetVec = BestPoint.getNearestPoint(entity).subtract(eyePos).normalize();
                double dot = lookVec.dotProduct(targetVec);

                if (dot > bestFovDot) {
                    bestFovDot = dot;
                    best = (LivingEntity) entity;
                }
            }
        }

        if (target == null || !isValidEntity(target)) {
            this.target = best;
        }
    }

    private void slothTest(LivingEntity target) {
        if (target == null) return;

        Vec3d point = resolveMultipoint(target, BestPoint.getPoint2(target), 6);
        if (target.isGliding() && predictate.getValue()) {
            point = PredictUtils.predict(target, predictValue.getValue());
        }
        boolean isLooking = RaytraceUtil.rayTrace(mc.player.getRotationVector(), 6, target.getBoundingBox().expand(-0,-1,-0));
        var idealRotation = new Rotation(RotationUtil.calculate(point));
        float targetYaw = idealRotation.getYaw();
        float targetPitch = idealRotation.getPitch();
        float randomFactor = (float) Math.random();

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;

        float distance = mc.player.distanceTo(target) / 30 ;
        if(!isLooking && mc.player.getAttackCooldownProgress(1) >= 0.7f){
            distance += 0.03f / 1.5f;

            stopWatch.reset();
        }
        if(!isLooking ){
            distance += 0.0075f / 1.5f;

            stopWatch.reset();
        }
        else{
            distance *= 0.15f + (randomFactor * 0.2f);

        }
        var smooth = Math.min(Math.max(distance, 0), 0.12f);

        float newYaw = lastYaw + (deltaYaw) * smooth;
        float newPitch = lastPitch + (deltaPitch * 0.5f) * smooth;

        float gcd = GCDFixer.getGCDValue();
        newYaw -= (newYaw - lastYaw) % gcd;
        newPitch -= (newPitch - lastPitch) % gcd;

        newPitch = MathHelper.clamp(newPitch, -90f, 90f);

        var legitRot = new Rotation(newYaw, newPitch);

        RotationComponent.update(legitRot, 360, 360, 360, 360, 0, 1, clientLook.getValue());

        lastYaw = legitRot.getYaw();
        lastPitch = legitRot.getPitch();
    }

    /**
     * Funtime ротация — onTick часть: вычисляет ЦЕЛЕВОЙ угол.
     *
     * Голова двигается как маятник: target = pureYaw + sin(phase) * width.
     * Phase плавно растёт каждый кадр (см. updateFuntimePerFrame), поэтому
     * target постоянно меняется → голова всегда в движении.
     *
     * После удара phase инвертируется (умножается на -1) — маятник «толкается»
     * и идёт в противоположную сторону.
     *
     * Когда крит готов и голова проходит около центра цели — там она замедляется
     * (на самом деле просто проходит быстро через 0), и в этот момент происходит удар.
     */
    private void updateFuntimeRotation(LivingEntity target) {
        if (mc.player == null || target == null) return;

        funtimeActive = true;

        // Инициализация текущего yaw/pitch при первом тике активности.
        if (funtimeCurrentYaw == 0 && funtimeCurrentPitch == 0) {
            funtimeCurrentYaw = mc.player.getYaw();
            funtimeCurrentPitch = mc.player.getPitch();
        }

        // ── Появилась новая цель → быстрый первый snap ────────────────────
        if (target != funtimePrevTarget) {
            funtimePrevTarget = target;
            funtimeOscPhase = 0f;
            funtimeFastSnapTicks = 5;
        }

        if (funtimeFastSnapTicks > 0) {
            funtimeFastSnapTicks--;
        }
    }

    /**
     * Funtime per-frame: вызывается каждый кадр (60+ FPS).
     * Использует "маятниковую" модель — phase непрерывно растёт со временем,
     * целевой угол = pureYaw + sin(phase) * width. Голова никогда не стоит.
     *
     * Linear lerp + frame-rate-independent factor: 1 - exp(-speed * dt).
     */
    private void updateFuntimePerFrame() {
        if (mc.player == null) return;
        // Берём актуальную цель — она может меняться между тиками.
        LivingEntity tgt = target != null ? target : lastTarget;
        if (tgt == null || !tgt.isAlive()) return;

        long now = System.nanoTime();
        if (funtimeLastFrameNanos == 0L) {
            funtimeLastFrameNanos = now;
            return;
        }
        float dt = (now - funtimeLastFrameNanos) / 1_000_000_000f; // секунды
        funtimeLastFrameNanos = now;
        if (dt <= 0f) return;
        if (dt > 0.1f) dt = 0.1f;

        // ── Считаем точку прицеливания КАЖДЫЙ КАДР с предиктом ────────────
        // В движении цель и игрок смещаются между тиками — если считать pureYaw
        // только в onGameUpdate, прицел отстаёт. Поэтому здесь, на per-frame
        // уровне, мы используем предсказание движения цели вперёд на ~2 тика.
        Vec3d targetPos;
        if (predictate.getValue()) {
            targetPos = PredictUtils.predict(tgt, 2.0);
        } else {
            targetPos = tgt.getBoundingBox().getCenter();
        }
        Vec3d playerEye = mc.player.getEyePos();
        Vec3d delta = targetPos.subtract(playerEye);
        funtimePureYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90f;
        funtimePurePitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.hypot(delta.x, delta.z)));

        // Phase двигается со скоростью funtimeSide × phaseSpeed.
        float phaseSpeed = 5.5f;
        funtimeOscPhase += funtimeSide * phaseSpeed * dt;

        // Целевой угол = pureYaw + sin(phase) × width (маятник).
        float oscWidth = 44f;
        float oscOffset = (float) Math.sin(funtimeOscPhase) * oscWidth;
        float oscPitchOffset = (float) Math.cos(funtimeOscPhase * 0.6) * (oscWidth / 5f);

        funtimeTargetYaw = funtimePureYaw + oscOffset;
        funtimeTargetPitch = funtimePurePitch + oscPitchOffset;

        // ── Скорость lerp'а ────────────────────────────────────────────────
        boolean canAttackReady = Endless.getInstance().getIdealHitUtils().cooldownIsReached(false);
        boolean nearCenter = Math.abs(oscOffset) < 6f;
        float lerpSpeed;
        if (funtimeFastSnapTicks > 0) {
            lerpSpeed = 45f;
        } else if (canAttackReady && nearCenter) {
            // Когда крит готов и мы возле центра — финальный быстрый snap
            // ТОЧНО на pureYaw без offset'а маятника, чтобы удар прошёл в хитбокс.
            funtimeTargetYaw = funtimePureYaw;
            funtimeTargetPitch = funtimePurePitch;
            lerpSpeed = 35f;
        } else {
            lerpSpeed = 22.5f;
        }

        // Frame-rate-independent linear lerp.
        float factor = 1f - (float) Math.exp(-lerpSpeed * dt);
        float yawDiff = MathHelper.wrapDegrees(funtimeTargetYaw - funtimeCurrentYaw);
        float pitchDiff = funtimeTargetPitch - funtimeCurrentPitch;

        funtimeCurrentYaw += yawDiff * factor;
        funtimeCurrentPitch += pitchDiff * factor;
        funtimeCurrentPitch = MathHelper.clamp(funtimeCurrentPitch, -89f, 89f);

        // GCD-фикс
        float gcd = GCDFixer.getGCDValue();
        float outYaw = funtimeCurrentYaw - ((funtimeCurrentYaw - lastYaw) % gcd);
        float outPitch = funtimeCurrentPitch - ((funtimeCurrentPitch - lastPitch) % gcd);
        outPitch = MathHelper.clamp(outPitch, -89f, 89f);

        RotationComponent.update(new Rotation(outYaw, outPitch),
                360f, 45f, 45f, 45f, 0, 1, clientLook.getValue());

        lastYaw = outYaw;
        lastPitch = outPitch;
    }

    private Vec3d resolveMultipoint(LivingEntity target, Vec3d point, double range) {
        if (!smartAim.getValue() || target == null) {
            return point;
        }

        return BestPoint.getNearestVisiblePoint(target, point, range);
    }

    private void renderPredictPoint(MatrixStack matrices, Camera camera, float tickDelta) {
        if (target == null || !target.isGliding()) return;

        Vec3d predictPos = PredictUtils.predict(target, predictValue.getValue());
        Vec3d camPos = camera.getPos();

        double renderX = predictPos.x - camPos.x;
        double renderY = predictPos.y - camPos.y;
        double renderZ = predictPos.z - camPos.z;

        float size = 0.35f;
        int color = ColorProvider.getColorClient();

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 1;

        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);

        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, size, size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, -size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, -size).color(r, g, b, a);

        buffer.vertex(matrix, size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, size, size, size).color(r, g, b, a);

        buffer.vertex(matrix, -size, -size, size).color(r, g, b, a);
        buffer.vertex(matrix, -size, size, size).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        matrices.pop();
    }


    /**
//     * Neuro rotation — использует NeuroProfile, обученный через aim-тренажёр.
     */
    private void updateNeuroRotation(LivingEntity target) {
        if (target == null || mc.player == null) return;

        var manager = dev.endless.util.neuro.trainer.NeuroProfileManager.get();
        var profile = manager.getActive();
        if (profile == null) {
            // Fallback на старую систему паттернов если нет профиля
            dev.endless.util.rotation.PatternManager.getInstance().updateRotation(target, lastYaw, lastPitch);
            return;
        }

        // Лениво создаём predictor
        if (neuroPredictor == null || neuroPredictor.getProfile() != profile) {
            neuroPredictor = new dev.endless.util.neuro.trainer.NeuroProfilePredictor(profile);
            neuroLastTarget = null;
        }

        // Целимся в центр хитбокса с предиктом
        net.minecraft.util.math.Vec3d aimPoint = target.isGliding() && predictate.getValue()
                ? PredictUtils.predict(target, predictValue.getValue())
                : target.getBoundingBox().getCenter();

        var rotation = new Rotation(RotationUtil.calculate(aimPoint));
        float wantedYaw = rotation.getYaw();
        float wantedPitch = rotation.getPitch();

        if (neuroLastTarget != target) {
            neuroLastTarget = target;
            neuroPredictor.newTarget(lastYaw, lastPitch, wantedYaw, wantedPitch);
        }

        float[] step = neuroPredictor.stepTowards(wantedYaw, wantedPitch);
        float newYaw = step[0];
        float newPitch = MathHelper.clamp(step[1], -89.5f, 89.5f);

        var legitRot = new Rotation(newYaw, newPitch);
        RotationComponent.update(legitRot, 360, 360, 360, 360, 0, 1, clientLook.getValue());
        lastYaw = legitRot.getYaw();
        lastPitch = legitRot.getPitch();
    }

    private dev.endless.util.neuro.trainer.NeuroProfilePredictor neuroPredictor;
    private LivingEntity neuroLastTarget;

    /**
     * Плавное «довод» реальной камеры (mc.player.yaw/pitch) к последней цели
     * сразу после выключения KillAura. Срабатывает, если на момент disable
     * был активный target.
     */
    private void tickDisableTransition() {
        if (!disableTransitionActive) return;
        if (mc.player == null || mc.world == null
                || disableTransitionTarget == null
                || !disableTransitionTarget.isAlive()) {
            disableTransitionActive = false;
            return;
        }

        long elapsed = System.currentTimeMillis() - disableTransitionStartMs;
        float t = MathHelper.clamp(elapsed / (float) DISABLE_TRANSITION_MS, 0f, 1f);
        // Ease-out cubic — резко стартует, плавно садится.
        float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);

        // Целевая точка — корпус с предиктом, если цель в элитре.
        Vec3d aim = disableTransitionTarget.getBoundingBox().getCenter();
        if (predictate.getValue() && disableTransitionTarget.isGliding()) {
            aim = PredictUtils.predict(disableTransitionTarget, predictValue.getValue());
        }
        float[] ideal = RotationUtil.calculateAngle(aim);
        float targetYaw = ideal[0];
        float targetPitch = MathHelper.clamp(ideal[1], -89.5f, 89.5f);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - disableTransitionStartYaw);
        float pitchDelta = targetPitch - disableTransitionStartPitch;

        float newYaw = disableTransitionStartYaw + yawDelta * eased;
        float newPitch = MathHelper.clamp(
                disableTransitionStartPitch + pitchDelta * eased, -89.5f, 89.5f);

        // Двигаем именно реальную камеру игрока — модуль уже выключен, поэтому
        // RotationComponent больше не выставляет ротацию.
        mc.player.setYaw(newYaw);
        mc.player.setPitch(newPitch);

        if (t >= 1.0f) {
            disableTransitionActive = false;
            disableTransitionTarget = null;
        }
    }

    @Override
    public void onEnable() {
        target = null;
        razvorotikTicks = 0;
        Endless.getInstance().getModuleStorage().setSpeedAcceleration(0);
        funtimeShakeStartMs = -1L;
        // Funtime: сбрасываем per-frame state.
        funtimeSide = Math.random() < 0.5 ? 1 : -1;
        funtimeOscPhase = 0f;
        funtimePrevTarget = null;
        funtimeFastSnapTicks = 0;
        funtimeCurrentYaw = mc.player != null ? mc.player.getYaw() : 0f;
        funtimeCurrentPitch = mc.player != null ? mc.player.getPitch() : 0f;
        funtimeActive = false;
        funtimeLastFrameNanos = 0L;

        if (!renderListenerRegistered) {
            WorldRenderEvents.LAST.register(renderListener);
            renderListenerRegistered = true;
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Когда выключаем KillAura — фиксируем камеру в том угле, который последним
        // отправлялся на сервер (lastYaw/lastPitch). Это нужно потому, что во время
        // работы аура шлёт server-side ротацию через RotationComponent.update, и сервер
        // видит игрока смотрящим именно туда. Если мы оставим клиентский yaw как есть,
        // он окажется развёрнут "куда смотрел до ауры" — рассинхрон с сервером =
        // флаг от античита. Поэтому синхронизируем клиентский view с server-углом.
        if (mc.player != null && (lastYaw != 0f || lastPitch != 0f)) {
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(MathHelper.clamp(lastPitch, -89.5f, 89.5f));
            mc.player.prevYaw = lastYaw;
            mc.player.prevPitch = MathHelper.clamp(lastPitch, -89.5f, 89.5f);
        }

        // Доворот камеры на последнюю цель отключён — оставляем камеру там, где
        // её оставила аура.
        disableTransitionActive = false;
        disableTransitionTarget = null;

        target = null;
        ticksToAttack = 0;
        speedAcceleration = 0;
        razvorotikTicks = 0;
        isResolving = false;
        resolverPoint = null;
        Endless.getInstance().getModuleStorage().setSpeedAcceleration(0);
        Endless.getInstance().getModuleStorage().setRandomness(1);

        super.onDisable();
    }
}






