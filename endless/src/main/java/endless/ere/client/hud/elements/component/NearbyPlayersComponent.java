package endless.ere.client.hud.elements.component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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

public class NearbyPlayersComponent extends DraggableHudElement {
    private final Animation animationWidth = new Animation(200L, 100.0f, Easing.QUAD_IN_OUT);
    private final Animation animationVisible = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);

    // Maximum distance considered "near" for color interpolation (blocks)
    private static final float MAX_DIST = 60.0f;
    // Show players up to this radius
    private static final float SHOW_RADIUS = 100.0f;

    public NearbyPlayersComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void render(CustomDrawContext ctx) {
        if (mc.player == null || mc.world == null) return;

        List<AbstractClientPlayerEntity> players = new ArrayList<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player) continue;
            if (mc.player.distanceTo(p) > SHOW_RADIUS) continue;
            players.add(p);
        }
        players.sort(Comparator.comparingDouble(p -> mc.player.distanceTo(p)));

        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        Font font = Fonts.MEDIUM.getFont(6.0f);

        animationVisible.update(mc.currentScreen instanceof ChatScreen || !players.isEmpty());

        // Compute width based on rows
        float maxRowW = 100.0f;
        for (AbstractClientPlayerEntity p : players) {
            String name = p.getName().getString();
            String dist = String.format("%.1fm", mc.player.distanceTo(p));
            float w = 24.0f + font.width(name) + 16.0f + font.width(dist);
            if (w > maxRowW) maxRowW = w;
        }

        float width = animationWidth.update(maxRowW);
        float headerH = 18.0f;
        float rowH = 11.0f;
        float height = headerH + rowH * players.size() + (players.isEmpty() ? 0 : 4f);
        if (players.isEmpty()) height = headerH;

        this.width = width;
        this.height = height;

        float x = this.x;
        float y = this.y;

        ctx.pushMatrix();
        ctx.getMatrices().translate(x + width / 2.0f, y + height / 2.0f, 0.0f);
        ctx.getMatrices().scale(animationVisible.getValue(), animationVisible.getValue(), 1.0f);
        ctx.getMatrices().translate(-(x + width / 2.0f), -(y + height / 2.0f), 0.0f);

        BorderRadius radius = BorderRadius.all(4.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, height, 21.0f, radius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, height, radius, theme.getForegroundLight().mulAlpha(0.85f));

        BorderRadius headerRadius = height > headerH + 0.5f ? BorderRadius.top(4.0f, 4.0f) : BorderRadius.all(4.0f);
        DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, headerH, 35.0f, headerRadius, ColorRGBA.WHITE);
        ctx.drawRoundedRect(x, y, width, headerH, headerRadius, theme.getBackgroundColor().mulAlpha(0.65f));
        DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, width, headerH, headerRadius, 0.18f);
        if (height > headerH + 0.5f) {
            ctx.drawRect(x, y + headerH, width, 1.0f, theme.getForegroundStroke());
        }

        // Header icon (people) - using "P" or fallback "L"
        ctx.drawText(iconFont, "P", x + 8.0f, y + (headerH - iconFont.height()) / 2.0f, theme.getColor());
        ctx.drawText(iconFont, "M", x + width - 8.0f - iconFont.width("M"), y + (headerH - iconFont.height()) / 2.0f, theme.getWhiteGray());
        ctx.drawText(font, "Nearby (" + players.size() + ")", x + 8.0f + iconFont.width("P") + 4.0f, y + (headerH - font.height()) / 2.0f, theme.getWhite());

        // Rows
        if (animationVisible.getValue() > 0.01f && !players.isEmpty()) {
            ctx.drawRoundedRect(x, y + headerH, width, height - headerH, BorderRadius.bottom(4.0f, 4.0f), new ColorRGBA(0, 0, 0, 255));
            float rowY = y + headerH + 2f;
            for (AbstractClientPlayerEntity p : players) {
                String name = p.getName().getString();
                float dist = mc.player.distanceTo(p);
                String distStr = String.format("%.1fm", dist);

                // distance color: 0 = red, MAX_DIST or more = green
                float t = Math.min(Math.max(dist / MAX_DIST, 0.0f), 1.0f);
                ColorRGBA distColor = new ColorRGBA(255, 60, 60, 255).mix(new ColorRGBA(80, 230, 90, 255), t);

                ctx.drawText(font, name, x + 8.0f, rowY + (rowH - font.height()) / 2.0f, theme.getWhite());
                ctx.drawText(font, distStr, x + width - 8.0f - font.width(distStr), rowY + (rowH - font.height()) / 2.0f, distColor);
                rowY += rowH;
            }
        }

        ctx.drawRoundedBorder(x, y, width, height, 0.1f, radius, theme.getForegroundStroke());
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), x, y, width, height, 0.1f, Math.min(20.0f, Math.max(12.0f, height / 2.5f)), theme.getColor(), radius);
        ctx.popMatrix();
    }
}
