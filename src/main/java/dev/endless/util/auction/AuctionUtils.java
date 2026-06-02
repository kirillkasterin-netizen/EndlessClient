package dev.endless.util.auction;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилиты для разбора аукционных предметов HolyWorld.
 *
 * <p>compareItem(a, b) — где a это лот с аукциона, b — наш эталон.
 * Сравнение упрощённое: совпадение по типу Item + (имени ИЛИ чарам).
 * Этого достаточно для HolyWorld т.к. одинаковые предметы продаются
 * с одинаковыми именами, а уникальные (Eternity/Infinity) различаются по чарам/лору.
 */
public final class AuctionUtils {

    public static final Pattern PRICE_DOLLAR = Pattern.compile("\\$\\s*(\\d+(?:[\\s,]\\d{3})*(?:\\.\\d{2})?)");
    public static final Pattern PRICE_CURRENCY = Pattern.compile("(\\d+(?:[\\s,.]?\\d{3})*)\\s*¤");
    public static final Pattern PRICE_LABEL = Pattern.compile(
            "(?i)(?:цена|price)[^0-9]*?(\\d{1,3}(?:[\\s,.]?\\d{3})*)");

    private AuctionUtils() {}

    /**
     * Достаёт цену из лора предмета. -1 если не нашли.
     * Сначала ищет «Цена: N», потом «N¤», потом «$N».
     * Берёт максимальное найденное число (часто это итоговая цена за стак).
     */
    public static int getPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        var lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return -1;

