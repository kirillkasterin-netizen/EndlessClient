package ru.zenith.implement.features.modules.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import ru.zenith.api.event.EventHandler;
import ru.zenith.api.feature.module.Module;
import ru.zenith.api.feature.module.ModuleCategory;
import ru.zenith.api.feature.module.setting.implement.BindSetting;
import ru.zenith.api.feature.module.setting.implement.ValueSetting;
import ru.zenith.api.system.font.Fonts;
import ru.zenith.api.system.shape.ShapeProperties;
import ru.zenith.common.util.color.ColorUtil;
import ru.zenith.common.util.entity.PlayerInventoryUtil;
import ru.zenith.common.util.other.StopWatch;
import ru.zenith.common.util.render.Render2DUtil;
import ru.zenith.implement.events.keyboard.KeyEvent;
import ru.zenith.implement.events.packet.PacketEvent;
import ru.zenith.implement.events.player.TickEvent;
import ru.zenith.implement.features.draggables.Notifications;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoBuy extends Module {

    private static AutoBuy instance;

    // Настройки
    final BindSetting menuKey = new BindSetting("MenuKey", "Открыть меню").setKey(GLFW.GLFW_KEY_UNKNOWN);
    final BindSetting autoBuyBind = new BindSetting("AutoBuyBind", "Включить AutoBuy").setKey(GLFW.GLFW_KEY_UNKNOWN);
    final ValueSetting updateDelay = new ValueSetting("UpdateDelay", "Задержка обновления (мс)").setValue(500).range(100, 2000);
    final ValueSetting anarchyChangeDelay = new ValueSetting("Anarchy Change Delay", "Менять анархию каждые (мин)").setValue(5).range(3, 10);
    final ValueSetting clickDelay = new ValueSetting("Click Delay", "Задержка между кликами (мс)").setValue(200).range(50, 500);

    boolean autoBuyEnabled = false;
    final Map<String, ItemTarget> targets = new LinkedHashMap<>();
    boolean isScanning = false;
    boolean isBuying = false;
    boolean waitingForPurchaseConfirm = false;
    boolean hasClickedThisTick = false;

    final StopWatch scanWatch = new StopWatch();
    final StopWatch updateWatch = new StopWatch();
    final StopWatch anarchyWatch = new StopWatch();
    final StopWatch clickCooldown = new StopWatch();
    GenericContainerScreen currentAuctionScreen = null;

    // Все доступные анархии
    final List<Integer> allAnarchies = new ArrayList<>();

    static final Path CONFIG_PATH = Paths.get("AutoBuyConfig.json");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Pattern PRICE_PATTERN = Pattern.compile("Цен[аaAАыЫ]?:?\\s*([\\d,\\s\\.]+)", Pattern.CASE_INSENSITIVE);

    public AutoBuy() {
        super("AutoBuy", "Auto Buy", ModuleCategory.MISC);
        setup(menuKey, autoBuyBind, updateDelay, anarchyChangeDelay, clickDelay);
        initAllAnarchies();
        initDefaultTargets();
        loadConfig();
        instance = this;
    }

    public static AutoBuy getInstance() { return instance; }
    public boolean isEnabled() { return isState(); }

    public void setPriceForItem(String itemId, int price) {
        ItemTarget target = targets.get(itemId);
        if (target != null) {
            target.buyPrice = price;
            saveConfig();
            Notifications.getInstance().addList("§aЦена для " + target.displayName + " установлена: " + formatPrice(price), 2000);
        }
    }

    private void initAllAnarchies() {
        for (int i = 103; i <= 112; i++) allAnarchies.add(i);
        for (int i = 208; i <= 231; i++) allAnarchies.add(i);
        for (int i = 305; i <= 319; i++) allAnarchies.add(i);
        for (int i = 504; i <= 512; i++) allAnarchies.add(i);
        for (int i = 901; i <= 904; i++) allAnarchies.add(i);
    }

    private void initDefaultTargets() {
        // Обычные предметы (по типу предмета)
        addTargetItem("golden_apple", "Золотое яблоко", Items.GOLDEN_APPLE, 0);
        addTargetItem("enchanted_golden_apple", "Зач. яблоко", Items.ENCHANTED_GOLDEN_APPLE, 0);
        addTargetItem("elytra", "Элитры", Items.ELYTRA, 0);
        addTargetItem("netherite_ingot", "Незерит слиток", Items.NETHERITE_INGOT, 0);
        addTargetItem("spawner", "Спавнер", Items.SPAWNER, 0);
        addTargetItem("diamond", "Алмаз", Items.DIAMOND, 0);
        addTargetItem("beacon", "Маяк", Items.BEACON, 0);
        addTargetItem("sniffer_egg", "Яйцо нюхача", Items.SNIFFER_EGG, 0);
        addTargetItem("trial_key", "Ключ испытаний", Items.TRIAL_KEY, 0);
        addTargetItem("dragon_head", "Голова дракона", Items.DRAGON_HEAD, 0);
        addTargetItem("villager_spawn_egg", "Яйцо крестьянина", Items.VILLAGER_SPAWN_EGG, 0);

        // Динамит BLACK (по лору)
        addTargetLore("dynamite_black", "Динамит BLACK", Items.TNT,
                List.of("Этот динамит взрывается", "в 10 раз сильнее обычного", "и способен взорвать обсидиан"), 0);

        // Динамит WHITE (по лору)
        addTargetLore("dynamite_white", "Динамит WHITE", Items.TNT,
                List.of("Этот динамит взрывается", "в 10 раз сильнее обычного"), 0);

        // Серебро (по лору)
        addTargetLore("silver", "Серебро", Items.IRON_NUGGET,
                List.of("Это валюта для покупки", "отмычек к тайникам", "у Знахаря (/warp stash)"), 0);

        // Трапка (по лору)
        addTargetLore("trapka", "Трапка", Items.NETHERITE_SCRAP, List.of("Нерушимая клетка"), 0);

        // Сферы (по лору)
        addTargetLore("sphere_beast", "Сфера Бестии", Items.PLAYER_HEAD,
                List.of("вериная дикая мощь", "Обостряет реакции", "Укрепляя ваше тело."), 0);
        addTargetLore("sphere_satyr", "Сфера Сатира", Items.PLAYER_HEAD,
                List.of("Шёпот Сатира звучит", "Ускоряя расправу", "Но сковывая прыжок."), 0);
        addTargetLore("sphere_chaos", "Сфера Хаоса", Items.PLAYER_HEAD, List.of("Хаос искажает реальность"), 0);
        addTargetLore("sphere_ares", "Сфера Ареса", Items.PLAYER_HEAD, List.of("Дух Ареса пылает внутри"), 0);
        addTargetLore("sphere_hydra", "Сфера Гидры", Items.PLAYER_HEAD, List.of("Живучесть темных глубин"), 0);
        addTargetLore("sphere_titan", "Сфера Титана", Items.PLAYER_HEAD, List.of("Мощь Титанов крепка"), 0);

        // Талисманы (по лору)
        addTargetLore("talisman_demon", "Талисман Демона", Items.TOTEM_OF_UNDYING,
                List.of("Печать разжигает ярость", "Ускоряя удары сердца", "И силу каждой атаки."), 0);
        addTargetLore("talisman_discord", "Талисман Раздора", Items.TOTEM_OF_UNDYING,
                List.of("Раздор жаждет хаоса", "Даруя безумный темп", "Но разрушая броню."), 0);
        addTargetLore("talisman_rage", "Талисман Ярости", Items.TOTEM_OF_UNDYING, List.of("Чистая, дикая агрессия"), 0);
        addTargetLore("talisman_crusher", "Талисман Крушителя", Items.TOTEM_OF_UNDYING, List.of("Легендарный символ"), 0);
        addTargetLore("talisman_tyrant", "Талисман Тирана", Items.TOTEM_OF_UNDYING, List.of("Тиран подавляет слабых"), 0);

        // Зелья (по названию)
        addTargetName("potion_assassin", "[★] Зелье Ассасина", Items.SPLASH_POTION, 0);
        addTargetName("potion_holy_water", "[★] Святая вода", Items.SPLASH_POTION, 0);
        addTargetName("potion_paladin", "[★] Зелье Палладина", Items.SPLASH_POTION, 0);
        addTargetName("potion_sleeping", "[★] Снотворное", Items.SPLASH_POTION, 0);
        addTargetName("potion_clapper", "[★] Хлопушка", Items.SPLASH_POTION, 0);
        addTargetName("potion_wrath", "[★] Зелье Гнева", Items.SPLASH_POTION, 0);
        addTargetName("potion_radiation", "[★] Зелье Радиации", Items.SPLASH_POTION, 0);

        // Броня Крушителя (по названию)
        addTargetName("crusher_pickaxe", "Кирка Крушителя", Items.NETHERITE_PICKAXE, 0);
        addTargetName("crusher_leggings", "Поножи Крушителя", Items.NETHERITE_LEGGINGS, 0);
        addTargetName("crusher_chestplate", "Нагрудник Крушителя", Items.NETHERITE_CHESTPLATE, 0);
        addTargetName("crusher_helmet", "Шлем Крушителя", Items.NETHERITE_HELMET, 0);
        addTargetName("crusher_boots", "Ботинки Крушителя", Items.NETHERITE_BOOTS, 0);
    }

    private void addTargetItem(String id, String displayName, net.minecraft.item.Item item, int buyPrice) {
        targets.put(id, new ItemTarget(id, displayName, new ItemStack(item), null, buyPrice, false, true));
    }

    private void addTargetName(String id, String displayName, net.minecraft.item.Item item, int buyPrice) {
        targets.put(id, new ItemTarget(id, displayName, new ItemStack(item), null, buyPrice, true, false));
    }

    private void addTargetLore(String id, String displayName, net.minecraft.item.Item item, List<String> loreKeywords, int buyPrice) {
        targets.put(id, new ItemTarget(id, displayName, new ItemStack(item), loreKeywords, buyPrice, false, false));
    }

    @Override
    public void activate() {
        super.activate();
        anarchyWatch.reset();
        clickCooldown.reset();
    }

    @Override
    public void deactivate() {
        saveConfig();
        autoBuyEnabled = false;
        isScanning = false;
        isBuying = false;
        waitingForPurchaseConfirm = false;
        hasClickedThisTick = false;
        super.deactivate();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(menuKey.getKey()) && mc.currentScreen == null) openMenu();

        if (e.isKeyDown(autoBuyBind.getKey()) && mc.currentScreen == null) {
            if (mc.player != null) {
                mc.player.networkHandler.sendChatCommand("ah");
                autoBuyEnabled = true;
                waitingForPurchaseConfirm = false;
                isBuying = false;
                Notifications.getInstance().addList("§aAutoBuy включен", 2000);
            }
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof net.minecraft.network.packet.s2c.play.GameMessageS2CPacket msg) {
            String message = msg.content().getString();

            if (message.contains("Вы успешно купили") || message.contains("куплен")) {
                if (waitingForPurchaseConfirm) {
                    waitingForPurchaseConfirm = false;
                    isBuying = false;
                    hasClickedThisTick = false;
                    Notifications.getInstance().addList("§aПокупка подтверждена!", 1000);

                    // Закрываем меню после успешной покупки
                    new Thread(() -> {
                        try {
                            Thread.sleep(500);
                            mc.execute(() -> {
                                if (mc.currentScreen != null) {
                                    mc.currentScreen.close();
                                }
                            });
                            Thread.sleep(1500);
                            mc.execute(() -> {
                                if (mc.player != null && autoBuyEnabled) {
                                    mc.player.networkHandler.sendChatCommand("ah");
                                }
                            });
                        } catch (Exception ignored) {}
                    }).start();
                }
            }

            if (message.contains("не хватает") || message.contains("Monet") || message.contains("монет")) {
                if (waitingForPurchaseConfirm) {
                    waitingForPurchaseConfirm = false;
                    isBuying = false;
                    hasClickedThisTick = false;
                    Notifications.getInstance().addList("§cНе хватает денег!", 2000);

                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                            mc.execute(() -> {
                                if (mc.player != null && autoBuyEnabled) {
                                    mc.player.networkHandler.sendChatCommand("ah");
                                }
                            });
                        } catch (Exception ignored) {}
                    }).start();
                }
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!autoBuyEnabled) return;

        // Меняем анархию только если автобай включен и не в процессе покупки
        if (autoBuyEnabled && !isBuying && !waitingForPurchaseConfirm && anarchyWatch.finished((long) anarchyChangeDelay.getValue() * 60 * 1000)) {
            changeRandomAnarchy();
            anarchyWatch.reset();
        }

        if (waitingForPurchaseConfirm || isBuying) return;

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            String title = screen.getTitle().getString().toLowerCase();
            if (title.contains("аукцион") || title.contains("auction")) {
                currentAuctionScreen = screen;
                isScanning = true;
            }
        } else {
            isScanning = false;
            currentAuctionScreen = null;
        }

        if (isScanning && currentAuctionScreen != null) {
            if (scanWatch.every(50)) scanSlots();
            if (updateWatch.every((long) updateDelay.getValue())) {
                refreshPage();
                updateWatch.reset();
            }
        }
    }

    private void changeRandomAnarchy() {
        if (mc.player == null || allAnarchies.isEmpty()) return;
        Random random = new Random();
        int randomAnarchy = allAnarchies.get(random.nextInt(allAnarchies.size()));
        mc.player.networkHandler.sendChatCommand("an" + randomAnarchy);
        Notifications.getInstance().addList("§eСмена анархии на /an" + randomAnarchy, 2000);
    }

    private void scanSlots() {
        if (currentAuctionScreen == null || isBuying || waitingForPurchaseConfirm) return;

        var handler = currentAuctionScreen.getScreenHandler();
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory == mc.player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || isRefreshButton(stack)) continue;

            analyzeAndBuyItem(slot, stack);
            if (isBuying) break; // Прерываем сканирование если начали покупку
        }
    }

    private void analyzeAndBuyItem(Slot slot, ItemStack stack) {
        List<String> lore = getLore(stack);
        int totalPrice = parsePriceFromLore(lore);
        if (totalPrice <= 0) return;

        int count = stack.getCount();
        int pricePerItem = totalPrice / count;
        String itemName = Formatting.strip(stack.getName().getString()).toLowerCase();

        for (ItemTarget target : targets.values()) {
            if (target.buyPrice <= 0) continue;

            boolean matches = false;

            if (target.loreKeywords != null && !target.loreKeywords.isEmpty()) {
                matches = checkLoreFullMatch(lore, target.loreKeywords);
            }

            if (!matches && target.isCheckByName()) {
                String targetName = Formatting.strip(target.displayName).toLowerCase();
                if (itemName.contains(targetName)) matches = true;
            }

            if (!matches && target.isCheckByItem()) {
                if (stack.getItem() == target.displayStack.getItem()) matches = true;
            }

            if (matches && pricePerItem <= target.buyPrice) {
                performPurchase(slot, target, totalPrice, pricePerItem, count);
                break;
            }
        }
    }

    private boolean checkLoreFullMatch(List<String> itemLore, List<String> targetLore) {
        if (itemLore == null || targetLore == null) return false;

        int matches = 0;
        for (String targetLine : targetLore) {
            String cleanTarget = Formatting.strip(targetLine);
            if (cleanTarget == null) continue;

            for (String itemLine : itemLore) {
                String cleanItem = Formatting.strip(itemLine);
                if (cleanItem != null && cleanItem.contains(cleanTarget)) {
                    matches++;
                    break;
                }
            }
        }
        return matches >= targetLore.size();
    }

    private void performPurchase(Slot slot, ItemTarget target, int total, int perItem, int count) {
        if (isBuying || waitingForPurchaseConfirm) return;
        if (!clickCooldown.finished((long) clickDelay.getValue())) return;

        isBuying = true;
        clickCooldown.reset();

        // Один клик для покупки
        PlayerInventoryUtil.clickSlot(slot.id, 0, SlotActionType.QUICK_MOVE, false);

        waitingForPurchaseConfirm = true;

        Notifications.getInstance().addList(String.format("§eПокупка: %s x%d за %d$", target.displayName, count, total), 3000);

        // Таймаут на случай если сообщение не придет
        new Thread(() -> {
            try {
                Thread.sleep(7000);
                if (waitingForPurchaseConfirm) {
                    mc.execute(() -> {
                        waitingForPurchaseConfirm = false;
                        isBuying = false;
                        Notifications.getInstance().addList("§cТаймаут покупки", 2000);
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private int parsePriceFromLore(List<String> lore) {
        for (String line : lore) {
            String clean = Formatting.strip(line);
            if (clean == null) continue;
            Matcher m = PRICE_PATTERN.matcher(clean);
            if (m.find()) {
                try {
                    String priceStr = m.group(1).replaceAll("[,\\s\\.]", "");
                    return Integer.parseInt(priceStr);
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private boolean isRefreshButton(ItemStack stack) {
        String name = stack.getName().getString().toLowerCase();
        return name.contains("обновить") || name.contains("refresh");
    }

    private void refreshPage() {
        if (currentAuctionScreen == null || isBuying || waitingForPurchaseConfirm) return;
        var handler = currentAuctionScreen.getScreenHandler();
        for (Slot slot : handler.slots) {
            if (isRefreshButton(slot.getStack())) {
                PlayerInventoryUtil.clickSlot(slot.id, 0, SlotActionType.PICKUP, false);
                break;
            }
        }
    }

    private List<String> getLore(ItemStack stack) {
        LoreComponent loreComp = stack.get(DataComponentTypes.LORE);
        return loreComp != null ? loreComp.lines().stream().map(Text::getString).toList() : Collections.emptyList();
    }

    private void openMenu() { mc.setScreen(new AutoBuyScreen()); }

    // Квадратное меню
    private class AutoBuyScreen extends Screen {
        private static final int SLOT_SIZE = 44;
        private static final int PADDING = 6;
        private static final int COLS = 5;
        private static final int ROWS = 5;

        private float x, y;
        private int menuWidth = COLS * (SLOT_SIZE + PADDING) + PADDING * 2;
        private int menuHeight = ROWS * (SLOT_SIZE + PADDING) + PADDING * 2 + 20;
        private boolean dragging = false;
        private double dragX, dragY;
        private int scrollOffset = 0;
        private int maxScroll = 0;
        private static float lastX = -1, lastY = -1;

        protected AutoBuyScreen() {
            super(Text.of("AutoBuy"));
            int totalItems = targets.size();
            int totalRows = (int) Math.ceil(totalItems / (double) COLS);
            maxScroll = Math.max(0, totalRows - ROWS);

            if (lastX == -1) {
                x = (mc.getWindow().getScaledWidth() - menuWidth) / 2f;
                y = (mc.getWindow().getScaledHeight() - menuHeight) / 2f;
            } else {
                x = lastX;
                y = lastY;
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (dragging) {
                x = (float) (mouseX - dragX);
                y = (float) (mouseY - dragY);
                lastX = x;
                lastY = y;
            }

            Render2DUtil.blur.render(ShapeProperties.create(context.getMatrices(), x, y, menuWidth, menuHeight)
                    .round(12).outlineColor(ColorUtil.getOutline()).thickness(2).color(ColorUtil.getRect(0.85f)).build());

            Fonts.getSize(14).drawCenteredString(context.getMatrices(), "AutoBuy", x + menuWidth / 2f, y + 8, -1);

            int startIndex = scrollOffset * COLS;
            int endIndex = Math.min(startIndex + ROWS * COLS, targets.size());

            List<ItemTarget> itemsList = new ArrayList<>(targets.values());

            for (int idx = startIndex; idx < endIndex; idx++) {
                ItemTarget item = itemsList.get(idx);
                int localIdx = idx - startIndex;
                int row = localIdx / COLS;
                int col = localIdx % COLS;

                int ix = (int) (x + PADDING + 5 + col * (SLOT_SIZE + PADDING));
                int iy = (int) (y + 25 + row * (SLOT_SIZE + PADDING));
                boolean active = item.buyPrice > 0;

                Render2DUtil.blur.render(ShapeProperties.create(context.getMatrices(), ix, iy, SLOT_SIZE, SLOT_SIZE)
                        .round(8).outlineColor(active ? 0xFF55FF55 : 0xFF444444).thickness(1.5f)
                        .color(active ? 0x4000FF00 : 0xAA202020).build());

                Render2DUtil.defaultDrawStack(context, item.displayStack, ix + 14, iy + 14, false, false, 1.0F);

                if (active) {
                    Fonts.getSize(8).drawCenteredString(context.getMatrices(), formatPrice(item.buyPrice),
                            ix + SLOT_SIZE / 2f, iy + SLOT_SIZE - 6, 0xFF55FF55);
                }
            }
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (maxScroll > 0) {
                scrollOffset -= (int) verticalAmount;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseY >= y && mouseY <= y + 20 && mouseX >= x && mouseX <= x + menuWidth) {
                dragging = true;
                dragX = mouseX - x;
                dragY = mouseY - y;
                return true;
            }

            int startIndex = scrollOffset * COLS;
            List<ItemTarget> itemsList = new ArrayList<>(targets.values());

            for (int idx = startIndex; idx < Math.min(startIndex + ROWS * COLS, itemsList.size()); idx++) {
                ItemTarget item = itemsList.get(idx);
                int localIdx = idx - startIndex;
                int row = localIdx / COLS;
                int col = localIdx % COLS;

                int ix = (int) (x + PADDING + 5 + col * (SLOT_SIZE + PADDING));
                int iy = (int) (y + 25 + row * (SLOT_SIZE + PADDING));

                if (mouseX >= ix && mouseX <= ix + SLOT_SIZE && mouseY >= iy && mouseY <= iy + SLOT_SIZE) {
                    if (button == 0) {
                        item.buyPrice = 0;
                        saveConfig();
                        Notifications.getInstance().addList("Сброшена цена для " + item.displayName, 1000);
                    } else if (button == 1) {
                        mc.setScreen(new EditPriceScreen(item, this));
                    }
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
            dragging = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override public boolean shouldPause() { return false; }
    }

    private class EditPriceScreen extends Screen {
        final ItemTarget target;
        final Screen parent;
        String buffer = "";
        float ex, ey;
        int editWidth = 180, editHeight = 95;
        boolean dragging = false;
        double dragX, dragY;

        protected EditPriceScreen(ItemTarget target, Screen parent) {
            super(Text.of("Edit Price"));
            this.target = target;
            this.parent = parent;
            this.buffer = target.buyPrice > 0 ? String.valueOf(target.buyPrice) : "";
            ex = (mc.getWindow().getScaledWidth() - editWidth) / 2f;
            ey = (mc.getWindow().getScaledHeight() - editHeight) / 2f;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);

            if (dragging) {
                ex = (float) (mouseX - dragX);
                ey = (float) (mouseY - dragY);
            }

            Render2DUtil.blur.render(ShapeProperties.create(context.getMatrices(), ex, ey, editWidth, editHeight)
                    .round(10).outlineColor(0xFF888888).thickness(1.5f).color(0xCC151515).build());

            Fonts.getSize(13).drawCenteredString(context.getMatrices(), target.displayName, ex + editWidth / 2f, ey + 14, -1);

            int bx = (int)ex + 10, by = (int)ey + 38, bw = editWidth - 20, bh = 24;
            Render2DUtil.blur.render(ShapeProperties.create(context.getMatrices(), bx, by, bw, bh)
                    .round(5).outlineColor(0xFF55FF55).thickness(1.5f).color(0xAA252525).build());

            Fonts.getSize(10).drawString(context.getMatrices(), "Цена за шт:", bx, by - 8, 0xFFBBBBBB);

            String displayText = buffer.isEmpty() ? "0" : buffer;
            Fonts.getSize(13).drawString(context.getMatrices(), displayText, bx + 6, by + 6, 0xFFFFFFFF);

            drawButton(context, (int)ex + 10, (int)ey + editHeight - 22, 55, 18, "Отмена", 0xFF444444);
            drawButton(context, (int)ex + editWidth - 65, (int)ey + editHeight - 22, 55, 18, "Ок", 0xFF33AA33);
        }

        private void drawButton(DrawContext context, int bx, int by, int bw, int bh, String text, int color) {
            Render2DUtil.blur.render(ShapeProperties.create(context.getMatrices(), bx, by, bw, bh)
                    .round(4).thickness(1).outlineColor(color).color(0xAA202020).build());
            Fonts.getSize(10).drawCenteredString(context.getMatrices(), text, bx + bw / 2f, by + bh / 2f - 3, color);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX >= ex && mouseX <= ex + editWidth && mouseY >= ey && mouseY <= ey + 25) {
                dragging = true;
                dragX = mouseX - ex;
                dragY = mouseY - ey;
                return true;
            }

            if (mouseX >= ex + 10 && mouseX <= ex + 65 && mouseY >= ey + editHeight - 22 && mouseY <= ey + editHeight - 4) {
                mc.setScreen(parent);
                return true;
            }

            if (mouseX >= ex + editWidth - 65 && mouseX <= ex + editWidth - 10 && mouseY >= ey + editHeight - 22 && mouseY <= ey + editHeight - 4) {
                try {
                    int price = buffer.isEmpty() ? 0 : Integer.parseInt(buffer);
                    target.buyPrice = price;
                    saveConfig();
                    Notifications.getInstance().addList(target.displayName + " §aцена установлена: " + formatPrice(price), 1500);
                } catch (NumberFormatException ignored) {}
                mc.setScreen(parent);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
            dragging = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                try {
                    target.buyPrice = buffer.isEmpty() ? 0 : Integer.parseInt(buffer);
                    saveConfig();
                } catch (Exception e) { target.buyPrice = 0; }
                mc.setScreen(parent);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                mc.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (Character.isDigit(chr) && buffer.length() < 9) {
                buffer += chr;
                return true;
            }
            return true;
        }
    }

    private String formatPrice(int p) {
        if (p >= 1_000_000) return String.format("%.1fM", p/1_000_000f);
        if (p >= 1_000) return String.format("%.1fK", p/1_000f);
        return String.valueOf(p);
    }

    private void saveConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("autoBuyEnabled", autoBuyEnabled);
            Map<String, Integer> data = new HashMap<>();
            targets.values().forEach(t -> {
                if (t.buyPrice > 0) data.put(t.id, t.buyPrice);
            });
            config.put("buyPrices", data);

            String json = GSON.toJson(config);
            Files.writeString(CONFIG_PATH, json);
        } catch (Exception ignored) {}
    }

    private void loadConfig() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            String content = Files.readString(CONFIG_PATH);
            if (content == null || content.trim().isEmpty() || !content.trim().startsWith("{")) {
                Files.deleteIfExists(CONFIG_PATH);
                return;
            }

            Map<String, Object> config = GSON.fromJson(content, new TypeToken<Map<String, Object>>(){}.getType());
            if (config != null) {
                if (config.containsKey("autoBuyEnabled")) {
                    autoBuyEnabled = (boolean) config.get("autoBuyEnabled");
                }
                @SuppressWarnings("unchecked")
                Map<String, Integer> data = (Map<String, Integer>) config.get("buyPrices");
                if (data != null) {
                    targets.values().forEach(t -> t.buyPrice = data.getOrDefault(t.id, 0));
                }
            }
        } catch (Exception e) {
            try {
                Files.deleteIfExists(CONFIG_PATH);
            } catch (Exception ignored) {}
        }
    }

    @Getter
    public static class ItemTarget {
        final String id, displayName;
        final ItemStack displayStack;
        final List<String> loreKeywords;
        final boolean checkByName;
        final boolean checkByItem;
        int buyPrice;

        public ItemTarget(String id, String displayName, ItemStack stack, List<String> lore, int price, boolean checkByName, boolean checkByItem) {
            this.id = id;
            this.displayName = displayName;
            this.displayStack = stack;
            this.loreKeywords = lore;
            this.buyPrice = price;
            this.checkByName = checkByName;
            this.checkByItem = checkByItem;
        }
    }
}