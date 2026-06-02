package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.events.impl.other.EventModuleToggle;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.display.shader.GlProgram;
import endless.ere.utility.render.level.Render3DUtil;

/**
 * Рисует под игроком полое кольцо ("пончик"): внутри пусто, а по краям —
 * объёмная площадка, на которой проигрываются 3D-шейдеры темы клиента
 * (плазма, туманность, звёзды, паутина).
 *
 * <p>Появление и исчезновение анимированы: кольцо вырастает из центра
 * с лёгким "поп"-эффектом, закручиваясь на место, и плавно тает при
 * выключении. Чтобы анимация выхода успела отрисоваться, модуль
 * откладывает отписку от шины событий до её завершения.
 */
@ModuleAnnotation(name = "ShaderJump", category = Category.RENDER,
        description = "Полое кольцо-площадка под игроком с 3D-шейдером и анимациями")
public final class ShaderJump extends Module {

    public static final ShaderJump INSTANCE = new ShaderJump();

    private final ModeSetting effect    = new ModeSetting("Эффект", "Плазма", "Туманность", "Звёзды", "Паутина");
    private final NumberSetting outer     = new NumberSetting("Внешний радиус", 1.4f, 0.6f, 4f, 0.1f);
    private final NumberSetting inner     = new NumberSetting("Внутренний радиус", 0.9f, 0.2f, 3.5f, 0.1f);
    private final NumberSetting thickness = new NumberSetting("Толщина", 0.18f, 0.02f, 1f, 0.02f);
    private final NumberSetting speed      = new NumberSetting("Скорость шейдера", 1f, 0.1f, 3f, 0.1f);
    private final NumberSetting scale      = new NumberSetting("Яркость", 1f, 0.2f, 2f, 0.05f);
    private final NumberSetting spin       = new NumberSetting("Вращение", 20f, 0f, 180f, 1f);
    private final NumberSetting wave       = new NumberSetting("Волна", 0.05f, 0f, 0.4f, 0.01f);
    private final NumberSetting animTime   = new NumberSetting("Время анимации", 0.45f, 0.1f, 1.5f, 0.05f);
    private final BooleanSetting bob        = new BooleanSetting("Парение", true);
    private final BooleanSetting outline    = new BooleanSetting("Обводка", true);
    private final BooleanSetting onlyInAir  = new BooleanSetting("Только в воздухе", false);

    private static final int SEGMENTS = 72;
    private static final int WAVE_COUNT = 5;

    /** 0 — скрыто, 1 — полностью показано. Драйвер всех анимаций появления. */
    private final Animation reveal = new Animation(450, 0f, Easing.QUARTIC_OUT);
    /** "Поп"-эффект масштаба с лёгким перелётом за единицу. */
    private final Animation pop = new Animation(520, 0f, Easing.BACK_OUT);

    private boolean listening;
    private boolean closing;

    private ShaderJump() {
    }

    // ── Жизненный цикл (с отложенной отпиской для анимации выхода) ───────────

    @Override
    public void onEnable() {
        if (!listening) {
            EventManager.register(this);
            listening = true;
        }
        closing = false;
        EventManager.call(new EventModuleToggle(this, true));
    }

    @Override
    public void onDisable() {
        // Не отписываемся сразу: пусть кольцо доиграет анимацию исчезновения.
        closing = true;
        EventManager.call(new EventModuleToggle(this, false));
    }

    // ── Рендер ──────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (mc.world == null || mc.player == null || mc.gameRenderer == null) return;

        // Цель анимации: показываем, только если модуль активен (не закрывается)
        // и выполняется условие "только в воздухе".
        boolean shouldShow = !closing
                && (!onlyInAir.isEnabled() || !mc.player.isOnGround());

        long durationMs = (long) (animTime.getCurrent() * 1000f);
        reveal.setDuration(durationMs);
        pop.setDuration((long) (durationMs * 1.15f));

        reveal.animateTo(shouldShow ? 1f : 0f);
        pop.animateTo(shouldShow ? 1f : 0f);

        float revealRaw = reveal.update();
        float popRaw = pop.update();

        float appear = MathHelper.clamp(revealRaw, 0f, 1f);
        float radiusMul = Math.max(0f, popRaw);

