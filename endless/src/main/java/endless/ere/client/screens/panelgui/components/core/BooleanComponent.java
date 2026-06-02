package endless.ere.client.screens.panelgui.components.core;

import net.minecraft.client.gui.DrawContext;
import endless.ere.base.font.Fonts;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.screens.panelgui.RenderUtil;
import java.awt.Color;

/**
 * @author chuppachups1337
 * @since 04/10/2025
 */

public class BooleanComponent extends SettingComponent<BooleanSetting> {

    private static final int MAX_TEXT_LENGTH = 22;
    private static final float SCROLL_SPEED = 0.02f;
    private static final float SCROLL_BACK_MULTIPLIER = 1.0f;
    private static final long SCROLL_DELAY = 500;

    private float textShift = 0f;
    private Long hoverStartTime = null;
    private float maxTextShift = 0f;

    public BooleanComponent(BooleanSetting setting) {
        super(setting);
    }

    @Override
    public void draw(DrawContext g, float mouseX, float mouseY, float partialTicks, float parentAlpha) {
        float checkboxSize = 8f;
        float checkboxX = x + width - 20f;
        float checkboxY = y + (height - checkboxSize) / 2f;

        int textAlpha = (int) (parentAlpha * 255);
        int borderAlpha = (int) (parentAlpha * 45);
        int iconOnAlpha = (int) (parentAlpha * 255);
        int iconOffAlpha = (int) (parentAlpha * 255);
        int textColor = new Color(255, 255, 255, textAlpha).getRGB();
        int borderColor = new Color(255, 255, 255, borderAlpha).getRGB();
        int iconOnColor = new Color(0, 255, 0, iconOnAlpha).getRGB();
        int iconOffColor = new Color(255, 0, 0, iconOffAlpha).getRGB();

        String name = setting.getName();
        float startX = x + 18f;
        float nameWidth = Fonts.MEDIUM.getFont(7).getStringWidth(name);
        float maxVisibleWidth = width - 38f - checkboxSize;

        boolean isNameTooLong = name.length() > MAX_TEXT_LENGTH;
        boolean isHovered = isHovered(mouseX, mouseY, x, y, width, height);

        if (isNameTooLong) {
            maxTextShift = Math.max(0, nameWidth - maxVisibleWidth);
        } else {
            maxTextShift = 0;
        }

        if (isHovered && isNameTooLong && maxTextShift > 0) {
            if (hoverStartTime == null) {
                hoverStartTime = System.currentTimeMillis();
                textShift = 0f;
            } else {
                long elapsedTime = System.currentTimeMillis() - hoverStartTime;

                if (elapsedTime > SCROLL_DELAY) {
                    float timeForScroll = elapsedTime - SCROLL_DELAY;
                    float timeForward = maxTextShift / SCROLL_SPEED;
                    float timeBack = maxTextShift / (SCROLL_SPEED * SCROLL_BACK_MULTIPLIER);
                    float totalCycleTime = timeForward + timeBack;
                    float timeInCycle = timeForScroll % totalCycleTime;

                    if (timeInCycle <= timeForward) {
                        textShift = timeInCycle * SCROLL_SPEED;
                    } else {
                        float timeInBackPhase = timeInCycle - timeForward;
                        float shiftFromMax = timeInBackPhase * (SCROLL_SPEED * SCROLL_BACK_MULTIPLIER);
                        textShift = maxTextShift - shiftFromMax;
                    }
                    textShift = Math.max(0f, Math.min(maxTextShift, textShift));
                }
            }
        } else {
            hoverStartTime = null;
            textShift = 0f;
        }

        g.enableScissor((int) startX, (int) y, (int) (x + width - 20f - checkboxSize - 5), (int) (y + height));
        Fonts.MEDIUM.getFont(7).drawString(g, name, startX - textShift, y + 5f, textColor);
        g.disableScissor();

        RenderUtil.drawRoundedRectOutline(g, checkboxX, checkboxY - 1, checkboxSize +2, checkboxSize +2, 3f,0.1f, borderColor, 1);

        if (setting.isEnabled()) {
            Fonts.ICONS.getFont(6).drawString(g, "E" ,checkboxX +1.5f, checkboxY +0.3f , iconOnColor);
        } else {
            Fonts.ICONS.getFont(5).drawString(g, "B" ,checkboxX + 2.1f, checkboxY+1f, iconOffColor);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (isHovered(mouseX, mouseY, x, y, width, height) && button == 0) {
            setting.toggle();
            hoverStartTime = null;
            textShift = 0f;
            return true;
        }
        return false;
    }
}