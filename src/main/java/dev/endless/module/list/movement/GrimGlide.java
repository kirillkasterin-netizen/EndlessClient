package dev.endless.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import dev.endless.event.list.EventPlayerUpdate;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.util.math.StopWatch;
import dev.endless.util.network.ServerDetector;
import dev.endless.util.render.math.MathUtil;

@ModuleInformation(moduleName = "GrimGlide",
        moduleDesc = "ну типо в попе ковыряет и ты летишь",
        moduleCategory = ModuleCategory.MOVEMENT)
public class GrimGlide extends Module {

    private final StopWatch ticks = new StopWatch();
    private int ticksTwo = 0;

    @Subscribe
    private void onMotion(EventPlayerUpdate event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isGliding()) return;

        ticksTwo++;
        Vec3d pos = mc.player.getPos();
        float yaw = mc.player.getYaw();
        double forward = 0.087;
        double motion = MathUtil.getBps(mc.player);
        float speedCap = ServerDetector.isReallyWorld() ? 48f : 52f;

        if (motion >= speedCap) {
            forward = 0f;
            motion = 0;
        }

        double dx = -Math.sin(Math.toRadians(yaw)) * forward;
        double dz = Math.cos(Math.toRadians(yaw)) * forward;

        mc.player.setVelocity(
                dx * MathUtil.random(1.1f, 1.21f),
                mc.player.getVelocity().y - 0.02f,
                dz * MathUtil.random(1.1f, 1.21f)
        );

        if (ticks.isReached(50)) {
            mc.player.setPos(
                    pos.getX() + dx,
                    pos.getY(),
                    pos.getZ() + dz
            );
            ticks.reset();
        }

        mc.player.setVelocity(
                dx * MathUtil.random(1.1f, 1.21f),
                mc.player.getVelocity().y + 0.016f,
                dz * MathUtil.random(1.1f, 1.21f)
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        ticks.reset();
        ticksTwo = 0;
    }
}
