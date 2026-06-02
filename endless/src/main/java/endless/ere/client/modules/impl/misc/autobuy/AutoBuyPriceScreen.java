package endless.ere.client.modules.impl.misc.autobuy;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.Vector2f;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import endless.ere.Endless;
import endless.ere.base.animations.base.Animation;
import endless.ere.base.animations.base.Easing;
import endless.ere.base.autobuy.item.ItemBuy;
import endless.ere.base.font.Fonts;
import endless.ere.client.screens.panelgui.RenderUtil;
import endless.ere.utility.render.display.TextBox;
import endless.ere.utility.render.display.base.BorderRadius;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI настройки цен в стиле Panel: blur + hud-grid + плавный fade-in.
 * Список предметов из {@link endless.ere.base.autobuy.AutoBuyManager} (funtime/hollyworld/vanilla),
 * состояние enabled/maxPrice — в {@link PriceStore}.
 */
public class AutoBuyPriceScreen extends Screen {

    private static final float WIDTH = 380f;
    private static final float HEIGHT = 320f;
    private static final float HEADER_H = 28f;
    private static final float TAB_H = 22f;
    private static final float ROW_H = 28f;
    private static final float ROW_GAP = 3f;
    private static final float PAD = 8f;

    private final Animation alpha = new Animation(220, 0.0f, Easing.CUBIC_OUT);
    private final double ANIM_S = 0.22f;

    private float x, y;
    private String tab;

    private float scroll = 0f;
    private float smoothScroll = 0f;

    private TextBox activeBox = null;
    private String activeItem = null;

    public AutoBuyPriceScreen() { this("FunTime"); }

    public AutoBuyPriceScreen(String startTab) {
        super(Text.literal("AutoBuy Prices"));
        this.tab = startTab;
        PriceStore.getInstance().load();
        alpha.setValue(0f);
    }

    @Override
    protected void init() {
        super.init();
        this.x = (client.getWindow().getScaledWidth() - WIDTH) / 2f;
        this.y = (client.getWindow().getScaledHeight() - HEIGHT) / 2f;
        alpha.setDuration((long) (ANIM_S * 1000));
        alpha.setEasing(Easing.CUBIC_OUT);
        alpha.animateTo(1.0f);
    }

    @Override
    public void tick() {
        super.tick();
        alpha.update();
        if (alpha.getTargetValue() == 0.0D && !(!alpha.isDone())) {
            client.setScreen(null);
        }
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float partialTick) {
        float a = alpha.getValue();
        int bgAlpha = (int) (a * 200);
        RenderUtil.drawRoundedRect(g, 0, 0, client.getWindow().getScaledWidth(),
                client.getWindow().getScaledHeight(), 0f, new Color(0, 0, 0, bgAlpha).getRGB());
    }

    private List<ItemBuy> currentItems() {
        var mgr = Endless.getInstance().getAutoBuyManager();
        if (mgr == null) return List.of();
        return switch (tab) {
            case "FunTime"   -> mgr.getFuntime();
            case "HolyWorld" -> mgr.getHollyworld();
            case "Vanilla"   -> mgr.getVanilla();
            default          -> List.of();
        };
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float partialTicks) {
        float a = alpha.getValue();
        if (a <= 0.01f) return;

        int panelAlpha = (int) (a * 230);
        int headerAlpha = (int) (a * 255);

        // фон + блюр + сетка
        RenderUtil.Blur.drawBlur(g, x, y, WIDTH, HEIGHT, 8f, 10, -1);
        RenderUtil.drawRoundedRect(g, x, y, WIDTH, HEIGHT, 8f, new Color(18, 14, 22, panelAlpha).getRGB());
        endless.ere.utility.render.display.shader.DrawUtil.drawHudGrid(
                g.getMatrices(), x, y, WIDTH, HEIGHT,
                BorderRadius.all(8f), 14f, 0.6f, 0.045f);

        renderHeader(g, headerAlpha);
        renderTabs(g, mouseX, mouseY, headerAlpha);
        renderList(g, mouseX, mouseY, a);
    }

    private void renderHeader(DrawContext g, int headerAlpha) {
        int themeColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor()
                .withAlpha(headerAlpha).getRGB();

        // Заголовок AutoBuy крупнее, "Цены" мельче, на одной линии но "Цены" чуть выше базовой
        Fonts.MEDIUM.getFont(13).drawString(g, "AutoBuy",
                x + 14f, y + 9f, new Color(235, 235, 235, headerAlpha).getRGB());

        // "Цены" - меньше, поднята на уровень текста AutoBuy (выше базовой линии)
        float autoBuyW = Fonts.MEDIUM.getFont(13).getStringWidth("AutoBuy");
        Fonts.MEDIUM.getFont(8).drawString(g, "Цены",
                x + 14f + autoBuyW + 6f, y + 12f, themeColor);

        // подсказка справа
        String hint = "ESC  сохранить и закрыть";
        float w = Fonts.MEDIUM.getFont(7).getStringWidth(hint);
        Fonts.MEDIUM.getFont(7).drawString(g, hint,
                x + WIDTH - w - 14f, y + 13f, new Color(160, 160, 160, headerAlpha).getRGB());

        int sepAlpha = (int) (headerAlpha * 0.12f);
        RenderUtil.drawRoundedRect(g, x + 10f, y + HEADER_H - 2f,
                WIDTH - 20f, 0.6f, 0.3f, new Color(255, 255, 255, sepAlpha).getRGB());
    }

