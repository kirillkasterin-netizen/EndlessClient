package endless.ere.utility.render.display.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import org.joml.Matrix4f;
import endless.ere.Endless;
import endless.ere.client.modules.impl.render.Interface;
import endless.ere.utility.interfaces.IWindow;
import endless.ere.utility.math.MathUtil;
import endless.ere.utility.render.display.Render2DUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomSprite;
import endless.ere.utility.render.display.base.Gradient;
import endless.ere.utility.render.display.base.color.ColorRGBA;



@UtilityClass
public class DrawUtil implements IWindow {

    public static final float DEFAULT_SMOOTHNESS = 0.8f;

    public GlProgram rectangleProgram;
    private GlProgram squircleProgram;
    private GlProgram roundedTextureProgram;
    private GlProgram squircleTextureProgram;
    private GlProgram borderProgram;
    private GlProgram figmaBorderProgram;
    private GlProgram loadingProgram;
    private GlProgram gradientRectangleProgram;
    public GlProgram glow3dProgram;
    public GlProgram waterCausticProgram;
    public GlProgram starSparkleProgram;
    public GlProgram blockCobwebProgram;
    public GlProgram blockNebulaProgram;
    public GlProgram blockPlasmaProgram;
    public GlProgram blockStarfieldProgram;
    public GlProgram skyProgram;
    public GlProgram handsMaskDiffProgram;
    public GlProgram handsGlowProgram;
    public GlProgram handsOverlayProgram;
    public GlProgram handsKawaseDownProgram;
    public GlProgram handsKawaseUpProgram;
    public GlProgram handsBlockOverlayProgram;
    public GlProgram hudGlowProgram;
    public GlProgram hudDotsProgram;
    public GlProgram hudGridProgram;
    public GlProgram tintProgram;
    public BlurProgram blurProgram;

    private final CustomRenderTarget buffer = new CustomRenderTarget(false);

