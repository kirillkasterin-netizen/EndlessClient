package dev.endless.ui.autobuy;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import dev.endless.util.IMinecraft;
import dev.endless.util.auction.AutoBuyItemSettings;
import dev.endless.util.auction.AutoBuyManager;
import dev.endless.util.auction.AutoBuySettingsManager;
import dev.endless.util.auction.AutoBuyableItem;
import dev.endless.util.cursor.CursorManager;
import dev.endless.util.render.helper.HoverUtil;
import dev.endless.util.render.math.Scissor;
import dev.endless.util.render.msdf.Fonts;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Кастомный экран AutoBuy: топбар с тоглами/полями, сайдбар категорий со скроллом
 * и список предметов справа со скроллом. Поля задержки поддерживают range вида "400-500".
 */
public class AutoBuyScreen extends Screen implements IMinecraft {

    private static final int WIDTH = 600;
    private static final int HEIGHT = 380;

    // Layout
    private float guiX, guiY;
    private float sideX, sideY, sideW, sideH;
    private float listX, listY, listW, listH;

    // Категории
    private final Map<String, List<AutoBuyableItem>> byCategory = new LinkedHashMap<>();
    private String selectedCategory = null;

    // Скролл списка
    private float listScroll = 0f;
    private float listTargetScroll = 0f;
    // Скролл сайдбара
    private float sideScroll = 0f;
    private float sideTargetScroll = 0f;

    private String search = "";
    private boolean searchFocused = false;

    // Inline-редактирование: editingId → буфер
    private String editingId = null;
    private String editingBuffer = "";

    public AutoBuyScreen() {
        super(Text.literal("AutoBuy"));
        rebuildCategories();
    }

