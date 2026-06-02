package endless.ere.base.font;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.text.Text;

@Getter
@AllArgsConstructor
public class Font {

    private MsdfFont font;
    private float size;

    public float height() {
        // Так называемый рокстарвский MAGIC VALUE
        return size * 0.7F;
    }

    public float width(String text) {
        return font.getWidth(text, size);
    }

    public float width(Text text) {
        return font.getTextWidth(text, size);
    }

    public float getStringWidth(String text) {
        return width(text);
    }

    public float getHeight() {
        return height();
    }

    public void drawString(net.minecraft.client.gui.DrawContext g, String text, float x, float y, int color) {
        MsdfRenderer.renderText(font, text, size, color, g.getMatrices().peek().getPositionMatrix(), x, y, 0);
    }

    public void drawCenteredString(net.minecraft.client.gui.DrawContext g, String text, float x, float y, int color) {
        float textWidth = width(text);
        MsdfRenderer.renderText(font, text, size, color, g.getMatrices().peek().getPositionMatrix(), x - textWidth / 2f, y, 0);
    }

}