package dev.endless.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.endless.module.settings.SliderSetting;
import dev.endless.ui.component.Component;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.math.Animation;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SliderComponent extends Component {
    private final SliderSetting setting;
    private boolean drag;
    private final Animation sliderAnimation = new Animation(Easing.QUINTIC_OUT, 100);

    public SliderComponent(SliderSetting setting) {
        this.setting = setting;
    }

    private double round(double num, double increment) {
        var v = (double) Math.round(num / increment) * increment;
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatNumber(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.min(getAlphaAnimSetting().getValue(), 1);
        int alphaInt = (int) (255 * alpha);

        String numberText = formatNumber(setting.getValue());
        float trackWidth = width - 14f;

        DrawUtil.drawRound(x + 1.5f, y + 0.5f, width - 3f, 15f, 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorField(), (int) (110 * alpha)));

        sliderAnimation.run((float) (trackWidth * (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin())));

        // Название слайдера - используем colorText
        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + 4.5f, y + 3f, 
                ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt), 6.5f, 0.6f, 1.0f, trackWidth);

        // Значение слайдера - используем colorInactiveText
        DrawUtil.drawText(Fonts.SFREGULAR.get(), numberText, x + width - 4.5f - Fonts.SFREGULAR.get().getWidth(numberText, 6.5f), y + 1f, 
                ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), alphaInt), 6.5f);

        // Окно слайдера (фон трека) - используем colorSliderWindow
        float trackY = y + 12.5f;
        float trackH = 3.5f;
        float trackX = x + 5f;
        DrawUtil.drawRound(trackX, trackY, trackWidth, trackH, trackH / 2f,
                ColorProvider.setAlpha(ColorProvider.getColorSliderWindow(), (int) (180 * alpha)));

        // Заполненная часть - используем colorSlider
        float fillWidth = MathHelper.clamp(sliderAnimation.getValue(), 0, trackWidth);
        int sliderColor = ColorProvider.setAlpha(ColorProvider.getColorSlider(), alphaInt);
        DrawUtil.drawRound(trackX, trackY, fillWidth, trackH, trackH / 2f, sliderColor);

        // Круг слайдера - используем colorSliderCircle
        float circleX = trackX + fillWidth;
        float knob = 6f;
        DrawUtil.drawRound(circleX - knob / 2f, trackY + (trackH - knob) / 2f, knob, knob, knob / 2f,
                ColorProvider.rgba(255, 255, 255, alphaInt));

        if (HoverUtil.isHovered(mouseX, mouseY, trackX, y + 8f, trackWidth, 8f)) {
            CursorManager.requestHand();
        }

        if (drag) {
            double val = (mouseX - trackX) / trackWidth * (setting.getMax() - setting.getMin()) + setting.getMin();
            setting.setValue((float) MathHelper.clamp(round(val, setting.getStep()), setting.getMin(), setting.getMax()));
        }

        setHeight(16);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float trackWidth = width - 14f;
        float trackX = x + 5f;
        if (HoverUtil.isHovered(mouseX, mouseY, trackX, y + 8f, trackWidth, 8f) && button == 0) {
            drag = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        drag = false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) drag = false;
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
