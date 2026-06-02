package dev.endless.ui;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.endless.module.Module;
import dev.endless.module.settings.*;
import dev.endless.ui.component.Component;
import dev.endless.ui.component.impl.*;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.render.builders.Builder;
import dev.endless.util.render.builders.states.QuadColorState;
import dev.endless.util.render.builders.states.QuadRadiusState;
import dev.endless.util.render.builders.states.SizeState;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.math.Animation;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;
import dev.endless.util.render.renderers.impl.BuiltTexture;

@Getter
public class ModuleComponent extends Component {
    private static final Identifier SETTINGS_ICON = Identifier.of("minecraft", "icons/menu/setting.png");

    private final Module module;
    private final Panel panel;

    private final Animation animation = new Animation(Easing.QUINTIC_OUT, 320);
    private final Animation hoverAnim = new Animation(Easing.QUINTIC_OUT, 300);
    private final Animation enabledAnim = new Animation(Easing.QUINTIC_OUT, 400);

    public boolean open;
    private boolean isHovered;
    private boolean binding;

    private final ObjectArrayList<Component> components = new ObjectArrayList<>();

    public ModuleComponent(Module module, Panel panel) {
        this.module = module;
        this.panel = panel;
        for (Setting setting : module.getSettings()) {
            switch (setting) {
                case BooleanSetting option -> components.add(new BooleanComponent(option));
                case ModeSetting option -> components.add(new ModeComponent(option));
                case ModeListSetting option -> components.add(new ModeListComponent(option));
                case SliderSetting option -> components.add(new SliderComponent(option));
                case BindSetting option -> components.add(new BindComponent(option));
                case ThemeSetting option -> components.add(new ThemeComponent(option));
                case ColorSetting option -> components.add(new ColorPickerComponent(option));
                default -> {}
            }
        }
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        isHovered = HoverUtil.isHovered(mouseX, mouseY, x, y, width, 19);

        hoverAnim.run(isHovered);
        animation.run(open);
        enabledAnim.run(module.isEnabled());

        if (HoverUtil.isHovered(mouseX, mouseY, x, y, width, 19)) CursorManager.requestHand();

        float alpha = Math.max(Math.min(panel.getAnimationAlpha().getValue(), 1), 0);

        int textColor = ColorProvider.interpolateColor(
                ColorProvider.setAlpha(ColorProvider.getColorInactiveText(), (int)(255 * alpha)),
                ColorProvider.setAlpha(ColorProvider.getColorText(), (int)(255 * alpha)),
                enabledAnim.getValue()
        );

        float highlightProgress = Math.max(hoverAnim.getValue(), enabledAnim.getValue());
        int outlineAlpha = (int) ((25 + (40 * highlightProgress)) * alpha);

        int outlineColor = ColorProvider.rgba(255, 255, 255, outlineAlpha);
        int innerColor = ColorProvider.interpolateColor(
                ColorProvider.setAlpha(ColorProvider.getColorMain(), (int)(70 * alpha)),
                ColorProvider.setAlpha(ColorProvider.getColorVisualModules(), (int)(50 * alpha)),
                enabledAnim.getValue()
        );

        float currentHeight = 19f + ((height - 19f) * animation.getValue());

        DrawUtil.drawRound(x, y, width, currentHeight - 0.5f, 3f, innerColor);

        // Тонкая обводка в цвет темы (видна при включённом модуле или ховере)
        if (highlightProgress > 0.01f) {
            int borderColor = ColorProvider.setAlpha(ColorProvider.getColorClient(), (int)(80 * highlightProgress * alpha));
            Builder.border()
                    .size(new SizeState(width + 0.5f, currentHeight - 0.25f))
                    .radius(new QuadRadiusState(3f))
                    .color(new QuadColorState(borderColor))
                    .thickness(0.5f)
                    .smoothness(1f, 1f)
                    .build()
                    .render(x, y);
        }

        if (binding) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Нажмите клавишу...", x + width / 2f - Fonts.SFREGULAR.get().getWidth("Нажмите клавишу...", 7.5f) / 2f, y + 5.75f, ColorProvider.rgba(255, 255, 255, (int)(255 * alpha)), 7.5f);
        } else {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), module.getName(), x + 4.5f, y + 5.25f, textColor, 7.5f);

            if (!components.isEmpty()) {
                float iconSize = 7.5f;
                float iconX = x + width - iconSize - 4.5f;
                float iconY = y + 6f;
                int texId = MinecraftClient.getInstance().getTextureManager().getTexture(SETTINGS_ICON).getGlId();
                BuiltTexture gear = Builder.texture()
                        .size(new SizeState(iconSize, iconSize))
                        .radius(QuadRadiusState.NO_ROUND)
                        .color(new QuadColorState(textColor))
                        .texture(0, 0, 1, 1, texId)
                        .smoothness(1f)
                        .build();
                gear.render(matrixStack.peek().getPositionMatrix(), iconX, iconY);
            }
        }

        if (animation.getValue() > 0.01f) {
            float compY = y + 17.5f;
            float panelTop = panel.getY() + 20;
            float panelBottom = panel.getY() + panel.getHeight() - 4;
            float settingsY = y + 19;
            float settingsBottom = y + currentHeight;

            float intersectY = Math.max(settingsY, panelTop);
            float intersectBottom = Math.min(settingsBottom, panelBottom);
            float intersectHeight = Math.max(0, intersectBottom - intersectY);

            float darkHeight = currentHeight - 19f;
            if (darkHeight > 0) {
                DrawUtil.drawRound(x + 1f, y + 19, width - 2f, darkHeight, 0f, ColorProvider.rgba(0, 0, 0, (int)(30 * alpha * animation.getValue())));
            }

            for (Component component : components) {
                component.getAlphaAnim().setValue(Math.min(panel.getAnimationAlpha().getValue(), 1));
                component.getAlphaAnimSetting().run(component.isVisible());

                float visibleProgress = MathHelper.clamp(component.getAlphaAnimSetting().getValue(), 0f, 1f);
                if (component.isVisible() || visibleProgress > 0) {
                    component.setX(x);
                    component.setY(compY);
                    component.setWidth(width - 4);

                    dev.endless.util.render.math.Scissor.push();
                    dev.endless.util.render.math.Scissor.setFromComponentCoordinates(x, intersectY, width, intersectHeight);

                    component.render(matrixStack, mouseX, mouseY, partialTicks);

                    dev.endless.util.render.math.Scissor.unset();
                    dev.endless.util.render.math.Scissor.pop();

                    compY += component.getHeight() * visibleProgress;
                }
            }
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY, 19)) {
            if (button == 0) module.setEnabled(!module.isEnabled());
            if (button == 1 && !components.isEmpty()) open = !open;
            if (button == 2) binding = !binding;
        }

        if (open) {
            for (Component component : components) {
                if (component.isVisible() && component.getAlphaAnimSetting().getValue() > 0.5f) {
                    component.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (open) {
            for (Component component : components) {
                component.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                module.setKey(-1);
            } else {
                module.setKey(keyCode);
            }
            binding = false;
            return;
        }

        if (open) {
            for (Component component : components) {
                component.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private boolean isHovered(double mouseX, double mouseY, float heightCheck) {
        return HoverUtil.isHovered(mouseX, mouseY, x, y, width, heightCheck);
    }
}
