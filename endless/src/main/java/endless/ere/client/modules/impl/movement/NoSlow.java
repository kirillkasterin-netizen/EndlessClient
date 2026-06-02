package endless.ere.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;


import net.minecraft.util.Hand;
import endless.ere.base.events.impl.player.EventSlowWalking;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.utility.game.player.PlayerIntersectionUtil;

@ModuleAnnotation(name = "NoSlow", category = Category.MOVEMENT, description = "Убирает замедление во время еды")
public final class NoSlow extends Module {
    public static final NoSlow INSTANCE = new NoSlow();

    private NoSlow() {
    }

    @Override
    public void onDisable() {
        lastUseTime = 0;
        super.onDisable();
    }

    private final ModeSetting mode = new ModeSetting("Мод");
    private final ModeSetting.Value grimNew = new ModeSetting.Value(mode, "Grim New");
    private final ModeSetting.Value hw = new ModeSetting.Value(mode, "Grim old").select();
    private BooleanSetting sprint = new BooleanSetting("Спринт",true, hw::isSelected);
    private int ticks = 0;
    private int lastUseTime = 0;
    @EventTarget
    public void onItemUse(EventSlowWalking e) {
        if(!this.isEnabled()) return;
        
        if(grimNew.isSelected()){
            if(mc.player.getItemUseTime() %2==0){
                e.setCancelled(true);
            }
        }
        if(hw.isSelected()){
            if(sprint.isEnabled()){
                mc.player.setSprinting(mc.player.canSprint()	&& mc.player.isWalking() &&	 !mc.player.isBlind() && !mc.player.isGliding()&& (!mc.player.shouldSlowDown() || mc.player.isSubmergedInWater()));
            }
            // Убираем отправку пакета с противоположной рукой - это вызывает BadPacketsJ
            // Hand hand = mc.player.getActiveHand();
            // PlayerIntersectionUtil.useItem(hand.equals(Hand.MAIN_HAND) ? Hand.OFF_HAND : Hand.MAIN_HAND);
            e.setCancelled(true);
        }

    }

//  так называемы мега зако обход
    @EventTarget
    public void update(EventUpdate tickEvent) {
        if (mc.player.isUsingItem() &&mc.player.isOnGround()) {
//           mc.player.setSprinting(true);
//           mc.player.sendSprintingPacket();
//           mc.player.jump();
         //  ticks = 1;
        }else {
            ticks=0;
        }
    }
}