    private void renderTabs(DrawContext g, int mouseX, int mouseY, int headerAlpha) {
        String[] tabs = { "FunTime", "HolyWorld", "Vanilla" };
        float tabsY = y + HEADER_H + 4f;
        float tabsX = x + PAD;
        float totalW = WIDTH - PAD * 2;
        float tabW = (totalW - 4f * (tabs.length - 1)) / tabs.length;

        int themeColor = Endless.getInstance().getThemeManager().getCurrentTheme().getColor()
                .withAlpha(headerAlpha).getRGB();

        for (String t : tabs) {
            boolean active = t.equals(tab);
            boolean hovered = mouseX >= tabsX && mouseX <= tabsX + tabW
                    && mouseY >= tabsY && mouseY <= tabsY + TAB_H;

            int bg = active ? new Color(90, 36, 60, (int)(headerAlpha * 0.85f)).getRGB()
                    : (hovered ? new Color(40, 32, 50, (int)(headerAlpha * 0.7f)).getRGB()
                    : new Color(28, 22, 36, (int)(headerAlpha * 0.6f)).getRGB());

            RenderUtil.drawRoundedRect(g, tabsX, tabsY, tabW, TAB_H, 4f, bg);

            // Названия серверов крупнее
            float tw = Fonts.MEDIUM.getFont(10).getStringWidth(t);
            int color = active ? new Color(255, 255, 255, headerAlpha).getRGB()
                    : new Color(160, 160, 160, headerAlpha).getRGB();
            Fonts.MEDIUM.getFont(10).drawString(g, t, tabsX + (tabW - tw) / 2f, tabsY + 6f, color);

            tabsX += tabW + 4f;
        }
    }

    private void renderList(DrawContext g, int mouseX, int mouseY, float a) {
        float listX = x + PAD;
        float listY = y + HEADER_H + TAB_H + 8f;
        float listW = WIDTH - PAD * 2;
        float listH = HEIGHT - (HEADER_H + TAB_H + 8f) - PAD;

        smoothScroll += (scroll - smoothScroll) * 0.2f;

        List<ItemBuy> items = currentItems();

        g.enableScissor((int) listX, (int) listY, (int) (listX + listW), (int) (listY + listH));

        float cy = listY + smoothScroll;
        for (ItemBuy item : items) {
            if (cy + ROW_H >= listY - ROW_H && cy <= listY + listH + ROW_H) {
                renderRow(g, item, listX, cy, listW, mouseX, mouseY, a);
            }
            cy += ROW_H + ROW_GAP;
        }

        g.disableScissor();

        // скроллбар
        float contentH = items.size() * (ROW_H + ROW_GAP) - ROW_GAP;
        float maxScroll = Math.max(0f, contentH - listH);
        if (maxScroll > 0) {
            float trackH = listH;
            float thumbH = Math.max(15f, trackH * (listH / contentH));
            float scrollPercent = Math.abs(smoothScroll) / maxScroll;
            float thumbY = listY + (trackH - thumbH) * scrollPercent;
            float scrollX = listX + listW - 3f;
            int scrollColor = new Color(255, 255, 255, (int)(a * 120)).getRGB();
            RenderUtil.drawRoundedRect(g, scrollX, thumbY, 2f, thumbH, 1f, scrollColor);
        }
    }