    private void rebuildCategories() {
        byCategory.clear();
        for (AutoBuyableItem it : AutoBuyManager.get().getAllItems()) {
            byCategory.computeIfAbsent(it.getCategory(), k -> new java.util.ArrayList<>()).add(it);
        }
        if (selectedCategory == null && !byCategory.isEmpty()) {
            selectedCategory = byCategory.keySet().iterator().next();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        CursorManager.reset();
        CursorManager.resetIBeam();
        CursorManager.resetClick();

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        DrawUtil.drawRound(0, 0, sw, sh, 0, ColorProvider.rgba(15, 15, 20, 200));

        guiX = (sw - WIDTH) / 2f;
        guiY = (sh - HEIGHT) / 2f;

        DrawUtil.drawRoundBlur(guiX, guiY, WIDTH, HEIGHT, 8, ColorProvider.rgba(75, 75, 75, 180), 18);
        DrawUtil.drawRound(guiX, guiY, WIDTH, HEIGHT, 8, ColorProvider.rgba(20, 20, 25, 150));

        // Шапка
        DrawUtil.drawText(Fonts.SFBOLD.get(), "AutoBuy", guiX + 12, guiY + 10, ColorProvider.getColorClient(), 11);
        String stateLabel = "Состояние: " + describeState(AutoBuyManager.get().getState());
        DrawUtil.drawText(Fonts.SFREGULAR.get(), stateLabel,
                guiX + 12, guiY + 22, ColorProvider.rgba(160, 160, 170, 255), 6.5f);
        renderCloseButton(mouseX, mouseY);

        // Топ-бар
        renderTopBar(context, mouseX, mouseY);

        // Сайдбар + список
        sideX = guiX + 10;
        sideY = guiY + 100;
        sideW = 130;
        sideH = HEIGHT - 110;
        renderSidebar(sideX, sideY, sideW, sideH, mouseX, mouseY);

        listX = sideX + sideW + 8;
        listY = sideY;
        listW = WIDTH - (listX - guiX) - 10;
        listH = sideH;
        renderList(context, listX, listY, listW, listH, mouseX, mouseY);

        long w = mc.getWindow().getHandle();
        if (CursorManager.shouldBeHand()) GLFW.glfwSetCursor(w, GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR));
        else if (CursorManager.shouldIBeam()) GLFW.glfwSetCursor(w, GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR));
        else GLFW.glfwSetCursor(w, GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR));
    }

    private void renderCloseButton(int mouseX, int mouseY) {
        float bx = guiX + WIDTH - 22;
        float by = guiY + 10;
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, bx, by, 14, 14);
        if (hov) CursorManager.requestHand();
        DrawUtil.drawRound(bx, by, 14, 14, 4, hov ? ColorProvider.rgba(180, 60, 60, 200) : ColorProvider.rgba(40, 40, 45, 180));
        DrawUtil.drawText(Fonts.SFBOLD.get(), "x", bx + 4.5f, by + 3.5f, ColorProvider.rgba(255, 255, 255, 255), 7);
    }

    // ─────────────────── TOP BAR ───────────────────

    private void renderTopBar(DrawContext ctx, int mouseX, int mouseY) {
        AutoBuyManager mgr = AutoBuyManager.get();
        float y = guiY + 40;
        float h = 50;

        DrawUtil.drawRound(guiX + 10, y, WIDTH - 20, h, 5, ColorProvider.rgba(28, 28, 33, 180));

        toggleZones.clear();
        toggleHandlers.clear();
        numberZones.clear();

        // Тоглы
        renderToggle(guiX + 18, y + 8, "AutoBuy", mgr.isEnabled(), mouseX, mouseY, () -> {
            mgr.setEnabled(!mgr.isEnabled());
            AutoBuySettingsManager.save();
        });
        renderToggle(guiX + 110, y + 8, "Парсер", mgr.isAutoParserEnabled(), mouseX, mouseY, () -> {
            mgr.setAutoParserEnabled(!mgr.isAutoParserEnabled());
            AutoBuySettingsManager.save();
        });

        // Поля
        renderNumberField(guiX + 200, y + 8, 80, "Глобал. лимит",
                String.valueOf(mgr.getGlobalMaxPrice()),
                "global_max", false, mouseX, mouseY, (v) -> {
                    mgr.setGlobalMaxPrice(parseInt(v, mgr.getGlobalMaxPrice()));
                    AutoBuySettingsManager.save();
                });

        renderNumberField(guiX + 290, y + 8, 70, "Buy мс",
                mgr.getBuyDelayString(),
                "buy_delay", true, mouseX, mouseY, (v) -> {
                    mgr.setBuyDelayString(v);
                    AutoBuySettingsManager.save();
                });

        renderNumberField(guiX + 370, y + 8, 70, "Refresh мс",
                mgr.getRefreshIntervalString(),
                "refresh_int", true, mouseX, mouseY, (v) -> {
                    mgr.setRefreshIntervalString(v);
                    AutoBuySettingsManager.save();
                });

        renderNumberField(guiX + 450, y + 8, 60, "Парсер мс",
                mgr.getParserIntervalString(),
                "parser_int", true, mouseX, mouseY, (v) -> {
                    mgr.setParserIntervalString(v);
                    AutoBuySettingsManager.save();
                });

        renderNumberField(guiX + 520, y + 8, 60, "Парсер %",
                String.valueOf((int) Math.round(mgr.getParserMultiplier() * 100)),
                "parser_pct", false, mouseX, mouseY, (v) -> {
                    int pct = parseInt(v, 90);
                    mgr.setParserMultiplier(pct / 100.0);
                    AutoBuySettingsManager.save();
                });

        // Поиск
        float sx = guiX + 18;
        float sy = y + 30;
        float sww = WIDTH - 36;
        renderSearch(sx, sy, sww, 14, mouseX, mouseY);
    }

    private void renderToggle(float x, float y, String label, boolean state,
                              int mouseX, int mouseY, Runnable onClick) {
        float w = 85, h = 18;
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, x, y, w, h);
        if (hov) CursorManager.requestHand();

        int bg = state
                ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 200)
                : ColorProvider.rgba(45, 45, 50, 200);
        DrawUtil.drawRound(x, y, w, h, 4, bg);

        float dotW = 6, dotH = 6;
        float dotX = state ? x + w - dotW - 4 : x + 4;
        DrawUtil.drawRound(dotX, y + h / 2 - dotH / 2, dotW, dotH, dotH / 2,
                ColorProvider.rgba(255, 255, 255, 255));

        DrawUtil.drawText(Fonts.SFREGULAR.get(), label,
                state ? x + 5 : x + 14, y + 5,
                ColorProvider.rgba(255, 255, 255, 255), 6.5f);

        toggleZones.put(label, new float[]{x, y, w, h});
        toggleHandlers.put(label, onClick);
    }

    private final Map<String, float[]> toggleZones = new java.util.LinkedHashMap<>();
    private final Map<String, Runnable> toggleHandlers = new java.util.LinkedHashMap<>();

    private void renderNumberField(float x, float y, float w, String label,
                                   String value, String id, boolean allowDash,
                                   int mouseX, int mouseY,
                                   java.util.function.Consumer<String> onCommit) {
        float h = 18;
        boolean editing = id.equals(editingId);
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, x, y, w, h);
        if (hov) CursorManager.requestIBeam();

        DrawUtil.drawRound(x, y, w, h, 4,
                editing ? ColorProvider.rgba(35, 35, 45, 220) : ColorProvider.rgba(28, 28, 32, 180));
        if (editing) {
            DrawUtil.drawRound(x, y, w, 0.7f, 0,
                    ColorProvider.setAlpha(ColorProvider.getColorClient(), 220));
        }

        String show = editing ? editingBuffer : value;
        DrawUtil.drawText(Fonts.SFREGULAR.get(), show, x + 4, y + 5,
                ColorProvider.rgba(220, 220, 230, 255), 6.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), label,
                x, y + h + 1, ColorProvider.rgba(140, 140, 150, 200), 5.5f);

        numberZones.put(id, new NumZone(x, y, w, h, value, allowDash, onCommit));
    }

    private record NumZone(float x, float y, float w, float h, String currentValue,
                           boolean allowDash, java.util.function.Consumer<String> onCommit) {}
    private final Map<String, NumZone> numberZones = new java.util.LinkedHashMap<>();

    private void renderSearch(float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean hov = HoverUtil.isHovered(mouseX, mouseY, x, y, w, h);
        if (hov) CursorManager.requestIBeam();
        DrawUtil.drawRound(x, y, w, h, 4, ColorProvider.rgba(25, 25, 30, 200));
        String show = (search.isEmpty() && !searchFocused) ? "Поиск…" : search;
        String cur = searchFocused && System.currentTimeMillis() % 1000 > 500 ? "|" : "";
        int color = (search.isEmpty() && !searchFocused)
                ? ColorProvider.rgba(120, 120, 130, 255)
                : ColorProvider.rgba(220, 220, 230, 255);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), show + cur, x + 5, y + 4, color, 7);
        searchZone = new float[]{x, y, w, h};
    }

    private float[] searchZone = null;

    // ─────────────────── SIDEBAR ───────────────────

    /** Имя псевдо-категории «История покупок». */
    private static final String CAT_HISTORY = "История покупок";

    private void renderSidebar(float x, float y, float w, float h, int mouseX, int mouseY) {
        DrawUtil.drawRound(x, y, w, h, 5, ColorProvider.rgba(25, 25, 30, 180));

        sideScroll += (sideTargetScroll - sideScroll) * 0.2f;

        Scissor.push();
        Scissor.setFromComponentCoordinates(x + 1, y + 1, w - 2, h - 2);

        float catH = 22;
        float catSp = 3;
        float padY = 6f;
        float catY = y + padY - sideScroll;
        sidebarZones.clear();

        // Сначала — пункт "История"
        java.util.List<String> tabs = new java.util.ArrayList<>();
        tabs.add(CAT_HISTORY);
        tabs.addAll(byCategory.keySet());

        for (String cat : tabs) {
            boolean visible = catY + catH >= y && catY <= y + h;
            if (visible) {
                boolean selected = cat.equals(selectedCategory);
                boolean hov = HoverUtil.isHovered(mouseX, mouseY, x + 4, catY, w - 8, catH);
                if (hov && mouseY >= y && mouseY <= y + h) CursorManager.requestHand();

                int bg = selected
                        ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 180)
                        : (hov ? ColorProvider.rgba(40, 40, 50, 200) : ColorProvider.rgba(0, 0, 0, 0));
                DrawUtil.drawRound(x + 4, catY, w - 8, catH, 4, bg);

                int textColor = selected
                        ? ColorProvider.rgba(255, 255, 255, 255)
                        : ColorProvider.rgba(200, 200, 210, 255);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), cat, x + 10, catY + 7, textColor, 7);

                if (CAT_HISTORY.equals(cat)) {
                    int n = AutoBuyManager.get().getPurchaseHistory().size();
                    String countStr = String.valueOf(n);
                    float cw = Fonts.SFREGULAR.get().getWidth(countStr, 6);
                    DrawUtil.drawText(Fonts.SFREGULAR.get(), countStr,
                            x + w - 8 - cw, catY + 7,
                            ColorProvider.rgba(160, 160, 170, 255), 6);
                } else {
                    int count = byCategory.get(cat).size();
                    String countStr = String.valueOf(count);
                    float cw = Fonts.SFREGULAR.get().getWidth(countStr, 6);
                    DrawUtil.drawText(Fonts.SFREGULAR.get(), countStr,
                            x + w - 8 - cw, catY + 7,
                            ColorProvider.rgba(160, 160, 170, 255), 6);
                }
            }

            sidebarZones.put(cat, new float[]{x + 4, catY, w - 8, catH});
            catY += catH + catSp;
        }

        float contentH = tabs.size() * (catH + catSp);
        float maxScroll = Math.max(0, contentH - (h - padY * 2));
        sideTargetScroll = MathHelper.clamp(sideTargetScroll, 0, maxScroll);

        Scissor.unset();
        Scissor.pop();

        if (maxScroll > 0) {
            float sbW = 2f;
            float sbX = x + w - sbW - 2;
            float visibleRatio = (h - padY * 2) / contentH;
            float sbH = Math.max(20f, (h - padY * 2) * visibleRatio);
            float prog = sideScroll / maxScroll;
            float sbY = y + padY + (h - padY * 2 - sbH) * prog;
            DrawUtil.drawRound(sbX, sbY, sbW, sbH, 1f,
                    ColorProvider.setAlpha(ColorProvider.getColorClient(), 130));
        }
    }

    private final Map<String, float[]> sidebarZones = new java.util.LinkedHashMap<>();

    // ─────────────────── LIST ───────────────────

    private void renderList(DrawContext ctx, float x, float y, float w, float h, int mouseX, int mouseY) {
        DrawUtil.drawRound(x, y, w, h, 5, ColorProvider.rgba(25, 25, 30, 180));

        // Если выбрана история — отрисуем её
        if (CAT_HISTORY.equals(selectedCategory)) {
            renderHistoryList(x, y, w, h, mouseX, mouseY);
            return;
        }

        listScroll += (listTargetScroll - listScroll) * 0.18f;

        Scissor.push();
        Scissor.setFromComponentCoordinates(x + 1, y + 1, w - 2, h - 2);

        List<AutoBuyableItem> visible = currentVisible();

        float rowH = 32;
        float rowSp = 4;
        float padY = 6f;
        float curY = y + padY - listScroll;
        itemZones.clear();

        for (AutoBuyableItem item : visible) {
            boolean inViewport = curY + rowH >= y && curY <= y + h;
            if (inViewport) {
                renderRow(ctx, item, x + 6, curY, w - 12, rowH, mouseX, mouseY);
            } else {
                // Регистрируем нулевые зоны чтобы не было фантомных кликов
                itemZones.put(item.getId(), new ItemZone(item, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
            }
            curY += rowH + rowSp;
        }

        float contentH = visible.size() * (rowH + rowSp);
        float maxScroll = Math.max(0, contentH - (h - padY * 2));
        listTargetScroll = MathHelper.clamp(listTargetScroll, 0, maxScroll);

        Scissor.unset();
        Scissor.pop();

        if (maxScroll > 0) {
            float sbW = 2f;
            float sbX = x + w - sbW - 2;
            float visibleRatio = (h - padY * 2) / contentH;
            float sbH = Math.max(20f, (h - padY * 2) * visibleRatio);
            float prog = listScroll / maxScroll;
            float sbY = y + padY + (h - padY * 2 - sbH) * prog;
            DrawUtil.drawRound(sbX, sbY, sbW, sbH, 1f,
                    ColorProvider.setAlpha(ColorProvider.getColorClient(), 130));
        }
    }

    private List<AutoBuyableItem> currentVisible() {
        if (CAT_HISTORY.equals(selectedCategory)) return java.util.List.of();
        if (!search.isEmpty()) {
            return AutoBuyManager.get().getAllItems().stream()
                    .filter(it -> it.getDisplayName().toLowerCase().contains(search.toLowerCase())
                            || it.getCategory().toLowerCase().contains(search.toLowerCase()))
                    .collect(Collectors.toList());
        }
        return byCategory.getOrDefault(selectedCategory, java.util.List.of());
    }

    private void renderHistoryList(float x, float y, float w, float h, int mouseX, int mouseY) {
        listScroll += (listTargetScroll - listScroll) * 0.18f;

        Scissor.push();
        Scissor.setFromComponentCoordinates(x + 1, y + 1, w - 2, h - 2);

        java.util.List<AutoBuyManager.PurchaseEntry> history = AutoBuyManager.get().getPurchaseHistory();
        float rowH = 26;
        float rowSp = 3;
        float padY = 6f;
        float curY = y + padY - listScroll;

        if (history.isEmpty()) {
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Покупок пока нет",
                    x + 12, y + 12, ColorProvider.rgba(140, 140, 150, 200), 7);
        }

        long now = System.currentTimeMillis();
        long totalSpent = 0;
        for (AutoBuyManager.PurchaseEntry e : history) totalSpent += e.totalPrice();

        // Сводка сверху
        if (!history.isEmpty()) {
            DrawUtil.drawText(Fonts.SFBOLD.get(),
                    "Всего покупок: " + history.size() + " | потрачено: " + totalSpent + "$",
                    x + 8, curY + 4, ColorProvider.rgba(220, 220, 230, 255), 7);
            curY += 18;
        }

        for (AutoBuyManager.PurchaseEntry e : history) {
            boolean inViewport = curY + rowH >= y && curY <= y + h;
            if (inViewport) {
                DrawUtil.drawRound(x + 6, curY, w - 12, rowH, 3,
                        ColorProvider.rgba(35, 35, 40, 150));
                // Имя предмета
                DrawUtil.drawText(Fonts.SFREGULAR.get(),
                        e.itemName() + (e.count() > 1 ? " x" + e.count() : ""),
                        x + 12, curY + 4, ColorProvider.rgba(255, 255, 255, 255), 7);
                // Цена + время
                String age = formatAge(now - e.time());
                String priceStr = e.totalPrice() + "$"
                        + (e.count() > 1 ? " (" + e.unitPrice() + "$/шт)" : "");
                DrawUtil.drawText(Fonts.SFREGULAR.get(), priceStr,
                        x + 12, curY + 14, ColorProvider.setAlpha(ColorProvider.getColorClient(), 220), 6);
                float ageW = Fonts.SFREGULAR.get().getWidth(age, 6);
                DrawUtil.drawText(Fonts.SFREGULAR.get(), age,
                        x + w - 12 - ageW, curY + 14,
                        ColorProvider.rgba(150, 150, 160, 200), 6);
            }
            curY += rowH + rowSp;
        }

        // Кнопка очистки
        if (!history.isEmpty()) {
            float btnW = 90;
            float btnH = 16;
            float btnX = x + w - btnW - 8;
            float btnY = y + h - btnH - 6;
            boolean btnHov = HoverUtil.isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
            if (btnHov) CursorManager.requestHand();
            DrawUtil.drawRound(btnX, btnY, btnW, btnH, 4,
                    btnHov ? ColorProvider.rgba(180, 60, 60, 200) : ColorProvider.rgba(60, 30, 30, 180));
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "Очистить историю",
                    btnX + 8, btnY + 4, ColorProvider.rgba(255, 255, 255, 255), 6.5f);
            historyClearBtn = new float[]{btnX, btnY, btnW, btnH};
        } else {
            historyClearBtn = null;
        }

        float contentH = history.size() * (rowH + rowSp) + 18;
        float maxScroll = Math.max(0, contentH - (h - padY * 2));
        listTargetScroll = MathHelper.clamp(listTargetScroll, 0, maxScroll);

        Scissor.unset();
        Scissor.pop();
    }

    private float[] historyClearBtn = null;

    private static String formatAge(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "с назад";
        long m = s / 60;
        if (m < 60) return m + "м назад";
        long h = m / 60;
        return h + "ч назад";
    }

    private void renderRow(DrawContext ctx, AutoBuyableItem item,
                           float x, float y, float w, float h, int mouseX, int mouseY) {
        AutoBuyItemSettings s = item.getSettings();
        boolean inSide = mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listY && mouseY <= listY + listH;
        boolean hov = inSide && HoverUtil.isHovered(mouseX, mouseY, x, y, w, h);

        int bg = s.isEnabled()
                ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 80)
                : ColorProvider.rgba(35, 35, 40, 150);
        if (hov) {
            bg = s.isEnabled()
                    ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 110)
                    : ColorProvider.rgba(45, 45, 50, 180);
        }
        DrawUtil.drawRound(x, y, w, h, 4, bg);

        // Иконка
        ItemStack iconStack = null;
        try {
            iconStack = item.getIcon();
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + 6, y + 7, 0);
            ctx.drawItem(iconStack, 0, 0);
            ctx.getMatrices().pop();
        } catch (Exception ignored) {}

        DrawUtil.drawText(Fonts.SFREGULAR.get(), item.getDisplayName(),
                x + 28, y + 6, ColorProvider.rgba(255, 255, 255, 255), 7.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), item.getCategory(),
                x + 28, y + 17, ColorProvider.rgba(150, 150, 160, 255), 6);

        // Проверка damageable — для таких добавляем поле "мин дюра"
        boolean damageable = iconStack != null && iconStack.isDamageable();

        // Поле buyBelow (цена)
        float fX = x + w - (damageable ? 240 : 200);
        float fY = y + 7;
        float fW = damageable ? 80 : 100;
        float fH = 18;
        boolean editing = ("buy_" + item.getId()).equals(editingId);
        boolean fHov = inSide && HoverUtil.isHovered(mouseX, mouseY, fX, fY, fW, fH);
        if (fHov) CursorManager.requestIBeam();

        DrawUtil.drawRound(fX, fY, fW, fH, 4,
                editing ? ColorProvider.rgba(35, 35, 45, 220) : ColorProvider.rgba(20, 20, 24, 200));
        if (editing) {
            DrawUtil.drawRound(fX, fY, fW, 0.7f, 0,
                    ColorProvider.setAlpha(ColorProvider.getColorClient(), 220));
        }
        String shown = editing ? editingBuffer : (s.getBuyBelow() + "$");
        DrawUtil.drawText(Fonts.SFREGULAR.get(), "≤ " + shown,
                fX + 4, fY + 5, ColorProvider.rgba(220, 220, 230, 255), 7);

        // Поле minDurability (только для damageable)
        float dX = 0, dY = 0, dW = 0, dH = 0;
        if (damageable) {
            dX = x + w - 145;
            dY = y + 7;
            dW = 50;
            dH = 18;
            boolean editingDur = ("dur_" + item.getId()).equals(editingId);
            boolean dHov = inSide && HoverUtil.isHovered(mouseX, mouseY, dX, dY, dW, dH);
            if (dHov) CursorManager.requestIBeam();

            DrawUtil.drawRound(dX, dY, dW, dH, 4,
                    editingDur ? ColorProvider.rgba(35, 35, 45, 220) : ColorProvider.rgba(20, 20, 24, 200));
            if (editingDur) {
                DrawUtil.drawRound(dX, dY, dW, 0.7f, 0,
                        ColorProvider.setAlpha(ColorProvider.getColorClient(), 220));
            }
            String durShown = editingDur ? editingBuffer : String.valueOf(s.getMinDurability());
            DrawUtil.drawText(Fonts.SFREGULAR.get(), "⚒ " + durShown,
                    dX + 4, dY + 5, ColorProvider.rgba(220, 220, 230, 255), 7);
        }

        // Тогл
        float tX = x + w - 90;
        float tY = y + 7;
        float tW = 80;
        float tH = 18;
        boolean tHov = inSide && HoverUtil.isHovered(mouseX, mouseY, tX, tY, tW, tH);
        if (tHov) CursorManager.requestHand();

        int tBg = s.isEnabled()
                ? ColorProvider.setAlpha(ColorProvider.getColorClient(), 200)
                : ColorProvider.rgba(45, 45, 50, 200);
        DrawUtil.drawRound(tX, tY, tW, tH, 4, tBg);
        String tLabel = s.isEnabled() ? "Включено" : "Выключено";
        float lw = Fonts.SFREGULAR.get().getWidth(tLabel, 6.5f);
        DrawUtil.drawText(Fonts.SFREGULAR.get(), tLabel,
                tX + (tW - lw) / 2, tY + 5,
                ColorProvider.rgba(255, 255, 255, 255), 6.5f);

        itemZones.put(item.getId(),
                new ItemZone(item, x, y, w, h, fX, fY, fW, fH, dX, dY, dW, dH, tX, tY, tW, tH));
    }

    private record ItemZone(AutoBuyableItem item,
                            float rowX, float rowY, float rowW, float rowH,
                            float fieldX, float fieldY, float fieldW, float fieldH,
                            float durX, float durY, float durW, float durH,
                            float toggleX, float toggleY, float toggleW, float toggleH) {}
    private final Map<String, ItemZone> itemZones = new java.util.LinkedHashMap<>();

    // ─────────────────── INPUT ───────────────────

    private boolean inSidebar(double mouseX, double mouseY) {
        return mouseX >= sideX && mouseX <= sideX + sideW
                && mouseY >= sideY && mouseY <= sideY + sideH;
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX <= listX + listW
                && mouseY >= listY && mouseY <= listY + listH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Закрытие
        float bx = guiX + WIDTH - 22;
        float by = guiY + 10;
        if (HoverUtil.isHovered(mouseX, mouseY, bx, by, 14, 14) && button == 0) {
            this.close();
            return true;
        }

        // Кнопка очистки истории
        if (button == 0 && historyClearBtn != null
                && HoverUtil.isHovered(mouseX, mouseY,
                    historyClearBtn[0], historyClearBtn[1], historyClearBtn[2], historyClearBtn[3])) {
            AutoBuyManager.get().clearPurchaseHistory();
            return true;
        }

        if (button == 0) {
            // Тоглы топбара
            for (Map.Entry<String, float[]> e : toggleZones.entrySet()) {
                float[] z = e.getValue();
                if (HoverUtil.isHovered(mouseX, mouseY, z[0], z[1], z[2], z[3])) {
                    Runnable r = toggleHandlers.get(e.getKey());
                    if (r != null) r.run();
                    return true;
                }
            }

            // Поля числовые
            String clickedField = null;
            for (Map.Entry<String, NumZone> e : numberZones.entrySet()) {
                NumZone z = e.getValue();
                if (HoverUtil.isHovered(mouseX, mouseY, z.x, z.y, z.w, z.h)) {
                    clickedField = e.getKey();
                    break;
                }
            }
            if (clickedField != null) {
                commitEditing();
                editingId = clickedField;
                editingBuffer = numberZones.get(clickedField).currentValue;
                searchFocused = false;
                return true;
            }

            // Поиск
            if (searchZone != null && HoverUtil.isHovered(mouseX, mouseY,
                    searchZone[0], searchZone[1], searchZone[2], searchZone[3])) {
                searchFocused = true;
                commitEditing();
                return true;
            }
            searchFocused = false;

            // Сайдбар категорий — только если клик внутри сайдбара
            if (inSidebar(mouseX, mouseY)) {
                for (Map.Entry<String, float[]> e : sidebarZones.entrySet()) {
                    float[] z = e.getValue();
                    if (HoverUtil.isHovered(mouseX, mouseY, z[0], z[1], z[2], z[3])) {
                        selectedCategory = e.getKey();
                        listTargetScroll = 0;
                        listScroll = 0;
                        return true;
                    }
                }
                return true;
            }

            // Кликаем по строкам — только в пределах списка
            if (inList(mouseX, mouseY)) {
                for (ItemZone iz : itemZones.values()) {
                    if (iz.rowW <= 0) continue; // невидимая строка
                    if (HoverUtil.isHovered(mouseX, mouseY, iz.toggleX, iz.toggleY, iz.toggleW, iz.toggleH)) {
                        iz.item.setEnabled(!iz.item.isEnabled());
                        AutoBuySettingsManager.save();
                        return true;
                    }
                    if (HoverUtil.isHovered(mouseX, mouseY, iz.fieldX, iz.fieldY, iz.fieldW, iz.fieldH)) {
                        commitEditing();
                        editingId = "buy_" + iz.item.getId();
                        editingBuffer = String.valueOf(iz.item.getSettings().getBuyBelow());
                        return true;
                    }
                    if (iz.durW > 0
                            && HoverUtil.isHovered(mouseX, mouseY, iz.durX, iz.durY, iz.durW, iz.durH)) {
                        commitEditing();
                        editingId = "dur_" + iz.item.getId();
                        editingBuffer = String.valueOf(iz.item.getSettings().getMinDurability());
                        return true;
                    }
                }
                return true;
            }

            commitEditing();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inSidebar(mouseX, mouseY)) {
            sideTargetScroll -= verticalAmount * 22;
            return true;
        }
        if (inList(mouseX, mouseY)) {
            listTargetScroll -= verticalAmount * 22;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            commitEditing();
            this.close();
            return true;
        }
        if (editingId != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!editingBuffer.isEmpty()) editingBuffer = editingBuffer.substring(0, editingBuffer.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitEditing();
                return true;
            }
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingId != null) {
            boolean allowDash = false;
            NumZone zone = numberZones.get(editingId);
            if (zone != null) allowDash = zone.allowDash;
            // на полях buyBelow дефис не нужен
            boolean ok = (chr >= '0' && chr <= '9')
                    || (allowDash && chr == '-' && !editingBuffer.contains("-"));
            if (ok && editingBuffer.length() < 12) {
                editingBuffer += chr;
            }
            return true;
        }
        if (searchFocused) {
            if (chr >= 32 && search.length() < 32) search += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void commitEditing() {
        if (editingId == null) return;
        if (editingId.startsWith("buy_")) {
            String id = editingId.substring(4);
            AutoBuyableItem it = AutoBuyManager.get().getById(id);
            if (it != null) {
                it.getSettings().setBuyBelow(parseInt(editingBuffer, it.getSettings().getBuyBelow()));
                AutoBuySettingsManager.save();
            }
        } else if (editingId.startsWith("dur_")) {
            String id = editingId.substring(4);
            AutoBuyableItem it = AutoBuyManager.get().getById(id);
            if (it != null) {
                it.getSettings().setMinDurability(parseInt(editingBuffer, it.getSettings().getMinDurability()));
                AutoBuySettingsManager.save();
            }
        } else {
            NumZone z = numberZones.get(editingId);
            if (z != null) z.onCommit.accept(editingBuffer);
        }
        editingId = null;
        editingBuffer = "";
    }

    private static int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
    }

    private static String describeState(AutoBuyManager.State s) {
        return switch (s) {
            case IDLE -> "выключен";
            case OPEN_AH -> "открываю /ah";
            case WAIT_AH -> "жду аукцион";
            case SORT_NEW -> "сортирую (новые)";
            case ACTIVE -> "работа";
            case BUY_CONFIRM_WAIT -> "жду подтверждение";
            case BUY_CONFIRM_CLICK -> "подтверждаю покупку";
            case ANTIAC_CLOSE -> "анти-АЧ: закрываю";
            case ANTIAC_WALK -> "анти-АЧ: иду";
            case PARSER_SEARCH -> "парсер: поиск";
            case PARSER_WAIT_INTERVAL -> "парсер: пауза";
            case PARSER_WAIT_RESULT -> "парсер: жду";
            case PARSER_READ -> "парсер: читаю цены";
        };
    }

    @Override
    public boolean shouldPause() { return false; }
}
