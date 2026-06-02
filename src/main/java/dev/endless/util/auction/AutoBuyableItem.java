package dev.endless.util.auction;

import net.minecraft.item.ItemStack;

/**
 * Интерфейс для предмета, который AutoBuy умеет покупать с аукциона.
 *
 * Каждый предмет имеет:
 *  • displayName — человекочитаемое имя для UI
 *  • searchName  — текст для /ah search (автопарсер)
 *  • createReference() — эталонный ItemStack для compareItem()
 *  • settings — индивидуальные настройки (buyBelow, minCount, enabled)
 *  • category — UI-категория (Армор, Сферы, Талисманы и т.п.)
 */
public interface AutoBuyableItem {

    String getDisplayName();

    default String getSearchName() {
        return getDisplayName();
    }

    /** Эталонный itemstack для AuctionUtils.compareItem. */
    ItemStack createReference();

    /** Иконка для отображения в UI. */
    default ItemStack getIcon() {
        return createReference();
    }

    /** Стабильный ID для сериализации. */
    default String getId() {
        return getDisplayName();
    }

    /** Настройки покупки (buyBelow, enabled). */
    AutoBuyItemSettings getSettings();

    /** Категория для группировки в UI (например "Армор", "Сферы", "Зелья"). */
    default String getCategory() {
        return "Прочее";
    }

    default boolean isEnabled() {
        return getSettings().isEnabled();
    }

    default void setEnabled(boolean v) {
        getSettings().setEnabled(v);
    }
}
