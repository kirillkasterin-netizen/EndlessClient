package dev.endless.module.list.combat;

import com.google.common.eventbus.Subscribe;
import dev.endless.event.list.EventAttack;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.util.friend.Friend;
import dev.endless.util.friend.FriendRepository;

@ModuleInformation(moduleName = "No Friend Damage", moduleCategory = ModuleCategory.COMBAT)
public class NoFriendDamage extends Module {

    @Subscribe
    private void onAttack(EventAttack e) {
        for (Friend friend : FriendRepository.getFriends()) {
            if (e.getEntity() == mc.player) continue;
            if (!e.getEntity().getNameForScoreboard().equals(friend.name())) continue;
            e.cancelEvent();
        }
    }
}
