package dev.endless.util.auction.holyworld;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import dev.endless.util.auction.AutoBuyItemSettings;
import dev.endless.util.auction.AutoBuyableItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Все реализации HolyWorld*Item — упрощённые порты из essence fix.
 * Эталоны строятся через CustomData NBT-флаги, которые читает
 * {@link dev.endless.util.auction.AuctionUtils}.
 */
public final class HolyWorldItems {

    private HolyWorldItems() {}

    public static class EnchantmentData {
        public final RegistryKey<Enchantment> enchantment;
        public final int level;
        public EnchantmentData(RegistryKey<Enchantment> enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }

    public static class AttributeData {
        public final String attributeName;
        public final double amount;
        public final int operation;
        public final String slot;
        public AttributeData(String attributeName, double amount, int operation, String slot) {
            this.attributeName = attributeName;
            this.amount = amount;
            this.operation = operation;
            this.slot = slot;
        }
    }

    private static NbtList enchantsToNbt(EnchantmentData[] enchants) {
        NbtList list = new NbtList();
        if (enchants == null) return list;
        for (EnchantmentData e : enchants) {
            NbtCompound c = new NbtCompound();
            c.putString("id", e.enchantment.getValue().toString());
            c.putShort("lvl", (short) e.level);
            list.add(c);
        }
        return list;
    }

    private static NbtList attrsToNbt(AttributeData[] attrs) {
        NbtList list = new NbtList();
        if (attrs == null) return list;
        for (AttributeData a : attrs) {
            NbtCompound c = new NbtCompound();
            c.putString("AttributeName", a.attributeName);
            c.putDouble("Amount", a.amount);
            c.putInt("Operation", a.operation);
            c.putString("Slot", a.slot);
            list.add(c);
        }
        return list;
    }

    private static ItemStack applyEnchantments(ItemStack stack, EnchantmentData[] enchants) {
        if (enchants == null || enchants.length == 0) return stack;
        try {
            var registry = net.minecraft.client.MinecraftClient.getInstance()
                    .world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
            if (registry == null) return stack;
            ItemEnchantmentsComponent.Builder b = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
            for (EnchantmentData e : enchants) {
                try {
                    RegistryEntry<Enchantment> entry = registry.getEntry(e.enchantment.getValue()).orElseThrow();
                    b.add(entry, e.level);
                } catch (Exception ignored) {}
            }
            stack.set(DataComponentTypes.ENCHANTMENTS, b.build());
        } catch (Exception ignored) {}
        return stack;
    }

    /** Базовый класс с общими полями. */
    public static abstract class Base implements AutoBuyableItem {
        protected final String displayName;
        protected final String searchName;
        protected final Item material;
        protected final int defaultPrice;
        protected final String category;
        protected final AutoBuyItemSettings settings;

        protected Base(String displayName, String searchName, Item material, int defaultPrice, String category) {
            this.displayName = displayName;
            this.searchName = searchName;
            this.material = material;
            this.defaultPrice = defaultPrice;
            this.category = category;
            this.settings = new AutoBuyItemSettings(defaultPrice);
        }

        @Override public String getDisplayName() { return displayName; }
        @Override public String getSearchName() { return searchName != null ? searchName : displayName; }
        @Override public AutoBuyItemSettings getSettings() { return settings; }
        @Override public String getCategory() { return category; }
    }

    // ────────────────── HolyWorldItem (армор/оружие) ──────────────────
    public static class HolyWorldItem extends Base {
        private final EnchantmentData[] enchantments;
        private final AttributeData[] attributes;
        private final List<Text> lore;

        public HolyWorldItem(String displayName, Item material, int defaultPrice, String category,
                             EnchantmentData[] enchantments, List<Text> lore) {
            this(displayName, material, defaultPrice, category, enchantments, null, lore);
        }

        public HolyWorldItem(String displayName, Item material, int defaultPrice, String category,
                             EnchantmentData[] enchantments,
                             AttributeData[] attributes, List<Text> lore) {
            super(displayName, displayName, material, defaultPrice, category);
            this.enchantments = enchantments;
            this.attributes = attributes;
            this.lore = lore;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));

            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            if (enchantments != null && enchantments.length > 0) {
                nbt.put("RequiredEnchantments", enchantsToNbt(enchantments));
                applyEnchantments(stack, enchantments);
            }
            if (attributes != null && attributes.length > 0) {
                nbt.put("AttributeModifiers", attrsToNbt(attributes));
            }
            if (lore != null && !lore.isEmpty()) {
                stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
            }
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }

    // ────────────────── HolyWorldSphereItem ──────────────────
    public static class HolyWorldSphereItem extends Base {
        private final String texture;
        private final String sphereName;
        private final String requiredEffects;

        public HolyWorldSphereItem(String displayName, String searchName, Item material, int defaultPrice,
                                   String texture, String sphereName, String requiredEffects) {
            super(displayName, searchName, material, defaultPrice, "Сферы");
            this.texture = texture;
            this.sphereName = sphereName;
            this.requiredEffects = requiredEffects;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));

            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldSphere", true);
            if (sphereName != null) nbt.putString("sphereName", sphereName);
            if (requiredEffects != null) nbt.putString("requiredEffects", requiredEffects);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

