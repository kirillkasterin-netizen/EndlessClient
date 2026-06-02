package dev.endless.util.auction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import dev.endless.util.auction.holyworld.HolyWorldProvider;
import dev.endless.util.math.StopWatch;
import dev.endless.util.network.ServerDetector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton-сервис AutoBuy под HolyWorld.
 *
 * <h3>Pipeline AutoBuy</h3>
 * <pre>
 *  IDLE
 *    enable() → OPEN_AH ──/ah──▶ WAIT_AH ──экран открыт──▶
 *      ├─ если парсер вкл  → PARSER_*  (см. ниже)
 *      └─ иначе            → SORT_NEW (4 клика по воронке) → ACTIVE
 *
 *  ACTIVE  (на экране "Аукцион"):
 *    каждый тик: scan() — ищет САМЫЙ ДЕШЁВЫЙ матчинг по unit price → QUICK_MOVE
 *                после покупки 1сек POST_PURCHASE_DELAY
 *    каждые refreshInterval мс: QUICK_MOVE по изумруду
 *    если экран закрылся → OPEN_AH
 * </pre>
 *
 * <h3>Pipeline парсера</h3>
 * <pre>
 *  для каждого включённого предмета:
 *    PARSER_SEARCH ──/ah search "name"──▶ PARSER_WAIT_RESULT
 *      экран "Поиск" открыт ──▶ PARSER_READ (даём прогрузиться 500мс)
 *      сканируем экран "Поиск", min unit price → buyBelow = min × multiplier
 *      следующий: PARSER_WAIT_INTERVAL ──interval──▶ PARSER_SEARCH ...
 *  После окончания очереди: OPEN_AH → SORT_NEW → ACTIVE.
 * </pre>
 */
public final class AutoBuyManager {

    public enum State {
        IDLE,
        OPEN_AH,
        WAIT_AH,
        SORT_NEW,
        ACTIVE,
        /** Покупка кликнута — ждём появления окна «Покупка предмета». */
        BUY_CONFIRM_WAIT,
        /** Окно подтверждения открыто — клик по зелёному слоту. */
        BUY_CONFIRM_CLICK,
        /** Анти-АЧ: ждём перед закрытием. */
        ANTIAC_CLOSE,
        /** Анти-АЧ: движемся (двигаемся вперёд + крутим камеру). */
        ANTIAC_WALK,
        PARSER_SEARCH,
        PARSER_WAIT_INTERVAL,
        PARSER_WAIT_RESULT,
        PARSER_READ
    }

    private static AutoBuyManager INSTANCE;
    public static AutoBuyManager get() {
        if (INSTANCE == null) INSTANCE = new AutoBuyManager();
        return INSTANCE;
    }

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // ────────────────── Configurable settings ──────────────────

    private boolean enabled;
    private boolean autoParserEnabled;
    /** Флаг что парсерный цикл уже завершён в текущей сессии. Сбрасывается при включении модуля. */
    private boolean parserCycleDone = false;

    /** Задержка между сканами/покупками. */
    private long buyDelayMin = 400;
    private long buyDelayMax = 500;
    private long currentBuyDelay = 450;

    /** Жёсткая задержка после успешной покупки. Используем currentBuyDelay напрямую. */
    private long lastPurchaseTime = 0;

    /** Refresh (изумруд). */
    private long refreshIntervalMin = 1500;
    private long refreshIntervalMax = 2500;
    private long currentRefreshInterval = 2000;

    /** Парсер интервал между предметами. */
    private long parserIntervalMin = 2000;
    private long parserIntervalMax = 3000;
    private long currentParserInterval = 2500;

    private int globalMaxPrice = 100_000;
    private double parserMultiplier = 0.9;

    private final java.util.Random random = new java.util.Random();

    /** Каталог предметов. */
    private final Map<String, AutoBuyableItem> idIndex = new LinkedHashMap<>();
    private List<AutoBuyableItem> all;

