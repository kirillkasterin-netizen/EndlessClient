package dev.endless.ui.component.impl;

import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;
import dev.endless.module.settings.BindSetting;
import dev.endless.ui.component.Component;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.keyboard.KeyStorage;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

public class BindComponent extends Component {
    private final BindSetting setting;
    private boolean binding;

    private static final float SETTING_HEIGHT = 14f;
    private static final float BIND_H = 10f;
    private static final float PADDING = 4.5f;
    private static final float RADIUS = 1f;

    public BindComponent(BindSetting setting) {
        this.setting = setting;
    }

    private String getKeyText() {
        if (binding) return "...";
        return (setting.getValue() == -1) ? "None" : KeyStorage.getKey(setting.getValue());
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        float alpha = Math.max(Math.min(getAlphaAnimSetting().getValue(), 1), 0);
        int alphaInt = (int) (255 * alpha);

        if (alpha < 0.02f) return;

        DrawUtil.drawRound(x + 1.5f, y + 0.5f, width - 3f, SETTING_HEIGHT, 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorField(), (int) (110 * alpha)));

        String keyText = getKeyText();
        float keyTextW = Fonts.SFREGULAR.get().getWidth(keyText, 6.5f);
        float bindBoxW = Math.max(16f, keyTextW + 8f);
        float bindX = x + width - bindBoxW - PADDING;
        float bindY = y + (SETTING_HEIGHT - BIND_H) / 2f;

        if (HoverUtil.isHovered(mouseX, mouseY, bindX, bindY, bindBoxW, BIND_H))
            CursorManager.requestHand();

        DrawUtil.drawText(Fonts.SFREGULAR.get(), setting.getName(), x + PADDING, y + 3.5f,
                ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt), 6.5f);

        boolean hasBind = setting.getValue() != -1;
        int bgColor;
        if (binding) {
            bgColor = ColorProvider.setAlpha(ColorProvider.getColorButton(), (int)(80 * alpha));
        } else if (hasBind) {
            bgColor = ColorProvider.setAlpha(ColorProvider.getColorButton(), (int)(80 * alpha));
        } else {
            bgColor = ColorProvider.setAlpha(ColorProvider.getColorInactiveButton(), (int)(80 * alpha));
        }
        DrawUtil.drawRound(bindX, bindY, bindBoxW, BIND_H, 4f, bgColor);

        int textColor = (binding || hasBind)
                ? ColorProvider.setAlpha(ColorProvider.getColorText(), alphaInt)
                : ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), alphaInt);
        float keyX = bindX + (bindBoxW - keyTextW) / 2f;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), keyText, keyX, bindY + 1.25f, textColor, 6.5f);

        setHeight(SETTING_HEIGHT);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        String keyText = getKeyText();
        float keyTextW = Fonts.SFREGULAR.get().getWidth(keyText, 6.5f);
        float bindBoxW = Math.max(16f, keyTextW + 8f);
        float bindX = x + width - bindBoxW - PADDING;
        float bindY = y + (SETTING_HEIGHT - BIND_H) / 2f;

        if (binding) {
            if (button != 0) setting.setValue(button);
            binding = false;
        } else if (HoverUtil.isHovered(mouseX, mouseY, bindX, bindY, bindBoxW, BIND_H) && button == 0) {
            binding = true;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                setting.setValue(-1);
            } else {
                setting.setValue(keyCode);
            }
            binding = false;
        }
    }

    @Override
    public boolean isVisible() {
        return setting.visible.get();
    }
}
