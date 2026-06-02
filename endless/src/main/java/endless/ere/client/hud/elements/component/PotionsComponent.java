/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.render.RenderLayer
 *  net.minecraft.client.resource.language.I18n
 *  net.minecraft.entity.effect.StatusEffect
 *  net.minecraft.entity.effect.StatusEffectInstance
 *  net.minecraft.util.Identifier
 */
package endless.ere.client.hud.elements.component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class PotionsComponent
extends DraggableHudElement {
    private final Animation animationWidth = new Animation(200L, 100.0f, Easing.QUAD_IN_OUT);
    private final Animation animationScale = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Map<String, PotionModule> modules = new LinkedHashMap<String, PotionModule>();
    private final Animation animationVisible = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    Set<String> currentKeys = new HashSet<String>();

    public PotionsComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        if (PotionsComponent.mc.player == null) {
            return;
        }
        this.currentKeys.clear();
        ArrayList<StatusEffectInstance> effects = new ArrayList<>(PotionsComponent.mc.player.getActiveStatusEffects().values());
        for (StatusEffectInstance eff : effects) {
            String key = ((StatusEffect)eff.getEffectType().value()).getTranslationKey() + eff.getAmplifier();
            this.currentKeys.add(key);
            if (this.modules.containsKey(key)) continue;
            this.modules.put(key, new PotionModule(eff));
        }
        this.modules.values().removeIf(PotionModule::isDelete);
        boolean hidden = this.modules.isEmpty();
        this.animationScale.update(hidden ? 1 : 0);
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        Font font = Fonts.MEDIUM.getFont(6.0f);
        float x = this.x;
        float y = this.y;
        float height = 18.0f + (float)this.modules.values().stream().mapToDouble(PotionModule::getHeight).sum();
        float width = (float)this.modules.values().stream().mapToDouble(PotionModule::updateWidth).max().orElse(100.0);
        this.width = width = this.animationWidth.update(width);
        this.height = height;
        ctx.pushMatrix();
        BorderRadius radius = new BorderRadius(4.0f, 4.0f, 4.0f, 4.0f);
        this.animationVisible.update(PotionsComponent.mc.currentScreen instanceof ChatScreen || !this.modules.isEmpty());
        ctx.getMatrices().translate(x + width / 2.0f, y + height / 2.0f, 0.0f);
        ctx.getMatrices().scale(this.animationVisible.getValue(), this.animationVisible.getValue(), 1.0f);
        ctx.getMatrices().translate(-(x + width / 2.0f), -(y + height / 2.0f), 0.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, height, radius, theme.getForegroundLight().mulAlpha(0.85f));
        BorderRadius headerRadius = height > 18.5f ? BorderRadius.top(4.0f, 4.0f) : BorderRadius.all(4.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, 18.0f, 35.0f, headerRadius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, 18.0f, headerRadius, theme.getBackgroundColor().mulAlpha(0.65f));
        DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, width, 18.0f, headerRadius, 0.18f);
        if (height > 18.5f) {
            ctx.drawRect(x, y + 18.0f, width, 1.0f, theme.getForegroundStroke());
        }
        ctx.drawText(iconFont, "O", x + 8.0f, y + (18.0f - iconFont.height()) / 2.0f, theme.getColor());
        ctx.drawText(iconFont, "M", x + width - 8.0f - iconFont.width("M"), y + (18.0f - iconFont.height()) / 2.0f, theme.getWhiteGray());
        ctx.drawText(font, "Potions", x + 8.0f + 8.0f + 2.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
        if (this.animationVisible.getValue() == 1.0f) {
            float offsetY = y + 18.0f;
            int index = 0;
            for (PotionModule module : this.modules.values()) {
                module.render(ctx, x, offsetY, width, index);
                offsetY += module.getHeight();
                ++index;
            }
        }
        ctx.drawRoundedBorder(x, y, width, height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), x, y, width, height, 0.1f, Math.min(20.0f, Math.max(12.0f, height / 2.5f)), theme.getColor(), BorderRadius.all(4.0f));
        ctx.popMatrix();
    }

    private String getAmplifierText(int amplifier) {
        return String.valueOf(amplifier + 1);
    }

    private String formatDuration(int durationTicks) {
        int totalSeconds = durationTicks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private Identifier getEffectIcon(StatusEffect effect) {
        String id = effect.getTranslationKey().replace("effect.minecraft.", "").replace("effect.", "");
        return Identifier.of((String)"minecraft", (String)("textures/mob_effect/" + id + ".png"));
    }

    private class PotionModule {
        private final Animation animation = new Animation(150L, 0.01f, Easing.QUAD_IN_OUT);
        private final Animation animationColor = new Animation(200L, Easing.QUAD_IN_OUT);
        private StatusEffectInstance effect;

        public PotionModule(StatusEffectInstance effect) {
            this.effect = effect;
        }

        public float updateWidth() {
            Font font = Fonts.MEDIUM.getFont(6.0f);
            String name = I18n.translate((String)((StatusEffect)this.effect.getEffectType().value()).getTranslationKey(), (Object[])new Object[0]);
            String amp = PotionsComponent.this.getAmplifierText(this.effect.getAmplifier());
            String duration = PotionsComponent.this.formatDuration(this.effect.getDuration());
            float width = 100.0f;
            float moduleTextWidth = 24.0f + font.width(name + "   " + amp);
            float keyTextWidth = font.width(duration);
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
            String key = ((StatusEffect)this.effect.getEffectType().value()).getTranslationKey() + this.effect.getAmplifier();
            this.effect = new ArrayList<StatusEffectInstance>(PotionsComponent.mc.player.getActiveStatusEffects().values()).stream().filter(e -> (((StatusEffect)e.getEffectType().value()).getTranslationKey() + e.getAmplifier()).equals(key)).findAny().orElse(this.effect);
            this.animation.update(PotionsComponent.this.currentKeys.contains(key) ? 1 : 0);
            this.animationColor.update(i % 2 == 0 ? 1 : 0);
            Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
            Font font = Fonts.MEDIUM.getFont(6.0f);
            ColorRGBA background = new ColorRGBA(0, 0, 0, 255);
            ctx.pushMatrix();
            ctx.getMatrices().translate(x + width / 2.0f, y + 9.0f, 0.0f);
            ctx.getMatrices().scale(this.animation.getValue(), this.animation.getValue(), 1.0f);
            ctx.getMatrices().translate(-(x + width / 2.0f), -(y + 9.0f), 0.0f);
            ctx.drawRoundedRect(x, y, width, 18.0f, i == PotionsComponent.this.modules.size() - 1 ? BorderRadius.bottom(4.0f, 4.0f) : BorderRadius.ZERO, background);
            Identifier icon = PotionsComponent.this.getEffectIcon((StatusEffect)this.effect.getEffectType().value());
            ctx.pushMatrix();
            ctx.getMatrices().translate(x + 8.0f, y + 6.0f, 0.0f);
            ctx.drawSpriteStretched(RenderLayer::getGuiTextured, mc.getStatusEffectSpriteManager().getSprite(this.effect.getEffectType()), 0, 0, 6, 6);
            ctx.popMatrix();
            String name = I18n.translate((String)((StatusEffect)this.effect.getEffectType().value()).getTranslationKey(), (Object[])new Object[0]);
            String amp = PotionsComponent.this.getAmplifierText(this.effect.getAmplifier());
            ctx.drawText(Fonts.BOLD.getFont(8.0f), ".", x + 8.0f + 8.0f + 2.0f, y + 4.0f, theme.getWhiteGray());
            ctx.drawText(font, name, x + 8.0f + 8.0f + 8.0f, y + (18.0f - font.height()) / 2.0f, theme.getWhite());
            ctx.drawText(font, amp, x + 8.0f + 8.0f + 8.0f + font.width(name + "   "), y + (18.0f - font.height()) / 2.0f, theme.getGrayLight());
            String duration = PotionsComponent.this.formatDuration(this.effect.getDuration());
            float timePercent = Math.min(1.0f, (float)this.effect.getDuration() / 1200.0f);
            ColorRGBA timerColor = ColorRGBA.WHITE.mix(new ColorRGBA(255, 50, 50, 255), 1.0f - timePercent);
            ctx.drawText(font, duration, x + width - 8.0f - font.width(duration), y + (18.0f - font.height()) / 2.0f, timerColor);
            ctx.popMatrix();
        }

        public boolean isDelete() {
            return this.animation.getValue() == 0.0f;
        }
    }
}

