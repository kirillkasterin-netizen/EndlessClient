package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import endless.ere.Endless;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.ColorSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.render.display.base.color.ColorRGBA;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Анимированный наземный декаль в точке, откуда игрок прыгнул. Круг
 * раскрывается наружу, затухает и исчезает через {@link #LIFETIME_MS} мс.
 * Перенесён из WraithClient под событийную систему endless.
 */
@ModuleAnnotation(name = "Jump Circles", category = Category.RENDER,
        description = "Круги под игроком при прыжке")
public final class JumpCircles extends Module {

    public static final JumpCircles INSTANCE = new JumpCircles();

    private static final long LIFETIME_MS = 900L;

    private static final Identifier TEXTURE_STYLE_1 = Endless.id("textures/jumpcircle/jumpcircle_1.png");
    private static final Identifier TEXTURE_STYLE_2 = Endless.id("textures/jumpcircle/jumpcircle_2.png");

    private final ModeSetting style = new ModeSetting("Стиль", "Стиль 1", "Стиль 2");
    private final NumberSetting radius = new NumberSetting("Радиус", 1.6f, 0.6f, 4.0f, 0.1f);
    private final NumberSetting growth = new NumberSetting("Рост", 1.0f, 0.2f, 2.0f, 0.1f);
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Клиентский", "Кастомный", "Белый");
    private final ColorSetting customColor = new ColorSetting("Свой цвет", new ColorRGBA(0xFF8A6BFF),
            () -> colorMode.is("Кастомный"));

    private final CopyOnWriteArrayList<Circle> circles = new CopyOnWriteArrayList<>();
    private boolean prevOnGround;

    private JumpCircles() {
    }

    @Override
    public void onDisable() {
        circles.clear();
        prevOnGround = false;
        super.onDisable();
    }

    // ── Детект прыжка ───────────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            circles.clear();
            prevOnGround = false;
            return;
        }

        long now = System.currentTimeMillis();
        circles.removeIf(c -> now - c.time > LIFETIME_MS);

        boolean onGround = mc.player.isOnGround();
        double vy = mc.player.getVelocity().y;
        boolean jumpKey = mc.options.jumpKey.isPressed();

        if (prevOnGround && !onGround && vy > 0.0 && (jumpKey || vy > 0.30)) {
            circles.add(new Circle(mc.player.getPos().add(0, 0.02, 0)));
        }
        prevOnGround = onGround;
    }

    // ── Рендер ──────────────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(EventRender3D event) {
        if (circles.isEmpty() || mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        Identifier texture = style.is("Стиль 2") ? TEXTURE_STYLE_2 : TEXTURE_STYLE_1;
        int baseColor = resolveColor();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        long now = System.currentTimeMillis();
        for (Circle circle : circles) {
            renderCircle(matrices, camPos, circle, now, baseColor);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderTexture(0, 0);
    }

    private void renderCircle(MatrixStack matrices, Vec3d camPos, Circle circle, long now, int baseColor) {
        float progress = MathHelper.clamp((now - circle.time) / (float) LIFETIME_MS, 0f, 1f);
        if (progress >= 1f) return;

        float scale = MathHelper.lerp(easeOutQuad(progress), 0.35f, radius.getCurrent() * growth.getCurrent());
        float alpha = (1f - progress) * (1f - progress);
        int color = setAlpha(baseColor, (int) (alpha * 255f));

        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        double px = circle.position.x - camPos.x;
        double py = circle.position.y - camPos.y + 0.01;
        double pz = circle.position.z - camPos.z;

        matrices.push();
        matrices.translate(px, py, pz);
        Matrix4f m = matrices.peek().getPositionMatrix();
        float h = scale * 0.5f;

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(m, -h, 0f, -h).texture(0f, 0f).color(r, g, b, a);
        buffer.vertex(m, -h, 0f, h).texture(0f, 1f).color(r, g, b, a);
        buffer.vertex(m, h, 0f, h).texture(1f, 1f).color(r, g, b, a);
        buffer.vertex(m, h, 0f, -h).texture(1f, 0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private int resolveColor() {
        return switch (colorMode.get()) {
            case "Кастомный" -> customColor.getIntColor();
            case "Белый" -> 0xFFFFFFFF;
            default -> Endless.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB();
        };
    }

    private static int setAlpha(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private static float easeOutQuad(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private static final class Circle {
        final Vec3d position;
        final long time;

        Circle(Vec3d position) {
            this.position = position;
            this.time = System.currentTimeMillis();
        }
    }
}
