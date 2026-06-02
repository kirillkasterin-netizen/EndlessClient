/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.ItemStack
 */
package endless.ere.client.hud.elements.component;

import net.minecraft.item.ItemStack;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.mixin.accessors.DrawContextAccessor;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class InventoryComponent
extends DraggableHudElement {
    private final Animation toggleAnimation = new Animation(300L, Easing.SINE_IN_OUT);
    private final Animation inventoryChangeAnimation = new Animation(150L, Easing.SINE_IN_OUT);
    private String lastInventoryHash = "";
    private float lastWidth = 0.0f;
    private float lastHeight = 0.0f;

    public InventoryComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        if (InventoryComponent.mc.player == null) {
            this.toggleAnimation.update(0.0f);
            if (this.toggleAnimation.getValue() > 0.01f) {
                this.renderInventory(ctx, this.toggleAnimation.getValue());
            }
            return;
        }
        this.toggleAnimation.update(1.0f);
        if (this.toggleAnimation.getValue() <= 0.01f) {
            return;
        }
        String currentInventoryHash = "";
        int i = 9;
        while (i < 36) {
            ItemStack stack = InventoryComponent.mc.player.getInventory().getStack(i);
            currentInventoryHash = (String)currentInventoryHash + stack.getItem().toString() + stack.getCount();
            ++i;
        }
        if (!currentInventoryHash.equals(this.lastInventoryHash)) {
            this.inventoryChangeAnimation.update(0.0f);
            this.lastInventoryHash = currentInventoryHash;
        }
        this.inventoryChangeAnimation.update(1.0f);
        this.renderInventory(ctx, this.toggleAnimation.getValue() * this.inventoryChangeAnimation.getValue());
    }

    private void renderInventory(CustomDrawContext ctx, float animationValue) {
        if (InventoryComponent.mc.player == null) {
            Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
            ColorRGBA bgColor = theme.getForegroundColor();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(this.x + this.lastWidth / 2.0f, this.y + this.lastHeight / 2.0f, 0.0f);
            ctx.getMatrices().scale(animationValue, animationValue, 1.0f);
            ctx.getMatrices().translate(-(this.x + this.lastWidth / 2.0f), -(this.y + this.lastHeight / 2.0f), 0.0f);
            ctx.drawRoundedRect(this.x, this.y, this.lastWidth, this.lastHeight, BorderRadius.all(4.0f), bgColor);
            ctx.getMatrices().pop();
            return;
        }
        Font countFont = Fonts.MEDIUM.getFont(6.0f);
        float slotSize = 20.0f;
        float borderRadius = 4.0f;
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        ColorRGBA graySlotColor = theme.getForegroundColor();
        ColorRGBA themeSlotColor = theme.getForegroundLight();
        int columns = 9;
        int rows = 3;
        float gridWidth = (float)columns * slotSize;
        float gridHeight = (float)rows * slotSize;
        this.width = gridWidth;
        this.height = gridHeight;
        this.lastWidth = this.width;
        this.lastHeight = this.height;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(this.x + this.width / 2.0f, this.y + this.height / 2.0f, 0.0f);
        ctx.getMatrices().scale(animationValue, animationValue, 1.0f);
        ctx.getMatrices().translate(-(this.x + this.width / 2.0f), -(this.y + this.height / 2.0f), 0.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), this.x, this.y, this.width, this.height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        int row = 0;
        while (row < rows) {
            int col = 0;
            while (col < columns) {
                int slotIndex = 9 + row * 9 + col;
                ItemStack stack = InventoryComponent.mc.player.getInventory().getStack(slotIndex);
                float slotX = this.x + (float)col * slotSize;
                float slotY = this.y + (float)row * slotSize;
                ColorRGBA slotColor = (row + col) % 2 == 0 ? graySlotColor : themeSlotColor;
                float round = 4.0f;
                BorderRadius radius = col == 0 && row == 0 ? BorderRadius.top(round, 0.0f) : (col == 8 && row == 0 ? BorderRadius.top(0.0f, round) : (col == 0 && row == 2 ? BorderRadius.bottom(round, 0.0f) : (col == 8 && row == 2 ? BorderRadius.bottom(0.0f, round) : BorderRadius.ZERO)));
                ctx.drawRoundedRect(slotX, slotY, slotSize, slotSize, radius, slotColor);
                if (!stack.isEmpty()) {
                    ctx.pushMatrix();
                    ctx.getMatrices().translate((double)slotX + ((double)slotSize - 12.8) / 2.0, (double)slotY + ((double)slotSize - 12.8) / 2.0, 0.0);
                    ctx.getMatrices().scale(0.8f, 0.8f, 1.0f);
                    ctx.drawItem(stack, 0, 0);
                    ((DrawContextAccessor)((Object)ctx)).callDrawItemBar(stack, 0, 0);
                    ((DrawContextAccessor)((Object)ctx)).callDrawCooldownProgress(stack, 0, 0);
                    ctx.popMatrix();
                }
                ++col;
            }
            ++row;
        }
        ctx.drawRoundedBorder(this.x, this.y, gridWidth, gridHeight, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), this.x, this.y, gridWidth, gridHeight, 0.1f, 20.0f, theme.getColor(), BorderRadius.all(4.0f));
        ctx.getMatrices().pop();
    }
}

