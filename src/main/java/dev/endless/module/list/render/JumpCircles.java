package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
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
import dev.endless.event.list.EventTick;
import dev.endless.event.list.EventWorldRender;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.ColorSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spawns an animated, ground-anchored texture decal at the spot where the
 * player jumped from. The decal expands outward, fades out, and disappears
 * after {@link #LIFETIME_MS} milliseconds.
 *
 * Two visual styles are bundled, each backed by its own texture:
 *   <ul>
 *     <li><b>Стиль 1</b> — {@code jumpcircle_1.png}</li>
 *     <li><b>Стиль 2</b> — {@code jumpcircle_2.png}</li>
 *   </ul>
 */
@ModuleInformation(moduleName = "Jump Circles", moduleDesc = "Круги под игроком при прыжке", moduleCategory = ModuleCategory.RENDER)
public class JumpCircles extends Module {

    private static final long LIFETIME_MS = 900L;

    private static final Identifier TEXTURE_STYLE_1 = Identifier.of("mre", "textures/jumpcircle/jumpcircle_1.png");
    private static final Identifier TEXTURE_STYLE_2 = Identifier.of("mre", "textures/jumpcircle/jumpcircle_2.png");

    public final ModeSetting style = new ModeSetting("Стиль", "Стиль 1", "Стиль 1", "Стиль 2");
    public final SliderSetting radius = new SliderSetting("Радиус", 1.6, 0.6, 4.0, 0.1);
    public final SliderSetting growth = new SliderSetting("Рост", 1.0, 0.2, 2.0, 0.1);

    public final ModeSetting colorMode = new ModeSetting("Цвет", "Клиентский", "Клиентский", "Кастомный", "Белый");
    public final ColorSetting customColor = (ColorSetting) new ColorSetting("Свой цвет", 0xFF8A6BFF)
            .setVisible(() -> colorMode.is("Кастомный"));

    private final CopyOnWriteArrayList<Circle> circles = new CopyOnWriteArrayList<>();
    private boolean prevOnGround;

    @Override
    public void onDisable() {
        circles.clear();
        prevOnGround = false;
        super.onDisable();
    }

    // ── Spawn detection ─────────────────────────────────────────────────────

    @Subscribe
    private void onTick(EventTick event) {
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

        // Detect a real jump: was on ground last tick, in the air this tick,
        // moving up. Allow either the jump key being held or a strong upward
        // velocity, which catches Speed/Jump-boost cases.
        if (prevOnGround && !onGround && vy > 0.0 && (jumpKey || vy > 0.30)) {
            Vec3d feet = mc.player.getPos().add(0, 0.02, 0);
            circles.add(new Circle(feet));
        }
        prevOnGround = onGround;
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Subscribe
    private void onRender3D(EventWorldRender event) {
        if (circles.isEmpty()) return;
        if (mc.player == null || mc.world == null) return;

        Identifier texture = style.is("Стиль 2") ? TEXTURE_STYLE_2 : TEXTURE_STYLE_1;
        int baseColor = resolveColor();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrixStack();

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

    /**
     * Draws a single decal as a flat ground-aligned textured quad. The quad
     * scales outwards and fades out across its lifetime.
     */
    private void renderCircle(MatrixStack matrices, Vec3d camPos, Circle circle, long now, int baseColor) {
        float progress = MathHelper.clamp((now - circle.time) / (float) LIFETIME_MS, 0f, 1f);
        if (progress >= 1f) return;

        // Ease-out scale + ease-in alpha (sharp pop, smooth fade).
        float scale  = MathHelper.lerp(easeOutQuad(progress), 0.35f, radius.getFloatValue() * growth.getFloatValue());
        float alpha  = (1f - progress) * (1f - progress);
        int   color  = ColorProvider.setAlpha(baseColor, (int) (alpha * 255f));

        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8)  & 0xFF;
        int b =  color        & 0xFF;

        double px = circle.position.x - camPos.x;
        double py = circle.position.y - camPos.y + 0.01; // tiny lift to avoid z-fighting
        double pz = circle.position.z - camPos.z;

        matrices.push();
        matrices.translate(px, py, pz);
        Matrix4f m = matrices.peek().getPositionMatrix();
        float h = scale * 0.5f;

        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // Flat XZ quad with Y = 0 — sits on the ground.
        buffer.vertex(m, -h, 0f, -h).texture(0f, 0f).color(r, g, b, a);
        buffer.vertex(m, -h, 0f,  h).texture(0f, 1f).color(r, g, b, a);
        buffer.vertex(m,  h, 0f,  h).texture(1f, 1f).color(r, g, b, a);
        buffer.vertex(m,  h, 0f, -h).texture(1f, 0f).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private int resolveColor() {
        return switch (colorMode.getValue()) {
            case "Кастомный" -> customColor.getValue();
            case "Белый"     -> 0xFFFFFFFF;
            default          -> ColorProvider.getColorClient();
        };
    }

    private static float easeOutQuad(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    /** Tiny POJO carrying spawn position + spawn time. */
    private static final class Circle {
        final Vec3d position;
        final long time;

        Circle(Vec3d position) {
            this.position = position;
            this.time = System.currentTimeMillis();
        }
    }
}
