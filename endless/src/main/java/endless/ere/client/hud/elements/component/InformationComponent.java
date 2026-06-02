/*
 * Decompiled with CFR 0.152.
 */
package endless.ere.client.hud.elements.component;

import java.util.Locale;
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

public class InformationComponent
extends DraggableHudElement {
    Animation cordsWidthAnimation = new Animation(200L, Easing.QUAD_IN_OUT);
    Animation speedWidthAnimation = new Animation(200L, Easing.QUAD_IN_OUT);

    public InformationComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        float iconSize = 6.0f;
        float verticalPadding = 6.0f;
        float iconTextSpacing = 4.0f;
        float cellPadding = 5.0f;
        float borderRadius = 4.0f;
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        ColorRGBA mainBgColor = theme.getForegroundColor();
        ColorRGBA highlightBgColor = theme.getForegroundLight();
        ColorRGBA iconColor = theme.getColor();
        ColorRGBA textColor = theme.getWhite();
        ColorRGBA grayTextColor = theme.getGrayLight();
        Font font = Fonts.MEDIUM.getFont(6.0f);
        double speed = Math.hypot(InformationComponent.mc.player.getX() - InformationComponent.mc.player.prevX, InformationComponent.mc.player.getZ() - InformationComponent.mc.player.prevZ);
        String coordsText = String.format(Locale.US, "%d y%d z%d", (int)InformationComponent.mc.player.getX(), (int)InformationComponent.mc.player.getY(), (int)InformationComponent.mc.player.getZ());
        String speedText = String.format("%.2f", speed * 20.0).replace(",", ".");
        float coordsWidth = cellPadding * 2.0f + iconSize + iconTextSpacing + (float)coordsText.length() * 3.8f;
        float speedWidth = cellPadding * 2.0f + iconSize + iconTextSpacing + font.width(speedText) + font.width("bps");
        coordsWidth = this.cordsWidthAnimation.update(coordsWidth);
        speedWidth = this.speedWidthAnimation.update(speedWidth);
        float totalWidth = coordsWidth + speedWidth;
        float totalHeight = iconSize + verticalPadding * 2.0f;
        this.width = totalWidth;
        this.height = totalHeight;
        DrawUtil.drawBlur(ctx.getMatrices(), this.x, this.y, coordsWidth + speedWidth, totalHeight, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        ctx.drawRoundedRect(this.x, this.y, coordsWidth, totalHeight, BorderRadius.left(borderRadius, borderRadius), highlightBgColor.mulAlpha(0.85f));
        ctx.drawRoundedRect(this.x + coordsWidth, this.y, speedWidth, totalHeight, BorderRadius.right(borderRadius, borderRadius), mainBgColor.mulAlpha(0.85f));
        DrawUtil.drawHudBackground(ctx.getMatrices(), this.x, this.y, coordsWidth + speedWidth, totalHeight, BorderRadius.all(borderRadius), 0.14f);
        ctx.enableScissor((int)this.x, (int)this.y, (int)(this.x + coordsWidth), (int)(this.y + this.height));
        float currentX = this.x + cellPadding;
        float iconY = this.y + (totalHeight - iconSize) / 2.0f;
        float textY = this.y + (totalHeight - font.height()) / 2.0f;
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        ctx.drawText(iconFont, "J", currentX, iconY, iconColor);
        currentX += iconSize + iconTextSpacing;
        currentX = this.drawPrefixedText(ctx, font, "x", String.valueOf((int)InformationComponent.mc.player.getX()), currentX, textY, grayTextColor, textColor);
        currentX = this.drawPrefixedText(ctx, font, " y", String.valueOf((int)InformationComponent.mc.player.getY()), currentX, textY, grayTextColor, textColor);
        currentX = this.drawPrefixedText(ctx, font, " z", String.valueOf((int)InformationComponent.mc.player.getZ()), currentX, textY, grayTextColor, textColor);
        ctx.disableScissor();
        currentX = this.x + coordsWidth + cellPadding;
        ctx.enableScissor((int)currentX, (int)this.y, (int)(currentX + speedWidth), (int)(this.y + this.height));
        ctx.drawText(iconFont, "K", currentX, iconY, iconColor);
        currentX += iconSize + iconTextSpacing;
        currentX = this.drawPrefixedText(ctx, font, speedText, "bps", currentX, textY, textColor, grayTextColor);
        ctx.disableScissor();
        ctx.drawRoundedBorder(this.x, this.y, coordsWidth + speedWidth, totalHeight, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), this.x, this.y, coordsWidth + speedWidth, totalHeight, 0.1f, 12.0f, theme.getColor(), BorderRadius.all(4.0f));
    }

    private float drawPrefixedText(CustomDrawContext ctx, Font font, String prefix, String value, float x, float y, ColorRGBA prefixColor, ColorRGBA valueColor) {
        ctx.drawText(font, prefix, x, y, prefixColor);
        float prefixWidth = font.width(prefix);
        ctx.drawText(font, value, x + prefixWidth, y, valueColor);
        return x + (float)prefix.length() * 3.8f + (float)value.length() * 3.8f;
    }
}

