package endless.ere.client.screens.panelgui.components.core;

import net.minecraft.client.gui.DrawContext;

import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.base.font.Fonts;

import java.awt.Color;
import java.util.List;

/**
 * @author chuppachups1337
 * @since 04/10/2025
 */

public class ModeComponent extends SettingComponent<ModeSetting> {

    private static final float TEXT_HEIGHT = 10f;
    private static final float PADDING_X = 18f;
    private static final float PADDING_Y = 3f;
    private static final float MODE_SPACING_X = 9f;
    private static final float FONT_SIZE = 14f;
    private static final float MODE_FONT_SIZE = 13f;
    private static final float BACKGROUND_PADDING = 4f;

    public ModeComponent(ModeSetting setting) {
        super(setting);
    }

    private int calculateModeLines() {
        if (width <= 0) return 1;

        int lines = 1;
        float currentX = PADDING_X;
        List<ModeSetting.Value> modes = setting.getValues();

        for (ModeSetting.Value modeVal : modes) {
            String mode = modeVal.getName();
            float modeWidth = Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).getStringWidth(mode);
            float itemWidth = modeWidth + MODE_SPACING_X;

            if (currentX + itemWidth > width - PADDING_X) {
                lines++;
                currentX = PADDING_X + itemWidth;
            } else {
                currentX += itemWidth;
            }
        }
        return lines;
    }

    @Override
    public float getHeight() {

        int modeLines = calculateModeLines();
        float totalHeight = PADDING_Y;
        totalHeight += TEXT_HEIGHT;
        totalHeight += PADDING_Y;
        totalHeight += modeLines * TEXT_HEIGHT;
        totalHeight += (modeLines > 0 ? (modeLines - 1) * PADDING_Y : 0);
        totalHeight += PADDING_Y;

        return totalHeight;
    }

    @Override
    public void draw(DrawContext g, float mouseX, float mouseY, float partialTicks, float parentAlpha) {

        int headerTextAlpha = (int) (parentAlpha * 255);
        int modeTextAlpha = (int) (parentAlpha * 180);
        int selectedBgAlpha = (int) (parentAlpha * 255);
        int headerColor = new Color(255, 255, 255, headerTextAlpha).getRGB();

        Fonts.MEDIUM.getFont(FONT_SIZE / 2).drawString(g, setting.getName(), x + PADDING_X, y + PADDING_Y + 2f, headerColor);

        List<ModeSetting.Value> modes = setting.getValues();
        String currentMode = setting.get();

        float currentX = x + PADDING_X;
        float currentY = y + TEXT_HEIGHT + PADDING_Y * 2;

        for (ModeSetting.Value modeVal : modes) {
            String mode = modeVal.getName();
            float modeWidth = Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).getStringWidth(mode);
            float itemWidth = modeWidth + MODE_SPACING_X;

            if (currentX + itemWidth > x + width - PADDING_X) {
                currentX = x + PADDING_X;
                currentY += TEXT_HEIGHT + PADDING_Y;
            }

            int textColor = new Color(180, 180, 180, modeTextAlpha).getRGB(); // Цвет по умолчанию (серый)

            if (mode.equals(currentMode)) {
                textColor = new Color(255, 255, 255, modeTextAlpha).getRGB();

                int backgroundColor = new Color(90, 36, 60, selectedBgAlpha).getRGB();

                float rectX = currentX - BACKGROUND_PADDING / 2;
                float rectY = currentY - PADDING_Y + 2;
                float rectW = modeWidth + BACKGROUND_PADDING;
                float rectH = TEXT_HEIGHT;

                RenderUtil.drawRoundedRect(g,  rectX,  rectY,  rectW,  rectH, 2, backgroundColor);
            }

            Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).drawString(g, mode, currentX, currentY + 2f, textColor);
            currentX += itemWidth;
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (isHovered(mouseX, mouseY, x, y, width, getHeight())) {

            float modesYStart = y + TEXT_HEIGHT + PADDING_Y;

            if (mouseY < modesYStart) {
                if (button == 0) {
                    List<ModeSetting.Value> vals = setting.getValues();
                    if (!vals.isEmpty()) {
                        int index = vals.indexOf(setting.getValue());
                        setting.setValue(vals.get((index + 1) % vals.size()));
                    }
                    return true;
                }
            }
            else {
                List<ModeSetting.Value> modes = setting.getValues();
                float currentX = x + PADDING_X;
                float currentY = modesYStart + PADDING_Y;

                for (ModeSetting.Value modeVal : modes) {
                    String mode = modeVal.getName();
                    float modeWidth = Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).getStringWidth(mode);
                    float itemWidth = modeWidth + MODE_SPACING_X;

                    if (currentX + itemWidth > x + width - PADDING_X) {
                        currentX = x + PADDING_X;
                        currentY += TEXT_HEIGHT + PADDING_Y;
                    }

                    if (isHovered(mouseX, mouseY, currentX, currentY - PADDING_Y + 2, modeWidth + BACKGROUND_PADDING, TEXT_HEIGHT)) {
                        setting.set(mode);
                        return true;
                    }

                    currentX += itemWidth;
                }
            }
        }
        return false;
    }

    public boolean isHovered(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}