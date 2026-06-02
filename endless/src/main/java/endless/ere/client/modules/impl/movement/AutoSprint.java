package endless.ere.client.modules.impl.movement;

import com.darkmagician6.eventapi.EventTarget;


import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.utility.game.player.PlayerIntersectionUtil;

import java.util.List;

@ModuleAnnotation(name = "AutoSprint", category = Category.MOVEMENT, description = "Автоматически включает спринт")
public final class AutoSprint extends Module {
    public static final AutoSprint INSTANCE = new AutoSprint();
    private AutoSprint() {
    }
    @EventTarget
    public void onUpdate(EventUpdate event) {
        mc.options.sprintKey.setPressed(true);
    }
}
