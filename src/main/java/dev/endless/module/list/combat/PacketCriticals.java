package dev.endless.module.list.combat;

import com.google.common.eventbus.Subscribe;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import dev.endless.event.list.EventAttack;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.util.player.other.WorldUtils;

/**
 * Forces critical hits via "ground-spoof" packets.
 *
 * Vanilla servers consider an attack critical when the player has positive
 * downward velocity, isn't on the ground, isn't on a ladder, isn't in water,
 * and isn't riding. Sending two tiny PositionAndOnGround packets immediately
 * before/after the attack briefly makes the server believe the player is in
 * mid-air, so the next swing is rolled as a crit.
 *
 * The "Только в паутине" mode keeps the legacy behaviour from Wonderful — only
 * crits while in cobwebs, where this trick is most reliable across anti-cheats.
 */
@ModuleInformation(moduleName = "PacketCriticals", moduleDesc = "Бьет критами через пакеты", moduleCategory = ModuleCategory.COMBAT)
public class PacketCriticals extends Module {

    public final ModeSetting mode = new ModeSetting(
            "Режим", "Всегда", "Всегда", "Только в паутине"
    );

    public final BooleanSetting onlyOnGround = new BooleanSetting("Только на земле", true);

    @Subscribe
    public void onAttack(EventAttack event) {
        if (mc.player == null || mc.world == null) return;

        // Vanilla already gives us the crit if the player is mid-air with
        // negative motionY — no need to spoof packets and risk a flag.
        if (!mc.player.isOnGround() && onlyOnGround.getValue()) return;

        if (mode.is("Только в паутине") && !WorldUtils.isInWeb()) return;

        sendCritPackets();
    }

    /**
     * Sends two minimal position packets that flip the on-ground flag,
     * tricking the server into thinking the player is in the middle of a fall.
     */
    private void sendCritPackets() {
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();

        var net = mc.player.networkHandler;
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, false));
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
    }
}