    private void renderRow(DrawContext g, ItemBuy item, float rx, float ry, float rw,
                           float mouseX, float mouseY, float a) {
        String name = item.getDisplayName();
        PriceStore.Entry entry = PriceStore.getInstance().get(tab, name);
        boolean focused = activeItem != null && activeItem.equals(name);

        int rowBg = new Color(28, 22, 36, (int)(a * 200)).getRGB();
        int rowHover = new Color(40, 32, 50, (int)(a * 220)).getRGB();
        boolean rowHovered = mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + ROW_H;

        RenderUtil.drawRoundedRect(g, rx, ry, rw, ROW_H, 4f, rowHovered ? rowHover : rowBg);

        // чекбокс
        // чекбокс - крупнее, с галочкой
        float cbSize = 14f;
        float cbX = rx + 8f;
        float cbY = ry + (ROW_H - cbSize) / 2f;

        int themeRGB = endless.ere.Endless.getInstance().getThemeManager().getCurrentTheme().getColor()
                .withAlpha((int)(a * 255)).getRGB();
        int cbBg = entry.enabled ? themeRGB : new Color(45, 38, 55, (int)(a * 200)).getRGB();
        RenderUtil.drawRoundedRect(g, cbX, cbY, cbSize, cbSize, 4f, cbBg);
        if (entry.enabled) {
            // Галочка (глиф "k" в ICONS шрифте)
            int checkColor = new Color(255, 255, 255, (int)(a * 255)).getRGB();
            float cw = Fonts.ICONS.getFont(8).getStringWidth("k");
            Fonts.ICONS.getFont(8).drawString(g, "k",
                    cbX + (cbSize - cw) / 2f, cbY + 3.5f, checkColor);
        }

        // иконка - увеличенная (в 1.4x = ~22px)
        ItemStack stack = item.getItemStack();
        if (stack != null) {
            float iconX = cbX + cbSize + 7f;
            float iconY = ry + (ROW_H - 16 * 1.4f) / 2f;
            float scale = 1.4f;
            g.getMatrices().push();
            g.getMatrices().translate(iconX, iconY, 0);
            g.getMatrices().scale(scale, scale, 1f);
            g.drawItem(stack, 0, 0);
            g.getMatrices().pop();
        }

        // имя - крупнее
        Fonts.MEDIUM.getFont(9).drawString(g, name, rx + 56f, ry + 8.5f,
                new Color(230, 230, 230, (int)(a * 255)).getRGB());

        // кнопка парсинга (иконка поиска / стоп)
        float btnSize = 18f;
        float btnX = rx + rw - btnSize - 6f;
        float btnY = ry + (ROW_H - btnSize) / 2f;
        boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
        boolean isParsingThis = endless.ere.client.modules.impl.misc.AutoBuy.INSTANCE.isParsing()
                && name.equals(endless.ere.client.modules.impl.misc.AutoBuy.INSTANCE.getParseItemName());
        
        int btnBg;
        if (isParsingThis) {
            btnBg = new Color(170, 0, 0, (int)(a * 230)).getRGB();
        } else if (btnHovered) {
            btnBg = themeRGB;
        } else {
            btnBg = new Color(45, 38, 55, (int)(a * 200)).getRGB();
        }
        RenderUtil.drawRoundedRect(g, btnX, btnY, btnSize, btnSize, 4f, btnBg);
        
        // Иконка: используем глифы из ICONS шрифта
        // V = увеличительное стекло/значок поиска, S = квадрат стоп
        String iconChar = isParsingThis ? "S" : "V";
        int iconColor = new Color(255, 255, 255, (int)(a * 255)).getRGB();
        float iconW = Fonts.ICONS.getFont(9).getStringWidth(iconChar);
        Fonts.ICONS.getFont(9).drawString(g, iconChar,
                btnX + (btnSize - iconW) / 2f, btnY + 4.5f, iconColor);

        // поле цены
        float boxW = 70f;
        float boxX = btnX - boxW - 4f;
        float boxY = ry + 4f;
        float boxH = ROW_H - 8f;

        int boxBg = focused ? new Color(38, 30, 48, (int)(a * 220)).getRGB()
                : new Color(22, 18, 30, (int)(a * 200)).getRGB();
        RenderUtil.drawRoundedRect(g, boxX, boxY, boxW, boxH, 3f, boxBg);

        if (focused && activeBox != null) {
            // используем встроенный TextBox.render через CustomDrawContext
            endless.ere.utility.render.display.base.CustomDrawContext ctx =
                    endless.ere.utility.render.display.base.CustomDrawContext.of(g);
            activeBox.render(ctx, boxX + 6f, boxY + 3f,
                    new endless.ere.utility.render.display.base.color.ColorRGBA(255, 255, 255, (int)(a * 255)),
                    new endless.ere.utility.render.display.base.color.ColorRGBA(140, 140, 140, (int)(a * 255)));
        } else {
            String txt = entry.maxPrice > 0 ? formatPrice(entry.maxPrice) : "цена...";
            int color = entry.maxPrice > 0
                    ? new Color(255, 255, 255, (int)(a * 255)).getRGB()
                    : new Color(140, 140, 140, (int)(a * 255)).getRGB();
            // цена крупнее
            Fonts.MEDIUM.getFont(9).drawString(g, txt, boxX + 6f, boxY + 4f, color);
        }
    }

