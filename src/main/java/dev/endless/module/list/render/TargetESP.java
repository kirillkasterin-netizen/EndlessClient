package dev.endless.module.list.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import dev.endless.Endless;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.combat.KillAura;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Target ESP — visual marker on the current KillAura target.
 *
 * Modes:
 *  <ul>
 *    <li><b>Crystals</b>, <b>Prizraki</b> — original Wraith renderers.</li>
 *    <li><b>Картинка 1/2</b>, <b>Кольцо</b>, <b>Души</b>, <b>Призраки</b>,
 *        <b>Кубы</b>, <b>Кристаллы</b>, <b>Райдер</b> — ports from Wonderful.</li>
 *  </ul>
 */
@ModuleInformation(moduleName = "Target ESP", moduleDesc = "Визуальный эффект на текущей цели", moduleCategory = ModuleCategory.RENDER)
public class TargetESP extends Module {

    // ── Texture identifiers ──────────────────────────────────────────────────
    private static final Identifier WRAITH_BLOOM = Identifier.of("mre", "images/bloom.png");
    private static final Identifier WF_BLOOM     = Identifier.of("mre", "textures/targetesp/bloom.png");
    private static final Identifier WF_CAPTURE_1 = Identifier.of("mre", "textures/targetesp/targetesp_2.png");
    private static final Identifier WF_CAPTURE_2 = Identifier.of("mre", "textures/targetesp/targetesp_3.png");

    // ── Cube particle constants ─────────────────────────────────────────────
    static final long CUBE_ATTACH_LIFE_MS = 560L;
    static final long CUBE_FADE_LIFE_MS   = 320L;
    static final int  MAX_CUBE_PARTICLES  = 72;
    static final byte[][] CUBE_EDGES = {
            {-1,-1,-1, 1,-1,-1}, {1,-1,-1, 1,-1, 1}, {1,-1, 1,-1,-1, 1}, {-1,-1, 1,-1,-1,-1},
            {-1, 1,-1, 1, 1,-1}, {1, 1,-1, 1, 1, 1}, {1, 1, 1,-1, 1, 1}, {-1, 1, 1,-1, 1,-1},
            {-1,-1,-1,-1, 1,-1}, {1,-1,-1, 1, 1,-1}, {1,-1, 1, 1, 1, 1}, {-1,-1, 1,-1, 1, 1}
    };

    private static final float GHOST_ALPHA_MULT = 0.6f;
    private static final float CELKA_SPEED_MULT = 1.2f;
    private static final float SCALE_FACTOR = 0.007f;
    private static final float CUBE_SPAWN_INTERVAL = 0.022f;
    private static final int   CUBE_PARTICLES_PER_SPAWN = 1;

    // ── Settings ────────────────────────────────────────────────────────────
    public final ModeSetting mode = new ModeSetting("Режим", "Crystals",
            "Crystals", "Prizraki",
            "Картинка 1", "Картинка 2", "Кольцо", "Души", "Призраки",
            "Кубы", "Кристаллы", "Райдер");

    // Prizraki (original Wraith)
    private final SliderSetting ghostSpeed = (SliderSetting) new SliderSetting("Скорость призраков", 1.0, 0.5, 2.0, 0.1)
            .setVisible(() -> mode.is("Prizraki"));
    private final SliderSetting prizrakiSize = (SliderSetting) new SliderSetting("Размер призраков", 0.4, 0.1, 1.0, 0.05)
            .setVisible(() -> mode.is("Prizraki"));

    // Wonderful-port settings
    private final SliderSetting size = (SliderSetting) new SliderSetting("Размер", 1.15, 0.6, 2.5, 0.05)
            .setVisible(() -> mode.is("Картинка 1") || mode.is("Картинка 2"));
    private final SliderSetting rotateSpeed = (SliderSetting) new SliderSetting("Скорость вращения", 1.2, 0.2, 4.0, 0.05)
            .setVisible(() -> mode.is("Картинка 1") || mode.is("Картинка 2"));
    private final SliderSetting ringRadius = (SliderSetting) new SliderSetting("Радиус кольца", 0.5, 0.3, 1.5, 0.05)
            .setVisible(() -> mode.is("Кольцо"));
    private final SliderSetting ringSpeed = (SliderSetting) new SliderSetting("Скорость кольца", 1.0, 0.3, 3.0, 0.1)
            .setVisible(() -> mode.is("Кольцо"));
    private final SliderSetting bmwGhostCount = (SliderSetting) new SliderSetting("Кол-во призраков", 3, 2, 5, 1)
            .setVisible(() -> mode.is("Райдер"));
    private final SliderSetting bmwGhostLife  = (SliderSetting) new SliderSetting("Время жизни (мс)", 350, 150, 500, 25)
            .setVisible(() -> mode.is("Райдер"));
    private final SliderSetting bmwStrengthXZ = (SliderSetting) new SliderSetting("Цикл XZ", 2000, 1000, 5000, 100)
            .setVisible(() -> mode.is("Райдер"));
    private final SliderSetting bmwStrengthY  = (SliderSetting) new SliderSetting("Цикл Y", 1700, 1000, 5000, 100)
            .setVisible(() -> mode.is("Райдер"));
    private final BooleanSetting hurtColor = new BooleanSetting("Окрашивание при ударе", true);

    // ── Tracked state ───────────────────────────────────────────────────────
    private boolean registered = false;
    private float appearValue = 0f;
    private float scaleValue  = 0f;

    private LivingEntity lastTarget = null;
    private LivingEntity lastHandledTarget = null;
    private Vec3d lastTargetPos = null;
    private float lastTargetHeight = 1.8f;
    private float lastTargetWidth = 0.6f;

    // Картинка 1/2 oscillation
    private float rotProgress = 0f;
    private float rotFrom = -280f;
    private float rotTo = 280f;
    private long lastRotateUpdate = System.currentTimeMillis();

    // "Кристаллы" orbit state
    private float crystalRotationAngle = 0f;
    private float crystalAnimation = 0f;

    // "Кубы" particles
    private float spawnAccumulator = 0f;
    private long lastCubeTime = 0L;
    private final ArrayList<CubeParticle> cubeParticles = new ArrayList<>();
    private final ArrayList<CubeParticle> renderCubeParticles = new ArrayList<>();

    // "Райдер" trail
    private final CopyOnWriteArrayList<GlowPoint> bmwPoints = new CopyOnWriteArrayList<>();

    // Wraith original Crystals fade
    private float wCrystalsAnim = 0f;
    private LivingEntity wCrystalsLastTarget = null;

