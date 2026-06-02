package endless.ere.client.modules.impl.combat;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;


import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.utility.game.other.InventoryUtil;
import endless.ere.utility.game.other.NetworkUtils;
import endless.ere.utility.game.player.PlayerInventoryUtil;

@ModuleAnnotation(name = "ClickPearl", category = Category.COMBAT,description = "Кидает перл если он не в руках")
public final class ClickPearl extends Module {
    public static final ClickPearl INSTANCE = new ClickPearl();
    private ClickPearl() {
    }

    @Override
    public void onEnable() {
        PlayerInventoryUtil.swapAndUse(Items.ENDER_PEARL);
        super.onEnable();
        this.toggle();
    }
}
