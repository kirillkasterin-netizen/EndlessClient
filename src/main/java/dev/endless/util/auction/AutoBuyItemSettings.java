package dev.endless.util.auction;

import lombok.Getter;
import lombok.Setter;

/**
 * Per-item настройки. Адаптация {@code AutoBuyItemSettings} из essence fix.
 */
@Getter
@Setter
public class AutoBuyItemSettings {
    /** Покупаем если цена меньше или равна этому значению. */
    private int buyBelow;
    /** Минимальное количество в стаке (для стакающихся предметов). */
    private int minCount = 1;
    /** Минимальная оставшаяся прочность (для брони/оружия/элитр). 0 = не проверять. */
    private int minDurability = 0;
    /** Включён ли в автопокупку. */
    private boolean enabled;

    public AutoBuyItemSettings(int buyBelow) {
        this.buyBelow = buyBelow;
    }

    public AutoBuyItemSettings() {
        this.buyBelow = 0;
    }
}
