package dev.endless.util.auction.holyworld;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import dev.endless.util.auction.AutoBuyableItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.AttributeData;
import dev.endless.util.auction.holyworld.HolyWorldItems.EnchantmentData;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldBackpackItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldExpBottleItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldPotionItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldSphereItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldStandardPotionItem;
import dev.endless.util.auction.holyworld.HolyWorldItems.HolyWorldTalikItem;

import java.util.ArrayList;
import java.util.List;

/** Каталог HolyWorld предметов. Дефолтные цены — приблизительные ориентиры. */
public final class HolyWorldProvider {

    private static List<AutoBuyableItem> items;

    private HolyWorldProvider() {}

    public static List<AutoBuyableItem> getItems() {
        if (items == null) build();
        return items;
    }

    public static void reload() { items = null; }

    private static void build() {
        List<AutoBuyableItem> list = new ArrayList<>();

        // ───── Армор (Infinity = II, Eternity = I) ─────
        list.add(armor("Шлем Infinity", Items.NETHERITE_HELMET, "Армор Infinity", 30000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.AQUA_AFFINITY, 1),
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.RESPIRATION, 3),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый II"));
        list.add(armor("Нагрудник Infinity", Items.NETHERITE_CHESTPLATE, "Армор Infinity", 35000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый II"));
        list.add(armor("Поножи Infinity", Items.NETHERITE_LEGGINGS, "Армор Infinity", 33000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый II"));
        list.add(armor("Ботинки Infinity", Items.NETHERITE_BOOTS, "Армор Infinity", 28000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.DEPTH_STRIDER, 3),
                new EnchantmentData(Enchantments.FEATHER_FALLING, 4),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.SOUL_SPEED, 3),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый II"));

        list.add(armor("Шлем Eternity", Items.NETHERITE_HELMET, "Армор Eternity", 18000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.AQUA_AFFINITY, 1),
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.RESPIRATION, 3),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый I"));
        list.add(armor("Нагрудник Eternity", Items.NETHERITE_CHESTPLATE, "Армор Eternity", 20000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый I"));
        list.add(armor("Штаны Eternity", Items.NETHERITE_LEGGINGS, "Армор Eternity", 19000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый I"));
        list.add(armor("Ботинки Eternity", Items.NETHERITE_BOOTS, "Армор Eternity", 16000, new EnchantmentData[]{
                new EnchantmentData(Enchantments.BLAST_PROTECTION, 5),
                new EnchantmentData(Enchantments.DEPTH_STRIDER, 3),
                new EnchantmentData(Enchantments.FEATHER_FALLING, 4),
                new EnchantmentData(Enchantments.FIRE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROJECTILE_PROTECTION, 5),
                new EnchantmentData(Enchantments.PROTECTION, 5),
                new EnchantmentData(Enchantments.SOUL_SPEED, 3),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, "Непробиваемый I"));

        // ───── Оружие ─────
        list.add(new HolyWorldItem("Меч Eternity", Items.NETHERITE_SWORD, 25000, "Оружие",
                new EnchantmentData[]{
                        new EnchantmentData(Enchantments.BANE_OF_ARTHROPODS, 7),
                        new EnchantmentData(Enchantments.FIRE_ASPECT, 2),
                        new EnchantmentData(Enchantments.LOOTING, 5),
                        new EnchantmentData(Enchantments.MENDING, 1),
                        new EnchantmentData(Enchantments.SHARPNESS, 7),
                        new EnchantmentData(Enchantments.SMITE, 7),
                        new EnchantmentData(Enchantments.SWEEPING_EDGE, 3),
                        new EnchantmentData(Enchantments.UNBREAKING, 5)
                },
                List.of(
                        Text.literal("Богач I").formatted(Formatting.GRAY),
                        Text.literal("Разрушитель II").formatted(Formatting.GRAY),
                        Text.literal("Критический II").formatted(Formatting.GRAY)
                )));

        list.add(new HolyWorldItem("Кирка Eternity", Items.NETHERITE_PICKAXE, 20000, "Оружие",
                new EnchantmentData[]{
                        new EnchantmentData(Enchantments.EFFICIENCY, 10),
                        new EnchantmentData(Enchantments.FORTUNE, 5),
                        new EnchantmentData(Enchantments.MENDING, 1),
                        new EnchantmentData(Enchantments.UNBREAKING, 5)
                },
                List.of(
                        Text.literal("Магнетизм I").formatted(Formatting.GRAY),
                        Text.literal("Неразрушимость I").formatted(Formatting.GRAY),
                        Text.literal("Автоплавка").formatted(Formatting.GRAY),
                        Text.literal("Опытный III").formatted(Formatting.GRAY),
                        Text.literal("Бур II").formatted(Formatting.GRAY)
                )));

        list.add(new HolyWorldItem("Громовержец", Items.TRIDENT, 15000, "Оружие", new EnchantmentData[]{
                new EnchantmentData(Enchantments.IMPALING, 5),
                new EnchantmentData(Enchantments.LOOTING, 5),
                new EnchantmentData(Enchantments.LOYALTY, 3),
                new EnchantmentData(Enchantments.MENDING, 1),
                new EnchantmentData(Enchantments.UNBREAKING, 5)
        }, null));

        list.add(new HolyWorldItem("Арбалет Eternity", Items.CROSSBOW, 8000, "Оружие", new EnchantmentData[]{
                new EnchantmentData(Enchantments.MULTISHOT, 1),
                new EnchantmentData(Enchantments.PIERCING, 5),
                new EnchantmentData(Enchantments.QUICK_CHARGE, 3),
                new EnchantmentData(Enchantments.UNBREAKING, 3)
        }, List.of(Text.literal("Оглушение II").formatted(Formatting.GRAY))));

        list.add(new HolyWorldItem("Элитры", Items.ELYTRA, 20000, "Оружие", null, null));
        list.add(new HolyWorldItem("Броневая элитра", Items.ELYTRA, 30000, "Оружие",
                new EnchantmentData[]{ new EnchantmentData(Enchantments.UNBREAKING, 4) },
                new AttributeData[]{ new AttributeData("minecraft:generic.armor", 8.0, 0, "chest") },
                null));

        // ───── Талисманы ─────
        list.add(new HolyWorldTalikItem("Талисман Stinger", Items.TOTEM_OF_UNDYING, 12000,
                new AttributeData[]{
                        new AttributeData("minecraft:generic.movement_speed", 0.1, 1, "offhand"),
                        new AttributeData("minecraft:generic.attack_damage", 2.0, 0, "offhand"),
                        new AttributeData("minecraft:generic.armor", 2.0, 0, "offhand")
                },
                new EnchantmentData[]{ new EnchantmentData(Enchantments.UNBREAKING, 1) }));
        list.add(new HolyWorldTalikItem("Талисман Infinity", Items.TOTEM_OF_UNDYING, 25000,
                new AttributeData[]{
                        new AttributeData("minecraft:generic.attack_damage", 2.0, 0, "offhand"),
                        new AttributeData("minecraft:generic.armor", 2.0, 0, "offhand"),
                        new AttributeData("minecraft:generic.movement_speed", 0.2, 1, "offhand"),
                        new AttributeData("minecraft:generic.max_health", 2.0, 0, "offhand")
                },
                new EnchantmentData[]{ new EnchantmentData(Enchantments.UNBREAKING, 1) }));
        list.add(new HolyWorldTalikItem("Талисман Eternity", Items.TOTEM_OF_UNDYING, 18000,
                new AttributeData[]{
                        new AttributeData("minecraft:generic.attack_damage", 2.0, 0, "offhand"),
                        new AttributeData("minecraft:generic.armor", 2.0, 0, "offhand"),
                        new AttributeData("minecraft:generic.movement_speed", 0.2, 1, "offhand")
                },
                new EnchantmentData[]{ new EnchantmentData(Enchantments.UNBREAKING, 1) }));
        list.add(new HolyWorldTalikItem("Легендарный талисман", Items.TOTEM_OF_UNDYING, 8000,
                new AttributeData[]{
                        new AttributeData("minecraft:generic.armor", 2.0, 0, "offhand"),
                        new AttributeData("minecraft:generic.attack_damage", 2.0, 0, "offhand")
                },
                new EnchantmentData[]{ new EnchantmentData(Enchantments.UNBREAKING, 1) }));
        list.add(new HolyWorldItem("Тотем бессмертия", Items.TOTEM_OF_UNDYING, 5000, "Талисманы", null, null));

        // ───── Сферы ─────
        list.add(new HolyWorldSphereItem("Сфера Цербера", "Сфера Цербера", Items.PLAYER_HEAD, 15000,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjA5NWE3ZmQ5MGRhYTFiYmU3MDY5MDg5NzQwZTA1ZDBiZmM2NjI5NmVlM2M0MGVlNzFhNGUwYTY2MTZiMmJiYyJ9fX0=",
                "Cerber", "hms-damage:5,hms-rush:1"));
        list.add(new HolyWorldSphereItem("Сфера Флеша", "Сфера Флеша", Items.PLAYER_HEAD, 12000, null, "Flash", "hms-speed:3,hms-armor:1"));
        list.add(new HolyWorldSphereItem("Сфера Имморталити", "Сфера Имморталити", Items.PLAYER_HEAD, 14000, null, "Immortal", "hms-speed:2,hms-damage:3"));
        list.add(new HolyWorldSphereItem("Сфера Арморталити", "Сфера Арморталити", Items.PLAYER_HEAD, 16000, null, "Armortality", "hms-armor:2,hms-damage:2,hms-health:2"));
        list.add(new HolyWorldSphereItem("Сфера на скорость 3", "Сфера на скорость 3", Items.PLAYER_HEAD, 8000, null, "Speed3", null));
        list.add(new HolyWorldSphereItem("Сфера Eternity", "Сфера Eternity", Items.PLAYER_HEAD, 20000, null, "Eternity", "hms-speed:2,hms-damage:2,hms-armor:2"));
        list.add(new HolyWorldSphereItem("Сфера Stinger", "Сфера Stinger", Items.PLAYER_HEAD, 11000, null, "Stinger", "hms-speed:1,hms-armor:2,hms-damage:2"));

        // ───── Опыт ─────
        list.add(new HolyWorldExpBottleItem("Пузырек 15", "опыт 15", 800, 315));
        list.add(new HolyWorldExpBottleItem("Пузырек 30", "опыт 30", 1500, 1395));
        list.add(new HolyWorldExpBottleItem("Пузырек 50", "опыт 50", 5000, 5345));
        list.add(new HolyWorldExpBottleItem("Пузырек 100", "опыт 100", 25000, 30971));
        list.add(new HolyWorldExpBottleItem("Обычный пузырек опыта", "опытный", 50, 0));

        // ───── Рюкзаки ─────
        list.add(new HolyWorldBackpackItem("Рюкзак I", "рюкзак 1 уровень", Items.PINK_SHULKER_BOX, 1000, "1 уровень"));
        list.add(new HolyWorldBackpackItem("Рюкзак II", "рюкзак 2 уровень", Items.LIGHT_BLUE_SHULKER_BOX, 3000, "2 уровень"));
        list.add(new HolyWorldBackpackItem("Рюкзак III", "рюкзак 3 уровень", Items.RED_SHULKER_BOX, 8000, "3 уровень"));
        list.add(new HolyWorldBackpackItem("Рюкзак IV", "рюкзак 4 уровень", Items.MAGENTA_SHULKER_BOX, 15000, "4 уровень"));
        list.add(new HolyWorldBackpackItem("Рюкзак Infinity", "рюкзак infinity", Items.LIME_SHULKER_BOX, 50000, "infinity"));

        // ───── Пиротехника / Кринге ─────
        list.add(HolyWorldItems.pyrotechnic("Трапка", "Трапка", Items.POPPED_CHORUS_FRUIT, 200, "ALTERNATIVE_TRAP"));
        list.add(HolyWorldItems.pyrotechnic("Взрывная трапка", "Взрывная трапка", Items.PRISMARINE_SHARD, 500, "EXPLOSIVE_TRAP"));
        list.add(HolyWorldItems.pyrotechnic("Стан", "Стан", Items.NETHER_STAR, 800, "STUN_STAR"));
        list.add(HolyWorldItems.kringe("Взрывная штучка", "Взрывная штучка", Items.FIRE_CHARGE, 100, "ExplosiveStuff"));
        list.add(HolyWorldItems.kringe("Ком снега", "Ком снега", Items.SNOWBALL, 50, "SnowBall"));
        list.add(HolyWorldItems.kringe("Зелье победителя", "Зелье победителя", Items.POTION, 5000, "win-potion"));

        // ───── Руны ─────
        list.add(HolyWorldItems.rune("Руна Бессмертие", "Руна Бессмертие", Items.ORANGE_DYE, 15000, "immortality"));

        // ───── Кринге-эффекты ─────
        list.add(HolyWorldItems.kringeEffect("Охотник", "Охотник", Items.NETHERITE_SWORD, 8000, "EXP_DROPPER"));
        list.add(HolyWorldItems.kringeEffect("Снеговик", "Снеговик", Items.SNOW_BLOCK, 3000, "BLINDNESS"));
        list.add(HolyWorldItems.kringeEffect("Иллюминатор", "Иллюминатор", Items.SEA_LANTERN, 4000, "PORTHOLE"));
        list.add(HolyWorldItems.kringeEffect("Эндермен", "Эндермен", Items.ENDER_PEARL, 5000, "ENDERMAN"));
        list.add(HolyWorldItems.kringeEffect("Анти Фантом", "Анти Фантом", Items.PHANTOM_MEMBRANE, 3500, "ANTI_PHANTOM"));
        list.add(HolyWorldItems.kringeEffect("Телекинез", "Телекинез", Items.HONEY_BLOCK, 4500, "TELEKINESIS"));

        // ───── Зелья ─────
        list.add(new HolyWorldPotionItem("Улучшенное зелье силы", "Улучшенное зелье силы",
                Items.POTION, 1500, StatusEffects.STRENGTH, 2));
        list.add(new HolyWorldPotionItem("Улучшенное зелье скорости", "Улучшенное зелье скорости",
                Items.POTION, 1200, StatusEffects.SPEED, 2));
        list.add(new HolyWorldPotionItem("Зелье исцеления", "Зелье исцеления",
                Items.POTION, 800, StatusEffects.INSTANT_HEALTH, 1));
        list.add(new HolyWorldStandardPotionItem("Зелье черепашьей мощи", "Зелье черепашьей мощи",
                Items.POTION, 2000, "minecraft:long_turtle_master", 0x7FB4B8));
        list.add(new HolyWorldStandardPotionItem("Зелье черепашьей мощи II", "Зелье черепашьей мощи",
                Items.POTION, 3500, "minecraft:strong_turtle_master", 0x7FB4B8));

        items = list;
    }

    private static HolyWorldItem armor(String name, net.minecraft.item.Item material, String category,
                                       int defaultPrice,
                                       EnchantmentData[] enchants, String loreText) {
        return new HolyWorldItem(name, material, defaultPrice, category, enchants,
                List.of(Text.literal(loreText).formatted(Formatting.GRAY)));
    }
}
