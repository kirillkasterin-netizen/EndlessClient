package endless.ere.client.modules.impl.misc.autobuy;

import lombok.Getter;
import net.minecraft.item.ItemStack;

@Getter
public class PurchaseRecord {
    private final ItemStack item;
    private final String itemName;
    private final int quantity;
    private final int price;
    private final String sellerName;
    private final long purchaseTime;

    public PurchaseRecord(ItemStack item, String itemName, int quantity, int price, String sellerName) {
        this.item = item == null ? ItemStack.EMPTY : item.copy();
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.sellerName = sellerName == null ? "Неизвестно" : sellerName;
        this.purchaseTime = System.currentTimeMillis();
    }

    public String getFormattedTime() {
        long elapsed = System.currentTimeMillis() - purchaseTime;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / (1000 * 60)) % 60;
        long hours = elapsed / (1000 * 60 * 60);
        return hours + "ч " + minutes + "м " + seconds + "с";
    }

    public String getFormattedPrice() {
        StringBuilder result = new StringBuilder();
        String priceStr = String.valueOf(price);
        int count = 0;
        for (int i = priceStr.length() - 1; i >= 0; i--) {
            if (count > 0 && count % 3 == 0) {
                result.insert(0, '.');
            }
            result.insert(0, priceStr.charAt(i));
            count++;
        }
        return "$" + result;
    }

    public String getDisplayName() {
        return itemName + (quantity > 1 ? " x" + quantity : "");
    }
}
