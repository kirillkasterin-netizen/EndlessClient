package dev.endless.ui;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import dev.endless.Endless;
import dev.endless.module.ModuleCategory;
import dev.endless.ui.component.Component;
import dev.endless.util.IMinecraft;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.math.Animation;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.math.Scissor;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@Setter
public class Panel implements IMinecraft {
    public float x, y, width, height;
    public final ModuleCategory category;
    public List<ModuleComponent> moduleComponents = new ArrayList<>();
    private Animation animation = new Animation(Easing.QUINTIC_OUT, 350);
    private Animation animationAlpha = new Animation(Easing.BOUNCE_OUT, 350);
    private final Animation scrollbarAnim = new Animation(Easing.CUBIC_IN_OUT, 220);
    float scroll;
    float maxScroll;

    private final ClickGuiFrame parent;

    public Panel(ModuleCategory category, ClickGuiFrame parent) {
        this.category = category;
        this.parent = parent;
        Endless.getInstance().getModuleStorage().getModules().stream()
                .filter(m -> m.getCategory() == this.category)
                .sorted(Comparator.comparing(m -> m.getName().toLowerCase()))
                .forEach(m -> moduleComponents.add(new ModuleComponent(m, this)));
    }

    public void clampScroll() {
        if (maxScroll > 0) {
            scroll = MathHelper.clamp(scroll, -maxScroll, 0);
        } else {
            scroll = 0;
        }
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        animationAlpha.setValue(1);
        float alpha = Math.min(255 * animationAlpha.getValue(), 255);
        float alphaRatio = alpha / 255f;
        float cornerRadius = 8f;
        float headerHeight = 20f;

        
        
        
        int panelColor = ColorProvider.setAlpha(ColorProvider.getColorWindowBg(), (int)(30 * alphaRatio));

        DrawUtil.drawRoundBlur(x, y, width, height, cornerRadius, ColorProvider.rgba(75, 75, 75, (int)(255 * alphaRatio)), 20f);
        DrawUtil.drawRound(x, y, width, height, cornerRadius, panelColor);

        

        
        String title = category.name();
        String capitalizedTitle = title.substring(0, 1).toUpperCase() + title.substring(1).toLowerCase();
        float titleWidth = Fonts.SFREGULAR.get().getWidth(capitalizedTitle, 8.5f);

        String categoryIcon = switch (category) {
            case COMBAT -> "a";
            case MOVEMENT -> "b";
            case RENDER -> "c";
            case PLAYER -> "d";
            case MISC -> "e";
        };
        float iconSize = 8.5f;
        float iconWidth = Fonts.ICONS_MINCED.get().getWidth(categoryIcon, iconSize);
        
        // Текст слева
        float textX = x + 6f;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), capitalizedTitle, textX, y + 6f, ColorProvider.setAlpha(ColorProvider.getColorHeaderText(), (int) alpha), 8.5f);
        
        // Иконка справа
        float iconX = x + width - iconWidth - 6f;
        DrawUtil.drawText(Fonts.ICONS_MINCED.get(), categoryIcon, iconX, y + 6f, ColorProvider.setAlpha(ColorProvider.getColorIcons(), (int) alpha), iconSize);

        // Разделительная полоска под заголовком (как в endless)
        DrawUtil.drawRound(x + 6f, y + headerHeight - 1f, width - 12f, 0.5f, 0.25f,
                ColorProvider.rgba(255, 255, 255, (int)(25 * alphaRatio)));

        float offset = 2f;
        clampScroll();
        animation.run(scroll);

        Scissor.push();
        Scissor.setFromComponentCoordinates(x, y + headerHeight, width, height - headerHeight - 4);

        for (ModuleComponent component : moduleComponents) {
            if (parent.searchCheck(component.getModule().getName())) {
                continue;
            }

            component.setX(x + 3.5f);
            component.setY((float) (y + headerHeight + offset + animation.getValue()));
            component.setWidth(width - 7f);

            float baseHeight = 19f;
            float extraHeight = 0;
            if (component.getAnimation().getValue() > 0.01f) {
                for (Component comp : component.getComponents()) {
                    float visibleProgress = MathHelper.clamp(comp.getAlphaAnimSetting().getValue(), 0f, 1f);
                    if (comp.isVisible() || visibleProgress > 0f) {
                        extraHeight += comp.getHeight() * visibleProgress;
                    }
                }
            }
            component.setHeight(baseHeight + (extraHeight * (float) component.getAnimation().getValue()));

            Scissor.setFromComponentCoordinates(x, y + headerHeight, width, height - headerHeight - 4);
            component.render(matrixStack, mouseX, mouseY, partialTicks);
            Scissor.setFromComponentCoordinates(x, y + headerHeight, width, height - headerHeight - 4);

            offset += component.getHeight() + 3.5f;
        }
        maxScroll = Math.max(0, offset - (height - headerHeight - 8));
        scrollbarAnim.run(maxScroll > 0f);

        Scissor.unset();
        Scissor.pop();
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y + 20, width, height - 20)) {
            for (ModuleComponent moduleComponent : moduleComponents) {
                if (!parent.searchCheck(moduleComponent.getModule().getName())) {
                    moduleComponent.mouseClicked(mouseX, mouseY, button);
                }
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (ModuleComponent moduleComponent : moduleComponents) {
            if (!parent.searchCheck(moduleComponent.getModule().getName())) {
                moduleComponent.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (HoverUtil.isHovered(mouseX, mouseY, x, y, width, height)) {
            scroll += (float) (verticalAmount * 30f);
            clampScroll();
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        for (ModuleComponent moduleComponent : moduleComponents) {
            moduleComponent.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