        Integer best = null;
        for (Text line : lore.lines()) {
            String text = line.getString();
            if (text == null) continue;

            String found = lastMatch(PRICE_LABEL, text);
            if (found == null) found = lastMatch(PRICE_CURRENCY, text);
            if (found == null) found = lastMatch(PRICE_DOLLAR, text);
            if (found == null) continue;

            try {
                int v = Integer.parseInt(found.replaceAll("[\\s,.]", ""));
                if (v > 0 && (best == null || v > best)) best = v;
            } catch (NumberFormatException ignored) {}
        }
        return best != null ? best : -1;
    }

    private static String lastMatch(Pattern p, String s) {
        Matcher m = p.matcher(s);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.toLowerCase().trim()
                .replaceAll("§.", "")
                .replaceAll("\\s+", " ");
    }

    /**
     * Сравнивает лот {@code a} (с аукциона) с эталоном {@code b} (наш предмет).
     * Логика:
     *  1. Тип Item должен совпадать (PLAYER_HEAD пропускаем — для сфер)
     *  2. Если у эталона есть NBT-флаги (HolyWorldSphere/Talik/...) — спецсравнение
     *  3. Иначе:
     *     - если имя b содержится в имени a (или наоборот) → match
     *     - ИЛИ если у b есть чары, и все они присутствуют у a с нужным уровнем → match
     */
    public static boolean compareItem(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;

        var bCustom = b.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound bNbt = bCustom != null ? bCustom.copyNbt() : null;

        // Sphere — особый случай (PLAYER_HEAD по тексту имени)
        if (bNbt != null && bNbt.getBoolean("HolyWorldSphere")) {
            return a.getItem() == Items.PLAYER_HEAD && nameMatches(a, b);
        }

        // Тип Item должен совпадать
        if (a.getItem() != b.getItem()) return false;

        if (bNbt != null) {
            if (bNbt.getBoolean("HolyWorldExpBottle")) return matchExpBottle(a, bNbt);
            if (bNbt.getBoolean("HolyWorldBackpack")) return matchByName(a, bNbt.getString("backpackTier"))
                    || nameMatches(a, b);
            if (bNbt.getBoolean("HolyWorldPyrotechnic")
                    || bNbt.getBoolean("HolyWorldKringe")
                    || bNbt.getBoolean("HolyWorldRune")
                    || bNbt.getBoolean("HolyWorldKringeEffect")) {
                return nameMatches(a, b);
            }
            if (bNbt.getBoolean("HolyWorldStandardPotion")) return matchPotionId(a, bNbt);
            if (bNbt.getBoolean("HolyWorldPotion")) return nameMatches(a, b);
            if (bNbt.getBoolean("HolyWorldTalik")) {
                // Талисманы Eternity/Infinity/Stinger различаются по имени
                return nameMatches(a, b);
            }
        }

        // Универсальное сравнение: совпадение по имени ИЛИ по чарам/лору.
        if (nameMatches(a, b)) {
            // Если имена совпадают, но у эталона есть требования по чарам — проверим
            ItemEnchantmentsComponent bEnch = b.get(DataComponentTypes.ENCHANTMENTS);
            if (bEnch != null && !bEnch.isEmpty()) {
                return enchantsMatch(a, b);
            }
            return true;
        }

        // Имена не совпадают — но если у эталона строгие чары, может сойтись по ним
        ItemEnchantmentsComponent bEnch = b.get(DataComponentTypes.ENCHANTMENTS);
        if (bEnch != null && !bEnch.isEmpty()) {
            return enchantsMatch(a, b);
        }

        return false;
    }

    /** Совпадение по имени. Имя лота должно содержать имя эталона. */
    private static boolean nameMatches(ItemStack a, ItemStack b) {
        String aName = clean(a.getName().getString());
        String bName = clean(b.getName().getString());
        if (aName.isEmpty() || bName.isEmpty()) return false;
        return aName.contains(bName);
    }

    private static boolean matchByName(ItemStack a, String tag) {
        if (tag == null || tag.isEmpty()) return false;
        String aName = clean(a.getName().getString());
        return aName.contains(tag.toLowerCase());
    }

    private static boolean matchExpBottle(ItemStack a, NbtCompound bNbt) {
        String required = bNbt.getString("expLevelMatch");
        int xpAmount = bNbt.getInt("xpAmount");
        if (required == null || required.isEmpty()) return true;

        String aName = clean(a.getName().getString());
        // Извлекаем число из required ("опыт 15" → 15, "опытный" → 0)
        int requiredLevel = extractFirstInt(required);

        // Если в required нет числа (обычный пузырёк опыта) — матчим по любому пузырьку
        // без специальных уровней в имени (имя содержит "опыт" но не "30/50/100/...").
        if (requiredLevel <= 0) {
            return aName.contains("опыт") || aName.contains("опытный");
        }

        // Иначе ищем именно нужный уровень в имени лота
        // (в имени аукционных лотов обычно есть число уровня).
        int aLevel = extractFirstInt(aName);
        return aLevel == requiredLevel;
    }

    private static int extractFirstInt(String s) {
        if (s == null) return 0;
        StringBuilder sb = new StringBuilder();
        boolean started = false;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
                started = true;
            } else if (started) break;
        }
        if (sb.length() == 0) return 0;
        try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static boolean matchPotionId(ItemStack a, NbtCompound bNbt) {
        String potionId = bNbt.getString("potionId");
        if (potionId == null || potionId.isEmpty()) return true;
        var aPotion = a.get(DataComponentTypes.POTION_CONTENTS);
        if (aPotion == null) return false;
        return aPotion.potion().map(p -> p.getIdAsString().equals(potionId)).orElse(false);
    }

    /**
     * Проверяет что все чары эталона присутствуют у лота с уровнем не ниже требуемого.
     */
    public static boolean enchantsMatch(ItemStack a, ItemStack b) {
        ItemEnchantmentsComponent aEnch = a.get(DataComponentTypes.ENCHANTMENTS);
        ItemEnchantmentsComponent bEnch = b.get(DataComponentTypes.ENCHANTMENTS);
        if (bEnch == null || bEnch.isEmpty()) return true;
        if (aEnch == null || aEnch.isEmpty()) return false;

        for (RegistryEntry<Enchantment> e : bEnch.getEnchantments()) {
            int needLvl = bEnch.getLevel(e);
            int gotLvl = aEnch.getLevel(e);
            if (gotLvl < needLvl) return false;
        }
        return true;
    }
}
