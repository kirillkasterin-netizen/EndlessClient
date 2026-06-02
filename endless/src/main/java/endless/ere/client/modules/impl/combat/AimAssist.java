package endless.ere.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import endless.ere.base.events.impl.input.EventMouseRotation;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.render.EventHudRender;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.MultiBooleanSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.client.modules.impl.combat.wraith.WraithCombatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Плавно помогает доводиться по цели. Перенесено 1:1 из WraithClient.
 *
 * Логика разделена так же, как в оригинале:
 *   • тик ({@link #onUpdate}) — выбор цели и расчёт целевых углов;
 *   • кадр ({@link #onFrameTick}) — dt-сглаживание реальной камеры к цели,
 *     чтобы плавность не зависела от FPS.
 *
 * Мышь читается через {@link EventMouseRotation} (аналог LookEvent),
 * движение камеры применяется напрямую к yaw/pitch игрока — так же, как
 * Wraith двигал её через RotationComponent.
 */
@ModuleAnnotation(name = "Aim Assist", category = Category.COMBAT,
        description = "Плавно помогает доводиться по цели")
public final class AimAssist extends Module {

    public static final AimAssist INSTANCE = new AimAssist();

    private static final String[] PATTERNS = {"Adaptive", "Wave", "Circle", "Spiral"};

    // ── Основное ──────────────────────────────────────────────────────────────
    private final NumberSetting range = new NumberSetting("Дистанция", 4.5f, 1.0f, 8.0f, 0.1f);
    private final NumberSetting fov = new NumberSetting("FOV", 90f, 10f, 360f, 1f);
    private final NumberSetting perlinNoise = new NumberSetting("Perlin noise", 0.6f, 0f, 3f, 0.01f);
    private final NumberSetting priority = new NumberSetting("Приоритет ротации", 1f, 0f, 5f, 1f);

    // ── Скорость ──────────────────────────────────────────────────────────────
    private final NumberSetting yawVerticalSpeed = new NumberSetting("Скорость Yaw по вертикали", 2.4f, 0.1f, 15f, 0.1f);
    private final NumberSetting pitchVerticalSpeed = new NumberSetting("Скорость Pitch по вертикали", 2.8f, 0.1f, 15f, 0.1f);
    private final NumberSetting pitchHorizontalSpeed = new NumberSetting("Скорость Pitch по горизонтали", 1.6f, 0.1f, 15f, 0.1f);
    private final NumberSetting yawHorizontalSpeed = new NumberSetting("Скорость Yaw по горизонтали", 3.6f, 0.1f, 15f, 0.1f);
    private final NumberSetting maxStep = new NumberSetting("Макс. шаг за тик", 12f, 1f, 45f, 0.5f);
    private final ModeSetting smoothing = new ModeSetting("Кривая плавности", "Exponential", "Linear", "Quintic");

    // ── Точка прицеливания ────────────────────────────────────────────────────
    private final BooleanSetting multipoints = new BooleanSetting("MultiPoints", true);
    private final BooleanSetting randomOffsets = new BooleanSetting("Рандом оффсеты", true);
    private final MultiBooleanSetting hitZones = new MultiBooleanSetting("Зоны",
            new MultiBooleanSetting.Value("Голова", true),
            new MultiBooleanSetting.Value("Тело", true),
            new MultiBooleanSetting.Value("Ноги", false));

    // ── Человечность ──────────────────────────────────────────────────────────
    private final BooleanSetting humanMode = new BooleanSetting("Человеческий режим", true);
    private final BooleanSetting patternSwap = new BooleanSetting("Смена паттернов", true);
    private final NumberSetting patternSwapDelay = new NumberSetting("Задержка смены паттерна", 800f, 200f, 3000f, 50f,
            () -> patternSwap.isEnabled());

    private final BooleanSetting stutters = new BooleanSetting("Затупы", true);
    private final NumberSetting stutterChance = new NumberSetting("Шанс затупа", 6f, 0f, 100f, 1f, () -> stutters.isEnabled());
    private final NumberSetting stutterTicks = new NumberSetting("Длительность затупа", 2f, 1f, 8f, 1f, () -> stutters.isEnabled());

    private final BooleanSetting reactionDelay = new BooleanSetting("Задержка реакции", false);
    private final NumberSetting reactionMs = new NumberSetting("Реакция (мс)", 120f, 0f, 500f, 5f, () -> reactionDelay.isEnabled());

    // ── Активация ─────────────────────────────────────────────────────────────
    private final ModeSetting activation = new ModeSetting("Активация", "Только с ЛКМ", "Всегда", "При зажатой ПКМ", "При клике");
    private final NumberSetting clickHold = new NumberSetting("Удержание после клика (мс)", 180f, 0f, 1000f, 10f,
            () -> activation.is("При клике"));
    private final BooleanSetting onlyWeapon = new BooleanSetting("Только с оружием", true);
    private final BooleanSetting throughWalls = new BooleanSetting("Через стены", false);
    private final BooleanSetting pauseWhileEating = new BooleanSetting("Пауза при еде/блоке", true);
    private final BooleanSetting pauseInScreen = new BooleanSetting("Пауза в интерфейсе", true);
    private final BooleanSetting allowGliding = new BooleanSetting("Работать в элитре", false);
    private final BooleanSetting allowInVehicle = new BooleanSetting("Работать в транспорте", false);

    // ── Таргеты ───────────────────────────────────────────────────────────────
    private final MultiBooleanSetting targets = new MultiBooleanSetting("Таргеты",
            new MultiBooleanSetting.Value("Игроки", true),
            new MultiBooleanSetting.Value("Голые", true),
            new MultiBooleanSetting.Value("Монстры", false),
            new MultiBooleanSetting.Value("Животные", false));
    private final BooleanSetting ignoreInvisible = new BooleanSetting("Игнорить невидимых", true);
    private final BooleanSetting ignoreTeammates = new BooleanSetting("Игнорить тиммейтов", true);

    // ── Отрисовка ─────────────────────────────────────────────────────────────
    private final BooleanSetting drawFov = new BooleanSetting("Показывать FOV", true);

    // ── Внутреннее состояние ──────────────────────────────────────────────────
    private LivingEntity target;
    private long lastMouseMoveAt;
    private double lastMouseYawInput;
    private double lastMousePitchInput;
    private double lastMouseMagnitude;
    private long patternSwapAt;
    private int patternIndex;
    private int stutterTicksLeft;
    private long reactionAnchorMs;
    private LivingEntity reactionLockedTarget;
    private long lastClickAt;
    private double noiseTime;
    private Vec3d cachedAimPoint = Vec3d.ZERO;

    /** Текущий «целевой» yaw/pitch для интерполяции в каждом кадре. */
    private float targetYaw = 0f;
    private float targetPitch = 0f;
    private boolean hasTargetRotation = false;
    /** Точное время последнего frame-апдейта в нс — для расчёта dt. */
    private long lastFrameNanos = 0L;

    private AimAssist() {
    }

    // ── Мышь (аналог LookEvent) ─────────────────────────────────────────────

    @EventTarget
    public void onLook(EventMouseRotation event) {
        lastMouseYawInput = event.getCursorDeltaX();
        lastMousePitchInput = event.getCursorDeltaY();
        lastMouseMagnitude = Math.abs(event.getCursorDeltaX()) + Math.abs(event.getCursorDeltaY());
        if (lastMouseMagnitude > 0.01) {
            lastMouseMoveAt = System.currentTimeMillis();
        }
    }

    // ── Тик: выбор цели и целевых углов ──────────────────────────────────────

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.options != null && mc.options.attackKey.isPressed()) {
            lastClickAt = System.currentTimeMillis();
        }

        if (!canAssist()) {
            resetTransientState();
            return;
        }

        rotatePatternIfNeeded();
        tickStutters();

        target = findTarget();
        if (target == null) {
            cachedAimPoint = Vec3d.ZERO;
            reactionLockedTarget = null;
            reactionAnchorMs = 0L;
            hasTargetRotation = false;
            return;
        }

        if (!passReactionGate(target)) {
            hasTargetRotation = false;
            return;
        }
        if (stutterTicksLeft > 0) {
            hasTargetRotation = false;
            return;
        }

        cachedAimPoint = selectAimPoint(target);
        float[] rotations = WraithCombatUtil.calculateAngle(cachedAimPoint);
        targetYaw = rotations[0];
        targetPitch = rotations[1];
        hasTargetRotation = true;
    }

    // ── Кадр: dt-сглаживание реальной камеры к цели ──────────────────────────

    @EventTarget
    public void onFrameTick(EventRender3D event) {
        if (!isEnabled() || !hasTargetRotation || target == null || mc.player == null) return;
        if (!canAssist()) return;
        if (stutterTicksLeft > 0) return;

        long nowNanos = System.nanoTime();
        float dtSeconds;
        if (lastFrameNanos == 0L) {
            dtSeconds = 1f / 60f;
        } else {
            dtSeconds = (nowNanos - lastFrameNanos) / 1_000_000_000f;
        }
        lastFrameNanos = nowNanos;
        dtSeconds = MathHelper.clamp(dtSeconds, 0.001f, 0.1f);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float totalDiff = (float) Math.hypot(yawDiff, pitchDiff);
        if (totalDiff < 0.001f) return;

        double horizontalWeight = Math.abs(yawDiff) / (Math.abs(yawDiff) + Math.abs(pitchDiff) + 1.0E-4);
        double verticalWeight = 1.0 - horizontalWeight;

        float perSec = 20f; // 20 тиков/сек — скорости в настройках «за тик»
        float maxYawPerSec = (float) ((yawHorizontalSpeed.getCurrent() * horizontalWeight
                + yawVerticalSpeed.getCurrent() * verticalWeight) * perSec);
        float maxPitchPerSec = (float) ((pitchHorizontalSpeed.getCurrent() * horizontalWeight
                + pitchVerticalSpeed.getCurrent() * verticalWeight) * perSec);

        float maxYawStep = maxYawPerSec * dtSeconds;
        float maxPitchStep = maxPitchPerSec * dtSeconds;

        float baseAssist = MathHelper.clamp(totalDiff / Math.max(1.0f, getHalfFov()), 0.25f, 1.0f);
        baseAssist = applySmoothingCurve(baseAssist);
        float cooperationYaw = getCooperationFactor(yawDiff, lastMouseYawInput);
        float cooperationPitch = getCooperationFactor(pitchDiff, lastMousePitchInput);

        float mouseFactor = 1.0f;
        if (humanMode.isEnabled()) {
            long sinceMove = System.currentTimeMillis() - lastMouseMoveAt;
            if (sinceMove < 250L) {
                mouseFactor = MathHelper.clamp(0.85f + (float) lastMouseMagnitude / 10.0f, 0.85f, 1.25f);
            }
        }

        float yawStep = MathHelper.clamp(yawDiff * baseAssist * cooperationYaw * mouseFactor, -maxYawStep, maxYawStep);
        float pitchStep = MathHelper.clamp(pitchDiff * baseAssist * cooperationPitch * mouseFactor, -maxPitchStep, maxPitchStep);

        float[] patternOffset = getPatternOffset(target, totalDiff);
        float[] noiseOffset = getPerlinOffset(totalDiff);
        float frameScale = dtSeconds * perSec; // 1.0 на 50мс кадр, ~0.33 на 16мс
        yawStep += (patternOffset[0] + noiseOffset[0]) * frameScale;
        pitchStep += (patternOffset[1] + noiseOffset[1]) * frameScale;

        if (humanMode.isEnabled()) {
            yawStep = clampToMouseIntent(yawStep, lastMouseYawInput, maxYawStep * 1.15f);
            pitchStep = clampToMouseIntent(pitchStep, lastMousePitchInput, maxPitchStep * 1.15f);
        }

        float hardCap = maxStep.getCurrent() * frameScale;
        yawStep = MathHelper.clamp(yawStep, -hardCap, hardCap);
        pitchStep = MathHelper.clamp(pitchStep, -hardCap, hardCap);

        float finalYaw = currentYaw + yawStep;
        float finalPitch = MathHelper.clamp(currentPitch + pitchStep, -89.0f, 89.0f);

        mc.player.setYaw(finalYaw);
        mc.player.setPitch(finalPitch);
    }

    // ── FOV круг ──────────────────────────────────────────────────────────────

    @EventTarget
    public void onHud(EventHudRender event) {
        if (!drawFov.isEnabled() || mc.player == null || mc.currentScreen != null) return;

        float radius = getFovCircleRadius();
        if (radius <= 1.0f) return;

        float centerX = mc.getWindow().getScaledWidth() / 2.0f;
        float centerY = mc.getWindow().getScaledHeight() / 2.0f;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(1.0f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        Matrix4f matrix = event.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        int color = 0xBEFFFFFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        for (int i = 0; i <= 96; i++) {
            double angle = (Math.PI * 2.0 * i) / 96.0;
            float x = centerX + (float) Math.cos(angle) * radius;
            float y = centerY + (float) Math.sin(angle) * radius;
            buffer.vertex(matrix, x, y, 0.0f).color(r, g, b, a);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    // ── Активация / валидность ──────────────────────────────────────────────

    private boolean canAssist() {
        if (mc.player == null || mc.world == null) return false;
        if (pauseInScreen.isEnabled() && mc.currentScreen != null) return false;
        if (!allowInVehicle.isEnabled() && mc.player.hasVehicle()) return false;
        if (!allowGliding.isEnabled() && mc.player.isGliding()) return false;
        if (pauseWhileEating.isEnabled() && mc.player.isUsingItem()) return false;
        if (onlyWeapon.isEnabled() && !isCombatWeaponHeld()) return false;

        return switch (activation.get()) {
            case "Только с ЛКМ" -> mc.options.attackKey.isPressed();
            case "При зажатой ПКМ" -> mc.options.useKey.isPressed();
            case "При клике" -> System.currentTimeMillis() - lastClickAt <= clickHold.getCurrent();
            default -> true;
        };
    }

    private void tickStutters() {
        if (!stutters.isEnabled()) {
            stutterTicksLeft = 0;
            return;
        }
        if (stutterTicksLeft > 0) {
            stutterTicksLeft--;
            return;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) <= stutterChance.getCurrent()) {
            stutterTicksLeft = ThreadLocalRandom.current().nextInt(1, (int) stutterTicks.getCurrent() + 1);
        }
    }

    private void rotatePatternIfNeeded() {
        if (!patternSwap.isEnabled()) {
            patternIndex = 0;
            return;
        }
        long now = System.currentTimeMillis();
        if (now - patternSwapAt >= patternSwapDelay.getCurrent()) {
            patternSwapAt = now;
            patternIndex = (patternIndex + 1) % PATTERNS.length;
        }
    }

    private boolean passReactionGate(LivingEntity current) {
        if (!reactionDelay.isEnabled()) {
            reactionLockedTarget = current;
            return true;
        }
        long now = System.currentTimeMillis();
        if (reactionLockedTarget != current) {
            reactionLockedTarget = current;
            reactionAnchorMs = now;
            return false;
        }
        return now - reactionAnchorMs >= reactionMs.getCurrent();
    }

    private LivingEntity findTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) continue;
            Vec3d point = selectAimPoint(living);
            float[] angles = WraithCombatUtil.calculateAngle(point);
            float angleDistance = WraithCombatUtil.calculateFov(mc.player.getYaw(), mc.player.getPitch(), angles[0], angles[1]);
            if (angleDistance > getHalfFov()) continue;
            double score = angleDistance * 3.0 + mc.player.getEyePos().distanceTo(point);
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive() || entity == mc.player || entity instanceof ArmorStandEntity) return false;
        if (ignoreInvisible.isEnabled() && entity.isInvisible()) return false;
        if (ignoreTeammates.isEnabled() && mc.player.isTeammate(entity)) return false;
        if (mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) > range.getCurrent()) return false;

        if (entity instanceof PlayerEntity player) {
            if (AntiBot.INSTANCE.isEnabled() && AntiBot.INSTANCE.isBot(player)) return false;
            if (WraithCombatUtil.isFriend(player)) return false;
            if (player.getArmor() > 0 && !targets.isEnable("Игроки")) return false;
            if (player.getArmor() == 0 && !targets.isEnable("Голые")) return false;
        } else if ((entity instanceof HostileEntity || entity instanceof AmbientEntity) && !targets.isEnable("Монстры")) {
            return false;
        } else if ((entity instanceof PassiveEntity || entity instanceof FishEntity) && !targets.isEnable("Животные")) {
            return false;
        }

        if (!throughWalls.isEnabled()) {
            Vec3d point = getZonePoint(entity, "Тело");
            HitResult hitResult = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), point,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hitResult.getType() == HitResult.Type.BLOCK) return false;
        }
        return true;
    }

    private float applySmoothingCurve(float value) {
        return switch (smoothing.get()) {
            case "Linear" -> value;
            case "Quintic" -> {
                float u = 1.0f - value;
                yield 1.0f - u * u * u * u * u;
            }
            default -> {
                float u = 1.0f - value;
                yield 1.0f - u * u * u;
            }
        };
    }

    private float clampToMouseIntent(float assistStep, double mouseStep, float limit) {
        long sinceMove = System.currentTimeMillis() - lastMouseMoveAt;
        if (sinceMove > 250L || Math.abs(mouseStep) < 0.005) {
            return MathHelper.clamp(assistStep, -limit, limit);
        }
        float sameDirectionBoost = Math.signum((float) mouseStep) == Math.signum(assistStep) ? 1.15f : 0.9f;
        return MathHelper.clamp(assistStep * sameDirectionBoost, -limit, limit);
    }

    private float getCooperationFactor(float desiredDiff, double mouseInput) {
        if (!humanMode.isEnabled()) return 1.0f;
        long sinceMove = System.currentTimeMillis() - lastMouseMoveAt;
        if (sinceMove > 180L || Math.abs(mouseInput) < 0.01) return 1.0f;
        if (Math.signum(desiredDiff) == Math.signum((float) mouseInput)) return 1.25f;
        return 0.9f;
    }

    private Vec3d selectAimPoint(LivingEntity entity) {
        List<Vec3d> points = collectAimPoints(entity);
        if (points.isEmpty()) return entity.getBoundingBox().getCenter();

        Vec3d eyePos = mc.player.getEyePos();
        Vec3d best = points.get(0);
        double bestScore = Double.MAX_VALUE;
        for (Vec3d point : points) {
            float[] angles = WraithCombatUtil.calculateAngle(point);
            float fovScore = WraithCombatUtil.calculateFov(mc.player.getYaw(), mc.player.getPitch(), angles[0], angles[1]);
            double score = fovScore + eyePos.squaredDistanceTo(point) * 0.02;
            if (score < bestScore) {
                bestScore = score;
                best = point;
            }
        }
        return randomOffsets.isEnabled() ? addRandomOffset(entity, best) : best;
    }

    private List<Vec3d> collectAimPoints(LivingEntity entity) {
        List<String> zones = getActiveZones();
        List<Vec3d> points = new ArrayList<>();
        if (!multipoints.isEnabled()) {
            for (String zone : zones) points.add(getZonePoint(entity, zone));
            return points;
        }
        Box box = entity.getBoundingBox();
        double[] xs = {box.minX + box.getLengthX() * 0.2, box.getCenter().x, box.maxX - box.getLengthX() * 0.2};
        double[] zs = {box.minZ + box.getLengthZ() * 0.2, box.getCenter().z, box.maxZ - box.getLengthZ() * 0.2};
        for (String zone : zones) {
            double y = switch (zone) {
                case "Голова" -> box.minY + box.getLengthY() * 0.88;
                case "Ноги" -> box.minY + box.getLengthY() * 0.18;
                default -> box.minY + box.getLengthY() * 0.58;
            };
            for (double x : xs) for (double z : zs) points.add(new Vec3d(x, y, z));
        }
        return points;
    }

    private List<String> getActiveZones() {
        List<String> zones = new ArrayList<>();
        if (hitZones.isEnable("Голова")) zones.add("Голова");
        if (hitZones.isEnable("Тело")) zones.add("Тело");
        if (hitZones.isEnable("Ноги")) zones.add("Ноги");
        if (zones.isEmpty()) zones.add("Тело");
        return zones;
    }

    private Vec3d getZonePoint(LivingEntity entity, String zone) {
        Box box = entity.getBoundingBox();
        double x = box.getCenter().x;
        double z = box.getCenter().z;
        double y = switch (zone) {
            case "Голова" -> box.minY + box.getLengthY() * 0.9;
            case "Ноги" -> box.minY + box.getLengthY() * 0.18;
            default -> box.minY + box.getLengthY() * 0.58;
        };
        return new Vec3d(x, y, z);
    }

    private Vec3d addRandomOffset(LivingEntity entity, Vec3d point) {
        Box box = entity.getBoundingBox();
        double horizontal = Math.min(box.getLengthX(), box.getLengthZ()) * 0.18;
        double vertical = box.getLengthY() * 0.08;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return point.add(
                random.nextDouble(-horizontal, horizontal),
                random.nextDouble(-vertical, vertical),
                random.nextDouble(-horizontal, horizontal));
    }

    private float[] getPatternOffset(LivingEntity entity, float totalDiff) {
        String pattern = PATTERNS[patternIndex].toLowerCase(Locale.ROOT);
        double time = (System.currentTimeMillis() % 10_000L) / 280.0;
        float strength = MathHelper.clamp(totalDiff / 90.0f, 0.12f, 1.0f);
        return switch (pattern) {
            case "wave" -> new float[]{
                    (float) Math.sin(time + entity.getId() * 0.35) * 0.16f * strength,
                    (float) Math.cos(time * 1.3 + entity.getId() * 0.21) * 0.11f * strength};
            case "circle" -> new float[]{
                    (float) Math.cos(time) * 0.18f * strength,
                    (float) Math.sin(time) * 0.13f * strength};
            case "spiral" -> {
                float spiral = (float) ((Math.sin(time) + 1.0) * 0.5);
                yield new float[]{
                        (float) Math.cos(time * 1.2) * (0.08f + spiral * 0.16f) * strength,
                        (float) Math.sin(time * 1.2) * (0.06f + spiral * 0.12f) * strength};
            }
            default -> new float[]{
                    (float) Math.sin(time * 0.75 + entity.getId()) * 0.07f * strength,
                    (float) Math.cos(time * 0.95 + entity.getId()) * 0.05f * strength};
        };
    }

    private float[] getPerlinOffset(float totalDiff) {
        float amplitude = perlinNoise.getCurrent();
        if (amplitude <= 0.0f) return new float[]{0.0f, 0.0f};
        noiseTime += 0.043 + Math.min(lastMouseMagnitude, 8.0) * 0.0008;
        float diffFactor = MathHelper.clamp(totalDiff / 90.0f, 0.12f, 1.0f);
        float yaw = perlin1D(noiseTime) * amplitude * 0.14f * diffFactor;
        float pitch = perlin1D(noiseTime + 37.0) * amplitude * 0.09f * diffFactor;
        return new float[]{yaw, pitch};
    }

    private float perlin1D(double x) {
        int x0 = MathHelper.floor(x);
        double local = x - x0;
        double fade = local * local * local * (local * (local * 6.0 - 15.0) + 10.0);
        double g0 = gradient(x0);
        double g1 = gradient(x0 + 1);
        double v0 = g0 * local;
        double v1 = g1 * (local - 1.0);
        return (float) MathHelper.lerp((float) fade, (float) v0, (float) v1);
    }

    private double gradient(int x) {
        long hash = x * 0x27d4eb2dL;
        hash ^= hash >> 15;
        hash *= 0x85ebca6bL;
        hash ^= hash >> 13;
        return ((hash & 1L) == 0L) ? 1.0 : -1.0;
    }

    private float getHalfFov() {
        return Math.max(1.0f, fov.getCurrent() * 0.5f);
    }

    private float getFovCircleRadius() {
        float aimFov = fov.getCurrent();
        float screen = Math.min(mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        float gameFov = Math.max(1.0f, mc.options.getFov().getValue().floatValue());
        if (aimFov >= 180.0f) {
            return MathHelper.clamp(screen * (aimFov / 360.0f), 12.0f, screen);
        }
        double tangentRatio = Math.tan(Math.toRadians(aimFov * 0.5f)) / Math.tan(Math.toRadians(gameFov * 0.5f));
        return MathHelper.clamp((float) tangentRatio * (mc.getWindow().getScaledHeight() * 0.5f), 8.0f, screen);
    }

    private boolean isCombatWeaponHeld() {
        if (mc.player == null) return false;
        Object item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem;
    }

    private void resetTransientState() {
        target = null;
        cachedAimPoint = Vec3d.ZERO;
        stutterTicksLeft = 0;
        reactionLockedTarget = null;
        reactionAnchorMs = 0L;
        hasTargetRotation = false;
        lastFrameNanos = 0L;
    }

    @Override
    public void onEnable() {
        target = null;
        cachedAimPoint = Vec3d.ZERO;
        patternSwapAt = System.currentTimeMillis();
        patternIndex = 0;
        stutterTicksLeft = 0;
        reactionLockedTarget = null;
        reactionAnchorMs = 0L;
        lastClickAt = 0L;
        noiseTime = ThreadLocalRandom.current().nextDouble(0.0, 5000.0);
        hasTargetRotation = false;
        lastFrameNanos = 0L;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetTransientState();
        super.onDisable();
    }
}