    private String formatPrice(int price) {
        StringBuilder sb = new StringBuilder();
        String s = String.valueOf(price);
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (c > 0 && c % 3 == 0) sb.insert(0, '.');
            sb.insert(0, s.charAt(i));
            c++;
        }
        return "$" + sb;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        // вкладки
        String[] tabs = { "FunTime", "HolyWorld", "Vanilla" };
        float tabsY = y + HEADER_H + 4f;
        float tabsX = x + PAD;
        float totalW = WIDTH - PAD * 2;
        float tabW = (totalW - 4f * (tabs.length - 1)) / tabs.length;
        for (String t : tabs) {
            if (mx >= tabsX && mx <= tabsX + tabW && my >= tabsY && my <= tabsY + TAB_H) {
                tab = t;
                commitActiveBox();
                scroll = 0f;
                smoothScroll = 0f;
                return true;
            }
            tabsX += tabW + 4f;
        }

        // строки
        float listX = x + PAD;
        float listY = y + HEADER_H + TAB_H + 8f;
        float listW = WIDTH - PAD * 2;
        float listH = HEIGHT - (HEADER_H + TAB_H + 8f) - PAD;

        if (mx < listX || mx > listX + listW || my < listY || my > listY + listH) {
            commitActiveBox();
            return super.mouseClicked(mx, my, button);
        }

        List<ItemBuy> items = currentItems();
        float cy = listY + smoothScroll;
        for (ItemBuy item : items) {
            if (my >= cy && my <= cy + ROW_H) {
                String name = item.getDisplayName();
                PriceStore.Entry entry = PriceStore.getInstance().get(tab, name);

                // чекбокс
                if (mx >= listX + 8f && mx <= listX + 8f + 14f) {
                    entry.enabled = !entry.enabled;
                    return true;
                }

                // кнопка P/X (парсить/стоп)
                float btnSize = 18f;
                float btnX = listX + listW - btnSize - 6f;
                float btnY = (float) cy + (ROW_H - btnSize) / 2f;
                if (mx >= btnX && mx <= btnX + btnSize && my >= btnY && my <= btnY + btnSize) {
                    commitActiveBox();
                    var ab = endless.ere.client.modules.impl.misc.AutoBuy.INSTANCE;
                    if (ab.isParsing() && name.equals(ab.getParseItemName())) {
                        ab.stopParse();
                    } else if (!ab.isParsing()) {
                        // запуск парсинга - сам отправит /ah search
                        if (!ab.isEnabled()) {
                            if (client.player != null) {
                                client.player.sendMessage(net.minecraft.text.Text.of("§c[Parser] Включи модуль AutoBuy!"), false);
                            }
                        } else {
                            ab.startParse(name);
                            // закрываем GUI чтобы видеть парсинг
                            close();
                        }
                    }
                    return true;
                }

                // поле цены
                float boxW = 70f;
                float boxX = btnX - boxW - 4f;
                float boxY = (float) cy + 4f;
                float boxH = ROW_H - 8f;
                if (mx >= boxX && mx <= boxX + boxW && my >= boxY && my <= boxY + boxH) {
                    commitActiveBox();
                    activeItem = name;
                    activeBox = new TextBox(new Vector2f(boxX + 6f, boxY + 3f),
                            Fonts.MEDIUM.getFont(7), "цена...", boxW - 12f);
                    activeBox.setCharFilter(TextBox.CharFilter.NUMBERS_ONLY);
                    activeBox.setMaxLength(11);
                    if (entry.maxPrice > 0) activeBox.setText(String.valueOf(entry.maxPrice));
                    activeBox.onMouseClicked(mx, my, endless.ere.utility.game.other.MouseButton.fromButtonIndex(button));
                    return true;
                }
                return true;
            }
            cy += ROW_H + ROW_GAP;
        }

        commitActiveBox();
        return super.mouseClicked(mx, my, button);
    }

    private void commitActiveBox() {
        if (activeBox != null && activeItem != null) {
            String text = activeBox.getText();
            int v = 0;
            try { v = text.isEmpty() ? 0 : Integer.parseInt(text); } catch (NumberFormatException ignored) {}
            PriceStore.getInstance().get(tab, activeItem).maxPrice = v;
        }
        activeBox = null;
        activeItem = null;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (activeBox != null && activeBox.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (activeBox != null) activeBox.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        List<ItemBuy> items = currentItems();
        float listH = HEIGHT - (HEADER_H + TAB_H + 8f) - PAD;
        float contentH = items.size() * (ROW_H + ROW_GAP) - ROW_GAP;
        float maxScroll = Math.max(0f, contentH - listH);
        scroll += (float) (vertical * (ROW_H + ROW_GAP));
        if (scroll > 0f) scroll = 0f;
        if (scroll < -maxScroll) scroll = -maxScroll;
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void close() {
        commitActiveBox();
        PriceStore.getInstance().save();
        alpha.setDuration((long) (ANIM_S * 1000));
        alpha.setEasing(Easing.CUBIC_IN);
        alpha.animateTo(0f);
    }
}