    // Wraith original Prizraki seed timestamp
    private static final long PRIZRAKI_SEED_TIME = System.currentTimeMillis();

    // ── Listener ────────────────────────────────────────────────────────────
    private final WorldRenderEvents.Last listener = ctx ->
            onRenderWorldLast(ctx.matrixStack(), ctx.camera(), ctx.tickCounter().getTickDelta(true));

    @Override
    public void onEnable() {
        if (!registered) {
            WorldRenderEvents.LAST.register(listener);
            registered = true;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        appearValue = 0f;
        scaleValue = 0f;
        wCrystalsAnim = 0f;
        wCrystalsLastTarget = null;
        crystalRotationAngle = 0f;
        crystalAnimation = 0f;
        rotProgress = 0f;
        rotFrom = -280f;
        rotTo = 280f;
        spawnAccumulator = 0f;
        lastCubeTime = 0L;
        cubeParticles.clear();
        renderCubeParticles.clear();
        bmwPoints.clear();
        lastTarget = null;
        lastHandledTarget = null;
        lastTargetPos = null;
        super.onDisable();
    }

    private void onRenderWorldLast(MatrixStack matrices, Camera camera, float partialTicks) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.gameRenderer == null || camera == null) return;

        Entity raw = Endless.getInstance().getModuleStorage().get(KillAura.class).getTarget();
        LivingEntity target = null;
        if (raw instanceof LivingEntity living
                && raw != mc.player
                && !(raw instanceof ArmorStandEntity)
                && living.isAlive()) {
            target = living;
        }
        boolean hasTarget = target != null;

        appearValue = animateTo(appearValue, hasTarget ? 1f : 0f, 0.05f);
        scaleValue  = animateTo(scaleValue,  hasTarget ? 1f : 0.5f, 0.05f);

