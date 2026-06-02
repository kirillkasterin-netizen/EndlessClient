package endless.ere.client.screens.panelgui.components.core;

import net.minecraft.client.gui.DrawContext;
import endless.ere.client.modules.api.setting.impl.MultiBooleanSetting;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.base.font.Fonts;

import java.awt.Color;
import java.util.List;

/**
 * @author chuppachups1337
 * @since 04/10/2025
 */
public class MultiBooleanComponent extends SettingComponent<MultiBooleanSetting> {

    private static final float PADDING_X = 18f;
    private static final float PADDING_Y = 1f;
    private static final float TEXT_HEIGHT = 10f;
    private static final float MODE_SPACING_X = 9f;
    private static final float FONT_SIZE = 14f;
    private static final float MODE_FONT_SIZE = 13f;
    private static final float BACKGROUND_PADDING = 4f;

    public MultiBooleanComponent(MultiBooleanSetting setting) {
        super(setting);
        this.height = 15f + (setting.getBooleanSettings().size() * 12f);
    }

    @Override
    public float getHeight() {
        int modeLines = calculateModeLines();
        float totalHeight = PADDING_Y + TEXT_HEIGHT + PADDING_Y;
        totalHeight += modeLines * TEXT_HEIGHT;
        totalHeight += (modeLines > 0 ? (modeLines - 1) * PADDING_Y : 0);
        totalHeight += PADDING_Y;
        return totalHeight;
    }

    private int calculateModeLines() {
        if (width <= 0) return 1;

        int lines = 1;
        float currentX = PADDING_X;
        List<MultiBooleanSetting.Value> modes = setting.getBooleanSettings();

        for (MultiBooleanSetting.Value modeVal : modes) {
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
    public void draw(DrawContext g, float mouseX, float mouseY, float partialTicks, float parentAlpha) {
        int headerTextAlpha = (int) (parentAlpha * 255);
        int modeTextAlpha = (int) (parentAlpha * 180);

        String headerText = setting.getName();
        int headerColor = new Color(255, 255, 255, headerTextAlpha).getRGB();
        int counterColor = new Color(180, 180, 180, headerTextAlpha).getRGB();
        Fonts.MEDIUM.getFont(FONT_SIZE / 2).drawString(g, headerText, x + PADDING_X, y + PADDING_Y + 2f, headerColor);
        // Счётчик "X из Y" прижат к правому краю
        String counter = setting.getSelectedNames().size() + "/" + setting.getBooleanSettings().size();
        float counterW = Fonts.MEDIUM.getFont(FONT_SIZE / 2).getStringWidth(counter);
        Fonts.MEDIUM.getFont(FONT_SIZE / 2).drawString(g, counter,
                x + width - PADDING_X - counterW, y + PADDING_Y + 2f, counterColor);
        List<MultiBooleanSetting.Value> modes = setting.getBooleanSettings();
        float currentX = x + PADDING_X;
        float currentY = y + TEXT_HEIGHT + PADDING_Y * 2;

        for (MultiBooleanSetting.Value modeVal : modes) {
            String mode = modeVal.getName();
            float modeWidth = Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).getStringWidth(mode);
            float itemWidth = modeWidth + MODE_SPACING_X;

            if (currentX + itemWidth > x + width - PADDING_X) {
                currentX = x + PADDING_X;
                currentY += TEXT_HEIGHT + PADDING_Y;
            }

            int textColor = modeVal.isEnabled()
                    ? new Color(255, 255, 255, modeTextAlpha).getRGB()
                    : new Color(180, 180, 180, modeTextAlpha).getRGB();

            if (modeVal.isEnabled()) {
                int backgroundColor = new Color(90, 36, 60, (int) (parentAlpha * 255)).getRGB();
                RenderUtil.drawRoundedRect(g, currentX - BACKGROUND_PADDING / 2, currentY - PADDING_Y + 2, modeWidth + BACKGROUND_PADDING + 2  , TEXT_HEIGHT, 2, backgroundColor);
            }

            Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).drawString(g, mode, currentX, currentY + 4f, textColor);
            currentX += itemWidth;
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (isHovered(mouseX, mouseY, x, y, width, getHeight())) {

            float modesYStart = y + TEXT_HEIGHT + PADDING_Y;

            if (mouseY < modesYStart) {
                return false;
            } else {
                List<MultiBooleanSetting.Value> modes = setting.getBooleanSettings();
                float currentX = x + PADDING_X;
                float currentY = modesYStart + PADDING_Y;

                for (MultiBooleanSetting.Value modeVal : modes) {
                    String mode = modeVal.getName();
                    float modeWidth = Fonts.MEDIUM.getFont(MODE_FONT_SIZE / 2).getStringWidth(mode);
                    float itemWidth = modeWidth + MODE_SPACING_X;

                    if (currentX + itemWidth > x + width - PADDING_X) {
                        currentX = x + PADDING_X;
                        currentY += TEXT_HEIGHT + PADDING_Y;
                    }

                    if (isHovered(mouseX, mouseY, currentX, currentY - PADDING_Y + 2, modeWidth + BACKGROUND_PADDING, TEXT_HEIGHT)) {
                        modeVal.toggle();
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
