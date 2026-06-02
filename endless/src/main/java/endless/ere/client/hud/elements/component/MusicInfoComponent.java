/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  dev.redstones.mediaplayerinfo.IMediaSession
 *  dev.redstones.mediaplayerinfo.MediaInfo
 *  dev.redstones.mediaplayerinfo.MediaPlayerInfo
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.util.Identifier
 */
package endless.ere.client.hud.elements.component;

import dev.redstones.mediaplayerinfo.IMediaSession;
import dev.redstones.mediaplayerinfo.MediaInfo;
import dev.redstones.mediaplayerinfo.MediaPlayerInfo;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.utility.math.Timer;
import endless.ere.utility.render.display.Render2DUtil;
import endless.ere.utility.render.display.Texture;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class MusicInfoComponent
extends DraggableHudElement {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private MediaInfo mediaInfo = new MediaInfo("\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0422\u0440\u0435\u043a\u0430", "\u0410\u0440\u0442\u0438\u0441\u0442", new byte[0], 43L, 150L, false);
    private final Identifier artwork = Endless.id("icons/avatarmusic.png");
    private final Timer lastMedia = new Timer();
    public IMediaSession session;
    private final Animation exit = new Animation(200L, 0.0f, Easing.QUAD_IN_OUT);
    private final Animation fadeAnimation = new Animation(300L, Easing.CUBIC_OUT);
    private final Animation progressAnimation = new Animation(200L, Easing.CUBIC_OUT);

    public MusicInfoComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    @Override
    public void tick() {
        if (MusicInfoComponent.mc.player.age % 5 == 0) {
            this.executorService.execute(() -> {
                MediaInfo info;
                this.session = MediaPlayerInfo.Instance.getMediaSessions().stream().max(Comparator.comparing(s -> s.getMedia().getPlaying())).orElse(null);
                IMediaSession currentSession = this.session;
                if (!(currentSession == null || (info = currentSession.getMedia()).getTitle().isEmpty() && info.getArtist().isEmpty())) {
                    if (this.mediaInfo.getTitle().equals("\u041d\u0430\u0437\u0432\u0430\u043d\u0438\u0435 \u0422\u0440\u0435\u043a\u0430") || !Arrays.equals(this.mediaInfo.getArtworkPng(), info.getArtworkPng())) {
                        Render2DUtil.registerTexture(new Texture(this.artwork), info.getArtworkPng());
                    }
                    this.mediaInfo = info;
                    this.lastMedia.reset();
                }
            });
        }
    }

    @Override
    public void render(CustomDrawContext ctx) {
        try {
            this.exit.update(!this.lastMedia.finished(2000L) || MusicInfoComponent.mc.currentScreen instanceof ChatScreen);
            if (this.exit.getValue() == 0.0f) {
                return;
            }
            Font titleFont = Fonts.MEDIUM.getFont(6.0f);
            Font artistFont = Fonts.MEDIUM.getFont(5.0f);
            Font timeFont = Fonts.MEDIUM.getFont(6.0f);
            float coverBoxSize = 32.0f;
            float infoBoxWidth = 75.0f;
            float padding = 6.0f;
            float borderRadius = 4.0f;
            Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
            ColorRGBA bgLeft = theme.getForegroundLight();
            ColorRGBA bgRight = theme.getForegroundColor();
            ColorRGBA titleColor = theme.getWhite();
            ColorRGBA artistColor = theme.getGrayLight();
            ColorRGBA timeColor = theme.getColor();
            ColorRGBA progressBg = theme.getForegroundStroke();
            ColorRGBA progressFill = theme.getColor();
            this.width = coverBoxSize + infoBoxWidth;
            this.height = coverBoxSize;
            ctx.pushMatrix();
            float scaleX = this.x + this.width / 2.0f;
            float scaleY = this.y + this.height / 2.0f;
            ctx.getMatrices().translate(scaleX, scaleY, 0.0f);
            ctx.getMatrices().scale(this.exit.getValue(), this.exit.getValue(), 1.0f);
            ctx.getMatrices().translate(-scaleX, -scaleY, 1.0f);
            DrawUtil.drawBlur(ctx.getMatrices(), this.x, this.y, this.width, this.height, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
            ctx.drawRoundedRect(this.x, this.y, coverBoxSize, this.height, BorderRadius.left(borderRadius, borderRadius), bgLeft);
            ctx.drawRoundedRect(this.x + coverBoxSize, this.y, infoBoxWidth, this.height, BorderRadius.right(borderRadius, borderRadius), bgRight);
            ctx.drawRoundedBorder(this.x, this.y, coverBoxSize + infoBoxWidth, this.height, 0.1f, BorderRadius.all(4.0f), theme.getForegroundStroke());
            DrawUtil.drawRoundedCorner(ctx.getMatrices(), this.x, this.y, coverBoxSize + infoBoxWidth, this.height, 0.1f, 15.0f, theme.getColor(), BorderRadius.all(4.0f));
            float imagePadding = 4.0f;
            float imageSize = coverBoxSize - imagePadding * 2.0f;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0.0f, 0.0f, 0.0f);
            if (this.artwork != null) {
                DrawUtil.drawRoundedTexture(ctx.getMatrices(), this.artwork, this.x + imagePadding, this.y + imagePadding, imageSize, imageSize, BorderRadius.all(2.0f));
            }
            ctx.getMatrices().pop();
            float rightX = this.x + coverBoxSize;
            float rightContentX = rightX + padding;
            float rightContentWidth = infoBoxWidth - padding * 2.0f;
            float sliderY = this.y + this.height - 7.0f;
            float progress = this.mediaInfo.getDuration() > 0L ? (float)this.mediaInfo.getPosition() / (float)this.mediaInfo.getDuration() : 0.0f;
            this.progressAnimation.update(progress);
            float animatedProgress = this.progressAnimation.getValue();
            float alpha = this.fadeAnimation.getValue();
            ColorRGBA bgLeftAlpha = bgLeft.mulAlpha(alpha);
            ColorRGBA bgRightAlpha = bgRight.mulAlpha(alpha);
            ctx.drawRoundedRect(this.x, this.y, coverBoxSize, this.height, BorderRadius.left(borderRadius, borderRadius), bgLeftAlpha);
            ctx.drawRoundedRect(this.x + coverBoxSize, this.y, infoBoxWidth, this.height, BorderRadius.right(borderRadius, borderRadius), bgRightAlpha);
            ctx.drawRoundedRect(rightContentX, sliderY, rightContentWidth, 2.0f, BorderRadius.all(0.2f), progressBg);
            ctx.drawRoundedRect(rightContentX, sliderY, rightContentWidth * Math.min(1.0f, animatedProgress), 2.0f, BorderRadius.all(0.2f), progressFill);
            float titleY = this.y + 8.0f;
            float artistY = titleY + titleFont.height() + 2.0f;
            String timeString = this.formatTime(this.mediaInfo.getPosition());
            float timeWidth = timeFont.width(timeString);
            float maxTextWidth = rightContentWidth - timeWidth - 2.0f;
            String title = this.mediaInfo.getTitle();
            String artist = this.mediaInfo.getArtist();
            this.renderScrollingText(ctx, titleFont, title, rightContentX, titleY, titleColor, maxTextWidth);
            if (artist != null && !artist.isEmpty()) {
                this.renderScrollingText(ctx, artistFont, artist, rightContentX, artistY + 1.5f, artistColor, maxTextWidth);
            }
            ctx.drawText(timeFont, timeString, rightContentX + 3.0f + rightContentWidth - timeWidth, titleY, timeColor);
            ctx.popMatrix();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderScrollingText(CustomDrawContext ctx, Font font, String text, float x, float y, ColorRGBA color, float maxWidth) {
        float textW = font.width(text);
        float scroll = 0.0f;
        if (textW > maxWidth) {
            float scrollMax = textW - maxWidth;
            if (scrollMax < 0.0f) {
                scrollMax = 0.0f;
            }
            float pauseDuration = 1000.0f;
            float scrollDuration = 4000.0f;
            float totalCycle = pauseDuration + scrollDuration + pauseDuration + scrollDuration;
            long now = System.currentTimeMillis();
            float timeInCycle = now % (long)totalCycle;
            if (timeInCycle < pauseDuration) {
                scroll = 0.0f;
            } else if (timeInCycle < pauseDuration + scrollDuration) {
                float t = (timeInCycle - pauseDuration) / scrollDuration;
                scroll = t * scrollMax;
            } else if (timeInCycle < pauseDuration + scrollDuration + pauseDuration) {
                scroll = scrollMax;
            } else {
                float t = (timeInCycle - pauseDuration - scrollDuration - pauseDuration) / scrollDuration;
                scroll = scrollMax * (1.0f - t);
            }
        }
        ctx.enableScissor((int)Math.ceil(x - 1.0f), (int)Math.ceil(y - 1.0f), (int)Math.ceil(x - 1.0f + maxWidth + 2.0f), (int)Math.ceil(y - 1.0f + font.height() + 5.0f));
        ctx.drawText(font, text.toLowerCase(), x - scroll, y, color);
        ctx.disableScissor();
    }

    private String formatTime(long ms) {
        long minutes = ms / 60L;
        long seconds = ms % 60L;
        return String.format("%d:%02d", minutes, seconds);
    }
}
