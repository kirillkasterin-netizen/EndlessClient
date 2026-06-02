package dev.endless.module.list.movement;

import com.google.common.eventbus.Subscribe;
import dev.endless.event.list.EventTick;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;

@ModuleInformation(moduleName = "No Jump Delay", moduleCategory = ModuleCategory.MOVEMENT)
public class NoJumpDelay extends Module {

    @Subscribe
    private void onUpdate(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        mc.player.jumpingCooldown = 0;
    }
}
