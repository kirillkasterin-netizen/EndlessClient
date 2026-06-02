package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import dev.endless.event.list.EventTick;
import dev.endless.event.list.EventWorldRender;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ColorSetting;
import dev.endless.module.settings.ModeListSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@ModuleInformation(moduleName = "World Particles", moduleDesc = "Декоративные частицы в мире", moduleCategory = ModuleCategory.RENDER)
public class WorldParticles extends Module {

    private static final Identifier BLOOM_TEXTURE = Identifier.of("mre", "images/bloom.png");
    private static final long FADE_MS = 1000L;

    private static final float PINWHEEL_INNER_RADIUS = 0.08f;
    private static final float PINWHEEL_OUTER_RADIUS = 0.52f;
    private static final float PINWHEEL_HOOK_SIZE = 0.22f;

    private static final double SPAWN_HORIZONTAL_RANGE = 20.0;
    private static final double SPAWN_VERTICAL_BASE_MIN = 0.0;
    private static final double SPAWN_VERTICAL_BASE_MAX = 5.0;

    private static final float DIAMOND_RADIUS = 0.40f;
    private static final float DIAMOND_HEIGHT = 0.56f;

    private static final float TRIANGLE_HEIGHT = 0.62f;
    private static final float TRIANGLE_BASE_Y = -0.24f;
    private static final float TRIANGLE_BASE_RADIUS = 0.56f;

    private final ModeSetting particleMode = new ModeSetting("Вид партиклов", "3D", "2D", "3D");

    private final ModeListSetting particleType3D = (ModeListSetting) new ModeListSetting("Тип партиклов",
            new BooleanSetting("Вертушка", true),
            new BooleanSetting("Куб", false),
            new BooleanSetting("Ромб", false),
            new BooleanSetting("Треугольник", false)
    ).setVisible(() -> particleMode.is("3D"));

    private final SliderSetting particleCount = (SliderSetting) new SliderSetting("Количество", 100.0, 10.0, 200.0, 1.0)
            .setVisible(() -> particleMode.is("3D"));
    private final SliderSetting radius = (SliderSetting) new SliderSetting("Высота", 5.0, 0.0, 20.0, 1.0)
            .setVisible(() -> particleMode.is("3D"));

    private final BooleanSetting customColorEnabled = (BooleanSetting) new BooleanSetting("Свой цвет", false)
            .setVisible(() -> particleMode.is("3D"));
    private final ColorSetting customColor = (ColorSetting) new ColorSetting("Цвет", 0xFFFFFFFF)
            .setVisible(() -> particleMode.is("3D") && customColorEnabled.getValue());

    private final List<Particle> bladeParticles = new ArrayList<>();
    private final List<Particle> cubeParticles = new ArrayList<>();
    private final List<Particle> diamondParticles = new ArrayList<>();
    private final List<Particle> triangleParticles = new ArrayList<>();

    @Override
    public void onDisable() {
        bladeParticles.clear();
        cubeParticles.clear();
        diamondParticles.clear();
        triangleParticles.clear();
        super.onDisable();
    }

    @Subscribe
    private void onTick(EventTick event) {
        if (mc.player == null || mc.world == null) {
            bladeParticles.clear();
            cubeParticles.clear();
            diamondParticles.clear();
            triangleParticles.clear();
            return;
        }

        if (!particleMode.is("3D")) {
            bladeParticles.clear();
            cubeParticles.clear();
            diamondParticles.clear();
            triangleParticles.clear();
            return;
        }

        boolean spawnBlades    = particleType3D.isEnabled("Вертушка");
        boolean spawnCubes     = particleType3D.isEnabled("Куб");
        boolean spawnDiamonds  = particleType3D.isEnabled("Ромб");
        boolean spawnTriangles = particleType3D.isEnabled("Треугольник");

        int maxParticles = Math.max(1, particleCount.getIntValue());

        updateParticlePool(bladeParticles,    maxParticles, spawnBlades);
        updateParticlePool(cubeParticles,     maxParticles, spawnCubes);
        updateParticlePool(diamondParticles,  maxParticles, spawnDiamonds);
        updateParticlePool(triangleParticles, maxParticles, spawnTriangles);
    }

    private void updateParticlePool(List<Particle> pool, int maxParticles, boolean enabled) {
        if (!enabled) {
            pool.clear();
            return;
        }

        pool.removeIf(Particle::isDead);
        pool.forEach(Particle::tick);

        while (pool.size() > maxParticles) pool.remove(0);
        while (pool.size() < maxParticles) pool.add(createParticle());
    }

