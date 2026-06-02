/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.font.TextRenderer
 *  net.minecraft.client.render.RenderTickCounter
 *  net.minecraft.component.DataComponentTypes
 *  net.minecraft.item.ItemStack
 *  net.minecraft.text.MutableText
 *  net.minecraft.text.StringVisitable
 *  net.minecraft.text.Text
 *  net.minecraft.util.Formatting
 *  net.minecraft.util.math.ColorHelper
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.util.profiler.Profilers
 */
package endless.ere.client.hud.elements.component;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profilers;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.mixin.accessors.DrawContextAccessor;
import endless.ere.utility.mixin.accessors.InGameHudAccessor;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class HootBarComponent
extends DraggableHudElement {
    List<HotBarSlot> slots = new ArrayList<HotBarSlot>();

    public HootBarComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
        float itemSize = 24.0f;
        this.width = itemSize * 9.0f;
        this.height = itemSize;
        int i = 0;
        while (i < 9) {
            this.slots.add(new HotBarSlot(i));
            ++i;
        }
    }

    @Override
    public void render(CustomDrawContext ctx) {
        this.x = ((float)ctx.getScaledWindowWidth() - this.width) / 2.0f;
        float posX = this.getX();
        float posY = this.getY();
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        if (HootBarComponent.mc.interactionManager.hasCreativeInventory()) {
            this.renderHeldItemTooltip(ctx, posY - 35.0f);
            this.renderOverlayMessage(ctx, mc.getRenderTickCounter(), posY - 35.0f - 9.0f);
            Font font = Fonts.MEDIUM.getFont(7.0f);
            int k = HootBarComponent.mc.player.experienceLevel;
            ctx.drawText(font, String.valueOf(k), posX + this.width / 2.0f - font.width(String.valueOf(k)) / 2.0f, posY - 15.0f + font.height() / 2.0f, ColorRGBA.GREEN);
            DrawUtil.drawBlur(ctx.getMatrices(), this.x, this.y, this.width, this.height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
            ctx.drawRoundedRect(posX, posY, this.width, 24.0f, BorderRadius.all(4.0f), theme.getForegroundColor());
            ItemStack offHand = HootBarComponent.mc.player.getOffHandStack();
            if (!offHand.isEmpty()) {
                float offHandX = posX - this.height - 12.0f;
                float offHandY = posY;
                DrawUtil.drawBlur(ctx.getMatrices(), offHandX, offHandY, this.height, this.height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
                ctx.drawRoundedRect(offHandX, offHandY, this.height, this.height, BorderRadius.all(4.0f), theme.getForegroundColor());
                ctx.drawRoundedBorder(offHandX, offHandY, this.height, this.height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
                DrawUtil.drawRoundedCorner(ctx.getMatrices(), posX - this.height - 12.0f, posY, this.height, this.height, 0.1f, 15.0f, theme.getColor(), BorderRadius.all(4.0f));
                ctx.pushMatrix();
                ctx.getMatrices().translate((double)offHandX + 5.6, (double)offHandY + 5.6, 1.0);
                ctx.getMatrices().scale(0.8f, 0.8f, 0.8f);
                ctx.drawItem(offHand, 0, 0);
                ((DrawContextAccessor)((Object)ctx)).callDrawItemBar(offHand, 0, 0);
                ((DrawContextAccessor)((Object)ctx)).callDrawCooldownProgress(offHand, 0, 0);
                ctx.popMatrix();
                if (offHand.getCount() > 1) {
                    String countText = "x" + String.valueOf(offHand.getCount());
                    float countWidth = font.width(countText);
                    float countX = offHandX + 24.0f - countWidth - 1.0f;
                    float countY = offHandY + 24.0f - font.height() - 3.0f;
                    ctx.drawText(font, countText, countX, countY, theme.getGray());
                }
            }
            float xSlot = posX;
            for (HotBarSlot slot : this.slots) {
                slot.render(ctx, xSlot, posY, theme);
                xSlot += this.height;
            }
            ctx.drawRoundedBorder(this.x, this.y, this.width, 24.0f, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
            DrawUtil.drawRoundedCorner(ctx.getMatrices(), this.x, this.y, this.width, 24.0f, 0.1f, 15.0f, theme.getColor(), BorderRadius.all(4.0f));
            return;
        }
        if (HootBarComponent.mc.interactionManager.hasStatusBars()) {
            ctx.pushMatrix();
            ctx.getMatrices().translate((float)(-(ctx.getScaledWindowWidth() / 2 - 91)), (float)(-(ctx.getScaledWindowHeight() - 39)), 0.0f);
            ctx.getMatrices().scale(1.0f, 1.0f, 1.0f);
            ctx.getMatrices().translate(posX, 0.0f, 0.0f);
            ctx.getMatrices().translate(0.0f, posY - 15.0f, 0.0f);
            if (!HootBarComponent.mc.interactionManager.hasCreativeInventory()) {
                ((InGameHudAccessor)HootBarComponent.mc.inGameHud).invokeRenderStatusBars(ctx);
            }
            ctx.popMatrix();
            this.renderHeldItemTooltip(ctx, posY - 35.0f);
            this.renderOverlayMessage(ctx, mc.getRenderTickCounter(), posY - 35.0f - 9.0f);
            Font font = Fonts.MEDIUM.getFont(7.0f);
            int k = HootBarComponent.mc.player.experienceLevel;
            ctx.drawText(font, String.valueOf(k), posX + this.width / 2.0f - font.width(String.valueOf(k)) / 2.0f, posY - 15.0f + font.height() / 2.0f, ColorRGBA.GREEN);
            DrawUtil.drawBlur(ctx.getMatrices(), this.x, this.y, this.width, this.height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
            ctx.drawRoundedRect(posX, posY, this.width, 24.0f, BorderRadius.all(4.0f), theme.getForegroundColor());
            ItemStack offHand = HootBarComponent.mc.player.getOffHandStack();
            if (!offHand.isEmpty()) {
                float offHandX = posX - this.height - 12.0f;
                float offHandY = posY;
                DrawUtil.drawBlur(ctx.getMatrices(), offHandX, offHandY, this.height, this.height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
                ctx.drawRoundedRect(offHandX, offHandY, this.height, this.height, BorderRadius.all(4.0f), theme.getForegroundColor());
                ctx.drawRoundedBorder(offHandX, offHandY, this.height, this.height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
                DrawUtil.drawRoundedCorner(ctx.getMatrices(), posX - this.height - 12.0f, posY, this.height, this.height, 0.1f, 15.0f, theme.getColor(), BorderRadius.all(4.0f));
                ctx.pushMatrix();
                ctx.getMatrices().translate((double)offHandX + 5.6, (double)offHandY + 5.6, 1.0);
                ctx.getMatrices().scale(0.8f, 0.8f, 0.8f);
                ctx.drawItem(offHand, 0, 0);
                ((DrawContextAccessor)((Object)ctx)).callDrawItemBar(offHand, 0, 0);
                ((DrawContextAccessor)((Object)ctx)).callDrawCooldownProgress(offHand, 0, 0);
                ctx.popMatrix();
                if (offHand.getCount() > 1) {
                    String countText = "x" + String.valueOf(offHand.getCount());
                    float countWidth = font.width(countText);
                    float countX = offHandX + 24.0f - countWidth - 1.0f;
                    float countY = offHandY + 24.0f - font.height() - 3.0f;
                    ctx.drawText(font, countText, countX, countY, theme.getGray());
                }
            }
            float xSlot = posX;
            for (HotBarSlot slot : this.slots) {
                slot.render(ctx, xSlot, posY, theme);
                xSlot += this.height;
            }
            ctx.drawRoundedBorder(this.x, this.y, this.width, 24.0f, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
            DrawUtil.drawRoundedCorner(ctx.getMatrices(), this.x, this.y, this.width, 24.0f, 0.1f, 15.0f, theme.getColor(), BorderRadius.all(4.0f));
        }
    }

    private void renderHeldItemTooltip(CustomDrawContext context, float y) {
        Profilers.get().push("selectedItemName");
        if (HootBarComponent.mc.inGameHud.heldItemTooltipFade > 0 && !HootBarComponent.mc.inGameHud.currentStack.isEmpty()) {
            int l;
            MutableText mutableText = Text.empty().append(HootBarComponent.mc.inGameHud.currentStack.getName()).formatted(HootBarComponent.mc.inGameHud.currentStack.getRarity().getFormatting());
            if (HootBarComponent.mc.inGameHud.currentStack.contains(DataComponentTypes.CUSTOM_NAME)) {
                mutableText.formatted(Formatting.ITALIC);
            }
            int i = HootBarComponent.mc.textRenderer.getWidth((StringVisitable)mutableText);
            int j = (context.getScaledWindowWidth() - i) / 2;
            int k = (int)y;
            if (!HootBarComponent.mc.interactionManager.hasStatusBars() || HootBarComponent.mc.interactionManager.hasCreativeInventory()) {
                k += 14;
            }
            if ((l = (int)((float)HootBarComponent.mc.inGameHud.heldItemTooltipFade * 256.0f / 10.0f)) > 255) {
                l = 255;
            }
            if (l > 0) {
                context.getMatrices().push();
                context.getMatrices().translate((float)j, (float)k, 0.0f);
                Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
                context.drawTextWithBackground(HootBarComponent.mc.inGameHud.getTextRenderer(), (Text)mutableText, 0, 0, i, ColorHelper.withAlpha((int)l, (int)-1));
                context.getMatrices().pop();
            }
        }
        Profilers.get().pop();
    }

    public final void renderOverlayMessage(CustomDrawContext context, RenderTickCounter tickCounter, float y) {
        TextRenderer textRenderer = HootBarComponent.mc.inGameHud.getTextRenderer();
        if (HootBarComponent.mc.inGameHud.overlayMessage != null && HootBarComponent.mc.inGameHud.overlayRemaining > 0) {
            Profilers.get().push("overlayMessage");
            float f = (float)HootBarComponent.mc.inGameHud.overlayRemaining - tickCounter.getTickDelta(false);
            int i = (int)(f * 255.0f / 20.0f);
            if (i > 255) {
                i = 255;
            }
            if (i > 8) {
                context.getMatrices().push();
                context.getMatrices().translate((float)(context.getScaledWindowWidth() / 2), y, 0.0f);
                int j = HootBarComponent.mc.inGameHud.overlayTinted ? MathHelper.hsvToArgb((float)(f / 50.0f), (float)0.7f, (float)0.6f, (int)i) : ColorHelper.withAlpha((int)i, (int)-1);
                int k = textRenderer.getWidth((StringVisitable)HootBarComponent.mc.inGameHud.overlayMessage);
                context.getMatrices().translate((float)(-k) / 2.0f, -4.0f, 0.0f);
                context.drawTextWithBackground(textRenderer, HootBarComponent.mc.inGameHud.overlayMessage, 0, 0, k, j);
                context.getMatrices().pop();
            }
            Profilers.get().pop();
        }
    }

    @Override
    protected void renderXLine(CustomDrawContext ctx, DraggableHudElement.SheetCode nearest) {
    }

    class HotBarSlot {
        private final Animation animationEnable = new Animation(150L, 0.0f, Easing.QUAD_IN_OUT);
        private final BorderRadius borderRadius;
        private final int index;

        public HotBarSlot(int index) {
            this.borderRadius = index == 0 ? BorderRadius.left(4.0f, 4.0f) : (index == 8 ? BorderRadius.right(4.0f, 4.0f) : BorderRadius.ZERO);
            this.index = index;
        }

        public void render(CustomDrawContext ctx, float x, float y, Theme theme) {
            this.animationEnable.setDuration(80L);
            Font font = Fonts.MEDIUM.getFont(6.0f);
            this.animationEnable.update(this.index == HootBarComponent.mc.player.getInventory().selectedSlot ? 1 : 0);
            ColorRGBA bgColor = (this.index % 2 != 0 ? ColorRGBA.TRANSPARENT : theme.getForegroundLight()).mix(theme.getColor(), this.animationEnable.getValue());
            ColorRGBA textColor = theme.getGray().mix(theme.getWhite(), this.animationEnable.getValue());
            ItemStack stack = (ItemStack)HootBarComponent.mc.player.getInventory().main.get(this.index);
            ctx.drawRoundedRect(x, y, 24.0f, 24.0f, this.borderRadius, bgColor);
            ctx.pushMatrix();
            ctx.getMatrices().translate((double)x + 5.6, (double)y + 5.6, 1.0);
            ctx.getMatrices().scale(0.8f, 0.8f, 0.8f);
            ctx.drawItem(stack, 0, 0);
            ((DrawContextAccessor)((Object)ctx)).callDrawItemBar(stack, 0, 0);
            ((DrawContextAccessor)((Object)ctx)).callDrawCooldownProgress(stack, 0, 0);
            ctx.popMatrix();
            ctx.drawText(font, String.valueOf(this.index + 1), x + 2.0f, y + 2.0f, textColor);
            if (stack.getCount() > 1) {
                String countText = "x" + String.valueOf(stack.getCount());
                float countWidth = font.width(countText);
                float countX = x + 24.0f - countWidth - 1.0f;
                float countY = y + 24.0f - font.height() - 3.0f;
                ctx.drawText(font, countText, countX, countY, textColor);
            }
        }
    }
}

