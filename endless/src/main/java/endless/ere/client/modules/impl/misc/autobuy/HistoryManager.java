package endless.ere.client.modules.impl.misc.autobuy;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import endless.ere.utility.interfaces.IMinecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Перенесено из Essence (code.essence.display.screens.autobuy.history.HistoryManager).
 * Хранит историю покупок и парсит цену/продавца из лора предмета.
 */
public class HistoryManager implements IMinecraft {
    private static HistoryManager instance;
    private final List<PurchaseRecord> history = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 100;

    private static final Pattern SELLER_PATTERN = Pattern.compile("⚕.*:\\s*([\\w\\d_]+)", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$\\s*(\\d+(?:[,\\s.]\\d{3})*(?:\\.\\d{2})?)");
    private static final Pattern PRICE_PATTERN_HOLYWORLD = Pattern.compile("Цена:.*?(\\d+(?:[\\s.]\\d{3})*)\\s*¤");

    private HistoryManager() {}

    public static HistoryManager getInstance() {
        if (instance == null) instance = new HistoryManager();
        return instance;
    }

    public void addPurchase(ItemStack item) {
        if (item == null || item.isEmpty()) return;

        String itemName = item.getName().getString()
                .replaceAll("^\\s*-\\s*", "")
                .replaceAll("\\s*-\\s*$", "")
                .trim();

        int quantity = item.getCount();
        int price = extractPrice(item);
        String seller = extractSeller(item);

        PurchaseRecord record = new PurchaseRecord(item, itemName, quantity, price, seller);
        history.add(0, record);

        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
    }

    public void addPurchaseFromMessage(String itemName, int price) {
        if (itemName == null || itemName.isEmpty() || price <= 0) return;

        String cleanItemName = itemName.replaceAll("§[0-9a-fk-or]", "").trim();
        String seller = findSellerInInventory(cleanItemName);
        String displayName = cleanItemName.replaceAll("^\\s*-\\s*", "").replaceAll("\\s*-\\s*$", "").trim();

        PurchaseRecord record = new PurchaseRecord(ItemStack.EMPTY, displayName, 1, price, seller);
        history.add(0, record);

        if (history.size() > MAX_HISTORY_SIZE) {
            history.remove(history.size() - 1);
        }
    }

    private String findSellerInInventory(String itemName) {
        if (mc.player == null) return "Неизвестно";

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            String stackName = stack.getName().getString().replaceAll("§[0-9a-fk-or]", "").trim();
            if (stackName.contains(itemName) || itemName.contains(stackName)) {
                String seller = extractSeller(stack);
                if (!"Неизвестно".equals(seller)) return seller;
            }
        }

        return "Неизвестно";
    }

    public List<PurchaseRecord> getHistory() {
        return new ArrayList<>(history);
    }

    public void clearHistory() {
        history.clear();
    }

    private int extractPrice(ItemStack stack) {
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return 0;

        for (Text line : lore.lines()) {
            String text = line.getString();

            Matcher hwMatcher = PRICE_PATTERN_HOLYWORLD.matcher(text);
            if (hwMatcher.find()) {
                try {
                    return Integer.parseInt(hwMatcher.group(1).replaceAll("[\\s.]", ""));
                } catch (NumberFormatException ignored) {}
            }

            Matcher matcher = PRICE_PATTERN.matcher(text);
            String lastFound = null;
            while (matcher.find()) lastFound = matcher.group(1);
            if (lastFound != null) {
                try {
                    return Integer.parseInt(lastFound.replaceAll("[\\s,.]", ""));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private String extractSeller(ItemStack stack) {
        if (stack == null) return "Неизвестно";
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return "Неизвестно";

        for (Text line : lore.lines()) {
            String text = line.getString();

            Matcher m = SELLER_PATTERN.matcher(text);
            if (m.find()) return m.group(1);

            if (text.contains("Продавец:") || text.contains("Продaвeц:")) {
                String[] parts = text.split("Продавец:|Продaвeц:");
                if (parts.length > 1) {
                    String sellerPart = parts[1].trim()
                            .replaceAll("§[0-9a-fk-or]", "")
                            .replaceAll("[▍▶▎]", "")
                            .trim();
                    String[] words = sellerPart.split("\\s+");
                    if (words.length > 0 && !words[0].isEmpty()) return words[0];
                }
            }
        }

        return "Неизвестно";
    }
}
