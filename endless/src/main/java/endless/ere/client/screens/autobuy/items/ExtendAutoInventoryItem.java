package endless.ere.client.screens.autobuy.items;

import endless.ere.base.autobuy.item.ItemBuy;
import endless.ere.client.screens.menu.settings.api.MenuSetting;

import java.util.List;

public abstract class ExtendAutoInventoryItem extends AutoInventoryItem {
    public ExtendAutoInventoryItem(ItemBuy itemBuy) {
        super(itemBuy);
    }
    public abstract List<MenuSetting> getEnchants();

}
