package dev.endless.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import dev.endless.event.list.EventPlayerUpdate;
import dev.endless.event.list.EventPopTotem;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.math.StopWatch;
import dev.endless.util.player.other.InventoryUtil;
import dev.endless.util.text.ValueUnit;

@ModuleInformation(moduleName = "Auto Totem", moduleCategory = ModuleCategory.COMBAT)
public class AutoTotem extends Module {
    private final SliderSetting health = new SliderSetting("Здоровье", ValueUnit.abbreviation("ХП"), 4, 1, 20, 0.1f);
    private final SliderSetting healthOnElytra = new SliderSetting("Здоровье на элитре", ValueUnit.abbreviation("ХП"), 11, 1, 20, 0.1f);
    private final BooleanSetting crystalsCheck = new BooleanSetting("Работать на кристалы", false);
    private final BooleanSetting tntCheck = new BooleanSetting("Работать на динамит", false);
    private final BooleanSetting saveEnchanted = new BooleanSetting("Сохранять зачарованные", true);
    private final SliderSetting tntRadius = new SliderSetting("Радиус динамита", ValueUnit.countable("блок", "блока", "блоков"), 8, 1, 100, 1f).setVisible(tntCheck::getValue);
    private final BooleanSetting slowness = new BooleanSetting("Замедление", false);
    private final SliderSetting slownessDuration = new SliderSetting("Длительность замедления", 100, 0, 160, 1).setVisible(slowness::getValue);
    private int swapBackItem = -1;

    private float cooldownTicks;

    private final StopWatch totemStopWatch = new StopWatch();

    @Subscribe
    private void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;

        if (cooldownTicks > 0) cooldownTicks--;

        updateSwap();
    }

    @Subscribe
    private void onPopTotem(EventPopTotem e) {
        if (mc.player == null || e.getPlayer() != mc.player) return;

        cooldownTicks = 5;
    }

    private boolean condition() {
        if (mc.player.isCreative() || mc.player.isSpectator()) return false;
        if (mc.world == null) return false;
        
        // Проверка на кристаллы
        var crystalsCheck = false;
        if (this.crystalsCheck.getValue()) {
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof EndCrystalEntity)) continue;
                if (mc.player.getY() >= entity.getY() && mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter()) <= 8) {
                    crystalsCheck = true;
                    break;
                }
            }
        }
        
        // Проверка на динамит
        var tntCheckResult = false;
        if (tntCheck.getValue()) {
            for (var entity : mc.world.getEntities()) {
                if (!(entity instanceof TntEntity)) continue;
                double distance = mc.player.getEyePos().distanceTo(entity.getBoundingBox().getCenter());
                if (distance <= tntRadius.getValue()) {
                    tntCheckResult = true;
                    System.out.println("[AutoTotem] TNT найден на расстоянии: " + distance + " блоков (радиус: " + tntRadius.getValue() + ")");
                    break;
                }
            }
        }
        
        return (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= health.getValue() 
            || (mc.player.getHealth() + mc.player.getAbsorptionAmount()) <= healthOnElytra.getValue() && mc.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA 
            || crystalsCheck
            || tntCheckResult;
    }

    private void updateSwap() {
        var offHand = mc.player.getOffHandStack();
        
        int totemSlot = -1;
        if (saveEnchanted.getValue()) {
            // Ищем сначала обычные тотемы
            totemSlot = InventoryUtil.searchItemStack(stack -> stack.isOf(Items.TOTEM_OF_UNDYING) && !stack.hasEnchantments());
            // Если обычных нет, берем любой (зачарованный)
            if (totemSlot == -1) {
                totemSlot = InventoryUtil.searchItem(Items.TOTEM_OF_UNDYING);
            }
        } else {
            totemSlot = InventoryUtil.searchItem(Items.TOTEM_OF_UNDYING);
        }

        if (condition() && totemStopWatch.isReached(300) && totemSlot != -1 && offHand.getItem() != Items.TOTEM_OF_UNDYING) {
            if (swapBackItem == -1 && !offHand.isEmpty()) swapBackItem = totemSlot;

            final int finalTotemSlot = totemSlot;
            if (finalTotemSlot >= 0 && finalTotemSlot <= 8) {
                swapTotem(() -> mc.interactionManager.clickSlot(0, 45, finalTotemSlot, SlotActionType.SWAP, mc.player));
            } else if (finalTotemSlot >= 8 && finalTotemSlot <= 45) {
                swapTotem(() -> mc.interactionManager.clickSlot(0, finalTotemSlot, 40, SlotActionType.SWAP, mc.player));
            }
            totemStopWatch.reset();
        }

        if (!condition() && swapBackItem != -1 && cooldownTicks == 0) {
            var swapBackSlot = swapBackItem;
            if (swapBackSlot >= 0 && swapBackSlot <= 8) {
                swapTotem(() -> mc.interactionManager.clickSlot(0, 45, swapBackSlot, SlotActionType.SWAP, mc.player));
            } else if (swapBackSlot >= 8 && swapBackSlot <= 45) {
                swapTotem(() -> mc.interactionManager.clickSlot(0, swapBackSlot, 40, SlotActionType.SWAP, mc.player));
            }
            swapBackItem = -1;
        }
    }

    private void swapTotem(Runnable action) {
        if (slowness.getValue()) {
            InventoryUtil.swapWithBypassPolar(action, (long) slownessDuration.getValue());
            return;
        }
        InventoryUtil.swapWithBypassGrim(action);
    }
}
