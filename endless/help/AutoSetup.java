package ru.zenith.implement.features.modules.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
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
import ru.zenith.common.util.entity.PlayerInventoryUtil;
import ru.zenith.common.util.other.StopWatch;
import ru.zenith.implement.events.keyboard.KeyEvent;
import ru.zenith.implement.events.player.TickEvent;
import ru.zenith.implement.features.draggables.Notifications;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoSetup extends Module {

    private static AutoSetup instance;

    final BindSetting setupBind = new BindSetting("SetupBind", "Запустить автонастройку цен").setKey(GLFW.GLFW_KEY_UNKNOWN);
    final ValueSetting discountPercent = new ValueSetting("Discount Percent", "Процент скидки от мин. цены").setValue(50).range(10, 90);
    final ValueSetting searchDuration = new ValueSetting("Search Duration", "Время поиска на странице (сек)").setValue(10).range(5, 60);
    final ValueSetting pageUpdateDelay = new ValueSetting("Page Update Delay", "Задержка обновления страницы (мс)").setValue(2000).range(1000, 5000);

    boolean isSettingUp = false;
    int currentItemIndex = 0;
    final StopWatch pageTimer = new StopWatch();
    final StopWatch refreshTimer = new StopWatch();
    final StopWatch retryTimer = new StopWatch();
    GenericContainerScreen currentAuctionScreen = null;
    String currentItemId = "";
    int bestPricePerItem = Integer.MAX_VALUE;
    boolean waitingForAuction = false;
    boolean retrying = false;
    int refreshCount = 0;

    final List<ItemSearchData> itemSearchList = new ArrayList<>();

    static final Path CONFIG_PATH = Paths.get("AutoSetupConfig.json");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Pattern PRICE_PATTERN = Pattern.compile("Цен[аaAАыЫ]?:?\\s*([\\d,\\s\\.]+)", Pattern.CASE_INSENSITIVE);

    public AutoSetup() {
        super("AutoSetup", "Auto Setup", ModuleCategory.MISC);
        setup(setupBind, discountPercent, searchDuration, pageUpdateDelay);
        initItemSearchList();
        instance = this;
    }

    public static AutoSetup getInstance() { return instance; }

    private void initItemSearchList() {
        itemSearchList.add(new ItemSearchData("golden_apple", "гэпл"));
        itemSearchList.add(new ItemSearchData("enchanted_golden_apple", "чарка"));
        itemSearchList.add(new ItemSearchData("elytra", "элитры"));
        itemSearchList.add(new ItemSearchData("netherite_ingot", "незеритовый слиток"));
        itemSearchList.add(new ItemSearchData("spawner", "спавнер"));
        itemSearchList.add(new ItemSearchData("diamond", "алмаз"));
        itemSearchList.add(new ItemSearchData("beacon", "маяк"));
        itemSearchList.add(new ItemSearchData("sniffer_egg", "яйцо нюхача"));
        itemSearchList.add(new ItemSearchData("trial_key", "ключ испытаний"));
        itemSearchList.add(new ItemSearchData("dragon_head", "голова дракона"));
        itemSearchList.add(new ItemSearchData("villager_spawn_egg", "яйцо жителя"));
        itemSearchList.add(new ItemSearchData("dynamite_black", "блэк"));
        itemSearchList.add(new ItemSearchData("dynamite_white", "вайт"));
        itemSearchList.add(new ItemSearchData("silver", "серебро"));
        itemSearchList.add(new ItemSearchData("trapka", "трапка"));
        itemSearchList.add(new ItemSearchData("sphere_beast", "сфера бестии"));
        itemSearchList.add(new ItemSearchData("sphere_satyr", "сфера сатира"));
        itemSearchList.add(new ItemSearchData("sphere_chaos", "сфера хаоса"));
        itemSearchList.add(new ItemSearchData("sphere_ares", "сфера ареса"));
        itemSearchList.add(new ItemSearchData("sphere_hydra", "сфера гидры"));
        itemSearchList.add(new ItemSearchData("sphere_titan", "сфера титана"));
        itemSearchList.add(new ItemSearchData("talisman_demon", "талисман Демона"));
        itemSearchList.add(new ItemSearchData("talisman_discord", "талисман Раздор"));
        itemSearchList.add(new ItemSearchData("talisman_rage", "ярости"));
        itemSearchList.add(new ItemSearchData("talisman_crusher", "талисман крушителя"));
        itemSearchList.add(new ItemSearchData("talisman_tyrant", "тиран"));
        itemSearchList.add(new ItemSearchData("potion_assassin", "зелье ассасина"));
        itemSearchList.add(new ItemSearchData("potion_holy_water", "святая вода"));
        itemSearchList.add(new ItemSearchData("potion_paladin", "зелье палладина"));
        itemSearchList.add(new ItemSearchData("potion_sleeping", "снотворное"));
        itemSearchList.add(new ItemSearchData("potion_clapper", "хлопушка"));
        itemSearchList.add(new ItemSearchData("potion_wrath", "зелье гнева"));
        itemSearchList.add(new ItemSearchData("potion_radiation", "зелье радиации"));
        itemSearchList.add(new ItemSearchData("crusher_pickaxe", "кирка крушителя"));
        itemSearchList.add(new ItemSearchData("crusher_leggings", "поножи крушителя"));
        itemSearchList.add(new ItemSearchData("crusher_chestplate", "нагрудник крушителя"));
        itemSearchList.add(new ItemSearchData("crusher_helmet", "шлем крушителя"));
        itemSearchList.add(new ItemSearchData("crusher_boots", "ботинки крушителя"));
    }

    @Override
    public void deactivate() {
        isSettingUp = false;
        waitingForAuction = false;
        retrying = false;
        currentAuctionScreen = null;
        super.deactivate();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (e.isKeyDown(setupBind.getKey()) && !isSettingUp && mc.currentScreen == null) {
            startSetup();
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!isSettingUp) return;

        if (retrying && retryTimer.finished(2000)) {
            retrying = false;
            searchCurrentItem();
        }

        if (mc.currentScreen instanceof GenericContainerScreen) {
            currentAuctionScreen = (GenericContainerScreen) mc.currentScreen;

            if (waitingForAuction) {
                waitingForAuction = false;
                retrying = false;
                refreshCount = 0;
                bestPricePerItem = Integer.MAX_VALUE;
                pageTimer.reset();
                refreshTimer.reset();
                Notifications.getInstance().addList("§aМеню открыто, начинаю сканирование...", 1000);
            }

            if (!waitingForAuction && !retrying && pageTimer.elapsedTime() < (long) searchDuration.getValue() * 1000) {
                scanPrices();

                if (refreshTimer.finished((long) pageUpdateDelay.getValue())) {
                    refreshPage();
                    refreshTimer.reset();
                    refreshCount++;
                    Notifications.getInstance().addList("§eОбновление страницы #" + refreshCount, 1000);
                }
            } else if (!waitingForAuction && !retrying && pageTimer.elapsedTime() >= (long) searchDuration.getValue() * 1000) {
                finishCurrentItem();
            }
        } else {
            if (!waitingForAuction && !retrying && isSettingUp && currentItemIndex < itemSearchList.size()) {
                Notifications.getInstance().addList("§cМеню не открыто, повтор через 2 сек...", 1000);
                retrying = true;
                retryTimer.reset();
            }
            currentAuctionScreen = null;
        }
    }

    private void startSetup() {
        if (AutoBuy.getInstance() == null) {
            Notifications.getInstance().addList("§cAutoBuy не найден!", 3000);
            return;
        }

        isSettingUp = true;
        currentItemIndex = 0;
        retrying = false;
        Notifications.getInstance().addList("§aЗапущена автонастройка цен...", 2000);
        searchNextItem();
    }

    private void searchNextItem() {
        if (currentItemIndex >= itemSearchList.size()) {
            finishSetup();
            return;
        }
        searchCurrentItem();
    }

    private void searchCurrentItem() {
        ItemSearchData data = itemSearchList.get(currentItemIndex);
        currentItemId = data.itemId;
        String searchTerm = data.searchTerm;

        Notifications.getInstance().addList("§eПоиск: " + searchTerm + " (" + (currentItemIndex + 1) + "/" + itemSearchList.size() + ")", 2000);

        if (mc.player != null) {
            mc.player.networkHandler.sendChatCommand("ah search " + searchTerm);
        }

        waitingForAuction = true;
        bestPricePerItem = Integer.MAX_VALUE;
    }

    private void scanPrices() {
        if (currentAuctionScreen == null) return;

        var handler = currentAuctionScreen.getScreenHandler();

        for (int i = 0; i < handler.slots.size(); i++) {
            Slot slot = handler.getSlot(i);
            if (slot.inventory == mc.player.getInventory()) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String name = stack.getName().getString().toLowerCase();
            if (name.contains("обновить") || name.contains("refresh")) continue;

            List<String> lore = getLore(stack);
            int totalPrice = parsePriceFromLore(lore);

            if (totalPrice > 0) {
                int count = stack.getCount();
                int pricePerItem = totalPrice / count;

                if (pricePerItem < bestPricePerItem) {
                    bestPricePerItem = pricePerItem;
                    Notifications.getInstance().addList("§aНовая мин. цена: " + formatPrice(pricePerItem) + " за шт (всего " + formatPrice(totalPrice) + " за " + count + "шт)", 2000);
                }
            }
        }
    }

    private void refreshPage() {
        if (currentAuctionScreen == null) return;
        var handler = currentAuctionScreen.getScreenHandler();
        for (Slot slot : handler.slots) {
            ItemStack stack = slot.getStack();
            String name = stack.getName().getString().toLowerCase();
            if (name.contains("обновить") || name.contains("refresh")) {
                PlayerInventoryUtil.clickSlot(slot.id, 0, SlotActionType.PICKUP, false);
                break;
            }
        }
    }

    private void finishCurrentItem() {
        if (bestPricePerItem != Integer.MAX_VALUE && bestPricePerItem > 0) {
            int discountedPrice = (int) (bestPricePerItem * (1 - discountPercent.getValue() / 100.0));
            if (discountedPrice < 0) discountedPrice = 0;
            setPriceForItem(currentItemId, discountedPrice);
            Notifications.getInstance().addList("§aУстановлена цена " + formatPrice(discountedPrice) + " за шт для " + currentItemId, 3000);
        } else {
            Notifications.getInstance().addList("§cНе найдена цена для " + currentItemId, 2000);
        }

        if (mc.currentScreen != null) {
            mc.currentScreen.close();
        }

        currentItemIndex++;

        new Thread(() -> {
            try {
                Thread.sleep(500);
                mc.execute(() -> {
                    if (currentItemIndex < itemSearchList.size()) {
                        searchNextItem();
                    } else {
                        finishSetup();
                    }
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void finishSetup() {
        isSettingUp = false;
        waitingForAuction = false;
        retrying = false;
        if (mc.currentScreen != null) {
            mc.currentScreen.close();
        }
        Notifications.getInstance().addList("§aАвтонастройка завершена! Цены сохранены.", 3000);
    }

    private void setPriceForItem(String itemId, int price) {
        AutoBuy autoBuy = AutoBuy.getInstance();
        if (autoBuy != null) {
            autoBuy.setPriceForItem(itemId, price);
        }
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

    private List<String> getLore(ItemStack stack) {
        var loreComp = stack.get(DataComponentTypes.LORE);
        return loreComp != null ? loreComp.lines().stream().map(Text::getString).toList() : Collections.emptyList();
    }

    private String formatPrice(int p) {
        if (p >= 1_000_000) return String.format("%.1fM", p/1_000_000f);
        if (p >= 1_000) return String.format("%.1fK", p/1_000f);
        return String.valueOf(p);
    }

    private static class ItemSearchData {
        final String itemId;
        final String searchTerm;

        ItemSearchData(String itemId, String searchTerm) {
            this.itemId = itemId;
            this.searchTerm = searchTerm;
        }
    }
}