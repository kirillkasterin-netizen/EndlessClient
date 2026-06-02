package endless.ere.base.autobuy.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Map;

public class NbtItemBuy extends ItemBuy {
    private final Map<String, String> nbtKeyValue;
    private boolean requireOriginalStar = false;

    public NbtItemBuy(ItemStack itemStack, String displayName, String searchName, Category maxSumBuy) {
        super(itemStack, displayName, searchName, maxSumBuy);
        nbtKeyValue = new HashMap<>();
    }

    public NbtItemBuy(ItemStack itemStack, String searchName, Category maxSumBuy, Map<String, String> nbtKeyValue) {
        super(itemStack, searchName, maxSumBuy);
        this.nbtKeyValue = nbtKeyValue;
    }

    public NbtItemBuy(ItemStack itemStack, String searchName, Category maxSumBuy, String key, String value) {
        super(itemStack, searchName, maxSumBuy);
        this.nbtKeyValue = new HashMap<>();
        this.nbtKeyValue.put(key, value);
    }

    /** Требовать наличие [★] в начале имени для оригинальности предмета. */
    public NbtItemBuy requireStar() {
        this.requireOriginalStar = true;
        return this;
    }

    @Override
    public boolean isBuy(ItemStack stack) {
        if (!super.isBuy(stack)) return false;
        
        // Проверка на оригинальность - звёздочка [★] в начале имени
        if (requireOriginalStar) {
            String name = stack.getName().getString();
            // Убираем форматирование §x и проверяем начало
            String cleanName = name.replaceAll("§[0-9a-fk-or]", "").trim();
            if (!cleanName.startsWith("[★]") && !cleanName.startsWith("[*]")) {
                return false;
            }
        }
        
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return false;
        NbtCompound nbt = customData.getNbt();

        for (Map.Entry<String, String> entry : nbtKeyValue.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ((!nbt.contains(key) || !nbt.get(key).toString().replaceAll(",?UUID:\\[I;[-0-9]+,[-0-9]+,[-0-9]+,[-0-9]+]", "").contains(value.replaceAll(",?UUID:\\[I;[-0-9]+,[-0-9]+,[-0-9]+,[-0-9]+]", "")))) {
                return false;
            }

        }
        return true;
    }

}