        // Анимация полностью свёрнута — рисовать нечего.
        if (appear <= 0.001f && reveal.isDone()) {
            if (closing) {
                EventManager.unregister(this);
                listening = false;
                closing = false;
            }
            return;
        }

        float tickDelta = event.getPartialTicks();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        double px = MathHelper.lerp(tickDelta, mc.player.prevX, mc.player.getX());
        double py = MathHelper.lerp(tickDelta, mc.player.prevY, mc.player.getY());
        double pz = MathHelper.lerp(tickDelta, mc.player.prevZ, mc.player.getZ());

        float outerR = outer.getCurrent();
        float innerR = Math.min(inner.getCurrent(), outerR - 0.1f);
        if (innerR < 0.05f) innerR = 0.05f;

        // Применяем "поп"-масштаб к радиусам, высоту растим по reveal.
        float effOuter = outerR * radiusMul;
        float effInner = innerR * radiusMul;
        float effHeight = thickness.getCurrent() * appear;

        long now = System.currentTimeMillis();

        // Лёгкое парение вверх-вниз.
        double bobOffset = bob.isEnabled() ? Math.sin(now / 600.0) * 0.03 * appear : 0.0;
        py += bobOffset;

        // Закручивание на место при появлении + постоянное вращение.
        float angleOffset = (now % 360_000L) / 1000f * spin.getCurrent();
        angleOffset += (1f - appear) * 180f;

        ColorRGBA accent = Endless.getInstance().getThemeManager().getCurrentTheme().getColor();

        renderRing(event.getMatrix(), cam, px, py, pz, effInner, effOuter, effHeight,
                angleOffset, appear, accent, now);

