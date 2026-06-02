package endless.ere.client.screens.panelgui.components;

import endless.ere.client.modules.api.setting.impl.*;
import endless.ere.client.screens.panelgui.components.core.*;
import net.minecraft.client.gui.DrawContext;
import endless.ere.Endless;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.setting.Setting;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.base.font.Fonts;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author chuppachups1337
 * @since 04/10/2025
 */


public class ModuleComponent implements IMinecraft {

    private final Module module;
    private final List<SettingComponent> settingComponents = new ArrayList<>();
    private float x, y, width;
    private final float height = 18f;
    private boolean binding = false;

    private boolean expanded = false;
    private float animatedHeight = height;
    private final Animation expandAnimation = new Animation(250, height, Easing.CUBIC_IN_OUT);

    public ModuleComponent(Module m) {
        this.module = m;
        List<Setting> settings = module.getSettings();
        for (Setting setting : settings) {
            if (setting instanceof BooleanSetting) {
                settingComponents.add(new BooleanComponent((BooleanSetting) setting));
            } else if (setting instanceof NumberSetting) {
                settingComponents.add(new NumberComponent((NumberSetting) setting));
            } else if (setting instanceof ModeSetting) {
                settingComponents.add(new ModeComponent((ModeSetting) setting));
            } else if (setting instanceof MultiBooleanSetting) {
                settingComponents.add(new MultiBooleanComponent((MultiBooleanSetting) setting));
            }
        }
    }

    /**
     * Отрисовка компонента. Принимает родительский alpha для анимации.
     * @param parentAlpha Текущее значение alpha (прозрачности) от родительской панели (0.0f до 1.0f).
     */

    public void draw(DrawContext g, float mouseX, float mouseY, float partialTicks, float parentAlpha) {
        float targetHeight = getTargetHeight();
        animatedHeight = expandAnimation.update(expanded ? targetHeight : height);

        int textAlphaOn = (int) (parentAlpha * 235);
        int textAlphaOff = (int) (parentAlpha * 130);
        int iconAlpha = (int) (parentAlpha * 175);
        int borderAlpha = (int) (parentAlpha * 22);
        int rowBgAlpha = (int) (parentAlpha * 110);

        float animFactor = Math.min(1.0f, (animatedHeight - height) / (targetHeight - height));
        if (targetHeight == height) {
            animFactor = 0.0f;
        }

        float settingsAlpha = parentAlpha * animFactor;

        boolean enabled = module.isEnabled();
        int textColor = enabled
                ? new Color(245, 245, 245, textAlphaOn).getRGB()
                : new Color(180, 180, 180, textAlphaOff).getRGB();
        int iconColor = new Color(180, 180, 180, iconAlpha).getRGB();
        int barAlpha = (int) (parentAlpha * 240);

        var font = Fonts.MEDIUM.getFont(8);

        float radius = 4f;
        float rowX = x + 8f;
        float rowW = width - 16f;

        // Лёгкая полупрозрачная подложка на всю высоту (включая раскрытые настройки)
        int rowBg = new Color(28, 22, 30, rowBgAlpha).getRGB();
        RenderUtil.drawRoundedRect(g, rowX, y, rowW, animatedHeight, radius, rowBg);

        // Тонкая обводка модуля растягивается вместе с настройками
        int borderColor = new Color(255, 255, 255, borderAlpha).getRGB();
        endless.ere.utility.render.display.shader.DrawUtil.drawRoundedBorder(
                g.getMatrices(), rowX, y, rowW, animatedHeight, 0.05f,
                endless.ere.utility.render.display.base.BorderRadius.all(radius),
                new endless.ere.utility.render.display.base.color.ColorRGBA(borderColor));

        // Полоска-индикатор слева при включённом модуле
        if (enabled) {
            int barColor = new Color(245, 245, 245, barAlpha).getRGB();
            RenderUtil.drawRoundedRect(g, rowX + 2f, y + 5f, 1.6f, height - 10f, 0.8f, barColor);
        }

        // Название модуля
        float labelX = rowX + 8f;
        font.drawString(g, module.getName(), labelX, y + 6f, textColor);

        // Иконка справа: точки если есть настройки
        if (!settingComponents.isEmpty()) {
            String dots = "...";
            float dotsW = Fonts.MEDIUM.getFont(10).getStringWidth(dots);
            Fonts.MEDIUM.getFont(10).drawString(g, dots,
                    rowX + rowW - 8f - dotsW, y + 3f, iconColor);
        }

        if (animatedHeight > height + 1) {
            float settingY = y + height - 2;

            g.enableScissor((int) x, (int) (y + height), (int) (x + width), (int) (y + animatedHeight));

            for (SettingComponent comp : settingComponents) {
                comp.setX(x);
                comp.setY(settingY);
                comp.setWidth(width);
                comp.draw(g, mouseX, mouseY, partialTicks, settingsAlpha);
                settingY += comp.getHeight();
            }
            g.disableScissor();
        }
    }

    public boolean isBinding() {
        return binding;
    }

    public boolean mouseClicked(float mouseX, float mouseY, int button) {

        boolean hovered = isHovered(mouseX, mouseY, x, y, width, height);

        if (binding) return true;

        if (hovered) {
            if (button == 0) {
                module.toggle();
                return true;
            } else if (button == 1) {
                if (!settingComponents.isEmpty()) {
                    expanded = !expanded;
                }
                return true;
            } else if (button == 2) {
                binding = true;
                return true;
            }
        }

        if (expanded && mouseY <= y + animatedHeight) {
            for (SettingComponent comp : settingComponents) {
                if (comp.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void mouseDragged(double mouseX, double mouseY, int button) {
        if (expanded && mouseY <= y + animatedHeight) {
            for (SettingComponent comp : settingComponents) {
                comp.mouseDragged(mouseX, mouseY, button);
            }
        }
    }

    public boolean mouseReleased(float mouseX, float mouseY, int button) {
        if (expanded && mouseY <= y + animatedHeight) {
            for (SettingComponent comp : settingComponents) {
                comp.mouseReleased(mouseX, mouseY, button);
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            module.setKeyCode(keyCode);
            binding = false;
            return true;
        }
        return false;
    }

    private float getTargetHeight() {
        float totalHeight = height;
        if (expanded) {
            for (SettingComponent comp : settingComponents) {
                totalHeight += comp.getHeight();
            }
        }
        return totalHeight;
    }

    private boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public Module getModule() {
        return module;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setWidth(float w) {
        this.width = w;
    }

    public float getAnimatedHeight() {
        return animatedHeight;
    }
}