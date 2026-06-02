package dev.endless.module.list.render.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import dev.endless.util.IMinecraft;
import dev.endless.util.base.Instance;
import dev.endless.util.render.math.Animation;
import dev.endless.util.render.math.Easing;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationsElement implements IMinecraft {

    private static final CopyOnWriteArrayList<Notification> notifications = new CopyOnWriteArrayList<>();

    public void post(String name, boolean enabled, dev.endless.module.ModuleCategory category) {
        Notification notification = new Notification(name, enabled);
        notification.category = category;
        notifications.add(0, notification);
    }

    public void postWarning(String text) {
        notifications.add(0, new Notification(text, true, true));
    }

    public void postTotem(Text tagText, boolean enchanted) {
        MutableText full = tagText.copy()
                .append(Text.literal(" потерял тотем, зачарован: ").setStyle(Style.EMPTY.withColor(0xFFFFFF)));
        Notification n = new Notification(full.getString(), true, true);
        n.isTotem = true;
        n.enchanted = enchanted;
        n.totemTagText = full;
        notifications.add(0, n);
    }

    public void render(DrawContext context) {
        float centerX = MinecraftClient.getInstance().getWindow().getScaledWidth() / 2f;
        float startY = (MinecraftClient.getInstance().getWindow().getScaledHeight() / 2f) + 20f;
        float offset = 0;

        for (Notification n : notifications) {
            if (System.currentTimeMillis() - n.time > n.duration && n.anim.getValue() <= 0.01) {
                notifications.remove(n);
                continue;
            }

            boolean expiring = System.currentTimeMillis() - n.time > n.duration;
            n.anim.run(expiring ? 0 : 1);

            double animValue = n.anim.getValue();
            if (animValue <= 0.01) continue;

            float clampedAlpha = (float) Math.max(0.0, Math.min(1.0, animValue));
            int alphaInt = (int) (255 * clampedAlpha);

            float height = 14.5f;
            String fullText;
            String iconCode;
            int iconColor;

            if (n.isWarning) {
                fullText = n.customText;
                iconCode = "G";
                iconColor = ColorProvider.rgba(255, 255, 255, alphaInt);
            } else {
                fullText = n.name;
                iconCode = n.enabled ? "J" : "K";
                iconColor = ColorProvider.rgba(255, 255, 255, alphaInt);
            }

            float textWidth = Fonts.SFMEDIUM.get().getWidth(fullText, 7f);
            float iconSize = n.isWarning ? 8f : 9f;
            float iconWidth = n.isTotem ? 10f : Fonts.ICONS_NURIK.get().getWidth(iconCode, iconSize);
            float dotExtra = n.isTotem ? 2f : 0f;

            // Текст статуса (enable / disable) вместо переключателя
            String statusText = n.enabled ? "enable" : "disable";
            float statusTextWidth = Fonts.SFMEDIUM.get().getWidth(statusText, 7f);

            // Новый расчет ширины для обычных уведомлений (иконка + | + текст + статус)
            float width;
            if (n.isWarning) {
                width = iconWidth + textWidth + 22f;
            } else if (n.isTotem) {
                width = iconWidth + textWidth + 18f + dotExtra;
            } else {
                // Иконка категории + разделитель + текст + статус
                float categoryIconWidth = Fonts.ICONS_MINCED.get().getWidth("a", 8f);
                float separatorWidth = Fonts.SFMEDIUM.get().getWidth("|", 7f);
                width = 4f + categoryIconWidth + 3f + separatorWidth + 3f + textWidth + 6f + statusTextWidth + 4f;
            }

            float x = centerX - (width / 2f);
            float y = startY + offset;

            context.getMatrices().push();
            context.getMatrices().translate(centerX, y + height / 2f, 0);
            context.getMatrices().scale((float) animValue, (float) animValue, 1f);
            context.getMatrices().translate(-centerX, -(y + height / 2f), 0);

            Interface interfaceModule = Instance.get(Interface.class);
            if (interfaceModule != null) interfaceModule.drawHeaderBackground(x, y, width, height, 2.5f, alphaInt);

            if (n.isTotem) {
                context.getMatrices().push();
                context.getMatrices().translate(x + 4f, y + 2.5f, 0);
                context.getMatrices().scale(0.6f, 0.6f, 1f);
                context.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), 0, 0);
                context.getMatrices().pop();
            } else if (n.isWarning) {
                DrawUtil.drawText(Fonts.ICONS_NURIK.get(), iconCode, x + 5, y + 4, iconColor, iconSize);
            }

            if (n.isTotem && n.totemTagText != null) {
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), n.totemTagText, x + 20f, y + 3f, 7f, alphaInt);
            } else if (n.isWarning) {
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), fullText, x + 23f, y + 3f, ColorProvider.rgba(255, 255, 255, alphaInt), 7f);
            } else {
                // Получаем иконку категории модуля
                String categoryIcon = switch (n.category) {
                    case COMBAT -> "a";
                    case MOVEMENT -> "b";
                    case RENDER -> "c";
                    case PLAYER -> "d";
                    case MISC -> "e";
                    default -> "e";
                };
                
                float iconSize2 = 8f;
                float iconWidth2 = Fonts.ICONS_MINCED.get().getWidth(categoryIcon, iconSize2);
                
                // Рисуем иконку категории слева
                DrawUtil.drawText(Fonts.ICONS_MINCED.get(), categoryIcon, x + 4f, y + 3.5f, ColorProvider.setAlpha(ColorProvider.getColorIcons(), alphaInt), iconSize2);
                
                // Рисуем разделитель "|"
                float separatorX = x + 4f + iconWidth2 + 3f;
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), "|", separatorX, y + 3.5f, ColorProvider.rgba(150, 150, 150, alphaInt), 7f);
                
                // Рисуем название функции
                float separatorWidth = Fonts.SFMEDIUM.get().getWidth("|", 7f);
                float textX = separatorX + separatorWidth + 3f;
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), fullText, textX, y + 3.5f, ColorProvider.rgba(255, 255, 255, alphaInt), 7f);

                // Рисуем статус справа: enable зелёным, disable красным
                int statusColor = n.enabled
                        ? ColorProvider.rgba(80, 220, 100, alphaInt)
                        : ColorProvider.rgba(230, 70, 70, alphaInt);
                float statusX = x + width - statusTextWidth - 4f;
                DrawUtil.drawText(Fonts.SFMEDIUM.get(), statusText, statusX, y + 3.5f, statusColor, 7f);
            }

            if (n.isTotem) {
                int dotColor = n.enchanted
                        ? ColorProvider.rgba(32, 255, 32, alphaInt)
                        : ColorProvider.rgba(255, 32, 32, alphaInt);
                DrawUtil.drawRound(x + width - 12f, y + (height - 6f) / 2f, 6f, 6f, 2f, dotColor);
            }

            context.getMatrices().pop();
            offset += (height + 3) * clampedAlpha;
        }
    }

    private static class Notification {
        String name;
        boolean enabled;
        long time;
        long duration = 1000;
        Animation anim = new Animation(Easing.BACK_OUT, 300);

        boolean isWarning = false;
        String customText;
        boolean isTotem = false;
        boolean enchanted = false;
        Text totemTagText = null;
        dev.endless.module.ModuleCategory category = dev.endless.module.ModuleCategory.MISC;

        public Notification(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
            this.time = System.currentTimeMillis();
        }

        public Notification(String customText, boolean enabled, boolean isWarning) {
            this.customText = customText;
            this.enabled = enabled;
            this.isWarning = isWarning;
            this.time = System.currentTimeMillis();
            this.duration = 2000;
        }
    }
}