    // ────────────────── State ──────────────────

    private State state = State.IDLE;
    private final StopWatch stateTimer = new StopWatch();
    private final StopWatch clickTimer = new StopWatch();
    private final StopWatch refreshTimer = new StopWatch();
    private int sortClickCount = 0;

    private final java.util.Deque<AutoBuyableItem> parserQueue = new java.util.ArrayDeque<>();
    private AutoBuyableItem parserCurrent;
    /** Был ли отправлен /ah search для текущего предмета. */
    private boolean parserCommandSent = false;

    /** Уже отсортировали в текущей сессии — повторно после переоткрытия не нужно. */
    private boolean sortedThisSession = false;

    /** Таймер анти-античита (каждые ~60 сек закрываем и открываем заново). */
    private final StopWatch antiAcTimer = new StopWatch();
    /** Длительность периода между анти-АЧ "разминками". */
    private long antiAcIntervalMs = 60_000;
    /** Сколько тиков идём вперёд после закрытия. */
    private int antiAcWalkTicks = 0;
    private float antiAcStartYaw = 0f;

    /** История покупок (последние 100). */
    public record PurchaseEntry(long time, String itemName, int count, int totalPrice, int unitPrice) {}
    private final java.util.Deque<PurchaseEntry> purchaseHistory = new java.util.ArrayDeque<>();

    // ────────────────── HolyWorld-specific constants ──────────────────

    /** Слот воронки на HolyWorld — последний в контейнере (6 рядов × 9 = 54 слота). */
    private static final int FUNNEL_SLOT = 53;
    /** Слот изумруда (refresh) — стандартно 49 (containerSize - 5). */
    private static final int REFRESH_FALLBACK = 49;

    /** История логов AutoBuy для UI. */
    private final java.util.Deque<String> log = new java.util.ArrayDeque<>();

    private AutoBuyManager() {
        rebuildIndex();
    }

    public void rebuildIndex() {
        all = new ArrayList<>(HolyWorldProvider.getItems());
        idIndex.clear();
        for (AutoBuyableItem item : all) idIndex.put(item.getId(), item);
    }

    public List<AutoBuyableItem> getAllItems() {
        if (all == null) rebuildIndex();
        return all;
    }
    public AutoBuyableItem getById(String id) { return idIndex.get(id); }

