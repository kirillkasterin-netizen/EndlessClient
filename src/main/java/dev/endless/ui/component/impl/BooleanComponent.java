package dev.endless.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.ui.component.Component;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

public class BooleanComponent extends Component {
    public final BooleanSetting setting; // Сделал публичным для доступа
    private boolean binding;

    public BooleanComponent(BooleanSetting setting) {
        this.setting = setting;
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.max(Math.min(getAlphaAnimSetting().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        DrawUtil.drawRound(x + 1.5f, y + 0.5f, width - 3f, 15f, 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorField(), (int) (110 * alpha)));

        float toggleW = 15f;
        float toggleH = 8f;
        float toggleX = x + width - toggleW - 2.5f;
        float toggleY = y + 4f;

        if (HoverUtil.isHovered(mouseX, mouseY, toggleX, toggleY, toggleW, toggleH)) {
            CursorManager.requestHand();
        }

        DrawUtil.drawText(Fonts.SFREGULAR.get(), binding ? "Binding..." : setting.getName(),
                x + 4.5f, y + 5f, ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt), 6.5f, 0.4f, 1f, width - toggleW - 8f);

        float anim = (float) setting.getAnimation().getValue();

        int inactiveIndicator = ColorProvider.setAlpha(ColorProvider.getColorInactiveIndicator(), alphaInt);
        int activeIndicator = ColorProvider.setAlpha(ColorProvider.getColorIndicator(), alphaInt);
        int trackColor = ColorProvider.interpolateColor(inactiveIndicator, activeIndicator, anim);
        DrawUtil.drawRound(toggleX, toggleY, toggleW, toggleH, 2.25f, trackColor);

        float knobSize = toggleH - 1f;
        float knobMinX = toggleX + 0.5f;
        float knobMaxX = toggleX + toggleW - knobSize - 0.5f;
        float knobX = knobMinX + (knobMaxX - knobMinX) * anim;
        float knobY = toggleY + 0.5f;
        // Круг слайдера используем для кнопки тогла
        DrawUtil.drawRound(knobX, knobY, knobSize, knobSize, 2.25f, ColorProvider.rgba(255, 255, 255, alphaInt));

        // Отображение бинда слева от ползунка
        if (setting.getKey() != -1 || binding) {
            String keyName = setting.getKey() != -1 ? dev.endless.util.keyboard.KeyStorage.getKey(setting.getKey()) : "?";
            if (keyName != null && !keyName.isEmpty()) {
                String displayKey = keyName.length() > 3 ? keyName.substring(0, 1) : keyName;
                
                float bindTextSize = 5f;
                float bindWidth = Fonts.SFREGULAR.get().getWidth(displayKey, bindTextSize);
                float bindX = toggleX - bindWidth - 6;
                float bindY = y + 5.5f;
                
                int bindColor = binding
                    ? ColorProvider.rgba(255, 200, 100, alphaInt) // Оранжевый при биндинге
                    : ColorProvider.setAlpha(ColorProvider.rgba(150, 150, 160, 255), alphaInt); // Серый обычно
                
                DrawUtil.drawText(Fonts.SFREGULAR.get(), "[" + displayKey + "]", bindX - 2, bindY, bindColor, bindTextSize);
            }
        }

        setHeight(16);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        float toggleW = 15f;
        float toggleH = 8f;
        float toggleX = x + width - toggleW - 2.5f;
        float toggleY = y + 4f;

        if (HoverUtil.isHovered(mouseX, mouseY, toggleX, toggleY, toggleW, toggleH)) {
            if (button == 0) setting.setValue(!setting.getValue());
            if (button == 2 && binding) {
                binding = false;
                return;
            }
            if (binding) {
                setting.setKey(button);
                binding = false;
            }
            if (button == 2) binding = true;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                binding = false;
                return;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                setting.setKey(-1);
                return;
            }
            setting.setKey(keyCode);
            binding = false;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
