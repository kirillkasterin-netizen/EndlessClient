package endless.ere.client.screens.panelgui.components.core;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import endless.ere.Endless;
import endless.ere.base.theme.Theme;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.base.font.Fonts;
import endless.ere.utility.render.display.base.color.ColorRGBA;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author chuppachups1337
 * @since 04/10/2025
 */

public class NumberComponent extends SettingComponent<NumberSetting> {

    private boolean dragging = false;
    private float targetValue;
    private final float ANIMATION_SPEED = 0.8f;
    private final float SLIDER_HEIGHT = 3f;
    private final float KNOB_SIZE = 7f;

    public NumberComponent(NumberSetting setting) {
        super(setting);
        this.height = 22f;
        this.targetValue = setting.getCurrent();
    }

    @Override
    public void draw(DrawContext g, float mouseX, float mouseY, float partialTicks, float parentAlpha) {

        float currentValue = setting.getCurrent();
        float interpolatedValue = MathHelper.lerp(ANIMATION_SPEED * partialTicks, currentValue, targetValue);
        setting.setCurrent(interpolatedValue);
        float currentValueForRender = setting.getCurrent();

        int baseAlpha = (int) (parentAlpha * 255);
        int sliderBgAlpha = (int) (parentAlpha * 55);
        int textAlpha = (int) (parentAlpha * 255);
        int valueAlpha = (int) (parentAlpha * 200);

        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        ColorRGBA accent = theme.getColor();

        float sliderX = x + 18;
        float sliderWidth = width - 36;
        float sliderY = y + 13.5f;
        final float textHeight = 14f;

        String valueStr = new BigDecimal(currentValueForRender).setScale(2, RoundingMode.HALF_UP).toString();

        float valueWidth = Fonts.MEDIUM.getFont((float) textHeight / 2).getStringWidth(valueStr);
        float valueX = x + width - valueWidth - 18;
        float textY = y + 2f;

        int nameColor = new Color(255, 255, 255, textAlpha).getRGB();
        int valueColor = new Color(210, 210, 210, valueAlpha).getRGB();
        int valuePillColor = new Color(40, 28, 40, (int) (parentAlpha * 170)).getRGB();

        final float padding = 3f;
        RenderUtil.drawRoundedRect(g, valueX - padding, textY - padding + 1,
                valueWidth + 2 * padding, textHeight - 4, 1.5f, valuePillColor);

        Fonts.MEDIUM.getFont((float) textHeight / 2).drawString(g, valueStr,
                valueX, textY, valueColor);

        // Имя клипуется до начала pill значения
        g.enableScissor((int) (x + 18f), (int) y, (int) (valueX - padding - 4), (int) (y + height));
        Fonts.MEDIUM.getFont((float) textHeight / 2).drawString(g, setting.getName(),
                x + 18f, textY, nameColor);
        g.disableScissor();

        // ── Track ──────────────────────────────────────────────────────────
        int trackBg = new Color(255, 255, 255, sliderBgAlpha).getRGB();
        RenderUtil.drawRoundedRect(g, sliderX, sliderY, sliderWidth, SLIDER_HEIGHT,
                0.6f, trackBg);

        float range = setting.getMax() - setting.getMin();
        float percentage = MathHelper.clamp((currentValueForRender - setting.getMin()) / range, 0f, 1f);
        float filledWidth = sliderWidth * percentage;

        // ── Filled accent ──────────────────────────────────────────────────
        int filledColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                (int) (parentAlpha * 235)).getRGB();
        RenderUtil.drawRoundedRect(g, sliderX, sliderY, filledWidth, SLIDER_HEIGHT,
                0.6f, filledColor);

        // ── Knob (квадратный с легким закруглением) ────────────────────────
        float knobX = sliderX + filledWidth - KNOB_SIZE / 2f;
        float knobY = sliderY + SLIDER_HEIGHT / 2f - KNOB_SIZE / 2f;

        int ringColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                (int) (parentAlpha * 230)).getRGB();
        RenderUtil.drawRoundedRect(g, knobX, knobY, KNOB_SIZE, KNOB_SIZE,
                1.2f, ringColor);

        // Inner core (white)
        float core = KNOB_SIZE - 2.5f;
        int coreColor = new Color(245, 245, 245, baseAlpha).getRGB();
        RenderUtil.drawRoundedRect(g, knobX + (KNOB_SIZE - core) / 2f,
                knobY + (KNOB_SIZE - core) / 2f, core, core, 0.8f, coreColor);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        float hitAreaX = x + 18 - KNOB_SIZE;
        float hitAreaWidth = width - 36 + 2 * KNOB_SIZE;
        float hitAreaY = y;
        float hitAreaHeight = height;

        if (button == 0 && isHovered(mouseX, mouseY, hitAreaX, hitAreaY, hitAreaWidth, hitAreaHeight)) {
            dragging = true;
            updateValue(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            updateValue((float) mouseX);
        }
    }

    @Override
    public void mouseReleased(float mouseX, float mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    private void updateValue(float mouseX) {
        float sliderX = x + 18;
        float sliderWidth = width - 36;
        float min = setting.getMin();
        float max = setting.getMax();
        float percentage = MathHelper.clamp((mouseX - sliderX) / sliderWidth, 0f, 1f);
        float calculatedValue = min + (max - min) * percentage;
        float increment = setting.getIncrement();

        calculatedValue = Math.round(calculatedValue / increment) * increment;
        calculatedValue = MathHelper.clamp(calculatedValue, min, max);

        targetValue = calculatedValue;
    }
}