    public void initializeShaders() {
        rectangleProgram = new GlProgram(Endless.id("rectangle/data"), VertexFormats.POSITION_COLOR);
        squircleProgram = new GlProgram(Endless.id("squircle/data"), VertexFormats.POSITION_COLOR);
        squircleTextureProgram = new GlProgram(Endless.id("squircle_texture/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        roundedTextureProgram = new GlProgram(Endless.id("texture/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        borderProgram = new GlProgram(Endless.id("border/data"), VertexFormats.POSITION_COLOR);
        figmaBorderProgram = new GlProgram(Endless.id("corner/data"), VertexFormats.POSITION_COLOR);

        loadingProgram = new GlProgram(Endless.id("loading/data"), VertexFormats.POSITION_COLOR);
        gradientRectangleProgram = new GlProgram(Endless.id("gradient_rectangle/data"), VertexFormats.POSITION_COLOR);
        glow3dProgram = new GlProgram(Endless.id("glow3d/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        waterCausticProgram = new GlProgram(Endless.id("watercaustic/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        starSparkleProgram = new GlProgram(Endless.id("starsparkle/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        blockCobwebProgram = new GlProgram(Endless.id("block_cobweb/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        blockNebulaProgram = new GlProgram(Endless.id("block_nebula/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        blockPlasmaProgram = new GlProgram(Endless.id("block_plasma/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        blockStarfieldProgram = new GlProgram(Endless.id("block_starfield/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        skyProgram = new GlProgram(Endless.id("sky/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        handsMaskDiffProgram = new GlProgram(Endless.id("hands/hands_mask_diff/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        handsGlowProgram = new GlProgram(Endless.id("hands/hands_glow/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        handsOverlayProgram = new GlProgram(Endless.id("hands/hands_overlay/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        handsKawaseDownProgram = new GlProgram(Endless.id("hands/hands_kawase_down/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        handsKawaseUpProgram = new GlProgram(Endless.id("hands/hands_kawase_up/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        handsBlockOverlayProgram = new GlProgram(Endless.id("hands/block_overlay/data"), VertexFormats.POSITION_TEXTURE_COLOR);
        hudGlowProgram = new GlProgram(Endless.id("hud_glow/data"), VertexFormats.POSITION_COLOR);
        hudDotsProgram = new GlProgram(Endless.id("hud_dots/data"), VertexFormats.POSITION_COLOR);
        hudGridProgram = new GlProgram(Endless.id("hud_grid/data"), VertexFormats.POSITION_COLOR);
        blurProgram = new BlurProgram();
        blurProgram.initShaders();
    }

    public void updateBuffer() {
        buffer.setClearColor(0f, 0f, 0f, 1f);
        buffer.setup();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        mc.getFramebuffer().beginRead();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, mc.getFramebuffer().getColorAttachment());
        drawQuad(0, 0, mw.getScaledWidth(), mw.getScaledHeight(), true);
        mc.getFramebuffer().endRead();
        RenderSystem.disableBlend();
        mc.getFramebuffer().beginWrite(true);
        buffer.stop();
    }

    private void drawQuad(float x, float y, float width, float height, boolean flip) {
        final BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        final int color = -1;

        float vTop = flip ? 0f : 1f;
        float vBottom = flip ? 1f : 0f;

        builder.vertex(x, y, 0F).texture(0f, vBottom).color(color);
        builder.vertex(x, y + height, 0F).texture(0f, vTop).color(color);
        builder.vertex(x + width, y + height, 0F).texture(1f, vTop).color(color);
        builder.vertex(x + width, y, 0F).texture(1f, vBottom).color(color);

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void drawLine(MatrixStack matrices, Vec2f from, Vec2f to, ColorRGBA color) {
        matrices.push();
        try {
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.lineWidth(1);

            drawSetup();

            BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            builder.vertex(matrix4f, from.x, from.y, 0).color(color.getRGB());
            builder.vertex(matrix4f, to.x, to.y, 0).color(color.getRGB());
            BufferRenderer.drawWithGlobalProgram(builder.end());

            drawEnd();

        } finally {
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1.0f);
            matrices.pop();
        }
    }

    public void drawBezier(MatrixStack matrices, Vec2f p0, Vec2f p1, Vec2f p2, Vec2f p3, ColorRGBA color, int resolution) {
        matrices.push();
        try {
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.lineWidth(1);

            drawSetup();

            BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= resolution; i++) {
                float t = (float) i / resolution;
                float x = (float) MathUtil.cubicBezier(t, p0.x, p1.x, p2.x, p3.x);
                float y = (float) MathUtil.cubicBezier(t, p0.y, p1.y, p2.y, p3.y);
                builder.vertex(matrix4f, x, y, 0).color(color.getRGB());
            }
            BufferRenderer.drawWithGlobalProgram(builder.end());

            drawEnd();

        } finally {
            RenderSystem.disableBlend();
            RenderSystem.lineWidth(1.0f);
            matrices.pop();
        }
    }

    private float cubicBezier(float t, float p0, float p1, float p2, float p3) {
        float u = 1 - t;
        float tt = t * t;
        float uu = u * u;

        return (uu * u * p0) + (3 * uu * t * p1) + (3 * u * tt * p2) + (tt * t * p3);
    }

    public void drawRect(MatrixStack matrices, float x, float y, float width, float height, ColorRGBA color) {
        matrices.push();

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        drawSetup();

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, x, y + height, 0).color(color.getRGB());
        builder.vertex(matrix4f, x + width, y + height, 0).color(color.getRGB());
        builder.vertex(matrix4f, x + width, y, 0).color(color.getRGB());
        builder.vertex(matrix4f, x, y, 0).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawSquircle(MatrixStack matrices, float x, float y, float width, float height, float squirt, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        squircleProgram.use();
        squircleProgram.findUniform("Size").set(width, height);
        squircleProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius() * squirt / 2F,
                borderRadius.bottomLeftRadius() * squirt / 2F,
                borderRadius.topRightRadius() * squirt / 2F,
                borderRadius.bottomRightRadius() * squirt / 2F
        );
        squircleProgram.findUniform("Smoothness").set(smoothness);
        squircleProgram.findUniform("CornerSmoothness").set(squirt);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawLoadingRect(MatrixStack matrices, float x, float y, float width, float height, float progress, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        loadingProgram.use();
        loadingProgram.findUniform("Size").set(width, height);
        loadingProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        loadingProgram.findUniform("Smoothness").set(smoothness);
        loadingProgram.findUniform("Progress").set(progress);
        loadingProgram.findUniform("StripeWidth").set(0f);
        loadingProgram.findUniform("Fade").set(0.5f);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawRoundedRect(MatrixStack matrices, float x, float y, float width, float height, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        rectangleProgram.use();
        rectangleProgram.findUniform("Size").set(width, height);
        rectangleProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        rectangleProgram.findUniform("Smoothness").set(smoothness);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawRoundedRect(MatrixStack matrices, float x, float y, float width, float height, BorderRadius borderRadius, ColorRGBA color1, ColorRGBA color2, ColorRGBA color3, ColorRGBA color4) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        gradientRectangleProgram.use();
        gradientRectangleProgram.findUniform("Size").set(width, height);
        gradientRectangleProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        gradientRectangleProgram.findUniform("Smoothness").set(smoothness);

        gradientRectangleProgram.findUniform("TopLeftColor").set(
                color1.getRed() / 255.0f,
                color1.getGreen() / 255.0f,
                color1.getBlue() / 255.0f,
                color1.getAlpha() / 255.0f
        );
        gradientRectangleProgram.findUniform("BottomLeftColor").set(
                color2.getRed() / 255.0f,
                color2.getGreen() / 255.0f,
                color2.getBlue() / 255.0f,
                color2.getAlpha() / 255.0f
        );
        gradientRectangleProgram.findUniform("BottomRightColor").set(
                color3.getRed() / 255.0f,
                color3.getGreen() / 255.0f,
                color3.getBlue() / 255.0f,
                color3.getAlpha() / 255.0f
        );
        gradientRectangleProgram.findUniform("TopRightColor").set(
                color4.getRed() / 255.0f,
                color4.getGreen() / 255.0f,
                color4.getBlue() / 255.0f,
                color4.getAlpha() / 255.0f
        );

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        // color1 - верхний левый
        // color2 - нижний левый
        // color3 - нижний правый
        // color4 - верхний правый
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color1.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color2.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color3.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color4.getRGB());

        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawRoundedRect(MatrixStack matrices, float x, float y, float width, float height, BorderRadius borderRadius, Gradient gradient) {
        drawRoundedRect(matrices, x, y, width, height, borderRadius,
                gradient.getTopLeftColor(),
                gradient.getBottomLeftColor(),
                gradient.getBottomRightColor(),
                gradient.getTopRightColor());
    }


    /**
     * Немного криво, но работает
     */
    public void drawRoundedBorder(MatrixStack matrices, float x, float y, float width, float height, float borderThickness, BorderRadius borderRadius, ColorRGBA borderColor) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float internalSmoothness = DEFAULT_SMOOTHNESS, externalSmoothness = 1.0F;

        borderProgram.use();
        borderProgram.findUniform("Size").set(width, height);
        borderProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        borderProgram.findUniform("Smoothness").set(internalSmoothness, externalSmoothness);
        borderProgram.findUniform("Thickness").set(borderThickness);

        drawSetup();

        float horizontalPadding = -externalSmoothness / 2.0F + externalSmoothness * 2.0F;
        float verticalPadding = externalSmoothness / 2.0F + externalSmoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(borderColor.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(borderColor.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(borderColor.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(borderColor.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }


    public void drawRoundedCorner(MatrixStack matrices, float x, float y, float width, float height, float borderThikenes, float delta, ColorRGBA color, BorderRadius radius) {
        if(!Interface.INSTANCE.isCorners()) return;
        //знаю это пиздец но я проебался с тем что сразу не сделал метод у DragHud
        x-=0.3f;
        y-=0.3f;
        width+=0.3f*2;
        height+=0.3f*2;
        drawRoundedCornerOnly(matrices, x, y, delta, delta, borderThikenes, radius, color, 0);
        drawRoundedCornerOnly(matrices, x + width - delta, y, delta, delta, borderThikenes, radius, color, 1);

        drawRoundedCornerOnly(matrices, x, y + height - delta, delta, delta, borderThikenes, radius, color, 2);
        drawRoundedCornerOnly(matrices, x + width - delta, y + height - delta, delta, delta, borderThikenes, radius, color, 3);

    }

    public void drawRoundedCornerOnly(MatrixStack matrices, float x, float y, float width, float height, float borderThickness, BorderRadius borderRadius, ColorRGBA borderColor, float cornerIdex) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float internalSmoothness = DEFAULT_SMOOTHNESS, externalSmoothness = 1.0F;

        figmaBorderProgram.use();
        figmaBorderProgram.findUniform("Size").set(width, height);
        figmaBorderProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        figmaBorderProgram.findUniform("Smoothness").set(internalSmoothness, externalSmoothness);
        figmaBorderProgram.findUniform("Thickness").set(borderThickness);
        figmaBorderProgram.findUniform("CornerIndex").set(cornerIdex);

        drawSetup();

        float horizontalPadding = -externalSmoothness / 2.0F + externalSmoothness * 2.0F;
        float verticalPadding = externalSmoothness / 2.0F + externalSmoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(borderColor.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(borderColor.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(borderColor.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(borderColor.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawTexture(MatrixStack matrices, Identifier identifier, float x, float y, float width, float height, ColorRGBA textureColor) {
        matrices.push();

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, identifier);

        drawSetup();

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix4f, x, y, 0.0F).texture(0.0F, 0.0F).color(textureColor.getRGB());
        builder.vertex(matrix4f, x, y + height, 0.0F).texture(0.0F, 1.0F).color(textureColor.getRGB());
        builder.vertex(matrix4f, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(textureColor.getRGB());
        builder.vertex(matrix4f, x + width, y, 0.0F).texture(1.0F, 0.0F).color(textureColor.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();

        // сбрасываем текстуру
        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();
    }
    public void drawTexture(MatrixStack matrices, Identifier identifier, float x, float y, float width, float height, Gradient textureColor) {
        matrices.push();

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, identifier);

        drawSetup();

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix4f, x, y, 0.0F).texture(0.0F, 0.0F).color(textureColor.getTopLeftColor().getRGB());
        builder.vertex(matrix4f, x, y + height, 0.0F).texture(0.0F, 1.0F).color(textureColor.getBottomLeftColor().getRGB());
        builder.vertex(matrix4f, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(textureColor.getBottomRightColor().getRGB());
        builder.vertex(matrix4f, x + width, y, 0.0F).texture(1.0F, 0.0F).color(textureColor.getTopRightColor().getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();

        // сбрасываем текстуру
        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();
    }

    public void drawTexture(MatrixStack matrices, Identifier identifier, float x, float y, float width, float height, float u1, float u2, float v1, float v2, ColorRGBA clor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        matrices.push();
        int color = clor.getRGB();

        // багчинг пошол нахуй
        // не

        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        float x2 = x + width;
        float y2 = y + height;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, identifier);

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix4f, x, y, 0.0F).texture(u1, v1).color(color);
        builder.vertex(matrix4f, x, y2, 0.0F).texture(u1, v2).color(color);
        builder.vertex(matrix4f, x2, y2, 0.0F).texture(u2, v2).color(color);
        builder.vertex(matrix4f, x2, y, 0.0F).texture(u2, v1).color(color);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();

        // сбрасываем текстуру
        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();
        RenderSystem.disableBlend();
    }

    public void drawSprite(MatrixStack matrices, CustomSprite sprite, float x, float y, float width, float height, ColorRGBA color) {
        drawTexture(matrices, sprite.getTexture(), x, y, width, height, 0, 1, 0, 1, color);
    }

    public void drawRoundedTexture(MatrixStack matrices, Identifier identifier, float x, float y, float width, float height, BorderRadius borderRadius) {
        drawRoundedTexture(matrices, identifier, x, y, width, height, borderRadius, ColorRGBA.WHITE);
    }

    public void drawRoundedTexture(MatrixStack matrices, Identifier identifier, float x, float y, float width, float height, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        roundedTextureProgram.use();
        RenderSystem.setShaderTexture(0, identifier);

        roundedTextureProgram.findUniform("Size").set(width, height);
        roundedTextureProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        roundedTextureProgram.findUniform("Smoothness").set(smoothness);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).texture(0.0F, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).texture(0.0F, 1.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).texture(1.0F, 1.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).texture(1.0F, 0.0F).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());
        drawEnd();

        // сбрасываем текстуру
        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();
    }

    /**
     * Объясняю как работает:
     * Это по сути тот же самый квадрат с закругленными краями, но с более размытыми краями, что как
     * раз и создает нужный нам эффект "тени"
     */
    public void drawShadow(MatrixStack matrices, float x, float y, float width, float height, float softness, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        rectangleProgram.use();
        rectangleProgram.findUniform("Size").set(width, height);
        rectangleProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius() * 3,
                borderRadius.bottomLeftRadius() * 3,
                borderRadius.topRightRadius() * 3,
                borderRadius.bottomRightRadius() * 3
        );
        rectangleProgram.findUniform("Smoothness").set(softness);

        drawSetup();

        float horizontalPadding = -softness / 2.0F + softness * 2.0F;
        float verticalPadding = softness / 2.0F + softness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, float blurRadius, float squirt, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = 0.03f;

        blurRadius /= 22.5f;

        if (blurRadius <= 0) return;

        blurProgram.setBlurRadius(2);
        squircleTextureProgram.use();
        RenderSystem.setShaderTexture(0, BlurProgram.getTexture());
        squircleTextureProgram.findUniform("Size").set(width, height);
        squircleTextureProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius() * squirt / 2F,
                borderRadius.bottomLeftRadius() * squirt / 2F,
                borderRadius.topRightRadius() * squirt / 2F,
                borderRadius.bottomRightRadius() * squirt / 2F
        );
        squircleTextureProgram.findUniform("Smoothness").set(0.1f);
        squircleTextureProgram.findUniform("CornerSmoothness").set(squirt);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float u = adjustedX / screenWidth;
        float v = (screenHeight - adjustedY - adjustedHeight) / screenHeight;
        float texWidth = adjustedWidth / screenWidth;
        float texHeight = adjustedHeight / screenHeight;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).texture(u, v + texHeight).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).texture(u, v).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).texture(u + texWidth, v).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).texture(u + texWidth, v + texHeight).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();

        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();
    }


    public void drawBlurHud(MatrixStack matrices, float x, float y, float width, float height, float blurRadius, BorderRadius borderRadius, ColorRGBA color) {
        drawBlurHudBooleanCheck(matrices,x,y,width,height,blurRadius,borderRadius,color,Interface.INSTANCE.isBlur(),Interface.INSTANCE.isGlow());
    }
    public void drawBlurHudBooleanCheck(MatrixStack matrices, float x, float y, float width, float height, float blurRadius, BorderRadius borderRadius, ColorRGBA color,boolean blur,boolean glow) {
        if(blur) {

            matrices.push();
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();

            blurRadius /= 22.5f;

            if (blurRadius <= 0) return;

            blurProgram.setBlurRadius(2);
            roundedTextureProgram.use();
            RenderSystem.setShaderTexture(0, BlurProgram.getTexture());

            roundedTextureProgram.findUniform("Size").set(width, height);
            roundedTextureProgram.findUniform("Radius").set(
                    borderRadius.topLeftRadius(),
                    borderRadius.bottomLeftRadius(),
                    borderRadius.topRightRadius(),
                    borderRadius.bottomRightRadius()
            );
            roundedTextureProgram.findUniform("Smoothness").set(0.01f);

            drawSetup();

            int screenWidth = mc.getWindow().getScaledWidth();
            int screenHeight = mc.getWindow().getScaledHeight();

            float u = x / screenWidth;
            float v = (screenHeight - y - height) / screenHeight;
            float texWidth = width / screenWidth;
            float texHeight = height / screenHeight;

            BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            builder.vertex(matrix4f, x, y, 0.0F).texture(u, v + texHeight).color(color.getRGB());
            builder.vertex(matrix4f, x, y + height, 0.0F).texture(u, v).color(color.getRGB());
            builder.vertex(matrix4f, x + width, y + height, 0.0F).texture(u + texWidth, v).color(color.getRGB());
            builder.vertex(matrix4f, x + width, y, 0.0F).texture(u + texWidth, v + texHeight).color(color.getRGB());
            BufferRenderer.drawWithGlobalProgram(builder.end());
            drawEnd();

            // сбрасываем текстуру
            RenderSystem.setShaderTexture(0, 0);
            matrices.pop();
        }
        if(glow){
            drawGlow(matrices, x, y, width, height,Interface.INSTANCE.getGlowRadius());
        }
    }
    public static void drawGlow(MatrixStack matrixStack, float x, float y, float width, float height, int glowRadius) {
        if (hudGlowProgram == null) {
            // fallback на старую CPU-генерацию, если шейдер ещё не загружен
            Render2DUtil.drawGradientBlurredShadow(matrixStack, x, y, width, height, glowRadius,
                    Endless.getInstance().getThemeManager().getClientColor());
            return;
        }

        Gradient gradient = Endless.getInstance().getThemeManager().getClientColor();
        float spread = Math.max(1f, glowRadius * 1.6f);
        float padX = x - spread;
        float padY = y - spread;
        float padW = width + spread * 2f;
        float padH = height + spread * 2f;

        matrixStack.push();
        Matrix4f matrix4f = matrixStack.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        hudGlowProgram.use();
        hudGlowProgram.findUniform("Size").set(padW, padH);
        hudGlowProgram.findUniform("Radius").set(0f, 0f, 0f, 0f);
        hudGlowProgram.findUniform("Spread").set(spread);
        hudGlowProgram.findUniform("Intensity").set(0.35f);
        setGlowColor(hudGlowProgram, "TopLeftColor",     gradient.getTopLeftColor());
        setGlowColor(hudGlowProgram, "TopRightColor",    gradient.getTopRightColor());
        setGlowColor(hudGlowProgram, "BottomLeftColor",  gradient.getBottomLeftColor());
        setGlowColor(hudGlowProgram, "BottomRightColor", gradient.getBottomRightColor());

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = padX - horizontalPadding / 2.0F;
        float adjustedY = padY - verticalPadding / 2.0F;
        float adjustedWidth = padW + horizontalPadding;
        float adjustedHeight = padH + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int color = 0xFFFFFFFF;
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrixStack.pop();
    }

    private static void setGlowColor(GlProgram program, String name, ColorRGBA color) {
        var uniform = program.findUniform(name);
        if (uniform == null) return;
        uniform.set(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    /**
     * Накладывает едва заметные плавающие точки на хедер HUD-панели.
     * Цвет точек берётся из текущей темы клиента.
     */
    public static void drawHudDots(MatrixStack matrices, float x, float y, float width, float height,
                                   BorderRadius borderRadius, float alpha) {
        if (hudDotsProgram == null) return;

        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        ColorRGBA themeColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor();
        float time = (System.currentTimeMillis() % 1_000_000L) / 1000f;

        hudDotsProgram.use();
        hudDotsProgram.findUniform("Size").set(width, height);
        hudDotsProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        hudDotsProgram.findUniform("Smoothness").set(smoothness);
        hudDotsProgram.findUniform("Time").set(time);
        hudDotsProgram.findUniform("Density").set(7.0f);
        hudDotsProgram.findUniform("DotColor").set(
                themeColor.getRed() / 255f, themeColor.getGreen() / 255f,
                themeColor.getBlue() / 255f, alpha);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int color = 0xFFFFFFFF;
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    /**
     * Универсальный helper - рисует фон HUD согласно настройке Interface.
     * Режимы: "Сетка" / "Точки" / "Нет".
     */
    public static void drawHudBackground(MatrixStack matrices, float x, float y, float width, float height,
                                         BorderRadius borderRadius, float alpha) {
        String mode = endless.ere.client.modules.impl.render.Interface.INSTANCE.getHudBackground();
        if ("Сетка".equals(mode)) {
            drawHudGrid(matrices, x, y, width, height, borderRadius, 14f, 0.6f, alpha * 0.25f);
        } else if ("Точки".equals(mode)) {
            drawHudDots(matrices, x, y, width, height, borderRadius, alpha);
        }
    }

    /** Сетка-пиксели в цвете темы (для панели/окон). */
    public static void drawHudGrid(MatrixStack matrices, float x, float y, float width, float height,
                                   BorderRadius borderRadius, float cell, float lineWidth, float alpha) {        if (hudGridProgram == null) return;

        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        ColorRGBA themeColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor();

        hudGridProgram.use();
        hudGridProgram.findUniform("Size").set(width, height);
        hudGridProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        hudGridProgram.findUniform("Smoothness").set(smoothness);
        hudGridProgram.findUniform("Cell").set(cell);
        hudGridProgram.findUniform("LineWidth").set(lineWidth);
        hudGridProgram.findUniform("Time").set((System.currentTimeMillis() % 1_000_000L) / 1000f);
        hudGridProgram.findUniform("GridColor").set(
                themeColor.getRed() / 255f, themeColor.getGreen() / 255f,
                themeColor.getBlue() / 255f, alpha);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int color = 0xFFFFFFFF;
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).color(color);
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).color(color);
        BufferRenderer.drawWithGlobalProgram(builder.end());

        drawEnd();
        matrices.pop();
    }

    public void drawBlur(MatrixStack matrices, float x, float y, float width, float height, float blurRadius, BorderRadius borderRadius, ColorRGBA color) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();

        blurRadius /= 22.5f;

        if (blurRadius <= 0) return;

        blurProgram.setBlurRadius(2);
        roundedTextureProgram.use();
        RenderSystem.setShaderTexture(0, BlurProgram.getTexture());

        roundedTextureProgram.findUniform("Size").set(width, height);
        roundedTextureProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        roundedTextureProgram.findUniform("Smoothness").set(0.01f);

        drawSetup();

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        float u = x / screenWidth;
        float v = (screenHeight - y - height) / screenHeight;
        float texWidth = width / screenWidth;
        float texHeight = height / screenHeight;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        builder.vertex(matrix4f, x, y, 0.0F).texture(u, v + texHeight).color(color.getRGB());
        builder.vertex(matrix4f, x, y + height, 0.0F).texture(u, v).color(color.getRGB());
        builder.vertex(matrix4f, x + width, y + height, 0.0F).texture(u + texWidth, v).color(color.getRGB());
        builder.vertex(matrix4f, x + width, y, 0.0F).texture(u + texWidth, v + texHeight).color(color.getRGB());
        BufferRenderer.drawWithGlobalProgram(builder.end());
        drawEnd();

        // сбрасываем текстуру
        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();

    }

    public void drawImage(MatrixStack matrices, BufferBuilder builder, double x, double y, double z, double width, double height, ColorRGBA color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        builder.vertex(matrix, (float) x, (float) (y + height), (float) z).texture(0, 1).color(color.getRGB());
        builder.vertex(matrix, (float) (x + width), (float) (y + height), (float) z).texture(1, 1).color(color.getRGB());
        builder.vertex(matrix, (float) (x + width), (float) y, (float) z).texture(1, 0).color(color.getRGB());
        builder.vertex(matrix, (float) x, (float) y, (float) z).texture(0, 0).color(color.getRGB());
    }

    public void drawImage(MatrixStack matrices, Identifier identifier, double x, double y, double z, double width, double height, ColorRGBA color) {
        RenderSystem.setShaderTexture(0, identifier);
        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        builder.vertex(matrix, (float) x, (float) (y + height), (float) z).texture(0, 1).color(color.getRGB());
        builder.vertex(matrix, (float) (x + width), (float) (y + height), (float) z).texture(1, 1).color(color.getRGB());
        builder.vertex(matrix, (float) (x + width), (float) y, (float) z).texture(1, 0).color(color.getRGB());
        builder.vertex(matrix, (float) x, (float) y, (float) z).texture(0, 0).color(color.getRGB());

        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    public void drawPlayerHeadWithRoundedShader(MatrixStack matrices, Identifier skinTexture, float x, float y, float size, BorderRadius borderRadius, ColorRGBA color) {
        drawRoundedTextureWithUV(matrices, skinTexture, x, y, size, size, borderRadius, color,
                8.0f / 64.0f,   // u1 - левый край головы
                8.0f / 64.0f,   // v1 - верхний край головы
                16.0f / 64.0f,  // u2 - правый край головы
                16.0f / 64.0f   // v2 - нижний край головы
        );
    }

    private void drawPlayerHatLayerWithRoundedShader(MatrixStack matrices, Identifier skinTexture, float x, float y, float size, BorderRadius borderRadius, ColorRGBA color) {
        // Включаем блендинг для прозрачности hat layer
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawRoundedTextureWithUV(matrices, skinTexture, x, y, size, size, borderRadius, color,
                40.0f / 64.0f,  // u1 - левый край hat layer
                8.0f / 64.0f,   // v1 - верхний край hat layer
                48.0f / 64.0f,  // u2 - правый край hat layer
                16.0f / 64.0f   // v2 - нижний край hat layer
        );

        RenderSystem.disableBlend();
    }

    public void drawRoundedTextureWithUV(MatrixStack matrices, Identifier identifier, float x, float y, float width, float height, BorderRadius borderRadius, ColorRGBA color, float u1, float v1, float u2, float v2) {
        matrices.push();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        float smoothness = DEFAULT_SMOOTHNESS;

        roundedTextureProgram.use();
        RenderSystem.setShaderTexture(0, identifier);

        roundedTextureProgram.findUniform("Size").set(width, height);
        roundedTextureProgram.findUniform("Radius").set(
                borderRadius.topLeftRadius(),
                borderRadius.bottomLeftRadius(),
                borderRadius.topRightRadius(),
                borderRadius.bottomRightRadius()
        );
        roundedTextureProgram.findUniform("Smoothness").set(smoothness);

        drawSetup();

        float horizontalPadding = -smoothness / 2.0F + smoothness * 2.0F;
        float verticalPadding = smoothness / 2.0F + smoothness;
        float adjustedX = x - horizontalPadding / 2.0F;
        float adjustedY = y - verticalPadding / 2.0F;
        float adjustedWidth = width + horizontalPadding;
        float adjustedHeight = height + verticalPadding;

        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        // Используем переданный цвет вместо жестко закодированного белого
        builder.vertex(matrix4f, adjustedX, adjustedY, 0.0F).texture(u1, v1).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX, adjustedY + adjustedHeight, 0.0F).texture(u1, v2).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY + adjustedHeight, 0.0F).texture(u2, v2).color(color.getRGB());
        builder.vertex(matrix4f, adjustedX + adjustedWidth, adjustedY, 0.0F).texture(u2, v1).color(color.getRGB());

        BufferRenderer.drawWithGlobalProgram(builder.end());
        drawEnd();

        RenderSystem.setShaderTexture(0, 0);
        matrices.pop();
    }


    public void drawSetup() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    public void drawEnd() {
        RenderSystem.disableBlend();
    }

    record HeadUV(float u1, float v1, float uSize, float vSize) {
    }
}