    // ────────────────── Toggles ──────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) {
        if (this.enabled == v) return;
        this.enabled = v;
        if (v) {
            // Старт всегда с открытия аукциона. Дальше развилка в stepWaitAh.
            state = State.OPEN_AH;
            sortClickCount = 0;
            sortedThisSession = false;
            stateTimer.reset();
            antiAcTimer.reset();
            parserCycleDone = false; // в новой сессии парсер должен пройти заново
            log(autoParserEnabled ? "AutoBuy включён — начинаю с парсера" : "AutoBuy включён");
        } else {
            state = State.IDLE;
            parserQueue.clear();
            parserCurrent = null;
            parserCommandSent = false;
            parserCycleDone = false;
            sortedThisSession = false;
            log("AutoBuy выключен");
        }
    }

    public boolean isAutoParserEnabled() { return autoParserEnabled; }
    public void setAutoParserEnabled(boolean v) {
        if (this.autoParserEnabled == v) return;
        this.autoParserEnabled = v;
        if (!enabled) {
            log(v ? "Парсер включён (запустится с AutoBuy)" : "Парсер выключен");
            return;
        }
        if (v) {
            parserCycleDone = false; // повторный запуск парсера
            refillParserQueue();
            if (parserQueue.isEmpty()) {
                log("Парсер: нет включённых предметов");
                return;
            }
            // Если уже на аукционе — стартуем сразу, иначе откроем
            if (mc.currentScreen instanceof GenericContainerScreen screen && isAuctionScreen(screen)) {
                startNextParserItem();
            } else {
                state = State.OPEN_AH;
                stateTimer.reset();
            }
            log("Парсер включён");
        } else {
            parserQueue.clear();
            parserCurrent = null;
            parserCommandSent = false;
            if (state == State.PARSER_SEARCH || state == State.PARSER_WAIT_RESULT
                    || state == State.PARSER_READ || state == State.PARSER_WAIT_INTERVAL) {
                state = State.OPEN_AH;
                stateTimer.reset();
            }
            log("Парсер выключен");
        }
    }

    public State getState() { return state; }

    // ────────────────── Settings (delays/limits) ──────────────────

    public long getBuyDelayMin() { return buyDelayMin; }
    public long getBuyDelayMax() { return buyDelayMax; }
    public void setBuyDelay(long min, long max) {
        this.buyDelayMin = Math.max(50, min);
        this.buyDelayMax = Math.max(this.buyDelayMin, max);
        rerollBuyDelay();
    }
    public void setBuyDelayString(String s) { long[] r = parseRange(s, buyDelayMin, buyDelayMax); setBuyDelay(r[0], r[1]); }
    public String getBuyDelayString() { return rangeStr(buyDelayMin, buyDelayMax); }
    private void rerollBuyDelay() {
        currentBuyDelay = buyDelayMin == buyDelayMax
                ? buyDelayMin
                : buyDelayMin + (long) (random.nextDouble() * (buyDelayMax - buyDelayMin));
    }

    public long getRefreshIntervalMin() { return refreshIntervalMin; }
    public long getRefreshIntervalMax() { return refreshIntervalMax; }
    public void setRefreshInterval(long min, long max) {
        this.refreshIntervalMin = Math.max(300, min);
        this.refreshIntervalMax = Math.max(this.refreshIntervalMin, max);
        rerollRefreshInterval();
    }
    public void setRefreshIntervalString(String s) { long[] r = parseRange(s, refreshIntervalMin, refreshIntervalMax); setRefreshInterval(r[0], r[1]); }
    public String getRefreshIntervalString() { return rangeStr(refreshIntervalMin, refreshIntervalMax); }
    private void rerollRefreshInterval() {
        currentRefreshInterval = refreshIntervalMin == refreshIntervalMax
                ? refreshIntervalMin
                : refreshIntervalMin + (long) (random.nextDouble() * (refreshIntervalMax - refreshIntervalMin));
    }

    public long getParserIntervalMin() { return parserIntervalMin; }
    public long getParserIntervalMax() { return parserIntervalMax; }
    public void setParserInterval(long min, long max) {
        this.parserIntervalMin = Math.max(500, min);
        this.parserIntervalMax = Math.max(this.parserIntervalMin, max);
        rerollParserInterval();
    }
    public void setParserIntervalString(String s) { long[] r = parseRange(s, parserIntervalMin, parserIntervalMax); setParserInterval(r[0], r[1]); }
    public String getParserIntervalString() { return rangeStr(parserIntervalMin, parserIntervalMax); }
    private void rerollParserInterval() {
        currentParserInterval = parserIntervalMin == parserIntervalMax
                ? parserIntervalMin
                : parserIntervalMin + (long) (random.nextDouble() * (parserIntervalMax - parserIntervalMin));
    }

    public int getGlobalMaxPrice() { return globalMaxPrice; }
    public void setGlobalMaxPrice(int v) { this.globalMaxPrice = Math.max(0, v); }

    public double getParserMultiplier() { return parserMultiplier; }
    public void setParserMultiplier(double v) { this.parserMultiplier = Math.min(1.5, Math.max(0.1, v)); }

    public long getAntiAcIntervalMs() { return antiAcIntervalMs; }
    public void setAntiAcIntervalMs(long v) { this.antiAcIntervalMs = Math.max(15_000, v); }

    public java.util.List<PurchaseEntry> getPurchaseHistory() {
        return new java.util.ArrayList<>(purchaseHistory);
    }
    public void clearPurchaseHistory() { purchaseHistory.clear(); }

    private void recordPurchase(String itemName, int count, int totalPrice, int unitPrice) {
        purchaseHistory.addFirst(new PurchaseEntry(System.currentTimeMillis(), itemName, count, totalPrice, unitPrice));
        while (purchaseHistory.size() > 100) purchaseHistory.removeLast();
    }

    private static long[] parseRange(String s, long fbMin, long fbMax) {
        if (s == null) return new long[]{fbMin, fbMax};
        s = s.trim();
        if (s.isEmpty()) return new long[]{fbMin, fbMax};
        try {
            int dash = s.indexOf('-');
            if (dash > 0) {
                long mn = Long.parseLong(s.substring(0, dash).trim());
                long mx = Long.parseLong(s.substring(dash + 1).trim());
                return new long[]{Math.min(mn, mx), Math.max(mn, mx)};
            }
            long v = Long.parseLong(s);
            return new long[]{v, v};
        } catch (NumberFormatException e) {
            return new long[]{fbMin, fbMax};
        }
    }
    private static String rangeStr(long mn, long mx) { return mn == mx ? String.valueOf(mn) : (mn + "-" + mx); }

    public java.util.Deque<String> getLog() { return log; }
    private void log(String msg) {
        log.addFirst("[" + nowStr() + "] " + msg);
        while (log.size() > 50) log.removeLast();
    }
    private static String nowStr() {
        java.time.LocalTime t = java.time.LocalTime.now();
        return String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
    }

    // ────────────────── Per-tick ──────────────────

    public void tick() {
        if (!enabled) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.getNetworkHandler() == null) return;
        if (!ServerDetector.isHolyWorld()) return;

        switch (state) {
            case IDLE -> {}
            case OPEN_AH -> stepOpenAh();
            case WAIT_AH -> stepWaitAh();
            case SORT_NEW -> stepSortNew();
            case ACTIVE -> stepActive();
            case BUY_CONFIRM_WAIT -> stepBuyConfirmWait();
            case BUY_CONFIRM_CLICK -> stepBuyConfirmClick();
            case ANTIAC_CLOSE -> {} // не используется напрямую (закрытие в stepActive)
            case ANTIAC_WALK -> stepAntiAcWalk();
            case PARSER_SEARCH -> stepParserSearch();
            case PARSER_WAIT_INTERVAL -> stepParserWaitInterval();
            case PARSER_WAIT_RESULT -> stepParserWaitResult();
            case PARSER_READ -> stepParserRead();
        }
    }

    // ────────────────── INIT FLOW ──────────────────

    private void stepOpenAh() {
        if (!stateTimer.isReached(200)) return;
        // Если экран уже открыт и это аукцион — пропускаем команду
        if (mc.currentScreen instanceof GenericContainerScreen screen && isAuctionScreen(screen)) {
            state = State.WAIT_AH;
            stateTimer.reset();
            return;
        }
        try {
            mc.player.networkHandler.sendChatCommand("ah");
            log("Открытие /ah");
        } catch (Exception ignored) {}
        state = State.WAIT_AH;
        stateTimer.reset();
    }

    private void stepWaitAh() {
        if (mc.currentScreen instanceof GenericContainerScreen screen && isAuctionScreen(screen)) {
            // Приоритет: парсер (если включён, есть предметы и цикл ещё не завершён)
            if (autoParserEnabled && !parserCycleDone) {
                refillParserQueue();
                if (!parserQueue.isEmpty()) {
                    log("Аукцион открыт, запускаю парсер");
                    startNextParserItem();
                    return;
                }
            }
            // Сортируем только при первом открытии в этой сессии
            if (!sortedThisSession) {
                state = State.SORT_NEW;
                sortClickCount = 0;
                stateTimer.reset();
                log("Аукцион открыт, сортирую");
            } else {
                state = State.ACTIVE;
                refreshTimer.reset();
                clickTimer.reset();
                rerollRefreshInterval();
                rerollBuyDelay();
                log("Аукцион открыт, продолжаю покупки");
            }
            return;
        }
        if (stateTimer.isReached(5000)) {
            state = State.OPEN_AH;
            stateTimer.reset();
        }
    }

    private void stepSortNew() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !isAuctionScreen(screen)) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }
        if (!stateTimer.isReached(180)) return;

        if (sortClickCount < 4) {
            clickSlotMode(FUNNEL_SLOT, SlotActionType.PICKUP);
            sortClickCount++;
            stateTimer.reset();
            if (sortClickCount == 4) log("Сортировка переключена на «Сначала новые»");
        } else {
            sortedThisSession = true;
            state = State.ACTIVE;
            refreshTimer.reset();
            clickTimer.reset();
            rerollRefreshInterval();
            rerollBuyDelay();
        }
    }

    // ────────────────── ACTIVE (BUY LOOP) ──────────────────

    private void stepActive() {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !isAuctionScreen(screen)) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }

        scanCurrentScreen(screen);

        if (refreshTimer.isReached(currentRefreshInterval)) {
            int slotToClick = findRefreshSlot(screen);
            if (slotToClick >= 0) {
                // На HolyWorld обновление = ЛКМ по изумруду
                clickSlotMode(slotToClick, SlotActionType.PICKUP);
            }
            refreshTimer.reset();
            rerollRefreshInterval();
        }

        // Анти-АЧ проверка ПОСЛЕ скана и refresh — если 60сек прошло, делаем разминку.
        if (antiAcTimer.isReached(antiAcIntervalMs)) {
            log("Анти-АЧ: закрываю аукцион");
            try { mc.player.closeHandledScreen(); } catch (Exception ignored) {}
            antiAcWalkTicks = 30 + random.nextInt(20);
            antiAcStartYaw = mc.player.getYaw();
            state = State.ANTIAC_WALK;
            stateTimer.reset();
            antiAcTimer.reset();
        }
    }

    /**
     * Анти-АЧ: идём вперёд несколько тиков + плавно поворачиваем yaw,
     * затем заново открываем /ah.
     */
    private void stepAntiAcWalk() {
        if (mc.player == null) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }

        if (antiAcWalkTicks > 0) {
            // Жмём W (forward) — через input
            if (mc.player.input != null) {
                mc.player.input.movementForward = 1.0f;
            }
            // Покрутим камеру: ±20 градусов yaw, +/- немного pitch
            float progress = 1f - (antiAcWalkTicks / 50f);
            float yawShift = (float) Math.sin(progress * Math.PI * 2) * 25f;
            mc.player.setYaw(antiAcStartYaw + yawShift);
            mc.player.setPitch(mc.player.getPitch() + (random.nextFloat() - 0.5f) * 0.3f);
            antiAcWalkTicks--;
            return;
        }

        // Закончили шагать — сбрасываем движение и открываем /ah
        if (mc.player.input != null) {
            mc.player.input.movementForward = 0f;
        }
        state = State.OPEN_AH;
        stateTimer.reset();
        log("Анти-АЧ: возвращаюсь к покупкам");
    }

    /**
     * Ищет слот изумруда (refresh) на HolyWorld аукционе.
     * Сначала пробует слот 49 (containerSize - 5 для 6 рядов), потом весь нижний ряд.
     */
    private int findRefreshSlot(GenericContainerScreen screen) {
        var slots = screen.getScreenHandler().slots;
        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;

        // Стандартный слот HolyWorld: containerSize - 5 = 49 для 6-рядного аукциона
        int candidate = containerSize - 5;
        if (candidate >= 0 && candidate < containerSize
                && slots.get(candidate).getStack().getItem() == Items.EMERALD) {
            return candidate;
        }

        int start = Math.max(0, containerSize - 9);
        int end = Math.min(slots.size(), containerSize);
        for (int i = start; i < end; i++) {
            if (slots.get(i).getStack().getItem() == Items.EMERALD) return i;
        }

        if (REFRESH_FALLBACK < containerSize
                && slots.get(REFRESH_FALLBACK).getStack().getItem() == Items.EMERALD) {
            return REFRESH_FALLBACK;
        }
        return -1;
    }

    private void scanCurrentScreen(GenericContainerScreen screen) {
        if (!clickTimer.isReached(currentBuyDelay)) return;
        // После покупки выдерживаем ту же buyDelay
        if (lastPurchaseTime > 0 && System.currentTimeMillis() - lastPurchaseTime < currentBuyDelay) return;

        List<AutoBuyableItem> enabledItems = collectEnabled();
        if (enabledItems.isEmpty()) return;

        var slots = screen.getScreenHandler().slots;
        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;
        // Скан только верхних рядов — последний ряд содержит управляющие элементы
        int scanLimit = Math.max(0, containerSize - 9);

        Slot bestSlot = null;
        AutoBuyableItem bestRef = null;
        int bestTotalPrice = Integer.MAX_VALUE;
        double bestUnitPrice = Double.MAX_VALUE;

        // Для отладки — статистика
        int matchedSlots = 0;
        int tooExpensive = 0;

        for (int i = 0; i < scanLimit && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            int totalPrice = AuctionUtils.getPrice(stack);
            if (totalPrice < 0) continue;

            int count = Math.max(1, stack.getCount());
            double unitPrice = (double) totalPrice / count;

            for (AutoBuyableItem ref : enabledItems) {
                ItemStack refStack = ref.createReference();
                if (!AuctionUtils.compareItem(stack, refStack)) continue;

                matchedSlots++;
                AutoBuyItemSettings s = ref.getSettings();
                int max = s.getBuyBelow() > 0 ? s.getBuyBelow() : globalMaxPrice;
                if (unitPrice > max || totalPrice > globalMaxPrice) {
                    tooExpensive++;
                    continue;
                }
                // Проверка прочности (для брони/оружия/элитр)
                if (s.getMinDurability() > 0 && stack.isDamageable()) {
                    int remaining = stack.getMaxDamage() - stack.getDamage();
                    if (remaining < s.getMinDurability()) continue;
                }
                if (unitPrice >= bestUnitPrice) continue;

                bestUnitPrice = unitPrice;
                bestTotalPrice = totalPrice;
                bestSlot = slot;
                bestRef = ref;
                break;
            }
        }

        if (bestSlot != null) {
            buy(bestSlot);
            clickTimer.reset();
            rerollBuyDelay();
            lastPurchaseTime = System.currentTimeMillis();
            int count = bestSlot.getStack().getCount();
            String displayName = bestRef.getDisplayName();
            log("Куплен «" + displayName + "» x" + count
                    + " за " + bestTotalPrice + "$ (=" + (int) bestUnitPrice + "$/шт)");
            recordPurchase(displayName, count, bestTotalPrice, (int) bestUnitPrice);
            // Переходим в подтверждение покупки
            state = State.BUY_CONFIRM_WAIT;
            stateTimer.reset();
        } else if (matchedSlots > 0 && tooExpensive == matchedSlots) {
            // Все совпадения слишком дорогие — лог раз в N тиков, чтобы не спамить
            if (clickTimer.isReached(currentBuyDelay * 5)) {
                log("Найдено " + matchedSlots + " совпадений, все дороже buyBelow");
                clickTimer.reset();
            }
        }
    }

    /** Если открылось окно «Покупка предмета» — переходим в клик. Иначе быстро возвращаемся в ACTIVE. */
    private void stepBuyConfirmWait() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString();
            if (title != null && title.contains("Покупка")) {
                state = State.BUY_CONFIRM_CLICK;
                stateTimer.reset();
                return;
            }
            // Если экран всё ещё аукцион — диалог не открылся, продолжаем сразу.
            if (isAuctionScreen(screen) && stateTimer.isReached(300)) {
                state = State.ACTIVE;
                return;
            }
        }
        // Полный таймаут (например экран закрылся вообще)
        if (stateTimer.isReached(1500)) {
            state = State.ACTIVE;
        }
    }

    /** Кликаем по зелёному слоту в окне подтверждения. */
    private void stepBuyConfirmClick() {
        if (!stateTimer.isReached(150)) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            // Окно закрылось — продолжаем покупки (аукцион переоткроется автоматически)
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }
        String title = screen.getTitle().getString();
        if (title == null || !title.contains("Покупка")) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }

        int confirmSlot = findGreenConfirmSlot(screen);
        if (confirmSlot < 0) confirmSlot = 0;
        clickSlotMode(confirmSlot, SlotActionType.PICKUP);
        log("Подтверждение покупки (slot " + confirmSlot + ")");
        state = State.OPEN_AH;
        stateTimer.reset();
    }

    /** Ищет зелёный (lime) слот в окне подтверждения. */
    private int findGreenConfirmSlot(GenericContainerScreen screen) {
        var slots = screen.getScreenHandler().slots;
        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;
        // Слоты-кнопки обычно — окрашенное стекло (PANE/BLOCK). Ищем lime/green.
        for (int i = 0; i < containerSize && i < slots.size(); i++) {
            var stack = slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            var item = stack.getItem();
            if (item == Items.LIME_STAINED_GLASS_PANE
                    || item == Items.LIME_STAINED_GLASS
                    || item == Items.GREEN_STAINED_GLASS_PANE
                    || item == Items.GREEN_STAINED_GLASS
                    || item == Items.LIME_CONCRETE
                    || item == Items.LIME_WOOL
                    || item == Items.LIME_DYE) {
                return i;
            }
        }
        return -1;
    }

    private void buy(Slot slot) {
        // На HolyWorld покупка = обычный левый клик по лоту (PICKUP).
        clickSlotMode(slot.id, SlotActionType.PICKUP);
    }

    private void clickSlotMode(int slotId, SlotActionType type) {
        try {
            mc.interactionManager.clickSlot(
                    mc.player.currentScreenHandler.syncId,
                    slotId, 0,
                    type,
                    mc.player);
        } catch (Exception ignored) {}
    }

    private List<AutoBuyableItem> collectEnabled() {
        List<AutoBuyableItem> list = new ArrayList<>();
        for (AutoBuyableItem item : getAllItems()) {
            if (item.getSettings().isEnabled()) list.add(item);
        }
        return list;
    }

    // ────────────────── PARSER ──────────────────

    private void refillParserQueue() {
        parserQueue.clear();
        for (AutoBuyableItem it : collectEnabled()) parserQueue.addLast(it);
    }

    private void startNextParserItem() {
        if (parserQueue.isEmpty()) return;
        parserCurrent = parserQueue.pollFirst();
        parserCommandSent = false;
        state = State.PARSER_SEARCH;
        stateTimer.reset();
    }

    private void stepParserSearch() {
        if (parserCurrent == null) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }
        if (!parserCommandSent) {
            try {
                mc.player.networkHandler.sendChatCommand("ah search " + parserCurrent.getSearchName());
                log("Парсер: ищу «" + parserCurrent.getSearchName() + "»");
            } catch (Exception ignored) {}
            parserCommandSent = true;
            state = State.PARSER_WAIT_RESULT;
            stateTimer.reset();
        }
    }

    private void stepParserWaitResult() {
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString();
            // На HolyWorld экран поиска имеет заголовок "Поиск (1/N)"
            if (title != null && (title.contains("Поиск") || title.contains("Аукцион"))) {
                state = State.PARSER_READ;
                stateTimer.reset();
                return;
            }
        }
        if (stateTimer.isReached(4000)) {
            log("Парсер: «" + parserCurrent.getSearchName() + "» — таймаут");
            advanceParser();
        }
    }

    private void stepParserRead() {
        if (parserCurrent == null) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }
        if (!stateTimer.isReached(500)) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            advanceParser();
            return;
        }

        var slots = screen.getScreenHandler().slots;
        int rows = screen.getScreenHandler().getRows();
        int containerSize = rows * 9;
        int scanLimit = Math.max(0, containerSize - 9);

        double minUnitPrice = Double.MAX_VALUE;
        ItemStack refStack = parserCurrent.createReference();

        for (int i = 0; i < scanLimit && i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            int totalPrice = AuctionUtils.getPrice(stack);
            if (totalPrice < 0) continue;

            int count = Math.max(1, stack.getCount());
            double unitPrice = (double) totalPrice / count;

            if (AuctionUtils.compareItem(stack, refStack)) {
                // Учитываем мин. прочность при выборе цены (если задана)
                int minDur = parserCurrent.getSettings().getMinDurability();
                if (minDur > 0 && stack.isDamageable()) {
                    int remaining = stack.getMaxDamage() - stack.getDamage();
                    if (remaining < minDur) continue;
                }
                if (unitPrice < minUnitPrice) {
                    minUnitPrice = unitPrice;
                }
            }
        }

        if (minUnitPrice != Double.MAX_VALUE) {
            int target = (int) Math.round(minUnitPrice * parserMultiplier);
            parserCurrent.getSettings().setBuyBelow(target);
            AutoBuySettingsManager.save();
            log("Парсер: «" + parserCurrent.getDisplayName() + "» min="
                    + (int) minUnitPrice + "$/шт → buyBelow=" + target + "$/шт");
        } else {
            log("Парсер: «" + parserCurrent.getDisplayName() + "» совпадений не найдено");
        }

        advanceParser();
    }

    /** Переключается на следующий предмет очереди парсера или завершает цикл. */
    private void advanceParser() {
        parserCurrent = null;
        parserCommandSent = false;

        if (!parserQueue.isEmpty()) {
            parserCurrent = parserQueue.pollFirst();
            state = State.PARSER_WAIT_INTERVAL;
            stateTimer.reset();
            rerollParserInterval();
            return;
        }

        // Очередь пуста — парсер завершён.
        parserCycleDone = true;
        // Закрываем аукцион чтобы открыть меню AutoBuy.
        try {
            if (mc.currentScreen instanceof GenericContainerScreen) {
                mc.player.closeHandledScreen();
            }
        } catch (Exception ignored) {}
        // Открываем меню AutoBuy с готовыми ценами.
        try {
            mc.setScreen(new dev.endless.ui.autobuy.AutoBuyScreen());
        } catch (Exception ignored) {}
        state = State.IDLE;
        // Также выключаем парсер чтобы при следующем включении модуля он
        // не запускался автоматом (юзер сам решит, нужно ли парсить заново)
        autoParserEnabled = false;
        AutoBuySettingsManager.save();
        log("Парсер: цикл завершён, открыт AutoBuy");
    }

    private void stepParserWaitInterval() {
        if (parserCurrent == null) {
            state = State.OPEN_AH;
            stateTimer.reset();
            return;
        }
        if (stateTimer.isReached(currentParserInterval)) {
            state = State.PARSER_SEARCH;
            stateTimer.reset();
        }
    }

    // ────────────────── Helpers ──────────────────

    /** Проверяет что экран — аукцион или поиск HolyWorld. */
    private static boolean isAuctionScreen(GenericContainerScreen screen) {
        String title = screen.getTitle().getString();
        if (title == null) return false;
        return title.contains("Аукцион") || title.contains("Аукционы")
                || title.contains("Поиск")
                || title.toLowerCase().contains("auction");
    }
}
