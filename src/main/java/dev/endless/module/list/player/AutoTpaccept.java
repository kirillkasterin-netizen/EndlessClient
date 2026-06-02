package dev.endless.module.list.player;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableObject;
import dev.endless.event.list.EventPacket;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.util.friend.Friend;
import dev.endless.util.friend.FriendRepository;
import dev.endless.util.packet.NetworkUtils;

@ModuleInformation(moduleName = "Auto Tpaccept", moduleCategory = ModuleCategory.PLAYER)
public class AutoTpaccept extends Module {

    public ActionResult interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (!mc.world.getWorldBorder().contains(hitResult.getBlockPos())) {
            return ActionResult.FAIL;
        } else {
            MutableObject<ActionResult> mutableObject = new MutableObject();
            mutableObject.setValue(mc.interactionManager.interactBlockInternal(player, hand, hitResult));
            NetworkUtils.sendSilentPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
            return mutableObject.getValue();
        }
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (mc.player == null || mc.world == null) return;

        if (e.getPacket() instanceof GameMessageS2CPacket p) {
            String raw = p.content().getString();
            if (raw.contains("телепортироваться") || raw.contains("has requested teleport") || raw.contains("просит к вам телепортироваться") || raw.contains("запрашивает телепорт к вам")) {
                boolean yes = false;

                for (Friend friend : FriendRepository.getFriends()) {
                    if (raw.contains(friend.name())) {
                        yes = true;
                        break;
                    }
                }

                if (!yes) return;

                mc.getNetworkHandler().sendChatCommand("tpaccept");
            }
        }
    }
}
