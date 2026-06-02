package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import endless.ere.Endless;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.level.Render3DUtil;

/**
 * Подсвечивает блок, на который смотрит игрок: мягкая обводка плюс
 * энергетические волны, нарисованные через GLSL-шейдер темы клиента.
 */
@ModuleAnnotation(name = "BlockGlow", category = Category.RENDER,
        description = "Подсветка блока под прицелом с волновым шейдером")
public final class BlockGlow extends Module {

    public static final BlockGlow INSTANCE = new BlockGlow();

    private final ModeSetting effect = new ModeSetting("Эффект", "Волны", "Искры", "Паутина", "Туманность", "Плазма", "Звёзды");
    private final NumberSetting waveScale = new NumberSetting("Масштаб", 1.4f, 0.4f, 4f, 0.1f);
    private final NumberSetting waveSpeed = new NumberSetting("Скорость", 1f, 0.1f, 3f, 0.1f);
    private final BooleanSetting outline = new BooleanSetting("Обводка", true);

    /** Лёгкий offset, чтобы шейдер не дрожал на гранях самого блока. */
    private static final float Z_FIGHT_BIAS = 0.0015f;

    private BlockGlow() {
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (mc.world == null || mc.player == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bhr)) return;
        if (bhr.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        Box base = state.getOutlineShape(mc.world, pos).getBoundingBox().offset(pos);

        ColorRGBA accent = Endless.getInstance().getThemeManager().getCurrentTheme().getColor();
        ColorRGBA second = Endless.getInstance().getThemeManager().getCurrentTheme().getSecondColor();

        long now = System.currentTimeMillis();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 360.0);

        if (outline.isEnabled()) {
            int outlineColor = accent.withAlpha(Math.round(180 + 60 * pulse)).getRGB();
            Render3DUtil.drawBox(base.expand(0.002), outlineColor, 1.4f, true, false, false);
        }

