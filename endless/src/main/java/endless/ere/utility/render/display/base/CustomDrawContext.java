package endless.ere.utility.render.display.base;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import endless.ere.base.font.Font;
import endless.ere.base.font.MsdfRenderer;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.utility.mixin.accessors.DrawContextAccessor;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

import java.util.Objects;

public class CustomDrawContext extends DrawContext implements IMinecraft {


    public CustomDrawContext(VertexConsumerProvider.Immediate vertexConsumerProvider) {
        super(mc, vertexConsumerProvider);

    }

    public CustomDrawContext(DrawContext originalContext) {
        super(mc, ((DrawContextAccessor) originalContext).getVertexConsumers());

    }

    public static CustomDrawContext of(DrawContext originalContext) {
        return new CustomDrawContext(originalContext);
    }

    public void drawText(Font font, String text, float x, float y, ColorRGBA color) {
        MsdfRenderer.renderText(font.getFont(), text, font.getSize(), color.getRGB(), getMatrices().peek().getPositionMatrix(), x, y, 0);
    }

    public void drawText(Font font, String text, float x, float y, Gradient color) {
        MsdfRenderer.renderText(font.getFont(), text, font.getSize(), color, getMatrices().peek().getPositionMatrix(), x, y, 0);
    }

    public void drawText(Font font, Text text, float x, float y) {
        MsdfRenderer.renderText(font.getFont(), text, font.getSize(), getMatrices().peek().getPositionMatrix(), x, y, 0);
    }

    public void drawSquircle(float x, float y, float width, float height, float squirt, BorderRadius borderRadius, ColorRGBA color) {
        DrawUtil.drawSquircle(this.getMatrices(), x, y, width, height, squirt, borderRadius, color);
    }

    public void drawRoundedRect(float x, float y, float width, float height, BorderRadius borderRadius, ColorRGBA color) {
        DrawUtil.drawRoundedRect(this.getMatrices(), x, y, width, height, borderRadius, color);
    }

    public void drawRoundedRect(float x, float y, float width, float height, BorderRadius borderRadius, Gradient gradient) {
        DrawUtil.drawRoundedRect(this.getMatrices(), x, y, width, height, borderRadius, gradient);
    }

    public void drawRect(float x, float y, float width, float height, ColorRGBA color) {
        DrawUtil.drawRect(this.getMatrices(), x, y, width, height, color);
    }

    public int drawTextWithBackground(TextRenderer textRenderer, Text text, int x, int y, int width,BorderRadius borderRadius, ColorRGBA textColor, ColorRGBA backgroundColor) {


        int var10001 = x - 3;
        int var10002 = y - 2;
        int var10003 = width + 6;
        Objects.requireNonNull(textRenderer);
        this.drawRoundedRect(var10001, var10002, var10003,  9 + 4,borderRadius,backgroundColor);


        return this.drawText(textRenderer, text, x, y, textColor.getRGB(), true);
    }

    public void drawSprite(CustomSprite sprite, float x, float y, float width, float height, ColorRGBA textureColor) {
        DrawUtil.drawSprite(this.getMatrices(), sprite, x, y, width, height, textureColor);
    }

    public void drawRoundedCorner(float x, float y, float width, float height, float borderThikenes, float widthCorner, ColorRGBA color, BorderRadius radius) {

        width = Math.round(width);
        height = Math.round(height);
        this.enableScissor((int) Math.ceil(x - 10), (int) (y - 10), (int) (x + widthCorner), (int) (y + widthCorner));
        drawRoundedBorder(x, y, width, height, borderThikenes, radius, color);
        this.disableScissor();

        this.enableScissor((int) (x + width - widthCorner), (int) (y - 10), (int) (x + width + 10), (int) (y + widthCorner));
        drawRoundedBorder(x, y, width, height, borderThikenes, radius, color);
        this.disableScissor();

        this.enableScissor((int) (x - 10), (int) (y + height - widthCorner), (int) (x + widthCorner), (int) (y + height + 10));
        drawRoundedBorder(x, y, width, height, borderThikenes, radius, color);
        this.disableScissor();

        this.enableScissor((int) (x + width - widthCorner), (int) (y + height - widthCorner), (int) (x + width + 10), (int) (y + height + 10));
        drawRoundedBorder(x, y, width, height, borderThikenes, radius, color);
        this.disableScissor();
    }

    public void drawRoundedBorder(float x, float y, float width, float height, float borderThickness, BorderRadius borderRadius, ColorRGBA borderColor) {
        DrawUtil.drawRoundedBorder(this.getMatrices(), x, y, width, height, borderThickness, borderRadius, borderColor);
    }

    public void drawTexture(Identifier identifier, float x, float y, float width, float height, ColorRGBA textureColor) {
        DrawUtil.drawTexture(this.getMatrices(), identifier, x, y, width, height, textureColor);
    }

    public void pushMatrix() {
        getMatrices().push();
    }

    public void popMatrix() {
        getMatrices().pop();
    }
}
