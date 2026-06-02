/*
 * Decompiled with CFR 0.152.
 */
package endless.ere.client.hud.elements.component;

import by.saskkeee.user.UserInfo;
import java.util.ArrayList;
import java.util.List;
import endless.ere.Endless;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.HudElement;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.game.other.TextUtil;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class WatermarkComponent extends DraggableHudElement {
    private final List<HudElement> elements = new ArrayList<HudElement>();

    public WatermarkComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        float x = this.x;
        float y = this.y;
        float leftSize = 17.5F;
        float height = 16.5F;
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();

        String rightIcon = "2";
        String rightText = String.valueOf(UserInfo.getUsername());
        String fpsIcon = "G";
        String fpsText = mc.getCurrentFps() + " fps";
        String msIcon = "H";
        int ping = mc.player != null && mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null
                ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() : 0;
        String msText = ping + " ms";
        
        String tpsIcon = "I";
        String tpsText = String.format(java.util.Locale.US, "%.1f tps", Endless.getInstance().getServerHandler().getTPS());
        String bpsIcon = "B";
        double bps = mc.player != null ? Math.hypot(mc.player.getX() - mc.player.prevX, mc.player.getZ() - mc.player.prevZ) * 20.0D : 0.0D;
        String bpsText = String.format(java.util.Locale.US, "%.1f bps", bps);
        
        int posX = mc.player != null ? (int)Math.floor(mc.player.getX()) : 0;
        int posY = mc.player != null ? (int)Math.floor(mc.player.getY()) : 0;
        int posZ = mc.player != null ? (int)Math.floor(mc.player.getZ()) : 0;
        String coordsIcon = "C";
        String coordsText = "x: " + posX + " y: " + posY + " z: " + posZ;

        Font iconFont = Fonts.ICONS.getFont(8.0f);
        Font textFont = Fonts.MEDIUM.getFont(7.0f);

        // Logo (watermark.png временно вместо буквы W) с трилинейным сглаживанием
        drawBlock(ctx, x, y, leftSize, height, theme);
        float imgSize = 11.5F;
        float imgX = x + (leftSize - imgSize) / 2.0F;
        float imgY = y + (height - imgSize) / 2.0F;
        net.minecraft.util.Identifier wmId = endless.ere.Endless.id("icons/watermark.png");
        net.minecraft.client.texture.AbstractTexture wmTex = mc.getTextureManager().getTexture(wmId);
        if (wmTex != null) {
            wmTex.setFilter(true, true);
        }
        DrawUtil.drawRoundedTexture(ctx.getMatrices(), wmId, imgX, imgY, imgSize, imgSize, BorderRadius.all(2.5F), theme.getColor());

        float rightGap = 2.0F;
        float textPad = 4.0F;
        float userTextPad = 2.0F;

        // User
        float rightX = x + leftSize + rightGap;
        float rightWidth = 4.0F + iconFont.width(rightIcon) + userTextPad + textFont.width(rightText) + 5.0F;
        drawBlock(ctx, rightX, y, rightWidth, height, theme);
        ctx.drawText(iconFont, rightIcon, rightX + 5.0F, y + 5.15F, theme.getColor());
        ctx.drawText(textFont, rightText, rightX + 5.0F + iconFont.width(rightIcon) + userTextPad, y + 5.7F, ColorRGBA.WHITE);

        // FPS
        float fpsX = rightX + rightWidth + rightGap;
        float fpsWidth = 4.0F + iconFont.width(fpsIcon) + textPad + textFont.width(fpsText) + 5.0F;
        drawBlock(ctx, fpsX, y, fpsWidth, height, theme);
        ctx.drawText(iconFont, fpsIcon, fpsX + 5.0F, y + 5.15F, theme.getColor());
        ctx.drawText(textFont, fpsText, fpsX + 5.0F + iconFont.width(fpsIcon) + textPad, y + 5.7F, ColorRGBA.WHITE);

        // MS
        float msX = fpsX + fpsWidth + rightGap;
        float msWidth = 4.0F + iconFont.width(msIcon) + textPad + textFont.width(msText) + 5.0F;
        drawBlock(ctx, msX, y, msWidth, height, theme);
        ctx.drawText(iconFont, msIcon, msX + 5.0F, y + 5.15F, theme.getColor());
        ctx.drawText(textFont, msText, msX + 5.0F + iconFont.width(msIcon) + textPad, y + 5.7F, ColorRGBA.WHITE);

        float topWidth = leftSize + rightGap + rightWidth + rightGap + fpsWidth + rightGap + msWidth;

        // Bottom Row
        float bottomY = y + height + 2.0F;
        float coordsHeight = 16.5F;

        // Coords
        float coordsWidth = 5.0F + iconFont.width(coordsIcon) + textPad + textFont.width(coordsText) + 4.0F;
        drawBlock(ctx, x, bottomY, coordsWidth, coordsHeight, theme);
        ctx.drawText(iconFont, coordsIcon, x + 5.0F, bottomY + 5.15F, theme.getColor());
        ctx.drawText(textFont, coordsText, x + 5.0F + iconFont.width(coordsIcon) + textPad, bottomY + 5.5F, ColorRGBA.WHITE);

        // TPS
        float tpsWidth = 4.0F + iconFont.width(tpsIcon) + textPad + textFont.width(tpsText) + 5.0F;
        float bottomTpsX = x + coordsWidth + rightGap;
        drawBlock(ctx, bottomTpsX, bottomY, tpsWidth, coordsHeight, theme);
        ctx.drawText(iconFont, tpsIcon, bottomTpsX + 3.5F, bottomY + 5.15F, theme.getColor());
        ctx.drawText(textFont, tpsText, bottomTpsX + 3.5F + iconFont.width(tpsIcon) + textPad, bottomY + 5.7F, ColorRGBA.WHITE);

        // BPS
        float bpsWidth = 4.0F + iconFont.width(bpsIcon) + textPad + textFont.width(bpsText) + 5.0F;
        float bottomBpsX = bottomTpsX + tpsWidth + rightGap;
        drawBlock(ctx, bottomBpsX, bottomY, bpsWidth, coordsHeight, theme);
        ctx.drawText(iconFont, bpsIcon, bottomBpsX + 4.0F, bottomY + 5.15F, theme.getColor());
        ctx.drawText(textFont, bpsText, bottomBpsX + 4.0F + iconFont.width(bpsIcon) + textPad, bottomY + 5.95F, ColorRGBA.WHITE);

        this.width = Math.max(topWidth, coordsWidth + rightGap + tpsWidth + rightGap + bpsWidth);
        this.height = height + 2.0F + coordsHeight;
    }

    private void drawBlock(CustomDrawContext ctx, float x, float y, float w, float h, Theme theme) {
        // Blur (no glow)
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, w, h, 11.0F, BorderRadius.all(4.0F), ColorRGBA.WHITE);
        // Чуть полупрозрачная подложка
        ColorRGBA bgColor = new ColorRGBA(0, 0, 0, 215);
        ctx.drawRoundedRect(x, y, w, h, BorderRadius.all(4.0F), bgColor);
        // Лёгкие точки темы клиента
        DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, w, h, BorderRadius.all(4.0F), 0.16f);
        // Outline/Border
        ctx.drawRoundedBorder(x, y, w, h, 0.1f, BorderRadius.all(4.0F), theme.getForegroundStroke());
        // Corner Triangles/Glow Highlights
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), x, y, w, h, 0.1f, 12.0f, ColorRGBA.BLACK, BorderRadius.all(4.0f));
    }
}

