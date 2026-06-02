package endless.ere.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;

import endless.ere.Endless;
import endless.ere.base.events.impl.input.EventKey;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.server.EventPacket;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.KeySetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.client.modules.impl.misc.autobuy.AutoBuyHistoryScreen;
import endless.ere.client.modules.impl.misc.autobuy.AutoBuyPriceScreen;
import endless.ere.client.modules.impl.misc.autobuy.HistoryManager;
import endless.ere.client.modules.impl.misc.autobuy.PriceStore;
import endless.ere.utility.math.Timer;

import org.lwjgl.glfw.GLFW;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перенесён из Essence (code.essence.features.impl.misc.AutoBuy).
 * <p>
 * Урезанная версия: содержит логику истории покупок, детект захода в аукцион,
 * #goto-цикл по координатам для HolyWorld и переподключение при дисконнекте.
 * Сетевой слой/парсеры/модель предметов оригинала не перенесены — их зависимости
 * не входили в исходную папку.
 */
@ModuleAnnotation(name = "AutoBuy", category = Category.MISC, description = "Автобай: история, #goto-цикл, реконнект")
public final class AutoBuy extends Module {

    public static final AutoBuy INSTANCE = new AutoBuy();

    // ===== настройки =====

    private final ModeSetting serverMode = new ModeSetting("Сервер", "FunTime", "SpookyTime", "HolyWorld");
    private final ModeSetting leaveType = new ModeSetting("Тип обхода", () -> serverMode.is("FunTime"), "Покупающий", "Проверяющий");
    private final NumberSetting timer = new NumberSetting("Таймер", 350f, 350f, 750f, 10f, () -> serverMode.is("FunTime"));
    private final ModeSetting versionSetting = new ModeSetting("Версия", () -> serverMode.is("FunTime"), "1.16.5", "1.21.4");
    private final BooleanSetting autoStorage = new BooleanSetting("Автоскладирование", "", false, () -> !serverMode.is("HolyWorld"));
    private final BooleanSetting historyOnAh = new BooleanSetting("Запись из лора", true);
    private final KeySetting openGuiKey = new KeySetting("Бинд GUI", GLFW.GLFW_KEY_GRAVE_ACCENT);
    private final KeySetting openPricesKey = new KeySetting("Бинд Цен", -1);
    
    // ===== Обход задержки (смена анархии 101-114) =====
    private final BooleanSetting bypassDelay = new BooleanSetting("Обход задержки", false);
    private final NumberSetting bypassInterval = new NumberSetting("Интервал смены (сек)", 40f, 10f, 300f, 5f, () -> bypassDelay.isEnabled());
    
    private static final int BYPASS_AN_MIN = 101;
    private static final int BYPASS_AN_MAX = 114;
    
    private final Timer bypassTimer = new Timer();
    private int bypassCurrentAn = BYPASS_AN_MIN;
    private boolean bypassRetryNext = false; // флаг что нужно сразу попробовать следующую (после кика)

    // ===== автопарсер =====
    // Парсер запускается ТОЛЬКО вручную по кнопке Start из GUI.
    // Он работает ПО ТЕКУЩЕМУ открытому экрану аукциона - читает цены и кликает Обновить.
    private final BooleanSetting autoParser = new BooleanSetting("Включить парсер", false);
    private final NumberSetting autoParserDiscount = new NumberSetting("Процент скидки", 20f, 5f, 90f, 1f, () -> autoParser.isEnabled());
    private final NumberSetting parserSampleCount = new NumberSetting("Кол-во проверок", 5f, 1f, 20f, 1f, () -> autoParser.isEnabled());
    private final NumberSetting parserRefreshDelay = new NumberSetting("Задержка обновлений", 1000f, 500f, 3000f, 100f, () -> autoParser.isEnabled());

    private static final Pattern PRICE_PATTERN = Pattern.compile("Цена:\\s*([\\d,.]+)");

    // ===== ручной парсер (StartAb) - парсит цены по текущему открытому аукциону =====
    private boolean parsing = false;
    private boolean parseWaitingForScreen = false;
    private int parseRefreshCount = 0;
    private long parseMinPrice = Long.MAX_VALUE;
    private final Timer parseTimer = new Timer();
    private String parseItemName = "";
    private final java.util.List<Long> parsePriceSamples = new java.util.ArrayList<>();
    
    // ===== Парсить всё (батч) =====
    private final java.util.List<String> batchQueue = new java.util.ArrayList<>();
    private boolean batchActive = false;
    private final Timer batchDelayTimer = new Timer();
    private static final long BATCH_ITEM_DELAY_MS = 1500L; // пауза между предметами
    
