package dev.endless.ui.component;

import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

public class SearchField {
    private float x, y, width, height;
    public String text = "";
    private boolean isFocused;
    private final String placeholder;

    private static final float ICON_BOX_W = 17f;
    private static final float RADIUS = 4f;

    public SearchField(String placeholder) {
        this.placeholder = placeholder;
    }

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y, width, height)) {
            CursorManager.requestIBeam();
        }

        DrawUtil.drawRoundBlur(x, y, width, height, RADIUS, ColorProvider.rgba(75, 75, 75, 255), 20f);
        DrawUtil.drawRound(x, y, width, height, RADIUS, ColorProvider.setAlpha(ColorProvider.getColorWindowBg(), 30));

        DrawUtil.drawRound(x, y, ICON_BOX_W, height, RADIUS, ColorProvider.setAlpha(ColorProvider.getColorHeaderBg(), 80));

        float iconW = Fonts.ICONS_MINCED.get().getWidth("l", 10f);
        float iconX = x + (ICON_BOX_W - iconW) / 2f + 1f;
        float iconY = y + (height / 2f) - 4.5f;
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), "l", iconX, iconY,
                ColorProvider.setAlpha(ColorProvider.getColorIcons(), 200), 10f);

        String textToDraw = text.isEmpty() && !isFocused ? placeholder : text;
        String cursor = isFocused && System.currentTimeMillis() % 1000 > 500 ? "|" : "";
        int textColor = text.isEmpty() && !isFocused
                ? ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), 255)
                : ColorProvider.setAlpha(ColorProvider.getColorText(), 255);

        DrawUtil.drawText(Fonts.SFREGULAR.get(), textToDraw + cursor,
                x + ICON_BOX_W + 4f, y + (height / 2f) - 3.5f, textColor, 7f);
    }

    public void charTyped(char codePoint, int modifiers) {
        if (isFocused && text.length() < 15) {
            text += codePoint;
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !text.isEmpty()) {
                text = text.substring(0, text.length() - 1);
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isFocused = false;
            }
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        isFocused = HoverUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }
}
