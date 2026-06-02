package endless.ere.client.modules.impl.player;

import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import com.darkmagician6.eventapi.EventTarget;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.interfaces.IMinecraft;

@ModuleAnnotation(name = "AutoArmor", category = Category.PLAYER, description = "Автоматически экипирует броню")
public final class AutoArmor extends Module implements IMinecraft {
    public static final AutoArmor INSTANCE = new AutoArmor();
    
    private AutoArmor() {
    }

    private final NumberSetting delay = new NumberSetting("Задержка", 25f, 1f, 1000f, 1f);

    private long lastEquipTime = 0;

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (isMoving()) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastEquipTime < delay.getCurrent()) return;

        for (int i = 0; i < 4; ++i) {
            ItemStack currentArmor = mc.player.getInventory().getArmorStack(i);
            if (currentArmor.isEmpty()) {
                for (int j = 0; j < 36; ++j) {
                    ItemStack stack = mc.player.getInventory().getStack(j);
                    if (!stack.isEmpty() && stack.getItem() instanceof ArmorItem) {
                        ArmorItem armorItem = (ArmorItem) stack.getItem();
                        if (getArmorSlotIndex(armorItem) == i) {
                            int slotToEquip = j;
                            if (slotToEquip < 9) slotToEquip += 36;
                            
                            mc.interactionManager.clickSlot(0, slotToEquip, 0, SlotActionType.QUICK_MOVE, mc.player);
                            lastEquipTime = currentTime;
                            return;
                        }
                    }
                }
            }
        }
    }

    private boolean isMoving() {
        return mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0;
    }

    private int getArmorSlotIndex(ArmorItem armor) {
        String itemName = armor.toString().toLowerCase();
        if (itemName.contains("helmet") || itemName.contains("skull")) return 3;
        if (itemName.contains("chestplate") || itemName.contains("tunic")) return 2;
        if (itemName.contains("leggings") || itemName.contains("pants")) return 1;
        if (itemName.contains("boots") || itemName.contains("shoes")) return 0;
        return 0;
    }
}