    @Subscribe
    private void onWorldRender(EventWorldRender event) {
        if (mc.player == null || mc.world == null) return;
        if (!particleMode.is("3D")) return;

        boolean drawPinwheels = particleType3D.isEnabled("Вертушка");
        boolean drawCubes     = particleType3D.isEnabled("Куб");
        boolean drawDiamonds  = particleType3D.isEnabled("Ромб");
        boolean drawTriangles = particleType3D.isEnabled("Треугольник");
        if (!drawPinwheels && !drawCubes && !drawDiamonds && !drawTriangles) return;

        MatrixStack matrices = event.getMatrixStack();
        float partialTicks = event.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        if (drawPinwheels && !bladeParticles.isEmpty()) {
            renderBillboards(matrices, bladeParticles, cameraPos, partialTicks);
            render3DLines(matrices, bladeParticles, ShapeType.PINWHEEL, cameraPos, partialTicks);
        }
        if (drawCubes && !cubeParticles.isEmpty()) {
            renderBillboards(matrices, cubeParticles, cameraPos, partialTicks);
            render3DLines(matrices, cubeParticles, ShapeType.CUBE, cameraPos, partialTicks);
        }
        if (drawDiamonds && !diamondParticles.isEmpty()) {
            renderBillboards(matrices, diamondParticles, cameraPos, partialTicks);
            render3DLines(matrices, diamondParticles, ShapeType.DIAMOND, cameraPos, partialTicks);
        }
        if (drawTriangles && !triangleParticles.isEmpty()) {
            renderBillboards(matrices, triangleParticles, cameraPos, partialTicks);
            render3DLines(matrices, triangleParticles, ShapeType.TRIANGLE, cameraPos, partialTicks);
        }
    }