    // ===== AutoBuy (автопокупка по настроенным ценам) =====
    private boolean autoBuyActive = false;
    private final Timer autoBuyClickCooldown = new Timer();
    private final Timer autoBuyRefreshTimer = new Timer();
    private final NumberSetting autoBuyClickDelay = new NumberSetting("Задержка кликов (мс)", 350f, 50f, 1000f, 10f);
    private final NumberSetting autoBuyRefreshDelay = new NumberSetting("Задержка обновления (мс)", 350f, 100f, 2000f, 50f);
    private boolean isBuying = false;
    private boolean waitingForPurchaseConfirm = false;
    private final Timer purchaseTimeout = new Timer();

    // ===== #goto цикл (HolyWorld) =====

    private static final long[] GOTO_DELAYS_MS = { 120_000L, 240_000L, 180_000L, 240_000L };
    private static final double[][] GOTO_WAYPOINTS = {
            { -11, 71, -46 },
            {  23, 69, -25 },
            {  -8, 69,  24 },
            {  50, 69,  57 }
    };
    private static final double GOTO_ARRIVAL_RADIUS = 2.0;
    private static final long GOTO_AFTER_ARRIVAL_MS = 1000L;

    private final Timer gotoTimer = new Timer();
    private int gotoStepIndex = 0;
    private boolean waitingForGotoArrival = false;
    private long gotoArrivedAtMs = 0L;
    private double gotoTargetX, gotoTargetY, gotoTargetZ;
    private boolean pendingGotoAfterClose = false;

    // ===== реконнект =====

    private final Timer reconnectTimer = new Timer();
    private boolean waitingForReconnect = false;
    private String lastServerAddress = "";

    // ===== аукцион =====

    private boolean inAuction = false;
    private long randomizedTimerValue = 350L;
    private final Timer updateTimer = new Timer();

    // паттерны выкупа (упрощённо)
    private static final Pattern BOUGHT_PATTERN_FT =
            Pattern.compile("(?:Вы купили|купили предмет)\\s*[\\-:]?\\s*(.+?)\\s*за\\s*\\$?\\s*([\\d\\s.,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOUGHT_PATTERN_HW =
            Pattern.compile("(?:куплен|приобрёл|приобретён)\\s+(.+?)\\s+за\\s+([\\d\\s.,]+)\\s*¤", Pattern.CASE_INSENSITIVE);

    private AutoBuy() {}

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
        PriceStore.getInstance().load();
        if (mc.getCurrentServerEntry() != null) {
            lastServerAddress = mc.getCurrentServerEntry().address;
        }
        generateRandomizedTimer();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        PriceStore.getInstance().save();
        resetState();
    }

    private void resetState() {
        inAuction = false;
        waitingForGotoArrival = false;
        gotoArrivedAtMs = 0L;
        pendingGotoAfterClose = false;
        waitingForReconnect = false;
        gotoTimer.reset();
        reconnectTimer.reset();
        updateTimer.reset();
    }

    private void generateRandomizedTimer() {
        float base = timer.getCurrent();
        float factor = 0.85f + (float) (Math.random() * 0.3f);
        randomizedTimerValue = (long) (base * factor);
    }

    // ===== открытие GUI по биндy =====

    @EventTarget
    public void onKey(EventKey e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        if (mc.currentScreen != null) return;
        if (openGuiKey.getKeyCode() != -1 && e.getKeyCode() == openGuiKey.getKeyCode()) {
            mc.setScreen(new AutoBuyHistoryScreen());
            return;
        }
        if (openPricesKey.getKeyCode() != -1 && e.getKeyCode() == openPricesKey.getKeyCode()) {
            String startTab = serverMode.is("HolyWorld") ? "HolyWorld"
                    : serverMode.is("SpookyTime") ? "Vanilla" : "FunTime";
            mc.setScreen(new AutoBuyPriceScreen(startTab));
        }
    }

    // ===== главный тик =====

    @EventTarget
    public void onTick(EventUpdate e) {
        if (mc.player == null || mc.world == null) {
            handleReconnect();
            return;
        }

        // запоминаем последний адрес для реконнекта
        if (mc.getCurrentServerEntry() != null) {
            lastServerAddress = mc.getCurrentServerEntry().address;
        }

        tickGotoCycle();
        tickAuctionState();
        tickParser();
        tickBatch();
        tickBypassDelay();
        tickAutoBuy();
    }
    
    // ===== AutoBuy =====
    
