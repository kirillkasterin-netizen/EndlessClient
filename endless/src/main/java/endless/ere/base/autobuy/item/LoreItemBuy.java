package endless.ere.base.autobuy.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * Совпадение по ключевым словам в лоре. Для предметов где Item одинаков
 * (например все сферы — PLAYER_HEAD), но различаются строки лора.
 */
public class LoreItemBuy extends ItemBuy {
    private final List<String> loreKeywords;

    public LoreItemBuy(ItemStack itemStack, String displayName, Category category, String... loreKeywords) {
        super(itemStack, displayName, category);
        this.loreKeywords = Arrays.asList(loreKeywords);
    }

    @Override
    public boolean isBuy(ItemStack stack) {
        if (!super.isBuy(stack)) return false;
        if (loreKeywords == null || loreKeywords.isEmpty()) return true;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return false;

        StringBuilder sb = new StringBuilder();
        for (Text line : lore.lines()) {
            sb.append(line.getString().replaceAll("§[0-9a-fk-or]", ""));
            sb.append('\n');
        }
        String fullLore = sb.toString();

        // все ключевые слова должны быть в лоре
        for (String keyword : loreKeywords) {
            if (!fullLore.contains(keyword)) return false;
        }
        return true;
    }
}
