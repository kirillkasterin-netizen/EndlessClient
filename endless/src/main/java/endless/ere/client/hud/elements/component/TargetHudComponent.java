/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.client.network.AbstractClientPlayerEntity
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.player.PlayerEntity
 */
package endless.ere.client.hud.elements.component;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.client.modules.impl.combat.Aura;
import endless.ere.utility.game.player.PlayerIntersectionUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class TargetHudComponent extends DraggableHudElement {
    private final Animation healthAnimation = new Animation(100L, Easing.LINEAR); // Уменьшил с 200 до 100 (быстрее)
    private final Animation toggleAnimation = new Animation(150L, Easing.QUAD_IN_OUT); // Уменьшил с 200 до 150
    private LivingEntity target;
    
    public TargetHudComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        this.width = 110.0f;
        this.height = 36.0f;
        Aura aura = Aura.INSTANCE;
        LivingEntity target = TargetHudComponent.mc.currentScreen instanceof ChatScreen ? TargetHudComponent.mc.player : aura.getTarget();
        this.setTarget((LivingEntity)target);
        if (this.toggleAnimation.getValue() == 0.0f || this.target == null) {
            return;
        }
        this.renderTargetHud(ctx, this.target, this.toggleAnimation.getValue());
    }

    private void renderTargetHud(CustomDrawContext ctx, LivingEntity target, float animation) {
        float posX = this.x;
        float posY = this.y;
        float width = 110.0f;
        float height = 36.0f;
        float headSize = 30.0f;
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        
        ctx.getMatrices().push();
        ctx.getMatrices().translate(posX + width / 2.0f, posY + height / 2.0f, 0.0f);
        ctx.getMatrices().scale(animation, animation, 1.0f);
        ctx.getMatrices().translate(-(posX + width / 2.0f), -(posY + height / 2.0f), 0.0f);
        
        // Background - в цвете верхнего заголовка модулей Interface (18, 14, 22), прозрачнее
        BorderRadius bgRadius = BorderRadius.all(2.5f);
        DrawUtil.drawBlur(ctx.getMatrices(), posX, posY, width, height, 21.0f, bgRadius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(posX, posY, width, height, bgRadius, new ColorRGBA(18, 14, 22, 165));
        DrawUtil.drawHudBackground(ctx.getMatrices(), posX, posY, width, height, bgRadius, 0.10f);
        
        // Голова
        float headX = posX + 3.0f;
        float headY = posY + 3.0f;
        
        if (target instanceof PlayerEntity) {
            PlayerEntity playerTarget = (PlayerEntity)target;
            DrawUtil.drawPlayerHeadWithRoundedShader(ctx.getMatrices(), ((AbstractClientPlayerEntity)playerTarget).getSkinTextures().texture(), headX, headY, headSize, BorderRadius.all(5.0f), ColorRGBA.WHITE);
        } else {
            Font qFont = Fonts.MEDIUM.getFont(26.0f);
            ctx.drawText(qFont, "?", headX + (headSize - qFont.width("?")) / 2.0f, headY + headSize / 2.0f - qFont.height() / 2.0f, ColorRGBA.WHITE);
        }
        
        // HP цифра
        float currentHp = PlayerIntersectionUtil.getHealth(target);
        int hpInt = Math.round(currentHp);
        String hpText = String.valueOf(hpInt) + " HP";
        Font hpFont = Fonts.MEDIUM.getFont(8.0f);
        float hpTextWidth = hpFont.width(hpText);
        float hpTextX = posX + width - hpTextWidth - 6.0f;
        ColorRGBA hpColor = theme.getColor().mix(new ColorRGBA(255, 50, 50, 255), 1.0f - Math.min(Math.max(currentHp / target.getMaxHealth(), 0.0f), 1.0f));
        ctx.drawText(hpFont, hpText, hpTextX, posY + 5.0f, hpColor);

        // Имя с фейдом для длинных имен
        String name = target.getName().getString();
        int nameLen = name.length();
        float lengthFade = 1.0f;
        if (nameLen > 8) {
            float t = Math.min((nameLen - 8) / 6.0f, 1.0f);
            lengthFade = 1.0f - (t * 0.75f);
        }
        
        float textX = posX + 36.0f;
        // Ограничиваем длину имени, чтобы не перекрывало текст HP
        float nameClipMaxWidth = (hpTextX - 4.0f) - textX;
        
        Font nameFont = Fonts.MEDIUM.getFont(8.0f);
        ColorRGBA nameColor = theme.getWhite().mulAlpha(lengthFade);
        
        // Клипуем имя
        ctx.enableScissor((int)textX, (int)posY, (int)(textX + nameClipMaxWidth), (int)(posY + height));
        ctx.drawText(nameFont, name, textX + 0.5f, posY + 5.0f, nameColor);
        ctx.disableScissor();
        
        // Полоска HP снизу блока
        float maxHealth = target.getMaxHealth();
        float hpPercent = Math.min(Math.max(currentHp / maxHealth, 0.0f), 1.0f);
        this.healthAnimation.update(hpPercent);
        float animatedHpPercent = this.healthAnimation.getValue();
        
        float hpBarHeight = 4.5f;
        float hpBarX = posX + 36.0f;
        float hpBarRightPad = 6.0f;
        float hpBarWidth = (posX + width - hpBarRightPad) - hpBarX;
        float hpBarY = posY + height - hpBarHeight - 13.0f;
        
        // Фон полоски (квадратная с легким закруглением)
        ctx.drawRoundedRect(hpBarX, hpBarY, hpBarWidth, hpBarHeight, BorderRadius.all(0.8f), new ColorRGBA(40, 40, 40, 150));
        
        // Полоска здоровья - от цвета темы к красному при низком HP
        if (animatedHpPercent > 0.0f) {
            ColorRGBA healthColor = theme.getColor().mix(new ColorRGBA(255, 50, 50, 255), 1.0f - hpPercent);
            ctx.drawRoundedRect(hpBarX, hpBarY, hpBarWidth * animatedHpPercent, hpBarHeight, BorderRadius.all(0.8f), healthColor);
        }
        
        // Броня ниже полоски HP
        if (target instanceof PlayerEntity) {
            PlayerEntity playerTarget = (PlayerEntity)target;
            float itemScale = 0.5f;
            float slotSize = 16.0f * itemScale;
            float itemX = hpBarX;
            float itemY = hpBarY + hpBarHeight + 2.0f;
            
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0.0f, 0.0f, 100.0f);
            
            // Main hand
            if (!playerTarget.getMainHandStack().isEmpty()) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(itemX, itemY, 0.0f);
                ctx.getMatrices().scale(itemScale, itemScale, 1.0f);
                ctx.drawItem(playerTarget.getMainHandStack(), 0, 0);
                ctx.getMatrices().pop();
            }
            itemX += slotSize;
            
            // Armor
            for (net.minecraft.item.ItemStack stack : playerTarget.getArmorItems()) {
                if (!stack.isEmpty()) {
                    ctx.getMatrices().push();
                    ctx.getMatrices().translate(itemX, itemY, 5.0f);
                    ctx.getMatrices().scale(itemScale, itemScale, 1.0f);
                    ctx.drawItem(stack, 0, 0);
                    ctx.getMatrices().pop();
                }
                itemX += slotSize;
            }
            
            // Off hand
            if (!playerTarget.getOffHandStack().isEmpty()) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(itemX, itemY, 10.0f);
                ctx.getMatrices().scale(itemScale, itemScale, 1.0f);
                ctx.drawItem(playerTarget.getOffHandStack(), 0, 0);
                ctx.getMatrices().pop();
            }
            
            ctx.getMatrices().pop();
        }
        
        ctx.drawRoundedBorder(posX, posY, width, height, 0.1f, BorderRadius.all(2.5f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), posX, posY, width, height, 0.1f, 12.0f, theme.getColor(), BorderRadius.all(2.5f));
        
        ctx.getMatrices().pop();
        this.width = width;
        this.height = height;
    }

    public void setTarget(LivingEntity target) {
        if (target == null) {
            this.toggleAnimation.update(0.0f);
            if (this.toggleAnimation.getValue() == 0.0f) {
                this.target = null;
            }
        } else if (target != this.target) {
            this.toggleAnimation.update(0.0f);
            if (this.toggleAnimation.getValue() == 0.0f) {
                this.target = target;
            }
        } else {
                            this.toggleAnimation.update(1.0f);
        }
    }
}