        if (outline.isEnabled() && appear > 0.02f) {
            int outlineColor = accent.withAlpha((int) (200 * appear)).getRGB();
            drawOutline(px, py, pz, effInner, effOuter, effHeight, angleOffset, appear, now, outlineColor);
        }
    }

    private GlProgram resolveProgram() {
        if (effect.is("Плазма"))     return DrawUtil.blockPlasmaProgram;
        if (effect.is("Туманность")) return DrawUtil.blockNebulaProgram;
        if (effect.is("Звёзды"))     return DrawUtil.blockStarfieldProgram;
        if (effect.is("Паутина"))    return DrawUtil.blockCobwebProgram;
        return DrawUtil.blockPlasmaProgram;
    }

    /** Вертикальное смещение верхней грани кольца для эффекта "волны". */
    private float waveAt(float angle, float waveTime, float appear) {
        float amp = wave.getCurrent();
        if (amp <= 0f) return 0f;
        return (float) Math.sin(angle * WAVE_COUNT + waveTime) * amp * appear;
    }

    private void renderRing(MatrixStack matrices, Vec3d cam, double cx, double cy, double cz,
                            float innerR, float outerR, float height, float angleOffset,
                            float appear, ColorRGBA accent, long now) {
        GlProgram program = resolveProgram();
        if (program == null) return;

        float time = (now % 1_000_000L) / 1000f * speed.getCurrent();
        float waveTime = now / 220f;

        float pulse = 0.85f + 0.15f * (float) Math.sin(now / 400.0);
        float alpha = MathHelper.clamp(scale.getCurrent() * 0.6f, 0.15f, 1f) * appear * pulse;

        program.use();
        var timeU = program.findUniform("time");
        if (timeU != null) timeU.set(time);
        var screenU = program.findUniform("screenSize");
        if (screenU != null) screenU.set((float) mc.getWindow().getFramebufferWidth(),
                (float) mc.getWindow().getFramebufferHeight());
        var alphaU = program.findUniform("alpha");
        if (alphaU != null) alphaU.set(alpha);
        setColorUniform(program, "baseColor", accent.withAlpha(255));

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float ox = (float) (cx - cam.x);
        float oz = (float) (cz - cam.z);
        float yBottom = (float) (cy - cam.y);
        int color = 0xFFFFFFFF;
        float step = (float) (Math.PI * 2.0 / SEGMENTS);
        float baseRad = (float) Math.toRadians(angleOffset);

        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = baseRad + i * step;
            float a1 = baseRad + (i + 1) * step;

            float cos0 = (float) Math.cos(a0), sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1), sin1 = (float) Math.sin(a1);

            float top0 = yBottom + height + waveAt(a0, waveTime, appear);
            float top1 = yBottom + height + waveAt(a1, waveTime, appear);

            float oxi0 = ox + cos0 * outerR, ozi0 = oz + sin0 * outerR;
            float oxi1 = ox + cos1 * outerR, ozi1 = oz + sin1 * outerR;
            float ixi0 = ox + cos0 * innerR, izi0 = oz + sin0 * innerR;
            float ixi1 = ox + cos1 * innerR, izi1 = oz + sin1 * innerR;

            float u0 = i / (float) SEGMENTS;
            float u1 = (i + 1) / (float) SEGMENTS;

            // верхняя грань кольца (площадка)
            buffer.vertex(matrix, oxi0, top0, ozi0).texture(u0, 1f).color(color);
            buffer.vertex(matrix, oxi1, top1, ozi1).texture(u1, 1f).color(color);
            buffer.vertex(matrix, ixi1, top1, izi1).texture(u1, 0f).color(color);
            buffer.vertex(matrix, ixi0, top0, izi0).texture(u0, 0f).color(color);

            // нижняя грань кольца
            buffer.vertex(matrix, ixi0, yBottom, izi0).texture(u0, 0f).color(color);
            buffer.vertex(matrix, ixi1, yBottom, izi1).texture(u1, 0f).color(color);
            buffer.vertex(matrix, oxi1, yBottom, ozi1).texture(u1, 1f).color(color);
            buffer.vertex(matrix, oxi0, yBottom, ozi0).texture(u0, 1f).color(color);

            // внешняя боковая стенка
            buffer.vertex(matrix, oxi0, yBottom, ozi0).texture(u0, 0f).color(color);
            buffer.vertex(matrix, oxi1, yBottom, ozi1).texture(u1, 0f).color(color);
            buffer.vertex(matrix, oxi1, top1, ozi1).texture(u1, 1f).color(color);
            buffer.vertex(matrix, oxi0, top0, ozi0).texture(u0, 1f).color(color);

            // внутренняя боковая стенка
            buffer.vertex(matrix, ixi0, yBottom, izi0).texture(u0, 0f).color(color);
            buffer.vertex(matrix, ixi0, top0, izi0).texture(u0, 1f).color(color);
            buffer.vertex(matrix, ixi1, top1, izi1).texture(u1, 1f).color(color);
            buffer.vertex(matrix, ixi1, yBottom, izi1).texture(u1, 0f).color(color);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void drawOutline(double cx, double cy, double cz, float innerR, float outerR,
                             float height, float angleOffset, float appear, long now, int color) {
        float step = (float) (Math.PI * 2.0 / SEGMENTS);
        float baseRad = (float) Math.toRadians(angleOffset);
        float waveTime = now / 220f;
        float yBottom = (float) cy;

        Vec3d prevOuterTop = null, prevInnerTop = null, prevOuterBot = null, prevInnerBot = null;
        for (int i = 0; i <= SEGMENTS; i++) {
            float a = baseRad + i * step;
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            float top = (float) cy + height + waveAt(a, waveTime, appear);

            Vec3d outerTop = new Vec3d(cx + cos * outerR, top, cz + sin * outerR);
            Vec3d innerTop = new Vec3d(cx + cos * innerR, top, cz + sin * innerR);
            Vec3d outerBot = new Vec3d(cx + cos * outerR, yBottom, cz + sin * outerR);
            Vec3d innerBot = new Vec3d(cx + cos * innerR, yBottom, cz + sin * innerR);

            if (prevOuterTop != null) {
                Render3DUtil.drawLine(prevOuterTop, outerTop, color, 1.2f, false);
                Render3DUtil.drawLine(prevInnerTop, innerTop, color, 1.2f, false);
                Render3DUtil.drawLine(prevOuterBot, outerBot, color, 1.2f, false);
                Render3DUtil.drawLine(prevInnerBot, innerBot, color, 1.2f, false);
            }
            prevOuterTop = outerTop;
            prevInnerTop = innerTop;
            prevOuterBot = outerBot;
            prevInnerBot = innerBot;
        }
    }

    private static void setColorUniform(GlProgram program, String name, ColorRGBA color) {
        var uniform = program.findUniform(name);
        if (uniform == null) return;
        uniform.set(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
    }
}
