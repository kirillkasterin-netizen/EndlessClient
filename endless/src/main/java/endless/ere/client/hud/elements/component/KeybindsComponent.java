/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ChatScreen
 */
package endless.ere.client.hud.elements.component;

import java.util.LinkedHashSet;
import java.util.Objects;
import net.minecraft.client.gui.screen.ChatScreen;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.utility.render.display.Keyboard;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class KeybindsComponent
extends DraggableHudElement {
    private final LinkedHashSet<KeyModule> keyModules = new LinkedHashSet();
    private final Animation animationWidth = new Animation(200L, 100.0f, Easing.QUAD_IN_OUT);
    private final Animation animationScale = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Animation animationVisible = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);

    public KeybindsComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        Endless.getInstance().getModuleManager().getActiveModules().forEach(module -> {
            if (module.getKeyCode() != -1 && !this.keyModules.contains(new KeyModule((Module)module))) {
                this.keyModules.addLast(new KeyModule((Module)module));
            }
        });
        this.keyModules.removeIf(km -> (!km.module.isEnabled() || km.module.getKeyCode() == -1) && km.isDelete());
        if (this.keyModules.isEmpty()) {
            this.animationScale.update(0.0f);
        } else {
            this.animationScale.update(this.keyModules.size() != 1 || this.keyModules.getFirst().animation.getTargetValue() != 0.0f ? 1 : 0);
        }
        float x = this.x;
        float y = this.y;
        float thickness = 1.5f;
        float height = (float)(18.0 + this.keyModules.stream().mapToDouble(key -> key.getHeight()).sum());
        float width = (float)this.keyModules.stream().mapToDouble(KeyModule::updateWidth).max().orElse(100.0);
        this.width = width = this.animationWidth.update(width);
        this.height = height;
        this.animationVisible.update(KeybindsComponent.mc.currentScreen instanceof ChatScreen || !this.keyModules.isEmpty());
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        ctx.pushMatrix();
        ctx.getMatrices().translate(x + width / 2.0f, y + height / 2.0f, 0.0f);
        ctx.getMatrices().scale(this.animationVisible.getValue(), this.animationVisible.getValue(), 1.0f);
        ctx.getMatrices().translate(-(x + width / 2.0f), -(y + height / 2.0f), 0.0f);
        BorderRadius radius6 = BorderRadius.all(6.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, height, radius6, theme.getForegroundLight().mulAlpha(0.85f));
        BorderRadius headerRadius = height > 18.5f ? BorderRadius.top(4.0f, 4.0f) : BorderRadius.all(4.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, 18.0f, 35.0f, headerRadius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, 18.0f, headerRadius, theme.getBackgroundColor().mulAlpha(0.65f));
        DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, width, 18.0f, headerRadius, 0.18f);
        if (height > 18.5f) {
            ctx.drawRect(x, y + 18.0f, width, 1.0f, theme.getForegroundStroke());
        }
        Font font = Fonts.MEDIUM.getFont(6.0f);
        // Иконка клавиатуры (L из ICONS-шрифта - используется в MenuKeySetting для биндов)
        ctx.drawText(iconFont, "L", x + 8.0f, y + (18.0f - iconFont.height()) / 2.0f, theme.getColor());
        ctx.drawText(iconFont, "M", x + width - 8.0f - iconFont.width("M"), y + (18.0f - iconFont.height()) / 2.0f, theme.getWhiteGray());
        ctx.drawText(font, "Keybinds", x + 8.0f + iconFont.width("L") + 4.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
        if (this.animationVisible.getValue() == 1.0f) {
            float kmY = y + 18.0f;
            int i = 0;
            ctx.enableScissor((int)x, (int)y, (int)(x + width), (int)(y + height));
            for (KeyModule km2 : this.keyModules) {
                km2.render(ctx, x, kmY, width, i);
                kmY += km2.getHeight();
                ++i;
            }
            ctx.disableScissor();
        }
        ctx.drawRoundedBorder(x, y, width, height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), x, y, width, height, 0.1f, Math.min(20.0f, Math.max(12.0f, height / 2.5f)), theme.getColor(), BorderRadius.all(4.0f));
        ctx.popMatrix();
    }

    class KeyModule
    implements Comparable<KeyModule> {
        private final Animation animation = new Animation(150L, Easing.QUAD_IN_OUT);
        private final Animation animationColor = new Animation(200L, Easing.QUAD_IN_OUT);
        private final Module module;
        private float width;

        public KeyModule(Module module) {
            this.module = module;
        }

        public float updateWidth() {
            float width = 100.0f;
            float moduleTextWidth = Fonts.MEDIUM.getWidth(this.module.getName(), 6.0f);
            float sizeBind = Math.round(Fonts.MEDIUM.getWidth(Keyboard.getKeyName(this.module.getKeyCode()), 6.0f));
            float keyTextWidth = 24.0f + sizeBind;
            float widthText = width - (keyTextWidth + 8.0f);
            if (widthText < 8.0f + moduleTextWidth + 8.0f) {
                float deltaWidth = moduleTextWidth + 8.0f + 8.0f - widthText;
                width += deltaWidth;
            }
            return width;
        }

        public float getHeight() {
            return 18.0f * this.animation.getValue();
        }

        public void render(CustomDrawContext ctx, float x, float y, float width, int i) {
            Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
            Font iconFont = Fonts.ICONS.getFont(6.0f);
            Font font = Fonts.MEDIUM.getFont(6.0f);
            this.animation.update(this.module.isEnabled() && this.module.getKeyCode() != -1 ? 1 : 0);
            ctx.pushMatrix();
            ctx.getMatrices().translate(x + width / 2.0f, y + 9.0f, 0.0f);
            float deltaANim = this.animation.getValue();
            ctx.getMatrices().scale(deltaANim, deltaANim, 1.0f);
            ctx.getMatrices().translate(-(x + width / 2.0f), -(y + 9.0f), 0.0f);
            float sizeBind = Math.round(Fonts.MEDIUM.getWidth(Keyboard.getKeyName(this.module.getKeyCode()), 6.0f));
            float keyTextWidth = 8.0f + sizeBind;
            float widthText = width - (keyTextWidth + 10.0f + 5.0f + 10.0f);
            this.animationColor.update(i % 2 == 0 ? 1 : 0);
            ColorRGBA backgroundColor = new ColorRGBA(0, 0, 0, 255);
            BorderRadius rowRadius = i == KeybindsComponent.this.keyModules.size() - 1 ? BorderRadius.bottom(4.0f, 4.0f) : BorderRadius.ZERO;
            ctx.drawRoundedRect(x, y, width, 18.0f, rowRadius, backgroundColor);
            ColorRGBA categoryColor = this.getCategoryColor(this.module.getCategory());
            ctx.drawText(iconFont, this.module.getCategory().getIcon(), x + 8.0f, y + (18.0f - iconFont.height()) / 2.0f, categoryColor);
            ctx.drawText(font, this.module.getName(), x + 8.0f + 8.0f + 8.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
            ctx.drawText(Fonts.BOLD.getFont(8.0f), ".", x + 8.0f + 8.0f + 2.0f, y + 4.0f, theme.getWhiteGray());
            ctx.drawText(font, Keyboard.getKeyName(this.module.getKeyCode()), x + width - keyTextWidth, y + (18.0f - font.height()) / 2.0f, theme.getColor());
            ctx.popMatrix();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            KeyModule keyModule = (KeyModule)obj;
            return Objects.equals(this.module.getName(), keyModule.module.getName());
        }

        public boolean isDelete() {
            return this.animation.getValue() == 0.0f;
        }

        private ColorRGBA getCategoryColor(Category category) {
            String categoryName = category.name().toUpperCase();
            switch (categoryName) {
                case "COMBAT":
                    return new ColorRGBA(255, 50, 50, 255);
                case "MOVEMENT":
                    return new ColorRGBA(200, 200, 200, 255);
                case "RENDER":
                    return new ColorRGBA(50, 120, 255, 255);
                case "PLAYER":
                    return new ColorRGBA(255, 220, 50, 255);
                case "MISC":
                    return new ColorRGBA(255, 140, 50, 255);
                default:
                    return new ColorRGBA(255, 255, 255, 255);
            }
        }

        public int hashCode() {
            return Objects.hash(this.module);
        }

        @Override
        public int compareTo(KeyModule o) {
            return this.module.getName().compareTo(o.module.getName());
        }
    }
}