        if (hasTarget) {
            lastTarget = target;
            lastHandledTarget = target;
            lastTargetPos = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ()));
            lastTargetHeight = target.getHeight();
            lastTargetWidth  = target.getWidth();
        }

        if (mode.is("Кристаллы")) {
            float cs = hasTarget ? 0.07f : 0.045f;
            crystalAnimation = animateTo(crystalAnimation, hasTarget ? 1f : 0f, cs);
            if (hasTarget) crystalRotationAngle += 0.8f;
        }

        switch (mode.getValue()) {
            case "Crystals" -> renderWraithCrystalsMode(matrices, camera, hasTarget, target, partialTicks);
            case "Prizraki" -> {
                if (hasTarget) renderPrizraki(matrices, camera, target, partialTicks);
            }
            case "Картинка 1", "Картинка 2" -> renderMarker3D(matrices, camera);
            case "Кольцо"   -> drawRing3D(matrices);
            case "Души"     -> drawSouls3D(matrices, camera);
            case "Призраки" -> drawCelka3D(matrices, camera);
            case "Кубы"     -> renderCubes(matrices, target, hasTarget, partialTicks);
            case "Кристаллы"-> renderWfCrystals(matrices, target, partialTicks);
            case "Райдер"   -> {
                if (hasTarget) addBMWGhosts(target,
                        Math.max(1, Math.round(bmwGhostCount.getFloatValue())),
                        Math.max(1, Math.round(bmwGhostLife.getFloatValue())),
                        getESPColor());
                bmwPoints.removeIf(GlowPoint::shouldRemove);
                drawBMW3D(matrices);
            }
            default -> { /* nothing */ }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wraith ORIGINAL — Crystals (with fade) + Prizraki
    // ────────────────────────────────────────────────────────────────────────

    private void renderWraithCrystalsMode(MatrixStack matrices, Camera camera,
                                          boolean hasTarget, LivingEntity target, float partialTicks) {
        if (hasTarget) {
            wCrystalsLastTarget = target;
            wCrystalsAnim = Math.min(1f, wCrystalsAnim + 0.07f);
            if (wCrystalsAnim > 0.001f)
                renderWraithCrystals(matrices, camera, wCrystalsLastTarget, wCrystalsAnim, partialTicks);
        } else {
            wCrystalsAnim = Math.max(0f, wCrystalsAnim - 0.045f);
            if (wCrystalsAnim > 0.001f && wCrystalsLastTarget != null)
                renderWraithCrystals(matrices, camera, wCrystalsLastTarget, wCrystalsAnim, partialTicks);
            else
                wCrystalsLastTarget = null;
        }
    }

    private void renderPrizraki(MatrixStack matrices, Camera camera, LivingEntity target, float partialTicks) {
        Vec3d camPos = camera.getPos();
        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();

        double tx = MathHelper.lerp(partialTicks, target.lastRenderX, target.getX());
        double ty = MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()) + 0.38 + target.getHeight() / 2.0;
        double tz = MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ());

        double rx = tx - camPos.x + 0.2;
        double ry = ty - camPos.y;
        double rz = tz - camPos.z;

        double radius = 0.4 + target.getWidth() / 2.0;
        float speed = 30f / ghostSpeed.getFloatValue();
        float gsz = prizrakiSize.getFloatValue();
        double distance = 6;
        int length = 34;
        long now = System.currentTimeMillis();

        int colorInt = ColorProvider.getColorClient();
        float r = ColorProvider.red(colorInt) / 255f;
        float g = ColorProvider.green(colorInt) / 255f;
        float b = ColorProvider.blue(colorInt) / 255f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WRAITH_BLOOM);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int trail = 0; trail < 3; trail++) {
            for (int i = 0; i < length; i++) {
                double angle = 0.05f * (now - PRIZRAKI_SEED_TIME - (i * distance)) / speed;
                double s = Math.sin(angle * Math.PI) * radius;
                double c = Math.cos(angle * Math.PI) * radius;
                double o = (trail == 0) ? Math.cos(angle * Math.PI) * radius : Math.sin(angle * Math.PI) * radius;

                float t = i / (float) (length - 1);
                float curSize = gsz * (1.0f - t * 0.5f);
                float alpha = 1.0f - t * 0.9f;

                double px = rx + (trail == 1 ? -s : s);
                double py = ry + o;
                double pz = rz + (trail == 2 ? c : -c);

                float cr = Math.min(1f, r * 1.5f), cg = Math.min(1f, g * 1.5f), cb = Math.min(1f, b * 1.5f);
                putBloomQuad(buffer, matrices, px, py, pz, curSize * 0.6f, r, g, b, alpha * 0.15f, camYaw, camPitch);
                putBloomQuad(buffer, matrices, px, py, pz, curSize * 0.35f, r, g, b, alpha * 0.35f, camYaw, camPitch);
                putBloomQuad(buffer, matrices, px, py, pz, curSize * 0.15f, cr, cg, cb, alpha, camYaw, camPitch);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        restoreRenderState();
    }

    private void renderWraithCrystals(MatrixStack matrices, Camera camera,
                                      LivingEntity target, float anim, float partialTicks) {
        if (target == null || mc.player == null) return;

        float eased = easeOutCubic(anim);
        float time = (mc.player.age + partialTicks) * 6.0f;

        Vec3d camPos = camera.getPos();
        double tx = MathHelper.lerp(partialTicks, target.lastRenderX, target.getX());
        double ty = MathHelper.lerp(partialTicks, target.lastRenderY, target.getY());
        double tz = MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ());

        float camYaw = camera.getYaw();
        float camPitch = camera.getPitch();

        float entityHeight = target.getHeight();
        float halfWidth = target.getWidth() * 0.5f;

        int baseColor = ColorProvider.getColorClient();
        float r = Math.min(1f, ColorProvider.red(baseColor) / 255f * 1.3f);
        float g = Math.min(1f, ColorProvider.green(baseColor) / 255f * 1.3f);
        float b = Math.min(1f, ColorProvider.blue(baseColor) / 255f * 1.3f);

        matrices.push();
        matrices.translate(tx - camPos.x, ty - camPos.y, tz - camPos.z);

        int crystalCount = 18;
        float pelvisY = entityHeight * 0.35f;
        float torsoY  = entityHeight * 0.55f;
        float neckY   = entityHeight * 0.74f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        boolean hasCrystals = false;

        for (int i = 0; i < crystalCount; i++) {
            float s1 = (float) Math.sin(i * 1.7f + 0.3f) * 0.5f + 0.5f;
            float s2 = (float) Math.cos(i * 2.3f + 0.7f) * 0.5f + 0.5f;
            float s3 = (float) Math.sin(i * 3.1f + 1.1f) * 0.5f + 0.5f;

            float angle = time + i * (360f / crystalCount) + s1 * 12f;
            float radius = halfWidth + 0.25f + s3 * 0.15f;
            float cx = radius * (float) Math.cos(Math.toRadians(angle));
            float cz = radius * (float) Math.sin(Math.toRadians(angle));
            float cy = s2 * entityHeight;

            float scale = 0.18f * eased;
            if (scale < 0.001f) continue;

            float lookY = getCrystalLookY(cy, entityHeight, pelvisY, torsoY, neckY);
            float dx = -cx, dy = lookY - cy, dz = -cz;
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx));
            float pitch = (float) Math.toDegrees(Math.atan2(dy, (float) Math.sqrt(dx * dx + dz * dz)));

            drawWraithCrystalShape(buf, matrices, cx, cy, cz, scale, yaw, pitch,
                    (int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(200 * anim));
            hasCrystals = true;
        }
        if (hasCrystals) BufferRenderer.drawWithGlobalProgram(buf.end());

        // Bloom pass
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WRAITH_BLOOM);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        BufferBuilder glowBuf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        boolean hasGlow = false;
        for (int i = 0; i < crystalCount; i++) {
            float s1 = (float) Math.sin(i * 1.7f + 0.3f) * 0.5f + 0.5f;
            float s2 = (float) Math.cos(i * 2.3f + 0.7f) * 0.5f + 0.5f;
            float s3 = (float) Math.sin(i * 3.1f + 1.1f) * 0.5f + 0.5f;
            float angle = time + i * (360f / crystalCount) + s1 * 12f;
            float radius = halfWidth + 0.25f + s3 * 0.15f;
            float cx = radius * (float) Math.cos(Math.toRadians(angle));
            float cz = radius * (float) Math.sin(Math.toRadians(angle));
            float cy = s2 * entityHeight;
            float scale = 0.18f * eased;
            if (anim * 0.15f > 0.001f && scale > 0.0001f) {
                putBloomQuad(glowBuf, matrices, cx, cy, cz, scale * 5.5f, r, g, b, anim * 0.15f, camYaw, camPitch);
                putBloomQuad(glowBuf, matrices, cx, cy, cz, scale * 3.5f, r, g, b, anim * 0.25f, camYaw, camPitch);
                hasGlow = true;
            }
        }
        if (hasGlow) BufferRenderer.drawWithGlobalProgram(glowBuf.end());
        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private float getCrystalLookY(float cy, float h, float pelvis, float torso, float neck) {
        float n = cy / h;
        if (n < 0.33f) return pelvis;
        if (n < 0.6f)  return torso;
        return neck;
    }

    private void drawWraithCrystalShape(BufferBuilder buf, MatrixStack ms, float x, float y, float z,
                                         float scale, float yaw, float pitch, int r, int g, int b, int a) {
        ms.push();
        ms.translate(x, y, z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(pitch));
        ms.scale(scale, scale, scale);
        Matrix4f m = ms.peek().getPositionMatrix();
        float w = 0.7f, h = 1.0f;
        triCol(buf, m,  h,0,0,  0,w,0,  0,0,w,  r,g,b,a);
        triCol(buf, m,  h,0,0,  0,w,0,  -w,0,0, r,g,b,a);
        triCol(buf, m,  h,0,0,  -w,0,0, 0,0,-w, r,g,b,a);
        triCol(buf, m,  h,0,0,  0,-w,0, w,0,0,  r,g,b,a);
        triCol(buf, m, -h,0,0,  0,w,0,  0,0,w,  r,g,b,a);
        triCol(buf, m, -h,0,0,  0,w,0,  -w,0,0, r,g,b,a);
        triCol(buf, m, -h,0,0,  -w,0,0, 0,0,-w, r,g,b,a);
        triCol(buf, m, -h,0,0,  0,-w,0, w,0,0,  r,g,b,a);
        ms.pop();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — image marker (Картинка 1 / Картинка 2)
    // ────────────────────────────────────────────────────────────────────────

    private Identifier getCaptureTexture() {
        return mode.is("Картинка 2") ? WF_CAPTURE_2 : WF_CAPTURE_1;
    }

    private void renderMarker3D(MatrixStack matrices, Camera camera) {
        if (lastTargetPos == null || appearValue <= 0.001f) return;

        Vec3d cam = camera.getPos();
        double worldX = lastTargetPos.x;
        double worldY = lastTargetPos.y + ((lastTargetHeight + 0.4f) * 0.5f);
        double worldZ = lastTargetPos.z;

        float baseSize = size.getFloatValue() * 12f;
        float renderSize = baseSize * scaleValue;

        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, (now - lastRotateUpdate) / 1000f);
        lastRotateUpdate = now;
        float cycleDuration = Math.max(0.35f, 2.2f / rotateSpeed.getFloatValue());
        rotProgress += dt / cycleDuration;
        while (rotProgress >= 1f) {
            rotProgress -= 1f;
            rotFrom = rotTo;
            rotTo = rotTo > 0f ? -280f : 280f;
        }
        float accel = sineInOut(rotProgress);
        float rotation = MathHelper.lerp(accel, rotFrom, rotTo);

        float hurtPC = getHurtPC(lastTarget);
        int baseColor = multAlpha(getESPColor(), appearValue);
        int redColor  = multAlpha(rgb(255, 3, 3), appearValue);
        int color = overCol(baseColor, redColor, hurtPC);

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getCaptureTexture());

        drawBillboard(matrices, camera, cam, worldX, worldY, worldZ, renderSize, color, rotation);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — Кольцо
    // ────────────────────────────────────────────────────────────────────────

    private void drawRing3D(MatrixStack matrices) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float entityHeight;
        LivingEntity target = lastTarget;
        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            entityHeight = target.getHeight();
        } else {
            vec = lastTargetPos;
            entityHeight = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double x = vec.x - cam.x;
        double y = vec.y - cam.y;
        double z = vec.z - cam.z;

        double duration = 2000.0 / ringSpeed.getValue();
        double elapsed = (System.currentTimeMillis() % (long) duration);
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);
        if (side) progress -= 1.0;
        else      progress = 1.0 - progress;

        progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
        double eased = entityHeight / 1.2 * (progress > 0.5 ? 1.0 - progress : progress) * (side ? -1 : 1);

        int baseCol = getESPColor();
        float hurtPC = getHurtPC(target);
        int redCol = rgb(255, 3, 3);
        int mainColor = overCol(baseCol, redCol, hurtPC);

        int colorWithAlpha = setAlpha(mainColor, 225f / 255f * appearValue);
        int colorTransparent = setAlpha(mainColor, 1f / 255f * appearValue);
        int colorFull = setAlpha(mainColor, appearValue);
        double radius = ringRadius.getValue();

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            float px = (float) (x + Math.cos(rad) * radius);
            float pz = (float) (z + Math.sin(rad) * radius);
            float py1 = (float) (y + entityHeight * progress);
            float py2 = (float) (y + entityHeight * progress + eased);
            buffer.vertex(matrix, px, py1, pz).color(colorWithAlpha);
            buffer.vertex(matrix, px, py2, pz).color(colorTransparent);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(1.5f);
        BufferBuilder line = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            float px = (float) (x + Math.cos(rad) * radius);
            float pz = (float) (z + Math.sin(rad) * radius);
            float py = (float) (y + entityHeight * progress);
            line.vertex(matrix, px, py, pz).color(colorFull);
        }
        BufferRenderer.drawWithGlobalProgram(line.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — Души
    // ────────────────────────────────────────────────────────────────────────

    private void drawSouls3D(MatrixStack matrices, Camera camera) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float height;
        LivingEntity target = lastTarget;
        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            height = target.getHeight();
        } else {
            vec = lastTargetPos;
            height = lastTargetHeight;
        }

        Vec3d cam = camera.getPos();
        double baseX = vec.x;
        double baseY = vec.y + (height / 2.0f);
        double baseZ = vec.z;
        double radius = 0.7;
        float fixedSize = 4.0f;
        long time = System.currentTimeMillis();
        float hurtPC = getHurtPC(target);
        int baseCol = getESPColor();
        int redCol = rgb(255, 3, 3);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WF_BLOOM);

        for (int trail = 0; trail < 3; trail++) {
            for (int i = 0; i < 20; i++) {
                float trailFactor = 1.0f - (float) i / 20.0f * 0.7f;
                double angle = 0.15 * (time - i * 10.0) / 25.0;
                double s = Math.sin(angle) * radius;
                double c = Math.cos(angle) * radius;
                double worldX, worldY, worldZ;
                switch (trail) {
                    case 0 -> { worldX = baseX + s; worldY = baseY + c; worldZ = baseZ - c; }
                    case 1 -> { worldX = baseX - s; worldY = baseY + s; worldZ = baseZ - c; }
                    default -> { worldX = baseX - s; worldY = baseY - s; worldZ = baseZ + c; }
                }

                float sz = fixedSize * trailFactor;
                float alphaTrail = appearValue * GHOST_ALPHA_MULT;
                int col = multAlpha(baseCol, alphaTrail * appearValue);
                int red = multAlpha(redCol, alphaTrail * appearValue);
                int color = overCol(col, red, hurtPC);

                drawStaticBillboard(matrices, camera, cam, worldX, worldY, worldZ, sz * 0.12f, color, 0);
                drawStaticBillboard(matrices, camera, cam, worldX, worldY, worldZ, sz * 0.21f, multAlpha(color, 0.45f), 0);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — Призраки
    // ────────────────────────────────────────────────────────────────────────

    private void drawCelka3D(MatrixStack matrices, Camera camera) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        LivingEntity target = lastTarget;
        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
        } else {
            vec = lastTargetPos;
        }

        Vec3d cam = camera.getPos();
        double bx = vec.x;
        double by = vec.y;
        double bz = vec.z;
        double t  = (System.currentTimeMillis() / 384.61539872299335) * CELKA_SPEED_MULT;
        double tv = (System.currentTimeMillis() / 666.6666666666666) * CELKA_SPEED_MULT;
        int baseCol = getESPColor();
        float fixedSize = 4.0f;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WF_BLOOM);

        float radius = 0.65f;
        for (int k = 0; k < 4; k++) {
            for (int j = 0; j < 20; j++) {
                float kf = (float) j / 20.0f;
                float sizeFactor = 1.0f - kf * 0.55f;

                double tj  = t  - j * 0.05;
                double tvj = tv - j * 0.05;
                double cyc = (Math.sin(tvj) + 1.0) * 0.5;

                double baseAngle = Math.toRadians(k * 90.0 + (tj * 50.0) % 360.0);
                double offX = Math.cos(baseAngle) * radius;
                double offZ = Math.sin(baseAngle) * radius;
                double offY = k % 2 == 0 ? 0.1 + 1.7 * cyc : 1.8 - 1.7 * cyc;

                double worldX = bx + offX;
                double worldY = by + offY;
                double worldZ = bz + offZ;

                float sz = fixedSize * sizeFactor;
                int finalAlpha = (int) (255.0f * appearValue * GHOST_ALPHA_MULT);
                int color = replAlpha(baseCol, finalAlpha);

                drawBillboard(matrices, camera, cam, worldX, worldY, worldZ, sz, color, 0);
                drawBillboard(matrices, camera, cam, worldX, worldY, worldZ, sz * 1.75f, multAlpha(color, 0.45f), 0);
            }
            radius *= -1.0f;
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — Кубы
    // ────────────────────────────────────────────────────────────────────────

    private void renderCubes(MatrixStack matrices, LivingEntity target, boolean hasTarget, float partialTicks) {
        long now = System.currentTimeMillis();
        if (lastCubeTime == 0L) lastCubeTime = now;
        float dt = Math.min((now - lastCubeTime) / 1000.0f, 0.1f);
        lastCubeTime = now;
        if (!Float.isFinite(dt) || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) return;

        if (hasTarget && target != null) {
            lastTarget = target;
            spawnAccumulator += dt;
            while (spawnAccumulator >= CUBE_SPAWN_INTERVAL) {
                spawnAccumulator -= CUBE_SPAWN_INTERVAL;
                if (cubeParticles.size() >= MAX_CUBE_PARTICLES) break;
                for (int i = 0; i < CUBE_PARTICLES_PER_SPAWN; i++) {
                    double rand = Math.random() * 360.0;
                    double px = Math.cos(Math.toRadians(rand)) * 0.7;
                    double py = 0.02 + Math.random() * 0.10;
                    double pz = Math.sin(Math.toRadians(rand)) * 0.7;
                    cubeParticles.add(new CubeParticle(target, px, py, pz));
                }
            }
        } else {
            spawnAccumulator = 0f;
        }

        renderCubeParticles.clear();
        for (int i = cubeParticles.size() - 1; i >= 0; i--) {
            CubeParticle p = cubeParticles.get(i);
            try {
                p.update(dt, now, hasTarget ? target : null);
                if (p.shouldRemove(now)) cubeParticles.remove(i);
                else renderCubeParticles.add(p);
            } catch (Throwable ignored) {
                cubeParticles.remove(i);
            }
        }
        if (renderCubeParticles.isEmpty()) return;

        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        LivingEntity colorTarget = hasTarget ? target : lastTarget;
        float hurtPC = getHurtPC(colorTarget);
        int baseColor = getESPColor();
        int redColor  = rgb(255, 3, 3);

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        // Cube faces
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder face = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        boolean hasFaces = false;
        for (CubeParticle p : renderCubeParticles) {
            int c = p.getRenderColor(baseColor, redColor, hurtPC, now);
            if (((c >> 24) & 0xFF) <= 0) continue;
            if (p.appendCubeFaces(face, matrices, camPos, partialTicks, c)) hasFaces = true;
        }
        if (hasFaces) BufferRenderer.drawWithGlobalProgram(face.end());

        // Cube edges
        BufferBuilder line = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        boolean hasLines = false;
        for (CubeParticle p : renderCubeParticles) {
            int c = p.getRenderColor(baseColor, redColor, hurtPC, now);
            if (((c >> 24) & 0xFF) <= 0) continue;
            if (p.appendCubeLines(line, matrices, camPos, partialTicks, c)) hasLines = true;
        }
        if (hasLines) BufferRenderer.drawWithGlobalProgram(line.end());

        // Bloom billboards
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WF_BLOOM);
        BufferBuilder bloom = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        boolean hasBloom = false;
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();
        for (CubeParticle p : renderCubeParticles) {
            int c = p.getRenderColor(baseColor, redColor, hurtPC, now);
            if (p.appendBloom(bloom, matrices, camPos, camYaw, camPitch, partialTicks, c, now)) hasBloom = true;
        }
        if (hasBloom) BufferRenderer.drawWithGlobalProgram(bloom.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — Кристаллы
    // ────────────────────────────────────────────────────────────────────────

    private void renderWfCrystals(MatrixStack ms, LivingEntity target, float partialTicks) {
        if (lastTargetPos == null || crystalAnimation <= 0.01f) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        int baseColor = ColorProvider.getColorClient();
        int color = multAlpha(baseColor, crystalAnimation);
        int glowColor = multAlpha(baseColor, crystalAnimation * 0.28f);
        float hurtPC = getHurtPC(target);
        if (hurtPC > 0f) {
            int hurt = multAlpha(rgb(255, 3, 3), crystalAnimation);
            color = overCol(color, hurt, hurtPC);
            glowColor = overCol(glowColor, multAlpha(hurt, 0.65f), hurtPC);
        }

        float entityWidth  = target != null ? target.getWidth()  : lastTargetWidth;
        float entityHeight = target != null ? target.getHeight() : lastTargetHeight;
        float width = entityWidth * 1.5f;

        Vec3d renderPos;
        if (target != null && target.isAlive()) {
            renderPos = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
        } else {
            renderPos = lastTargetPos;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        float orbitScale = 1.2f - 0.5f * crystalAnimation;
        ms.push();
        ms.translate(renderPos.x - cameraPos.x, renderPos.y - cameraPos.y, renderPos.z - cameraPos.z);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < 360; i += 20) {
            float angleRad = (float) Math.toRadians(i + crystalRotationAngle);
            float sin = (float) (Math.sin(angleRad) * width * orbitScale);
            float cos = (float) (Math.cos(angleRad) * width * orbitScale);
            float yOffset = 0.1f + entityHeight * Math.abs(MathHelper.sin(i));

            float dirX = -sin;
            float dirY = entityHeight / 2.0f - yOffset;
            float dirZ = -cos;
            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (length < 0.001f) continue;
            dirX /= length; dirY /= length; dirZ /= length;

            ms.push();
            ms.translate(sin, yOffset, cos);
            Vector3f initial = new Vector3f(0, 1, 0);
            Vector3f dir = new Vector3f(dirX, dirY, dirZ);
            Vector3f axis = new Vector3f();
            initial.cross(dir, axis);
            float axisLen = axis.length();
            if (axisLen >= 0.001f) {
                axis.div(axisLen);
                float dot = Math.max(-1f, Math.min(1f, initial.dot(dir)));
                float angle = (float) Math.acos(dot);
                ms.multiply(new Quaternionf().setAngleAxis(angle, axis.x, axis.y, axis.z));
            }
            renderWfCrystalShape(buffer, ms.peek().getPositionMatrix(), color);
            ms.pop();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        ms.pop();

        // Glow halo
        float glowBaseSize = 4.5f + entityWidth * 3.0f;
        float outerGlowSize = glowBaseSize * 1.28f;
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WF_BLOOM);

        Camera camera = mc.gameRenderer.getCamera();
        for (int i = 0; i < 360; i += 20) {
            float angleRad = (float) Math.toRadians(i + crystalRotationAngle);
            float sin = (float) (Math.sin(angleRad) * width * orbitScale);
            float cos = (float) (Math.cos(angleRad) * width * orbitScale);
            float yOffset = 0.1f + entityHeight * Math.abs(MathHelper.sin(i));

            double worldX = renderPos.x + sin;
            double worldY = renderPos.y + yOffset;
            double worldZ = renderPos.z + cos;
            drawBillboard(ms, camera, cameraPos, worldX, worldY, worldZ, outerGlowSize, multAlpha(glowColor, 0.24f), crystalRotationAngle + i);
            drawBillboard(ms, camera, cameraPos, worldX, worldY, worldZ, glowBaseSize, glowColor, -(crystalRotationAngle + i * 0.5f));
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void renderWfCrystalShape(BufferBuilder buffer, Matrix4f matrix, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b =  color        & 0xFF;
        int a = (color >> 24) & 0xFF;
        float w = 0.06f, h = 0.2f;
        triCol(buffer, matrix, 0,h,0,  w,0,0,  0,0,w,  r,g,b,a);
        triCol(buffer, matrix, 0,h,0,  0,0,w,  -w,0,0, r,g,b,a);
        triCol(buffer, matrix, 0,h,0,  -w,0,0, 0,0,-w, r,g,b,a);
        triCol(buffer, matrix, 0,h,0,  0,0,-w, w,0,0,  r,g,b,a);
        triCol(buffer, matrix, 0,-h,0, w,0,0,  0,0,w,  r,g,b,a);
        triCol(buffer, matrix, 0,-h,0, 0,0,w,  -w,0,0, r,g,b,a);
        triCol(buffer, matrix, 0,-h,0, -w,0,0, 0,0,-w, r,g,b,a);
        triCol(buffer, matrix, 0,-h,0, 0,0,-w, w,0,0,  r,g,b,a);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Wonderful — Райдер
    // ────────────────────────────────────────────────────────────────────────

    private void addBMWGhosts(LivingEntity entity, int cornersCount, int maxTime, int colorBase) {
        float xzRange = 0.7f;
        float yRange = entity.getHeight();
        int delayXZ = (int) bmwStrengthXZ.getValue();
        int delayY  = (int) bmwStrengthY.getValue();
        long time = System.currentTimeMillis();
        float rotateProgress = (float) (time % delayXZ) / (float) delayXZ;
        float xzRotate = rotateProgress * 360.0f;
        float yProgress = (float) (time % delayY) / (float) delayY;
        float yLrpPC = 0.5f - 0.5f * MathHelper.cos(yProgress * (float) (Math.PI * 2.0));

        for (int corner = 0; corner < cornersCount; corner++) {
            float cornersPC = (float) corner / (float) cornersCount;
            double yawRad = Math.toRadians(MathHelper.wrapDegrees(cornersPC * 360.0f + xzRotate));
            float offsetX = -(float) Math.sin(yawRad) * xzRange;
            float offsetY = yRange * yLrpPC;
            float offsetZ =  (float) Math.cos(yawRad) * xzRange;
            bmwPoints.add(new GlowPoint(offsetX, offsetY, offsetZ, maxTime, colorBase));
        }
    }

    private void drawBMW3D(MatrixStack matrices) {
        if (bmwPoints.isEmpty() || appearValue <= 0.001f) return;

        LivingEntity renderTarget = lastTarget != null ? lastTarget : lastHandledTarget;
        if (renderTarget == null && lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d basePos;
        if (renderTarget != null && renderTarget.isAlive()) {
            basePos = new Vec3d(
                    MathHelper.lerp(partialTicks, renderTarget.lastRenderX, renderTarget.getX()),
                    MathHelper.lerp(partialTicks, renderTarget.lastRenderY, renderTarget.getY()),
                    MathHelper.lerp(partialTicks, renderTarget.lastRenderZ, renderTarget.getZ())
            );
        } else {
            basePos = lastTargetPos;
        }
        if (basePos == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cam = camera.getPos();
        float hurtPC = getHurtPC(renderTarget);
        float fixedScreenSize = 6f;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, WF_BLOOM);

        for (GlowPoint point : bmwPoints) {
            float timePC = point.getTimeProgress();
            float trailFactor = 1.0f - timePC * 0.6f;
            double worldX = basePos.x + point.x;
            double worldY = basePos.y + point.y;
            double worldZ = basePos.z + point.z;
            float sz = fixedScreenSize * trailFactor;
            int alpha = MathHelper.clamp((int) (255 * appearValue * trailFactor * 0.8f), 0, 255);
            int col = replAlpha(point.baseColor, alpha);
            int red = replAlpha(rgb(255, 3, 3), alpha);
            int finalColor = overCol(col, red, hurtPC);
            drawBillboard(matrices, camera, cam, worldX, worldY, worldZ, sz, finalColor, 0);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Helpers — billboards, math, colors, GL state
    // ────────────────────────────────────────────────────────────────────────

    private void drawBillboard(MatrixStack matrices, Camera camera, Vec3d cameraPos,
                               double worldX, double worldY, double worldZ,
                               float baseScreenSize, int color, float rotation) {
        float distScale = getDistanceScale(cameraPos, worldX, worldY, worldZ);
        drawBillboardInternal(matrices, camera, cameraPos, worldX, worldY, worldZ,
                baseScreenSize * distScale * 0.5f, color, rotation);
    }

    private void drawStaticBillboard(MatrixStack matrices, Camera camera, Vec3d cameraPos,
                                     double worldX, double worldY, double worldZ,
                                     float worldSize, int color, float rotation) {
        drawBillboardInternal(matrices, camera, cameraPos, worldX, worldY, worldZ,
                worldSize * 0.5f, color, rotation);
    }

    private void drawBillboardInternal(MatrixStack matrices, Camera camera, Vec3d cameraPos,
                                       double worldX, double worldY, double worldZ,
                                       float half, int color, float rotation) {
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b =  color        & 0xFF;

        matrices.push();
        matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        if (rotation != 0) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(matrix, -half,  half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(matrix,  half,  half, 0).texture(1, 0).color(r, g, b, a);
        buffer.vertex(matrix,  half, -half, 0).texture(1, 1).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private float getDistanceScale(Vec3d cam, double worldX, double worldY, double worldZ) {
        double dx = worldX - cam.x, dy = worldY - cam.y, dz = worldZ - cam.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (float) Math.max(0.1, distance * SCALE_FACTOR);
    }

    private int getESPColor() {
        int color = ColorProvider.getColorClient();
        if (((color >> 24) & 0xFF) == 0) color |= 0xFF000000;
        return color;
    }

    private float getHurtPC(LivingEntity target) {
        if (!hurtColor.getValue() || target == null) return 0f;
        float partialTicks = mc != null ? mc.getRenderTickCounter().getTickDelta(true) : 0f;
        float hurtTicks = MathHelper.clamp(target.hurtTime - partialTicks, 0f, 10f);
        float p = hurtTicks / 10.0f;
        return p * p * (3.0f - 2.0f * p);
    }

    private static float animateTo(float current, float target, float delta) {
        if (current < target) return Math.min(current + delta, target);
        if (current > target) return Math.max(current - delta, target);
        return current;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    /** Sine in/out — same curve as Easings.SINE_IN_OUT in wonderful. */
    private static float sineInOut(float t) {
        return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
    }

    private void putBloomQuad(BufferBuilder builder, MatrixStack ms,
                               double x, double y, double z,
                               float scale, float r, float g, float b, float a,
                               float camYaw, float camPitch) {
        if (a <= 0.001f || scale <= 0.0001f) return;
        ms.push();
        ms.translate(x, y, z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        ms.scale(scale, scale, scale);

        Matrix4f m = ms.peek().getPositionMatrix();
        int ri = (int)(r * 255), gi = (int)(g * 255), bi = (int)(b * 255), ai = (int)(a * 255);
        builder.vertex(m, -0.5f,  0.5f, 0).texture(0f, 1f).color(ri, gi, bi, ai);
        builder.vertex(m,  0.5f,  0.5f, 0).texture(1f, 1f).color(ri, gi, bi, ai);
        builder.vertex(m,  0.5f, -0.5f, 0).texture(1f, 0f).color(ri, gi, bi, ai);
        builder.vertex(m, -0.5f, -0.5f, 0).texture(0f, 0f).color(ri, gi, bi, ai);
        ms.pop();
    }

    private static void triCol(BufferBuilder buf, Matrix4f m,
                               float x1, float y1, float z1,
                               float x2, float y2, float z2,
                               float x3, float y3, float z3,
                               int r, int g, int b, int a) {
        buf.vertex(m, x1, y1, z1).color(r, g, b, a);
        buf.vertex(m, x2, y2, z2).color(r, g, b, a);
        buf.vertex(m, x3, y3, z3).color(r, g, b, a);
    }

    private void restoreRenderState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    // ── Color utilities ─────────────────────────────────────────────────────

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int multAlpha(int color, float mult) {
        int a = MathHelper.clamp((int) (((color >> 24) & 0xFF) * mult), 0, 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int replAlpha(int color, int alpha) {
        alpha = MathHelper.clamp(alpha, 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int setAlpha(int color, float alpha) {
        return replAlpha(color, (int) (MathHelper.clamp(alpha, 0f, 1f) * 255));
    }

    private static int overCol(int color1, int color2, float factor) {
        factor = MathHelper.clamp(factor, 0f, 1f);
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF, a1 = (color1 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF, a2 = (color2 >> 24) & 0xFF;
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);
        int a = (int) (a1 + (a2 - a1) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Inner classes — GlowPoint, CubeParticle
    // ────────────────────────────────────────────────────────────────────────

    /** Single ghost dot for the "Райдер" trail. */
    private static final class GlowPoint {
        final float x, y, z;
        final long startTime;
        final int maxLife;
        final int baseColor;

        GlowPoint(float x, float y, float z, int maxLife, int baseColor) {
            this.x = x; this.y = y; this.z = z;
            this.startTime = System.currentTimeMillis();
            this.maxLife = maxLife;
            this.baseColor = baseColor;
        }

        boolean shouldRemove() { return System.currentTimeMillis() - startTime >= maxLife; }
        float getTimeProgress() {
            return MathHelper.clamp((System.currentTimeMillis() - startTime) / (float) maxLife, 0f, 1f);
        }
    }

    /** Cube particle for the "Кубы" mode. */
    private static final class CubeParticle {
        double x, y, z;
        double worldX, worldY, worldZ;
        long time;
        LivingEntity entity;
        boolean fading;
        long fadeStartTime;
        float vx, vy, vz;
        float rotX, rotY, rotZ;
        float rotSpeedX, rotSpeedY, rotSpeedZ;

        CubeParticle(LivingEntity entity, double x, double y, double z) {
            this.entity = entity;
            this.x = x; this.y = y; this.z = z;
            this.time = System.currentTimeMillis();
            this.rotX = (float) (Math.random() * 360.0);
            this.rotY = (float) (Math.random() * 360.0);
            this.rotZ = (float) (Math.random() * 360.0);
            this.rotSpeedX = 1.4f + (float) Math.random() * 3.4f;
            this.rotSpeedY = 1.4f + (float) Math.random() * 3.4f;
            this.rotSpeedZ = 1.4f + (float) Math.random() * 3.4f;
            this.vx = (float) ((Math.random() - 0.5) * 0.0022);
            this.vy = 0.031f + (float) Math.random() * 0.020f;
            this.vz = (float) ((Math.random() - 0.5) * 0.0022);
        }

        void update(float dt, long now, LivingEntity currentTarget) {
            float step = dt * 60.0f;
            rotX += rotSpeedX * step;
            rotY += rotSpeedY * step;
            rotZ += rotSpeedZ * step;
            if (fading) return;

            x += vx * step; y += vy * step; z += vz * step;
            vx *= 0.992f; vz *= 0.992f; vy *= 0.989f;

            if (entity != null) {
                double shoulder = Math.max(2.2, entity.getHeight() * 1.85);
                if (y >= shoulder) { y = shoulder; beginFade(now); return; }
            }
            boolean targetLost = currentTarget == null || entity == null || !entity.isAlive() || entity != currentTarget;
            if (targetLost || now - time >= CUBE_ATTACH_LIFE_MS) beginFade(now);
        }

        boolean shouldRemove(long now) { return fading && now - fadeStartTime >= CUBE_FADE_LIFE_MS; }

        int getRenderColor(int baseColor, int redColor, float hurtPC, long now) {
            float alpha = getAlpha(now);
            if (alpha <= 0.001f) return 0;
            int color = replAlpha(baseColor, (int) (alpha * 255.0f));
            int hurt  = replAlpha(redColor,  (int) (alpha * 255.0f));
            return overCol(color, hurt, hurtPC);
        }

        boolean appendCubeFaces(BufferBuilder fb, MatrixStack ms, Vec3d cam, float partialTicks, int color) {
            float alpha = ((color >> 24) & 0xFF) / 255.0f;
            if (alpha <= 0.001f) return false;
            Vec3d rp = getRenderPos(partialTicks);
            if (rp == null) return false;
            float fadeScale = fading
                    ? MathHelper.lerp(MathHelper.clamp((System.currentTimeMillis() - fadeStartTime) / (float) CUBE_FADE_LIFE_MS, 0f, 1f), 1f, 0.45f)
                    : 1f;
            float scale = 0.12f * fadeScale;

            ms.push();
            ms.translate(rp.x - cam.x, rp.y - cam.y, rp.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
            ms.scale(scale, scale, scale);
            Matrix4f m = ms.peek().getPositionMatrix();
            int faceColor = replAlpha(color, Math.max(1, (int) (((color >> 24) & 0xFF) * 0.16f)));
            addFace(fb, m, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, faceColor);
            ms.pop();
            return true;
        }

        boolean appendCubeLines(BufferBuilder lb, MatrixStack ms, Vec3d cam, float partialTicks, int color) {
            float alpha = ((color >> 24) & 0xFF) / 255.0f;
            if (alpha <= 0.001f) return false;
            Vec3d rp = getRenderPos(partialTicks);
            if (rp == null) return false;
            float fadeScale = fading
                    ? MathHelper.lerp(MathHelper.clamp((System.currentTimeMillis() - fadeStartTime) / (float) CUBE_FADE_LIFE_MS, 0f, 1f), 1f, 0.45f)
                    : 1f;
            float scale = 0.12f * fadeScale;

            ms.push();
            ms.translate(rp.x - cam.x, rp.y - cam.y, rp.z - cam.z);
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
            ms.scale(scale, scale, scale);
            Matrix4f m = ms.peek().getPositionMatrix();
            int edgeColor = replAlpha(color, Math.max(1, (int) (((color >> 24) & 0xFF) * 0.7f)));
            for (byte[] e : CUBE_EDGES) {
                lb.vertex(m, e[0] * 0.5f, e[1] * 0.5f, e[2] * 0.5f).color(edgeColor);
                lb.vertex(m, e[3] * 0.5f, e[4] * 0.5f, e[5] * 0.5f).color(edgeColor);
            }
            ms.pop();
            return true;
        }

        boolean appendBloom(BufferBuilder builder, MatrixStack ms, Vec3d camPos,
                            float camYaw, float camPitch, float partialTicks, int colorInt, long now) {
            float alpha = getAlpha(now);
            if (alpha <= 0.001f) return false;
            Vec3d rp = getRenderPos(partialTicks);
            if (rp == null) return false;
            float fadeScale = fading
                    ? MathHelper.lerp(MathHelper.clamp((now - fadeStartTime) / (float) CUBE_FADE_LIFE_MS, 0f, 1f), 1f, 0.55f)
                    : 1f;
            float glowScale = 0.95f * fadeScale;
            int ai = (int) (alpha * 0.15f * 255.0f);
            if (ai <= 0) return false;
            int r = (colorInt >> 16) & 0xFF, g = (colorInt >> 8) & 0xFF, b = colorInt & 0xFF;

            ms.push();
            ms.translate(rp.x - camPos.x, rp.y - camPos.y, rp.z - camPos.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
            ms.scale(glowScale, glowScale, glowScale);
            Matrix4f m = ms.peek().getPositionMatrix();
            builder.vertex(m, -0.5f,  0.5f, 0).texture(0f, 1f).color(r, g, b, ai);
            builder.vertex(m,  0.5f,  0.5f, 0).texture(1f, 1f).color(r, g, b, ai);
            builder.vertex(m,  0.5f, -0.5f, 0).texture(1f, 0f).color(r, g, b, ai);
            builder.vertex(m, -0.5f, -0.5f, 0).texture(0f, 0f).color(r, g, b, ai);
            ms.pop();
            return true;
        }

        private void beginFade(long now) {
            if (fading) return;
            Vec3d rp = getRenderPos(1.0f);
            if (rp != null) { worldX = rp.x; worldY = rp.y; worldZ = rp.z; }
            fadeStartTime = now;
            fading = true;
            entity = null;
        }

        private float getAlpha(long now) {
            if (!fading) {
                float fadeIn  = MathHelper.clamp((now - time) / 140.0f, 0f, 1f);
                float preFade = 1.0f - MathHelper.clamp((now - time - (CUBE_ATTACH_LIFE_MS - 120L)) / 120.0f, 0f, 0.35f);
                return fadeIn * preFade;
            }
            return 1.0f - MathHelper.clamp((now - fadeStartTime) / (float) CUBE_FADE_LIFE_MS, 0f, 1f);
        }

        private Vec3d getRenderPos(float partialTicks) {
            if (fading || entity == null) return new Vec3d(worldX, worldY, worldZ);
            return new Vec3d(
                    MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX()) + x,
                    MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY()) + y,
                    MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ()) + z
            );
        }

        private static void addFace(BufferBuilder buf, Matrix4f m,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    int color) {
            buf.vertex(m, x1, y1, z1).color(color);
            buf.vertex(m, x2, y1, z1).color(color);
            buf.vertex(m, x2, y1, z2).color(color);
            buf.vertex(m, x1, y1, z2).color(color);

            buf.vertex(m, x1, y2, z1).color(color);
            buf.vertex(m, x1, y2, z2).color(color);
            buf.vertex(m, x2, y2, z2).color(color);
            buf.vertex(m, x2, y2, z1).color(color);

            buf.vertex(m, x1, y1, z1).color(color);
            buf.vertex(m, x1, y2, z1).color(color);
            buf.vertex(m, x2, y2, z1).color(color);
            buf.vertex(m, x2, y1, z1).color(color);

            buf.vertex(m, x1, y1, z2).color(color);
            buf.vertex(m, x2, y1, z2).color(color);
            buf.vertex(m, x2, y2, z2).color(color);
            buf.vertex(m, x1, y2, z2).color(color);

            buf.vertex(m, x1, y1, z1).color(color);
            buf.vertex(m, x1, y1, z2).color(color);
            buf.vertex(m, x1, y2, z2).color(color);
            buf.vertex(m, x1, y2, z1).color(color);

            buf.vertex(m, x2, y1, z1).color(color);
            buf.vertex(m, x2, y2, z1).color(color);
            buf.vertex(m, x2, y2, z2).color(color);
            buf.vertex(m, x2, y1, z2).color(color);
        }
    }
}