    private void renderBillboards(MatrixStack matrices, List<Particle> particles, Vec3d cameraPos, float partialTicks) {
        if (particles.stream().noneMatch(p -> p.alpha > 0.0f)) return;

        Camera camera = mc.gameRenderer.getCamera();
        int baseColor = resolveParticleColor();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderTexture(0, BLOOM_TEXTURE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (Particle particle : particles) {
            if (particle.alpha <= 0.0f) continue;

            Vec3d pos = interpolate(particle.prevPos, particle.pos, partialTicks);
            float quadSize = 4.0f * particle.size;
            float halfSize = quadSize / 2.0f;
            int color = multAlpha(baseColor, particle.alpha * 0.2f);

            matrices.push();
            matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
            matrices.multiply(camera.getRotation());
            Matrix4f matrix = matrices.peek().getPositionMatrix();

            buffer.vertex(matrix, -halfSize, -halfSize, 0.0f).texture(0.0f, 1.0f).color(color);
            buffer.vertex(matrix,  halfSize, -halfSize, 0.0f).texture(1.0f, 1.0f).color(color);
            buffer.vertex(matrix,  halfSize,  halfSize, 0.0f).texture(1.0f, 0.0f).color(color);
            buffer.vertex(matrix, -halfSize,  halfSize, 0.0f).texture(0.0f, 0.0f).color(color);
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    private void render3DLines(MatrixStack matrices, List<Particle> particles, ShapeType shapeType,
                               Vec3d cameraPos, float partialTicks) {
        if (particles.stream().noneMatch(p -> p.alpha > 0.0f)) return;

        int baseColor = resolveParticleColor();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);

        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        for (Particle particle : particles) {
            if (particle.alpha <= 0.0f) continue;

            Vec3d pos = interpolate(particle.prevPos, particle.pos, partialTicks);
            Vec3d rot = interpolate(particle.prevRot, particle.rotation, partialTicks);

            matrices.push();
            matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
            matrices.multiply(new Quaternionf().rotationXYZ((float) rot.x, (float) rot.y, (float) rot.z));
            matrices.scale(particle.size, particle.size, particle.size);

            int bodyColor          = multAlpha(baseColor, particle.alpha * 0.38f);
            int hookColor          = multAlpha(baseColor, particle.alpha * 0.82f);
            int cubeColor          = multAlpha(baseColor, particle.alpha * 0.35f);
            int cubeOutlineColor   = multAlpha(baseColor, particle.alpha * 0.82f);
            int diamondEdgeColor   = multAlpha(baseColor, particle.alpha * 0.34f);
            int diamondOutlineColor = multAlpha(baseColor, particle.alpha * 0.82f);
            int triangleSideColor  = multAlpha(baseColor, particle.alpha * 0.36f);
            int triangleBaseColor  = multAlpha(baseColor, particle.alpha * 0.82f);

            switch (shapeType) {
                case PINWHEEL -> renderPinwheel(matrices, lineBuffer, bodyColor, hookColor);
                case CUBE -> renderCube(matrices, lineBuffer, cubeColor, cubeOutlineColor);
                case DIAMOND -> renderDiamond(matrices, lineBuffer, diamondEdgeColor, diamondOutlineColor);
                case TRIANGLE -> renderTriangle(matrices, lineBuffer, triangleSideColor, triangleBaseColor);
            }
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ── Shapes ───────────────────────────────────────────────────────────────

    private void renderCube(MatrixStack m, BufferBuilder buffer, int bodyColor, int outlineColor) {
        float h = 0.5f;
        line(m, buffer, new Vec3d(-h, -h, -h), new Vec3d( h,  h,  h), bodyColor);
        line(m, buffer, new Vec3d(-h, -h,  h), new Vec3d( h,  h, -h), bodyColor);
        line(m, buffer, new Vec3d(-h,  h, -h), new Vec3d( h, -h,  h), bodyColor);
        line(m, buffer, new Vec3d(-h,  h,  h), new Vec3d( h, -h, -h), bodyColor);

        line(m, buffer, new Vec3d(-h, -h, -h), new Vec3d( h, -h, -h), outlineColor);
        line(m, buffer, new Vec3d( h, -h, -h), new Vec3d( h, -h,  h), outlineColor);
        line(m, buffer, new Vec3d( h, -h,  h), new Vec3d(-h, -h,  h), outlineColor);
        line(m, buffer, new Vec3d(-h, -h,  h), new Vec3d(-h, -h, -h), outlineColor);

        line(m, buffer, new Vec3d(-h,  h, -h), new Vec3d( h,  h, -h), outlineColor);
        line(m, buffer, new Vec3d( h,  h, -h), new Vec3d( h,  h,  h), outlineColor);
        line(m, buffer, new Vec3d( h,  h,  h), new Vec3d(-h,  h,  h), outlineColor);
        line(m, buffer, new Vec3d(-h,  h,  h), new Vec3d(-h,  h, -h), outlineColor);

        line(m, buffer, new Vec3d(-h, -h, -h), new Vec3d(-h,  h, -h), outlineColor);
        line(m, buffer, new Vec3d( h, -h, -h), new Vec3d( h,  h, -h), outlineColor);
        line(m, buffer, new Vec3d( h, -h,  h), new Vec3d( h,  h,  h), outlineColor);
        line(m, buffer, new Vec3d(-h, -h,  h), new Vec3d(-h,  h,  h), outlineColor);
    }

    private void renderPinwheel(MatrixStack m, BufferBuilder buffer, int bodyColor, int hookColor) {
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 2.0);
            Vec3d dir = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
            Vec3d side = new Vec3d(-dir.z, 0.0, dir.x);

            Vec3d start = dir.multiply(PINWHEEL_INNER_RADIUS);
            Vec3d mid   = dir.multiply(PINWHEEL_OUTER_RADIUS);
            Vec3d tip   = mid.add(side.multiply(PINWHEEL_HOOK_SIZE));

            line(m, buffer, start, mid, bodyColor);
            line(m, buffer, mid, tip, hookColor);
        }

        line(m, buffer, new Vec3d(-0.08, 0.0, 0.0), new Vec3d(0.08, 0.0, 0.0), hookColor);
        line(m, buffer, new Vec3d(0.0, 0.0, -0.08), new Vec3d(0.0, 0.0, 0.08), hookColor);
    }

    private void renderDiamond(MatrixStack m, BufferBuilder buffer, int edgeColor, int outlineColor) {
        Vec3d top    = new Vec3d(0.0,  DIAMOND_HEIGHT, 0.0);
        Vec3d bottom = new Vec3d(0.0, -DIAMOND_HEIGHT, 0.0);
        Vec3d east   = new Vec3d( DIAMOND_RADIUS, 0.0, 0.0);
        Vec3d west   = new Vec3d(-DIAMOND_RADIUS, 0.0, 0.0);
        Vec3d south  = new Vec3d(0.0, 0.0,  DIAMOND_RADIUS);
        Vec3d north  = new Vec3d(0.0, 0.0, -DIAMOND_RADIUS);

        line(m, buffer, top, east, edgeColor);
        line(m, buffer, top, west, edgeColor);
        line(m, buffer, top, south, edgeColor);
        line(m, buffer, top, north, edgeColor);
        line(m, buffer, bottom, east, edgeColor);
        line(m, buffer, bottom, west, edgeColor);
        line(m, buffer, bottom, south, edgeColor);
        line(m, buffer, bottom, north, edgeColor);

        line(m, buffer, east, north, outlineColor);
        line(m, buffer, north, west, outlineColor);
        line(m, buffer, west, south, outlineColor);
        line(m, buffer, south, east, outlineColor);
    }

    private void renderTriangle(MatrixStack m, BufferBuilder buffer, int sideColor, int baseColor) {
        Vec3d apex = new Vec3d(0.0, TRIANGLE_HEIGHT, 0.0);
        Vec3d p1 = new Vec3d( TRIANGLE_BASE_RADIUS,          TRIANGLE_BASE_Y,  0.0);
        Vec3d p2 = new Vec3d(-TRIANGLE_BASE_RADIUS * 0.5,    TRIANGLE_BASE_Y,  TRIANGLE_BASE_RADIUS * 0.86);
        Vec3d p3 = new Vec3d(-TRIANGLE_BASE_RADIUS * 0.5,    TRIANGLE_BASE_Y, -TRIANGLE_BASE_RADIUS * 0.86);

        line(m, buffer, apex, p1, sideColor);
        line(m, buffer, apex, p2, sideColor);
        line(m, buffer, apex, p3, sideColor);

        line(m, buffer, p1, p2, baseColor);
        line(m, buffer, p2, p3, baseColor);
        line(m, buffer, p3, p1, baseColor);
    }

    private void line(MatrixStack m, BufferBuilder buffer, Vec3d start, Vec3d end, int color) {
        DrawUtil.vertexLine(m, buffer, start, end, color);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Particle createParticle() {
        Vec3d playerPos = mc.player.getPos();
        double r = radius.getValue();
        double minY = SPAWN_VERTICAL_BASE_MIN - r;
        double maxY = SPAWN_VERTICAL_BASE_MAX + r;

        return new Particle(
                playerPos.add(
                        random(-SPAWN_HORIZONTAL_RANGE, SPAWN_HORIZONTAL_RANGE),
                        random(minY, maxY),
                        random(-SPAWN_HORIZONTAL_RANGE, SPAWN_HORIZONTAL_RANGE)
                ),
                Vec3d.ZERO,
                new Vec3d(random(-1.0, 1.0), random(0.0, 2.0), random(-1.0, 1.0)),
                new Vec3d(random(-1.0, 1.0), random(-1.0, 1.0), random(-1.0, 1.0)),
                (long) random(1500.0, 4500.0),
                (float) random(0.1, 0.3)
        );
    }

    private int resolveParticleColor() {
        return customColorEnabled.getValue() ? customColor.getValue() : ColorProvider.getColorClient();
    }

    private static int multAlpha(int color, float multiplier) {
        int origAlpha = (color >> 24) & 0xFF;
        int newAlpha = MathHelper.clamp((int) (origAlpha * multiplier), 0, 255);
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static Vec3d interpolate(Vec3d from, Vec3d to, float alpha) {
        return new Vec3d(
                MathHelper.lerp(alpha, from.x, to.x),
                MathHelper.lerp(alpha, from.y, to.y),
                MathHelper.lerp(alpha, from.z, to.z)
        );
    }

    private static double random(double min, double max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private enum ShapeType {
        PINWHEEL,
        CUBE,
        DIAMOND,
        TRIANGLE
    }

    private static final class Particle {
        Vec3d prevPos;
        Vec3d prevRot;
        Vec3d pos;
        Vec3d rotation;
        Vec3d motion;
        Vec3d rotateMotion;
        long liveMs;
        float size;
        final long spawnTime = System.currentTimeMillis();
        float alpha;

        Particle(Vec3d pos, Vec3d rotation, Vec3d motion, Vec3d rotateMotion, long liveMs, float size) {
            this.pos = pos;
            this.rotation = rotation;
            this.motion = motion.multiply(0.04f);
            this.rotateMotion = rotateMotion.multiply(0.04f);
            this.liveMs = liveMs;
            this.size = size;
            this.prevPos = pos;
            this.prevRot = rotation;
            updateAlpha();
        }

        void tick() {
            prevPos = pos;
            prevRot = rotation;
            pos = pos.add(motion);
            rotation = rotation.add(rotateMotion);
            motion = motion.multiply(0.98);
            rotateMotion = rotateMotion.multiply(0.98);
            updateAlpha();
        }

        boolean isDead() {
            return getAgeMs() > liveMs + FADE_MS && alpha <= 0.0f;
        }

        private long getAgeMs() {
            return System.currentTimeMillis() - spawnTime;
        }

        private void updateAlpha() {
            long age = getAgeMs();
            float value;
            if (age < FADE_MS) {
                value = age / (float) FADE_MS;
            } else if (age > liveMs) {
                value = 1.0f - Math.min(1.0f, (age - liveMs) / (float) FADE_MS);
            } else {
                value = 1.0f;
            }
            alpha = MathHelper.clamp(value, 0.0f, 1.0f);
        }
    }
}