        Box expanded = base.expand(Z_FIGHT_BIAS);
        if (effect.is("Волны")) {
            renderWatercaustic(event.getMatrix(), expanded, accent, second, now);
        } else if (effect.is("Искры")) {
            renderSparkle(event.getMatrix(), expanded, accent, second, now);
        } else if (effect.is("Паутина")) {
            renderShader3d(event.getMatrix(), expanded, accent, now, DrawUtil.blockCobwebProgram);
        } else if (effect.is("Туманность")) {
            renderShader3d(event.getMatrix(), expanded, accent, now, DrawUtil.blockNebulaProgram);
        } else if (effect.is("Плазма")) {
            renderShader3d(event.getMatrix(), expanded, accent, now, DrawUtil.blockPlasmaProgram);
        } else if (effect.is("Звёзды")) {
            renderShader3d(event.getMatrix(), expanded, accent, now, DrawUtil.blockStarfieldProgram);
        }
    }

    // ── Watercaustic (живые волны) ──────────────────────────────────────────

    private void renderWatercaustic(MatrixStack matrices, Box box, ColorRGBA accent, ColorRGBA second, long now) {
        if (DrawUtil.waterCausticProgram == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float time = (now % 1_000_000L) / 1000f * waveSpeed.getCurrent();

        DrawUtil.waterCausticProgram.use();
        setColorUniform(DrawUtil.waterCausticProgram, "Color",
                second.withAlpha(120));
        setColorUniform(DrawUtil.waterCausticProgram, "PatternColor",
                accent.withAlpha(255));
        DrawUtil.waterCausticProgram.findUniform("Time").set(time);
        DrawUtil.waterCausticProgram.findUniform("Scale").set(waveScale.getCurrent());

        drawShaderBox(matrices, box, cam, GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    // ── Glow3D (мягкое свечение) ────────────────────────────────────────────

    private void renderGlow(MatrixStack matrices, Box box, ColorRGBA accent, long now) {
        if (DrawUtil.glow3dProgram == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        float pulse = 0.6f + 0.4f * (float) Math.sin(now / 280.0);
        ColorRGBA color = accent.withAlpha(Math.round(220 * pulse));
        DrawUtil.glow3dProgram.use();
        setColorUniform(DrawUtil.glow3dProgram, "ColorModulator", color);

        drawShaderBox(matrices, box, cam, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    // ── Star sparkle ────────────────────────────────────────────────────────

    private void renderSparkle(MatrixStack matrices, Box box, ColorRGBA accent, ColorRGBA second, long now) {
        if (DrawUtil.starSparkleProgram == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        float time = (now % 1_000_000L) / 1000f * waveSpeed.getCurrent();
        DrawUtil.starSparkleProgram.use();
        setColorUniform(DrawUtil.starSparkleProgram, "BackgroundColor",
                new ColorRGBA(0, 0, 0, 0));
        setColorUniform(DrawUtil.starSparkleProgram, "StarColor", accent.mix(second, 0.4f));
        DrawUtil.starSparkleProgram.findUniform("Time").set(time);
        DrawUtil.starSparkleProgram.findUniform("Scale").set(waveScale.getCurrent());

        drawShaderBox(matrices, box, cam, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    // ── 3D shader effects (cobweb / nebula / plasma / starfield) ────────────

    private void renderShader3d(MatrixStack matrices, Box box, ColorRGBA accent, long now,
                                endless.ere.utility.render.display.shader.GlProgram program) {
        if (program == null) return;
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float time = (now % 1_000_000L) / 1000f * waveSpeed.getCurrent();

        program.use();
        var timeU = program.findUniform("time");
        if (timeU != null) timeU.set(time);
        var screenU = program.findUniform("screenSize");
        if (screenU != null) screenU.set((float) mc.getWindow().getFramebufferWidth(),
                (float) mc.getWindow().getFramebufferHeight());
        var alphaU = program.findUniform("alpha");
        if (alphaU != null) alphaU.set(MathHelper.clamp(waveScale.getCurrent() * 0.5f, 0.2f, 1.0f));
        setColorUniform(program, "baseColor", accent.withAlpha(255));

        drawShaderBox(matrices, box, cam, GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void drawShaderBox(MatrixStack matrices, Box box, Vec3d cam, int src, int dst) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(src, dst);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float x1 = (float) (box.minX - cam.x);
        float y1 = (float) (box.minY - cam.y);
        float z1 = (float) (box.minZ - cam.z);
        float x2 = (float) (box.maxX - cam.x);
        float y2 = (float) (box.maxY - cam.y);
        float z2 = (float) (box.maxZ - cam.z);
        int color = 0xFFFFFFFF;

        // Bottom (y = y1)
        buffer.vertex(matrix, x1, y1, z2).texture(0f, 1f).color(color);
        buffer.vertex(matrix, x2, y1, z2).texture(1f, 1f).color(color);
        buffer.vertex(matrix, x2, y1, z1).texture(1f, 0f).color(color);
        buffer.vertex(matrix, x1, y1, z1).texture(0f, 0f).color(color);
        // Top (y = y2)
        buffer.vertex(matrix, x1, y2, z1).texture(0f, 0f).color(color);
        buffer.vertex(matrix, x2, y2, z1).texture(1f, 0f).color(color);
        buffer.vertex(matrix, x2, y2, z2).texture(1f, 1f).color(color);
        buffer.vertex(matrix, x1, y2, z2).texture(0f, 1f).color(color);
        // North (z = z1)
        buffer.vertex(matrix, x1, y2, z1).texture(0f, 0f).color(color);
        buffer.vertex(matrix, x1, y1, z1).texture(0f, 1f).color(color);
        buffer.vertex(matrix, x2, y1, z1).texture(1f, 1f).color(color);
        buffer.vertex(matrix, x2, y2, z1).texture(1f, 0f).color(color);
        // South (z = z2)
        buffer.vertex(matrix, x2, y2, z2).texture(0f, 0f).color(color);
        buffer.vertex(matrix, x2, y1, z2).texture(0f, 1f).color(color);
        buffer.vertex(matrix, x1, y1, z2).texture(1f, 1f).color(color);
        buffer.vertex(matrix, x1, y2, z2).texture(1f, 0f).color(color);
        // West (x = x1)
        buffer.vertex(matrix, x1, y2, z2).texture(0f, 0f).color(color);
        buffer.vertex(matrix, x1, y1, z2).texture(0f, 1f).color(color);
        buffer.vertex(matrix, x1, y1, z1).texture(1f, 1f).color(color);
        buffer.vertex(matrix, x1, y2, z1).texture(1f, 0f).color(color);
        // East (x = x2)
        buffer.vertex(matrix, x2, y2, z1).texture(0f, 0f).color(color);
        buffer.vertex(matrix, x2, y1, z1).texture(0f, 1f).color(color);
        buffer.vertex(matrix, x2, y1, z2).texture(1f, 1f).color(color);
        buffer.vertex(matrix, x2, y2, z2).texture(1f, 0f).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void setColorUniform(endless.ere.utility.render.display.shader.GlProgram program,
                                        String name, ColorRGBA color) {
        var uniform = program.findUniform(name);
        if (uniform == null) return;
        uniform.set(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
    }
}