            if (texture != null && material == Items.PLAYER_HEAD) {
                try {
                    GameProfile profile = new GameProfile(UUID.randomUUID(), "");
                    profile.getProperties().put("textures", new Property("textures", texture));
                    stack.set(DataComponentTypes.PROFILE, new ProfileComponent(profile));
                } catch (Exception ignored) {}
            }
            return stack;
        }
    }

    // ────────────────── HolyWorldTalikItem ──────────────────
    public static class HolyWorldTalikItem extends Base {
        private final AttributeData[] attributes;
        private final EnchantmentData[] enchantments;

        public HolyWorldTalikItem(String displayName, Item material, int defaultPrice,
                                  AttributeData[] attributes, EnchantmentData[] enchantments) {
            super(displayName, displayName, material, defaultPrice, "Талисманы");
            this.attributes = attributes;
            this.enchantments = enchantments;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            nbt.putBoolean("HolyWorldTalik", true);
            if (attributes != null) nbt.put("AttributeModifiers", attrsToNbt(attributes));
            if (enchantments != null) {
                nbt.put("RequiredEnchantments", enchantsToNbt(enchantments));
                applyEnchantments(stack, enchantments);
            }
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }

    // ────────────────── HolyWorldExpBottleItem ──────────────────
    public static class HolyWorldExpBottleItem extends Base {
        private final int xpAmount;

        public HolyWorldExpBottleItem(String displayName, String searchName, int defaultPrice, int xpAmount) {
            super(displayName, searchName, Items.EXPERIENCE_BOTTLE, defaultPrice, "Опыт");
            this.xpAmount = xpAmount;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(Items.EXPERIENCE_BOTTLE);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            nbt.putBoolean("HolyWorldExpBottle", true);
            nbt.putString("expLevelMatch", searchName != null ? searchName : "");
            nbt.putInt("xpAmount", xpAmount);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }

    // ────────────────── HolyWorldBackpackItem ──────────────────
    public static class HolyWorldBackpackItem extends Base {
        private final String tier;

        public HolyWorldBackpackItem(String displayName, String searchName, Item material, int defaultPrice, String tier) {
            super(displayName, searchName, material, defaultPrice, "Рюкзаки");
            this.tier = tier;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            nbt.putBoolean("HolyWorldBackpack", true);
            nbt.putString("backpackTier", tier);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }

    // ────────────────── Generic tagged item ──────────────────
    public static class HolyWorldTaggedItem extends Base {
        private final String boolFlag;
        private final String tagKey;
        private final String tagValue;

        public HolyWorldTaggedItem(String displayName, String searchName, Item material, int defaultPrice,
                                   String category,
                                   String boolFlag, String tagKey, String tagValue) {
            super(displayName, searchName, material, defaultPrice, category);
            this.boolFlag = boolFlag;
            this.tagKey = tagKey;
            this.tagValue = tagValue;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            nbt.putBoolean(boolFlag, true);
            nbt.putString(tagKey, tagValue);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }

    public static HolyWorldTaggedItem pyrotechnic(String name, String searchName, Item mat, int price, String type) {
        return new HolyWorldTaggedItem(name, searchName, mat, price, "Пиротехника", "HolyWorldPyrotechnic", "pyrotechnicType", type);
    }

    public static HolyWorldTaggedItem kringe(String name, String searchName, Item mat, int price, String type) {
        return new HolyWorldTaggedItem(name, searchName, mat, price, "Прочее", "HolyWorldKringe", "kringeType", type);
    }

    public static HolyWorldTaggedItem rune(String name, String searchName, Item mat, int price, String type) {
        return new HolyWorldTaggedItem(name, searchName, mat, price, "Руны", "HolyWorldRune", "runeType", type);
    }

    public static HolyWorldTaggedItem kringeEffect(String name, String searchName, Item mat, int price, String type) {
        return new HolyWorldTaggedItem(name, searchName, mat, price, "Кринге-эффекты", "HolyWorldKringeEffect", "kringeEffectType", type);
    }

    // ────────────────── HolyWorldStandardPotionItem ──────────────────
    public static class HolyWorldStandardPotionItem extends Base {
        private final String potionId;
        private final int color;

        public HolyWorldStandardPotionItem(String displayName, String searchName, Item material, int defaultPrice,
                                           String potionId, int color) {
            super(displayName, searchName, material, defaultPrice, "Зелья");
            this.potionId = potionId;
            this.color = color;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            stack.set(DataComponentTypes.POTION_CONTENTS,
                    new PotionContentsComponent(Optional.empty(), Optional.of(color), List.of(), Optional.empty()));
            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            nbt.putBoolean("HolyWorldStandardPotion", true);
            nbt.putString("potionId", potionId);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }

    // ────────────────── HolyWorldPotionItem (с эффектами) ──────────────────
    public static class HolyWorldPotionItem extends Base {
        private final RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect;
        private final int amplifier;

        public HolyWorldPotionItem(String displayName, String searchName, Item material, int defaultPrice,
                                   RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect,
                                   int amplifier) {
            super(displayName, searchName, material, defaultPrice, "Зелья");
            this.effect = effect;
            this.amplifier = amplifier;
        }

        @Override
        public ItemStack createReference() {
            ItemStack stack = new ItemStack(material);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(displayName));
            List<StatusEffectInstance> effects = new ArrayList<>();
            effects.add(new StatusEffectInstance(effect, 3600, amplifier - 1));
            stack.set(DataComponentTypes.POTION_CONTENTS,
                    new PotionContentsComponent(Optional.empty(), Optional.empty(), effects, Optional.empty()));
            NbtCompound nbt = new NbtCompound();
            nbt.putBoolean("HolyWorldItem", true);
            nbt.putBoolean("HolyWorldPotion", true);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            return stack;
        }
    }
}
