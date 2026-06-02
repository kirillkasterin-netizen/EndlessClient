package dev.endless.module.list.movement;

import com.google.common.eventbus.Subscribe;
import dev.endless.Endless;
import dev.endless.event.list.EventNoSlow;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.combat.KillAura;
import dev.endless.module.settings.ModeSetting;
import dev.endless.util.player.simulate.SimulatedPlayer;

@ModuleInformation(moduleName = "No Slow", moduleCategory = ModuleCategory.MOVEMENT)
public class NoSlow extends Module {

    private final ModeSetting mode = new ModeSetting("Мод", "Vanilla", "Vanilla", "Grim");

    @Subscribe
    private void onNoSlow(EventNoSlow e) {
        switch (mode.getValue()) {
            case "Vanilla" -> {
                if (!(Endless.getInstance().getModuleStorage().get(KillAura.class).getTarget() != null && SimulatedPlayer.simulateLocalPlayer(1).fallDistance > 0)) mc.player.setSprinting(true);
                e.cancelEvent();
            }
            case "Grim" -> {
                mc.player.setSprinting(mc.player.getItemUseTime() > 4 && !(Endless.getInstance().getModuleStorage().get(KillAura.class).getTarget() != null && SimulatedPlayer.simulateLocalPlayer(1).fallDistance > 0) && Endless.getInstance().getServerManager().getSprintingChangeTicks() > 0);
                if (mc.player.getItemUseTime() % 2 == 0) e.cancelEvent();
            }
        }
    }
}
