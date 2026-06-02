package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

import endless.ere.Endless;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.ColorSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.display.shader.GlProgram;

import java.util.List;

/**
 * Анимированное процедурное шейдерное небо. Перенесено из WraithClient.
 * Заменяет ванильное небо стилизованным фрагментным шейдером: рисуем
 * полноэкранный NDC-квад с depth-тестом GL_LEQUAL на дальней плоскости —
 * квад проходит только по «чистым» пиксельам неба.
 */
@ModuleAnnotation(name = "SkyShader", category = Category.RENDER, description = "Шейдерное небо")
public final class SkyShader extends Module {

    private static final long INITIAL_TIME = System.currentTimeMillis();
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    /** Названия пресетов. Индекс соответствует iMode в шейдере. */
    private static final List<String> MODES = List.of(
            "Сияние", "Космос", "Магма", "Океан", "Закат",
            "Гроза", "Неон", "Сумерки", "Рассвет", "Чужой мир"
    );

    // INSTANCE объявляется ПОСЛЕ MODES: конструктор читает MODES при создании
    // настройки mode, поэтому список должен быть инициализирован первым.
    public static final SkyShader INSTANCE = new SkyShader();

    private final ModeSetting mode = new ModeSetting("Режим", MODES.toArray(new String[0]));
    private final NumberSetting speed = new NumberSetting("Скорость", 1.0f, 0.0f, 5.0f, 0.05f);
    private final ModeSetting colorSource = new ModeSetting("Источник цвета", "Тема", "Свой");
    private final ColorSetting primaryColor = new ColorSetting("Основной цвет", new ColorRGBA(0xFFFF3358),
            () -> colorSource.is("Свой"));
    private final ColorSetting secondaryColor = new ColorSetting("Вторичный цвет", new ColorRGBA(0xFF6600AA),
            () -> colorSource.is("Свой"));

    private SkyShader() {
    }

    @EventTarget
    public void onRender3D(EventRender3D event) {
        GlProgram program = DrawUtil.skyProgram;
        if (program == null) return;
        if (mc.world == null || mc.gameRenderer == null) return;
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null) return;

        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        program.use();
        pushUniforms(program, camera);
        drawFullscreenNdcQuad();

        RenderSystem.depthFunc(prevDepthFunc);
        RenderSystem.depthMask(true);
        if (prevCull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (prevDepth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.defaultBlendFunc();
        if (!prevBlend) RenderSystem.disableBlend();
    }

    private void pushUniforms(GlProgram program, Camera camera) {
        float time = (System.currentTimeMillis() - INITIAL_TIME) / 1000.0f;
        int width = Math.max(1, mc.getWindow().getFramebufferWidth());
        int height = Math.max(1, mc.getWindow().getFramebufferHeight());
        float aspect = (float) width / (float) height;

        float yawRad = -camera.getYaw() * DEG_TO_RAD;
        float pitchRad = -camera.getPitch() * DEG_TO_RAD;

        double fovDeg = mc.options.getFov().getValue();
        float fovRad = (float) Math.toRadians(fovDeg);

        int color1 = resolvePrimaryColor();
        int color2 = resolveSecondaryColor();

        setF(program, "iTime", time);
        setV2(program, "iResolution", width, height);
        setF(program, "iSpeed", speed.getCurrent());
        setV3(program, "iColor", redf(color1), greenf(color1), bluef(color1));
        setV3(program, "iColor2", redf(color2), greenf(color2), bluef(color2));
        setI(program, "iMode", getModeIndex());
        setF(program, "iYaw", yawRad);
        setF(program, "iPitch", pitchRad);
        setF(program, "iFov", fovRad);
        setF(program, "iAspect", aspect);
    }

    private void drawFullscreenNdcQuad() {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(-1f, -1f, 0f).texture(0f, 0f).color(1f, 1f, 1f, 1f);
        buffer.vertex(-1f, 1f, 0f).texture(0f, 1f).color(1f, 1f, 1f, 1f);
        buffer.vertex(1f, 1f, 0f).texture(1f, 1f).color(1f, 1f, 1f, 1f);
        buffer.vertex(1f, -1f, 0f).texture(1f, 0f).color(1f, 1f, 1f, 1f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private int getModeIndex() {
        int idx = MODES.indexOf(mode.get());
        return idx < 0 ? 0 : idx;
    }

    private int resolvePrimaryColor() {
        if (colorSource.is("Свой")) return primaryColor.getIntColor();
        return Endless.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB();
    }

    private int resolveSecondaryColor() {
        if (colorSource.is("Свой")) return secondaryColor.getIntColor();
        return Endless.getInstance().getThemeManager().getCurrentTheme().getSecondColor().getRGB();
    }

    private static float redf(int color) {
        return ((color >> 16) & 0xFF) / 255f;
    }

    private static float greenf(int color) {
        return ((color >> 8) & 0xFF) / 255f;
    }

    private static float bluef(int color) {
        return (color & 0xFF) / 255f;
    }

    private static void setF(GlProgram program, String name, float value) {
        var u = program.findUniform(name);
        if (u != null) u.set(value);
    }

    private static void setI(GlProgram program, String name, int value) {
        var u = program.findUniform(name);
        if (u != null) u.set(value);
    }

    private static void setV2(GlProgram program, String name, float x, float y) {
        var u = program.findUniform(name);
        if (u != null) u.set(x, y);
    }

    private static void setV3(GlProgram program, String name, float x, float y, float z) {
        var u = program.findUniform(name);
        if (u != null) u.set(x, y, z);
    }
}