    /** Запускает автопокупку (отправляет /ah search и сканирует слоты по ценам из PriceStore). */
    public void startAutoBuy() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (autoBuyActive) {
            stopAutoBuy();
            return;
        }
        autoBuyActive = true;
        isBuying = false;
        waitingForPurchaseConfirm = false;
        autoBuyRefreshTimer.reset();
        autoBuyClickCooldown.reset();
        // открываем аукцион
        sendChatCommand("/ah");
        if (mc.player != null) {
            mc.player.sendMessage(Text.of("§a[AutoBuy] §fВключён - сканирую цены"), false);
        }
    }
    
    public void stopAutoBuy() {
        autoBuyActive = false;
        isBuying = false;
        waitingForPurchaseConfirm = false;
        if (mc.player != null) {
            mc.player.sendMessage(Text.of("§e[AutoBuy] §fВыключён"), false);
        }
    }
    
    public boolean isAutoBuyActive() {
        return autoBuyActive;
    }
    
    private void tickAutoBuy() {
        if (!autoBuyActive) return;
        if (mc.player == null || mc.world == null) return;
        if (parsing || batchActive) return; // не работаем если парсер активен
        
        // ждём подтверждения покупки (окно "Подозрительная цена")
        if (waitingForPurchaseConfirm) {
            // если открылось окно подтверждения - кликаем "Да"
            if (mc.currentScreen instanceof GenericContainerScreen confirmScreen) {
                String title = confirmScreen.getTitle().getString().toLowerCase();
                boolean isConfirmScreen = title.contains("подозрительная") 
                        || title.contains("подтверждение") 
                        || title.contains("confirm") 
                        || title.contains("покупка предмета")
                        || title.contains("покупка");
                if (isConfirmScreen) {
                    if (clickConfirmYes(confirmScreen)) {
                        // подтвердили - продолжаем покупать
                        waitingForPurchaseConfirm = false;
                        isBuying = false;
                        autoBuyClickCooldown.reset();
                        autoBuyRefreshTimer.reset();
                        if (mc.player != null) {
                            mc.player.sendMessage(Text.of("§a[AutoBuy] Подтвердил покупку"), false);
                        }
                        return;
                    }
                }
            }
            // Таймаут - 5 секунд (увеличил с 3 чтобы окно успело открыться)
            if (purchaseTimeout.finished(5000L)) {
                waitingForPurchaseConfirm = false;
                isBuying = false;
                autoBuyClickCooldown.reset();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.of("§c[AutoBuy] Таймаут покупки - окно не открылось"), false);
                }
            } else {
                return;
            }
        }
        
        // если экран закрыт - открыть /ah
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            if (autoBuyRefreshTimer.finished(2000L)) {
                sendChatCommand("/ah");
                autoBuyRefreshTimer.reset();
            }
            return;
        }
        
        String title = screen.getTitle().getString().toLowerCase();
        boolean isAuctionScreen = title.contains("аукцион") || title.contains("поиск");
        boolean isConfirmScreen = title.contains("подозрительная") 
                || title.contains("подтверждение") 
                || title.contains("confirm") 
                || title.contains("покупка");
        
        // если открылось окно подтверждения (даже без waitingForPurchaseConfirm) - подтверждаем
        if (isConfirmScreen) {
            clickConfirmYes(screen);
            waitingForPurchaseConfirm = false;
            isBuying = false;
            autoBuyClickCooldown.reset();
            return;
        }
        
        if (!isAuctionScreen) return;
        
        // Сканируем слоты на матч с настроенными предметами
        scanAuctionForBuy(screen);
        
        // Обновляем страницу через "Обновить" каждые N мс
        if (!isBuying && !waitingForPurchaseConfirm 
                && autoBuyRefreshTimer.finished((long) autoBuyRefreshDelay.getCurrent())) {
            clickRefreshButton(screen);
            autoBuyRefreshTimer.reset();
        }
    }
    
    /** Кликает на слот "Да/Купить" в окне подтверждения. */
    private boolean clickConfirmYes(GenericContainerScreen screen) {
        var handler = screen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(i);
            if (mc.player != null && slot.inventory == mc.player.getInventory()) continue;
            if (!slot.hasStack()) continue;
            net.minecraft.item.ItemStack stack = slot.getStack();
            String name = stack.getName().getString().toLowerCase();
            if (name.contains("да") || name.contains("подтвердить") || name.contains("купить") 
                    || name.contains("yes") || name.contains("confirm")
                    || stack.getItem() == net.minecraft.item.Items.LIME_STAINED_GLASS_PANE
                    || stack.getItem() == net.minecraft.item.Items.GREEN_WOOL
                    || stack.getItem() == net.minecraft.item.Items.LIME_WOOL) {
                if (mc.interactionManager != null && mc.player != null) {
                    mc.interactionManager.clickSlot(handler.syncId, slot.id, 0,
                            net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                    return true;
                }
            }
        }
        return false;
    }
    
    private void scanAuctionForBuy(GenericContainerScreen screen) {
        if (isBuying || waitingForPurchaseConfirm) return;
        if (!autoBuyClickCooldown.finished((long) autoBuyClickDelay.getCurrent())) return;
        
        String server = isFunTime() ? "FunTime" : isHolyWorld() ? "HolyWorld" : isSpookyTime() ? "Vanilla" : "FunTime";
        endless.ere.base.autobuy.AutoBuyManager abm = Endless.getInstance().getAutoBuyManager();
        if (abm == null) return;
        
        java.util.List<endless.ere.base.autobuy.item.ItemBuy> items = isFunTime() ? abm.getFuntime()
                : isHolyWorld() ? abm.getHollyworld() : abm.getVanilla();
        
        var handler = screen.getScreenHandler();
        // Сканируем только видимую часть аукциона (первые 45 = 5x9 слотов)
        int limit = Math.min(45, handler.slots.size());
        for (int i = 0; i < limit; i++) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(i);
            if (mc.player != null && slot.inventory == mc.player.getInventory()) continue;
            if (!slot.hasStack()) continue;
            
            net.minecraft.item.ItemStack stack = slot.getStack();
            String name = stack.getName().getString().toLowerCase();
            if (name.contains("обновить") || name.contains("refresh")) continue;
            
            int totalPrice = parsePriceFromLore(stack);
            if (totalPrice <= 0) continue;
            int count = Math.max(1, stack.getCount());
            int unitPrice = totalPrice / count;
            
            for (endless.ere.base.autobuy.item.ItemBuy itemBuy : items) {
                if (!itemBuy.isBuy(stack)) continue;
                
                int maxPrice = PriceStore.getInstance().getMaxPrice(server, itemBuy.getDisplayName());
                if (maxPrice <= 0) continue;
                if (!PriceStore.getInstance().isEnabled(server, itemBuy.getDisplayName())) continue;
                
                if (unitPrice <= maxPrice) {
                    performAutoBuy(slot, itemBuy.getDisplayName(), totalPrice, count);
                    return;
                }
            }
        }
    }
    
    private void performAutoBuy(net.minecraft.screen.slot.Slot slot, String displayName, int totalPrice, int count) {
        if (isBuying || waitingForPurchaseConfirm) return;
        if (mc.interactionManager == null || mc.player == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        // Проверка что предмет всё ещё в этом слоте
        if (slot.getStack().isEmpty()) return;
        
        isBuying = true;
        autoBuyClickCooldown.reset();
        
        // АКТУАЛЬНЫЙ syncId на момент клика (важно при обновлении страницы)
        int syncId = screen.getScreenHandler().syncId;
        int slotId = slot.id;
        
        // ЛКМ клик - открывает окно подтверждения
        mc.interactionManager.clickSlot(
                syncId,
                slotId,
                0,
                net.minecraft.screen.slot.SlotActionType.PICKUP,
                mc.player
        );
        
        waitingForPurchaseConfirm = true;
        purchaseTimeout.reset();
        
        if (mc.player != null) {
            mc.player.sendMessage(Text.of(
                    "§e[AutoBuy] §fКлик slot=" + slotId + " sync=" + syncId + ": §a" + displayName 
                            + " §7x" + count + " §fза §a$" + totalPrice
            ), false);
        }
    }
    
    /** Обход задержки - циклически шлёт /an<номер> 101-114, при кике пробует следующий.
     *  Работает ТОЛЬКО когда активен AutoBuy. */
    private void tickBypassDelay() {
        if (!bypassDelay.isEnabled()) return;
        if (!autoBuyActive) return; // только при активном AutoBuy
        if (mc.player == null || mc.player.networkHandler == null) return;
        
        long intervalMs = (long) (bypassInterval.getCurrent() * 1000f);
        // если был кик "сервер заполнен" - сразу пробуем следующий не дожидаясь интервала
        boolean ready = bypassRetryNext || bypassTimer.finished(intervalMs);
        if (!ready) return;
        
        // инициализация
        if (bypassCurrentAn < BYPASS_AN_MIN || bypassCurrentAn > BYPASS_AN_MAX) {
            bypassCurrentAn = BYPASS_AN_MIN;
        }
        
        sendChatCommand("/an" + bypassCurrentAn);
        if (mc.player != null) {
            String prefix = bypassRetryNext ? "§7[Bypass] §c→ §a/an" : "§7[Bypass] §f→ §a/an";
            mc.player.sendMessage(Text.of(prefix + bypassCurrentAn), false);
        }
        
        bypassRetryNext = false;
        // следующий по кругу
        bypassCurrentAn++;
        if (bypassCurrentAn > BYPASS_AN_MAX) bypassCurrentAn = BYPASS_AN_MIN;
        
        bypassTimer.reset();
    }
    
    /**
     * Запускает парсинг цен.
     * Если открыт экран аукциона - парсит его напрямую.
     * Если экран НЕ открыт - сам отправляет /ah search <предмет> и ждёт открытия.
     */
    public void startParse(String itemName) {
        startParseInternal(itemName, false);
    }
    
    /** Запускает парсинг с принудительной отправкой /ah search (для батча). */
    public void startParseForceSearch(String itemName) {
        startParseInternal(itemName, true);
    }
    
    private void startParseInternal(String itemName, boolean forceSearch) {
        if (!autoParser.isEnabled()) {
            if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Включи парсер в настройках!"), false);
            return;
        }
        if (mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        
        this.parseItemName = itemName;
        this.parsing = true;
        this.parseRefreshCount = 0;
        this.parseMinPrice = Long.MAX_VALUE;
        this.parsePriceSamples.clear();
        this.parseTimer.reset();
        
        // Если экран аукциона уже открыт И не форсируем поиск - парсим прямо сейчас
        // Иначе отправляем команду поиска и ждём открытия
        if (!forceSearch && mc.currentScreen instanceof GenericContainerScreen) {
            this.parseWaitingForScreen = false;
            if (mc.player != null) {
                int count = (int) parserSampleCount.getCurrent();
                mc.player.sendMessage(Text.of("§e[Parser] §fПарсинг " + itemName + " §7(" + count + " проверок)"), false);
            }
        } else {
            this.parseWaitingForScreen = true;
            sendChatCommand("/ah search " + itemName);
            if (mc.player != null) {
                mc.player.sendMessage(Text.of("§e[Parser] §fИщу " + itemName + " §7через /ah search..."), false);
            }
        }
    }
    
    /** Останавливает парсер. */
    public void stopParse() {
        if (parsing) {
            parsing = false;
            parseWaitingForScreen = false;
            parsePriceSamples.clear();
            if (mc.player != null) mc.player.sendMessage(Text.of("§e[Parser] §fОстановлено"), false);
        }
        if (batchActive) {
            batchActive = false;
            batchQueue.clear();
            if (mc.player != null) mc.player.sendMessage(Text.of("§e[Parser] §fБатч-парсинг остановлен"), false);
        }
    }
    
    /**
     * Парсит цены для всех включённых предметов текущего сервера.
     * Перенесено из Wirst (кнопка "Парсить всё" в аукционе).
     */
    public void parseAllEnabled() {
        if (!autoParser.isEnabled()) {
            if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Включи парсер в настройках!"), false);
            return;
        }
        if (mc.player == null || mc.player.networkHandler == null) return;
        
        String server = isFunTime() ? "FunTime" : isHolyWorld() ? "HolyWorld" : isSpookyTime() ? "Vanilla" : "FunTime";
        endless.ere.base.autobuy.AutoBuyManager abm = Endless.getInstance().getAutoBuyManager();
        if (abm == null) return;
        
        java.util.List<endless.ere.base.autobuy.item.ItemBuy> sourceList = isFunTime() ? abm.getFuntime() 
                : isHolyWorld() ? abm.getHollyworld() : abm.getVanilla();
        
        batchQueue.clear();
        for (endless.ere.base.autobuy.item.ItemBuy item : sourceList) {
            if (PriceStore.getInstance().isEnabled(server, item.getDisplayName())) {
                batchQueue.add(item.getDisplayName());
            }
        }
        
        if (batchQueue.isEmpty()) {
            if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Нет включённых предметов на " + server), false);
            return;
        }
        
        batchActive = true;
        batchDelayTimer.reset();
        if (mc.player != null) {
            mc.player.sendMessage(Text.of("§e[Parser] §fБатч-парсинг §a" + batchQueue.size() + "§f предметов через /ah search"), false);
        }
        // Не запускаем первый напрямую - tickBatch() сделает это сам
        // (закроет экран если открыт и отправит /ah search для каждого)
    }
    
    private void tickBatch() {
        // если батч активен и текущий парсинг завершился - запускаем следующий
        if (!batchActive) return;
        if (parsing) return;
        
        if (batchQueue.isEmpty()) {
            batchActive = false;
            if (mc.player != null) mc.player.sendMessage(Text.of("§a[Parser] §fБатч-парсинг завершён!"), false);
            return;
        }
        
        // Закрываем текущий экран чтобы парсер сам открыл новый через /ah search
        if (mc.currentScreen instanceof GenericContainerScreen && mc.player != null) {
            mc.player.closeHandledScreen();
            batchDelayTimer.reset(); // ставим таймер задержки сразу после закрытия
            return;
        }
        
        // Ждём задержку между предметами чтобы сервер успел обработать
        if (!batchDelayTimer.finished(BATCH_ITEM_DELAY_MS)) return;
        
        // Запускаем следующий с принудительной отправкой /ah search в чат
        String nextItem = batchQueue.remove(0);
        if (mc.player != null) {
            mc.player.sendMessage(Text.of("§7[Batch] §fСледующий: §e" + nextItem + " §7(осталось: " + batchQueue.size() + ")"), false);
        }
        startParseForceSearch(nextItem);
    }
    
    public boolean isParsing() {
        return parsing;
    }
    
    public String getParseItemName() {
        return parseItemName;
    }
    
    public int getParseProgress() {
        return parseRefreshCount;
    }
    
    public int getParseTotal() {
        return (int) parserSampleCount.getCurrent();
    }

    private void tickParser() {
        if (!parsing) return;
        
        // ждём открытия экрана аукциона после /ah search
        if (parseWaitingForScreen) {
            if (mc.currentScreen instanceof GenericContainerScreen screen) {
                String title = screen.getTitle().getString();
                if (title.contains("Поиск") || title.contains("Аукцион")) {
                    parseWaitingForScreen = false;
                    parseTimer.reset();
                    if (mc.player != null) {
                        int count = (int) parserSampleCount.getCurrent();
                        mc.player.sendMessage(Text.of("§e[Parser] §fЭкран открыт, парсинг " + parseItemName + " §7(" + count + " проверок)"), false);
                    }
                }
            } else if (parseTimer.finished(15000L)) {
                // экран не открылся за 15 сек - стоп
                parsing = false;
                parseWaitingForScreen = false;
                if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Экран аукциона не открылся, отмена"), false);
            }
            return;
        }
        
        // если экран закрылся - стопаем
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            parsing = false;
            parsePriceSamples.clear();
            if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Экран закрыт, парсинг прерван"), false);
            return;
        }
        
        if (!parseTimer.finished((long) parserRefreshDelay.getCurrent())) return;
        
        // читаем цены из текущего экрана аукциона
        long currentMin = readMinPriceFromAuction(screen);
        if (currentMin > 0 && currentMin < Long.MAX_VALUE) {
            parsePriceSamples.add(currentMin);
            if (currentMin < parseMinPrice) {
                parseMinPrice = currentMin;
            }
        } else {
            // если цены не найдены - возможно лор не в нужном формате
            if (parseRefreshCount == 0 && mc.player != null) {
                mc.player.sendMessage(Text.of("§e[Parser] §7Первая попытка: цены не найдены. Продолжаю..."), false);
            }
        }
        
        parseRefreshCount++;
        int targetCount = (int) parserSampleCount.getCurrent();
        
        if (parseRefreshCount >= targetCount) {
            // готово - сохраняем
            saveParsedPrice();
            parsing = false;
            parsePriceSamples.clear();
        } else {
            // жмём кнопку Обновить
            if (!clickRefreshButton(screen)) {
                if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Кнопка 'Обновить' не найдена"), false);
                parsing = false;
                parsePriceSamples.clear();
                return;
            }
            parseTimer.reset();
        }
    }
    
    /** Читает минимальную цену из текущего экрана аукциона.
     * Логика взята из Zenith AutoBuy - простой парсинг LoreComponent с гибкой регуляркой.
     */
    private long readMinPriceFromAuction(GenericContainerScreen screen) {
        long minPrice = Long.MAX_VALUE;
        int found = 0;
        var handler = screen.getScreenHandler();
        int limit = Math.min(45, handler.slots.size());
        
        for (int i = 0; i < limit; i++) {
            net.minecraft.screen.slot.Slot slot = handler.slots.get(i);
            // пропускаем инвентарь игрока
            if (mc.player != null && slot.inventory == mc.player.getInventory()) continue;
            if (!slot.hasStack()) continue;
            
            net.minecraft.item.ItemStack stack = slot.getStack();
            // пропускаем кнопку Обновить
            String name = stack.getName().getString().toLowerCase();
            if (name.contains("обновить") || name.contains("refresh")) continue;
            
            // парсим цену из лора
            int totalPrice = parsePriceFromLore(stack);
            if (totalPrice <= 0) continue;
            
            // цена за 1 шт
            int count = Math.max(1, stack.getCount());
            long unitPrice = totalPrice / count;
            
            if (unitPrice < minPrice) minPrice = unitPrice;
            found++;
        }
        
        return found > 0 ? minPrice : Long.MAX_VALUE;
    }
    
    private static final Pattern ZENITH_PRICE_PATTERN = Pattern.compile(
            "Цен[аaAАыЫ]?:?\\s*([\\d,\\s\\.]+)",
            Pattern.CASE_INSENSITIVE);
    
    /** Парсит цену из лора (Zenith-стиль). */
    private int parsePriceFromLore(net.minecraft.item.ItemStack stack) {
        net.minecraft.component.type.LoreComponent lore = stack.get(net.minecraft.component.DataComponentTypes.LORE);
        if (lore == null) return 0;
        
        for (Text text : lore.lines()) {
            String raw = text.getString();
            if (raw == null) continue;
            // убираем §x форматирование
            String clean = net.minecraft.util.Formatting.strip(raw);
            if (clean == null) continue;
            
            Matcher m = ZENITH_PRICE_PATTERN.matcher(clean);
            if (m.find()) {
                try {
                    String priceStr = m.group(1).replaceAll("[,\\s\\.]", "");
                    if (!priceStr.isEmpty()) {
                        return Integer.parseInt(priceStr);
                    }
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }
    
    /** Кликает по кнопке "Обновить" в открытом контейнере. */
    private boolean clickRefreshButton(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            net.minecraft.screen.slot.Slot slot = screen.getScreenHandler().slots.get(i);
            if (slot.hasStack() && slot.getStack().getName().getString().contains("Обновить")) {
                mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, slot.id, 0,
                        net.minecraft.screen.slot.SlotActionType.PICKUP, mc.player);
                return true;
            }
        }
        return false;
    }
    
    /** Сохраняет результат парсинга в PriceStore с применением скидки. */
    private void saveParsedPrice() {
        if (parsePriceSamples.isEmpty() || parseMinPrice == Long.MAX_VALUE) {
            if (mc.player != null) mc.player.sendMessage(Text.of("§c[Parser] Не удалось найти цены для " + parseItemName), false);
            return;
        }
        
        // медиана + минимум для устойчивости к выбросам
        java.util.Collections.sort(parsePriceSamples);
        long medianPrice = parsePriceSamples.get(parsePriceSamples.size() / 2);
        long basePrice = Math.min(medianPrice, parseMinPrice);
        
        double discount = autoParserDiscount.getCurrent() / 100.0;
        int targetPrice = (int) (basePrice * (1.0 - discount));
        
        String server = isFunTime() ? "FunTime" : isHolyWorld() ? "HolyWorld" : isSpookyTime() ? "Vanilla" : "FunTime";
        PriceStore.getInstance().setMaxPrice(server, parseItemName, targetPrice);
        
        if (mc.player != null) {
            mc.player.sendMessage(Text.of(
                "§a[Parser] §f" + parseItemName +
                " §7| мин: §f" + parseMinPrice +
                " §7| мед: §f" + medianPrice +
                " §7| итог: §a" + targetPrice
            ), false);
        }
    }

    private void tickAuctionState() {
        boolean isAuctionScreen = false;
        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString();
            isAuctionScreen = title.contains("Аукцион") || title.contains("Аукционы") || title.contains("Поиск");
        }

        if (isAuctionScreen && !inAuction) {
            inAuction = true;
            updateTimer.reset();
            generateRandomizedTimer();
        } else if (!isAuctionScreen && inAuction) {
            inAuction = false;
        }
    }

    // ===== #goto =====

    private void tickGotoCycle() {
        if (!serverMode.is("HolyWorld")) return;
        if (mc.player == null) return;

        // если ждём закрытия экрана чтобы дёрнуть #goto
        if (pendingGotoAfterClose) {
            if (!(mc.currentScreen instanceof GenericContainerScreen)) {
                sendGoto();
                pendingGotoAfterClose = false;
            }
            return;
        }

        // если уже летим к точке
        if (waitingForGotoArrival) {
            double dx = mc.player.getX() - gotoTargetX;
            double dy = mc.player.getY() - gotoTargetY;
            double dz = mc.player.getZ() - gotoTargetZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (dist <= GOTO_ARRIVAL_RADIUS) {
                if (gotoArrivedAtMs == 0L) gotoArrivedAtMs = System.currentTimeMillis();
                if (System.currentTimeMillis() - gotoArrivedAtMs >= GOTO_AFTER_ARRIVAL_MS) {
                    waitingForGotoArrival = false;
                    gotoArrivedAtMs = 0L;
                    sendChatCommand("/ah");
                    gotoStepIndex = (gotoStepIndex + 1) % GOTO_WAYPOINTS.length;
                    gotoTimer.reset();
                }
            } else {
                gotoArrivedAtMs = 0L;
            }
            return;
        }

        long delay = GOTO_DELAYS_MS[gotoStepIndex];
        if (!gotoTimer.finished(delay)) return;

        if (mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
            pendingGotoAfterClose = true;
        } else {
            sendGoto();
        }
    }

    private void sendGoto() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        double[] wp = GOTO_WAYPOINTS[gotoStepIndex];
        gotoTargetX = wp[0];
        gotoTargetY = wp[1];
        gotoTargetZ = wp[2];
        sendChatCommand("#goto " + (int) gotoTargetX + " " + (int) gotoTargetY + " " + (int) gotoTargetZ);
        waitingForGotoArrival = true;
        gotoArrivedAtMs = 0L;
        gotoTimer.reset();
    }

    private void sendChatCommand(String msg) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        // Все команды шлём как сообщение чата (со слешем) - так делает игрок вручную
        // Это надёжнее для русских символов и плагинных команд
        if (msg.startsWith("/") || msg.startsWith("#")) {
            mc.player.networkHandler.sendChatMessage(msg);
        } else {
            mc.player.networkHandler.sendChatMessage(msg);
        }
    }

    // ===== реконнект =====

    private void handleReconnect() {
        if (mc.currentScreen instanceof DisconnectedScreen) {
            // Если bypass + AutoBuy активны и сервер заполнен - пробуем следующий
            if (bypassDelay.isEnabled() && autoBuyActive) {
                bypassRetryNext = true;
                // выходим в главное меню чтобы снова мочь зайти
                if (mc.world == null) {
                    mc.setScreen(new TitleScreen());
                }
            }
            
            if (!waitingForReconnect) {
                waitingForReconnect = true;
                reconnectTimer.reset();
            }
        }

        if (waitingForReconnect && reconnectTimer.finished(5000L)) {
            if (mc.currentScreen instanceof DisconnectedScreen
                    || mc.currentScreen instanceof MultiplayerScreen
                    || mc.currentScreen instanceof TitleScreen) {
                tryReconnect();
                reconnectTimer.reset();
            }
        }

        if (waitingForReconnect && mc.player != null && mc.world != null) {
            waitingForReconnect = false;
        }
    }

    private void tryReconnect() {
        if (lastServerAddress == null || lastServerAddress.isEmpty()) return;
        try {
            ServerInfo serverInfo = new ServerInfo("AutoBuy Server", lastServerAddress, ServerInfo.ServerType.OTHER);
            ServerAddress serverAddress = ServerAddress.parse(lastServerAddress);
            ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc, serverAddress, serverInfo, false, null);
        } catch (Exception ignored) {}
    }

    // ===== пакеты =====

    @EventTarget
    public void onPacket(EventPacket e) {
        if (!e.isReceive()) return;

        if (e.getPacket() instanceof DisconnectS2CPacket disc) {
            // Обработка кика "Сервер заполнен" при обходе задержки (только если AutoBuy активен)
            if (bypassDelay.isEnabled() && autoBuyActive && disc.reason() != null) {
                String reason = disc.reason().getString().toLowerCase();
                if (reason.contains("заполнен") || reason.contains("full") || reason.contains("переполн")) {
                    bypassRetryNext = true;
                    if (mc.player != null) {
                        mc.player.sendMessage(Text.of("§c[Bypass] Сервер заполнен, пробую следующий..."), false);
                    }
                }
            }
            
            if (leaveType.is("Проверяющий")) {
                waitingForReconnect = true;
                reconnectTimer.reset();
            }
        }

        if (e.getPacket() instanceof GameMessageS2CPacket msg && historyOnAh.isEnabled()) {
            Text content = msg.content();
            if (content == null) return;
            String raw = content.getString();
            if (raw == null || raw.isEmpty()) return;

            tryRecordPurchase(raw);
            handleAutoBuyResponse(raw);
        }
    }
    
    /** Обрабатывает ответы сервера для AutoBuy (успешная покупка / нехватка денег). */
    private void handleAutoBuyResponse(String raw) {
        if (!autoBuyActive || !waitingForPurchaseConfirm) return;
        
        String lower = raw.toLowerCase();
        // успешная покупка
        if (lower.contains("успешно купили") || lower.contains("куплен") || lower.contains("приобрёл") || lower.contains("приобретён")) {
            waitingForPurchaseConfirm = false;
            isBuying = false;
            autoBuyClickCooldown.reset();
            if (mc.player != null) {
                mc.player.sendMessage(Text.of("§a[AutoBuy] §fПокупка подтверждена!"), false);
            }
            // закрываем экран и переоткрываем /ah
            if (mc.currentScreen instanceof GenericContainerScreen && mc.player != null) {
                mc.player.closeHandledScreen();
            }
            return;
        }
        // нехватка денег
        if (lower.contains("не хватает") || lower.contains("монет") || lower.contains("недостаточно")) {
            waitingForPurchaseConfirm = false;
            isBuying = false;
            if (mc.player != null) {
                mc.player.sendMessage(Text.of("§c[AutoBuy] Не хватает денег!"), false);
            }
        }
    }

    private void tryRecordPurchase(String raw) {
        Matcher mFt = BOUGHT_PATTERN_FT.matcher(raw);
        if (mFt.find()) {
            int price = parsePrice(mFt.group(2));
            if (price > 0) HistoryManager.getInstance().addPurchaseFromMessage(mFt.group(1), price);
            return;
        }

        Matcher mHw = BOUGHT_PATTERN_HW.matcher(raw);
        if (mHw.find()) {
            int price = parsePrice(mHw.group(2));
            if (price > 0) HistoryManager.getInstance().addPurchaseFromMessage(mHw.group(1), price);
        }
    }

    private int parsePrice(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.replaceAll("[\\s.,]", ""));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // публичные геттеры на случай если потребуются другим модулям
    public boolean isInAuction() { return inAuction; }
    public boolean isFunTime() { return serverMode.is("FunTime"); }
    public boolean isHolyWorld() { return serverMode.is("HolyWorld"); }
    public boolean isSpookyTime() { return serverMode.is("SpookyTime"); }
    public long getRandomizedTimer() { return randomizedTimerValue; }
}
