package dev.endless.module.list.player;

import com.google.common.eventbus.Subscribe;
import dev.endless.event.list.EventKeyInput;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BindSetting;

@ModuleInformation(moduleName = "Cords Dropper", moduleCategory = ModuleCategory.PLAYER)
public class CordsDropper extends Module {

    private final BindSetting bind = new BindSetting("Key",-1);

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (e.getAction() == 0) return;
        if (e.getKey() == bind.getValue()) {
            if (mc.player != null) {
                String message = String.format("! %.0f %.0f !!!", mc.player.getX(), mc.player.getZ());
                mc.player.networkHandler.sendChatMessage(message);
            }
        }
    }
}
