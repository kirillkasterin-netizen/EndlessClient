/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ChatScreen
 *  net.minecraft.text.Text
 */
package endless.ere.client.hud.elements.component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.font.Font;
import endless.ere.base.font.Fonts;
import endless.ere.base.theme.Theme;
import endless.ere.client.hud.elements.draggable.DraggableHudElement;
import endless.ere.client.modules.api.Module;
import endless.ere.utility.render.display.base.BorderRadius;
import endless.ere.utility.render.display.base.CustomDrawContext;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.DrawUtil;

public class NotifyComponent
extends DraggableHudElement {
    private final Animation toggleAnimation = new Animation(200L, Easing.QUAD_IN_OUT);
    private final Deque<BaseNotification> notifications = new ArrayDeque<BaseNotification>();

    public NotifyComponent(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, DraggableHudElement.Align align) {
        super(name, initialX, initialY, windowWidth, windowHeight, offsetX, offsetY, align);
    }

    public void addNotification(Module module, boolean enabled) {
        this.notifications.addLast(new ModuleNotification(module, enabled));
    }

    public void addTextNotification(String icon, Text text) {
        this.notifications.addLast(new TextNotification(icon, text));
    }

    @Override
    public void render(CustomDrawContext ctx) {
        this.width = 100.0f;
        this.height = 18.0f;
        long now = System.currentTimeMillis();
        Iterator<BaseNotification> iterator = this.notifications.iterator();
        this.toggleAnimation.update(NotifyComponent.mc.currentScreen instanceof ChatScreen && this.notifications.isEmpty());
        Theme theme = Endless.getInstance().getThemeManager().getCurrentTheme();
        Font textFont = Fonts.MEDIUM.getFont(6.0f);
        Font iconFont = Fonts.ICONS.getFont(6.0f);
        float baseX = this.x;
        float baseY = this.y;
        ctx.pushMatrix();
        ctx.getMatrices().translate(this.x + 50.0f, this.y + 9.0f, 0.0f);
        ctx.getMatrices().scale(this.toggleAnimation.getValue(), this.toggleAnimation.getValue(), 1.0f);
        ctx.getMatrices().translate(-(this.x + 50.0f), -(this.y + 9.0f), 0.0f);
        float notificationHeight = 18.0f;
        ColorRGBA notifyBg = new ColorRGBA(10, 10, 12, 215);
        ColorRGBA notifyAccent = theme.getForegroundLight();

        DrawUtil.drawBlur(ctx.getMatrices(), this.x, this.y, 90.0f, notificationHeight, 21.0f, BorderRadius.all(4.0f), ColorRGBA.WHITE);
        ctx.drawRoundedRect(this.x, this.y, 90.0f, notificationHeight, BorderRadius.all(4.0f), notifyBg);
        ctx.drawRoundedRect(this.x, this.y, 16.0f, notificationHeight, BorderRadius.left(4.0f, 4.0f), notifyAccent);
        DrawUtil.drawHudBackground(ctx.getMatrices(), this.x, this.y, 16.0f, notificationHeight, BorderRadius.left(4.0f, 4.0f), 0.18f);
        DrawUtil.drawRoundedCorner(ctx.getMatrices(), this.x, this.y, 90.0f, notificationHeight, 0.1f, 14.0f, theme.getColor(), BorderRadius.all(4.0f));
        ctx.drawText(iconFont, "A", this.x + (16.0f - iconFont.width("A")) / 2.0f + 1.0f, this.y + (notificationHeight - iconFont.height()) / 2.0f, theme.getColor());
        ctx.drawText(textFont, "\u041f\u0440\u0438\u043c\u0435\u0440 \u0443\u0432\u0435\u0434\u043e\u043c\u043b\u0435\u043d\u0438\u044f", this.x + 16.0f + 4.0f, this.y + (18.0f - textFont.height()) / 2.0f, theme.getWhite());
        baseY += (notificationHeight + 6.0f) * this.toggleAnimation.getValue();
        ctx.popMatrix();
        for (BaseNotification n : this.notifications) {
            float gap = 6.0f;
            float offset = n.offsetAnimation.getValue() * (notificationHeight + gap);
            if (offset < 100.0f) {
                n.render(ctx, baseX, baseY + offset, textFont, theme, notificationHeight, this);
                continue;
            }
            n.timestamp = System.currentTimeMillis();
        }
        int index = 0;
        while (iterator.hasNext()) {
            BaseNotification n = iterator.next();
            if (!n.fadingOut && now - n.timestamp > 1500L) {
                n.fadingOut = true;
                n.alphaAnimation.update(0.0f);
            }
            if (n.fadingOut && n.alphaAnimation.getValue() < 0.01f) {
                iterator.remove();
                continue;
            }
            if (!n.fadingOut) {
                if (n.offsetAnimation.isDone() && n.offsetAnimation.getValue() == 0.0f) {
                    n.offsetAnimation.reset(index);
                }
                n.offsetAnimation.update(index);
                ++index;
            }
            n.alphaAnimation.update(!n.fadingOut ? 1 : 0);
        }
    }

    private static abstract class BaseNotification {
        long timestamp;
        boolean fadingOut = false;
        final Animation offsetAnimation = new Animation(300L, Easing.QUAD_IN_OUT);
        final Animation alphaAnimation = new Animation(300L, Easing.QUAD_IN_OUT);

        private BaseNotification() {
        }

        abstract void render(CustomDrawContext var1, float var2, float var3, Font var4, Theme var5, float var6, NotifyComponent var7);
    }

    private static class ModuleNotification
    extends BaseNotification {
        final Module module;
        final boolean enabled;

        ModuleNotification(Module module, boolean enabled) {
            this.module = module;
            this.enabled = enabled;
        }

        @Override
        void render(CustomDrawContext ctx, float x, float y, Font textFont, Theme theme, float notificationHeight, NotifyComponent parent) {
            float alpha;
            if (this.timestamp == 0L) {
                this.timestamp = System.currentTimeMillis();
            }
            float borderRadius = 4.0f;
            float iconBgWidth = 16.0f;
            ColorRGBA headerBg = theme.getForegroundLight();
            ColorRGBA rowBg = new ColorRGBA(10, 10, 12, 215);
            ColorRGBA primary = this.enabled ? theme.getColor() : theme.getGrayLight();
            ColorRGBA textColor = this.enabled ? theme.getWhite() : theme.getGray();
            String moduleName = this.module.getName();
            String statusText = "  was " + (this.enabled ? "enabled" : "disabled");
            float moduleNameWidth = textFont.width(moduleName);
            float statusTextWidth = textFont.width(statusText);
            float width = iconBgWidth + 4.0f + moduleNameWidth + statusTextWidth + 4.0f;
            parent.height = notificationHeight;
            parent.width = 100.0f;
            float scale = alpha = this.alphaAnimation.getValue();
            Font iconFont = Fonts.ICONS.getFont(6.0f);
            ctx.getMatrices().push();
            ctx.getMatrices().translate((x += (100.0f - width) / 2.0f) + width / 2.0f, y + notificationHeight / 2.0f, 0.0f);
            ctx.getMatrices().scale(scale, scale, 1.0f);
            ctx.getMatrices().translate(-(x + width / 2.0f), -(y + notificationHeight / 2.0f), 0.0f);
            DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, notificationHeight, 21.0f, BorderRadius.all(borderRadius), ColorRGBA.WHITE);
            ctx.drawRoundedRect(x, y, width, notificationHeight, BorderRadius.all(borderRadius), rowBg);
            ctx.drawRoundedRect(x, y, iconBgWidth, notificationHeight, BorderRadius.left(borderRadius, borderRadius), headerBg);
            DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, iconBgWidth, notificationHeight, BorderRadius.left(borderRadius, borderRadius), 0.18f);
            String icon = this.module.getCategory().getIcon();
            float iconX = x + (iconBgWidth - iconFont.width(icon)) / 2.0f + 1.0f;
            float iconY = y + (notificationHeight - iconFont.height()) / 2.0f;
            ctx.drawText(iconFont, icon, iconX, iconY, primary);
            float textX = x + iconBgWidth + 4.0f;
            float textY = y + (notificationHeight - textFont.height()) / 2.0f;
            ctx.drawText(textFont, moduleName, textX, textY, primary);
            ctx.drawText(textFont, statusText, textX + moduleNameWidth, textY, textColor);
            ctx.getMatrices().pop();
        }
    }

    private static class TextNotification
    extends BaseNotification {
        final String icon;
        final Text text;

        TextNotification(String icon, Text text) {
            this.icon = icon;
            this.text = text;
        }

        @Override
        void render(CustomDrawContext ctx, float x, float y, Font textFont, Theme theme, float notificationHeight, NotifyComponent parent) {
            float alpha;
            if (this.timestamp == 0L) {
                this.timestamp = System.currentTimeMillis();
            }
            float borderRadius = 4.0f;
            float iconBgWidth = 16.0f;
            ColorRGBA headerBg = theme.getForegroundLight();
            ColorRGBA rowBg = new ColorRGBA(10, 10, 12, 215);
            ColorRGBA primary = theme.getColor();
            ColorRGBA textColor = theme.getWhite();
            float contentWidth = textFont.width(this.text);
            float width = iconBgWidth + 4.0f + contentWidth + 4.0f;
            parent.height = notificationHeight;
            parent.width = Math.max(parent.width, 100.0f);
            float scale = alpha = this.alphaAnimation.getValue();
            Font iconFont = Fonts.ICONS.getFont(6.0f);
            ctx.getMatrices().push();
            ctx.getMatrices().translate((x += (100.0f - width) / 2.0f) + width / 2.0f, y + notificationHeight / 2.0f, 0.0f);
            ctx.getMatrices().scale(scale, scale, 1.0f);
            ctx.getMatrices().translate(-(x + width / 2.0f), -(y + notificationHeight / 2.0f), 0.0f);
            DrawUtil.drawBlur(ctx.getMatrices(), x, y, width, notificationHeight, 21.0f, BorderRadius.all(borderRadius), ColorRGBA.WHITE);
            ctx.drawRoundedRect(x, y, width, notificationHeight, BorderRadius.all(borderRadius), rowBg);
            ctx.drawRoundedRect(x, y, iconBgWidth, notificationHeight, BorderRadius.left(borderRadius, borderRadius), headerBg);
            DrawUtil.drawHudBackground(ctx.getMatrices(), x, y, iconBgWidth, notificationHeight, BorderRadius.left(borderRadius, borderRadius), 0.18f);
            float iconX = x + (iconBgWidth - iconFont.width(this.icon)) / 2.0f + 1.0f;
            float iconY = y + (notificationHeight - iconFont.height()) / 2.0f;
            ctx.drawText(iconFont, this.icon, iconX, iconY, primary);
            float textX = x + iconBgWidth + 4.0f;
            float textY = y + (notificationHeight - textFont.height()) / 2.0f;
            ctx.drawText(textFont, this.text, textX, textY);
            ctx.getMatrices().pop();
        }
    }
}

