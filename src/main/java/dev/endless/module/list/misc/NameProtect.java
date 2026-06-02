package dev.endless.module.list.misc;

import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.util.friend.Friend;
import dev.endless.util.friend.FriendRepository;

@ModuleInformation(moduleName = "Streamer Mode", moduleCategory = ModuleCategory.MISC)
public class NameProtect extends Module {

    public final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);

    public String getCustomName() {
        return isEnabled() ? "endless" : mc.player.getNameForScoreboard();
    }

    public String getCustomName(String originalName) {
        if (!isEnabled() || mc.player == null) {
            return originalName;
        }

        String me = mc.player.getNameForScoreboard();
        if (originalName.contains(me)) {
            return originalName.replace(me, "endless");
        }

        if (hideFriends.getValue()) {
            var friends = FriendRepository.getFriends();
            for (Friend friend : friends) {
                if (originalName.contains(friend.name())) {
                    return originalName.replace(friend.name(), "endless");
                }
            }
        }

        return originalName;
    }
}
