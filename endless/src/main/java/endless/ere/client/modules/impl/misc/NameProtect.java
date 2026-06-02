package endless.ere.client.modules.impl.misc;

import endless.ere.Endless;
import com.darkmagician6.eventapi.EventTarget;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;

import java.util.List;

// ООО<<МИНЦЕТ ПАСТИНГ INC>>ООО
@ModuleAnnotation(name = "NameProtect", category = Category.MISC, description = "Защищает имена игроков")
public final class NameProtect extends Module {
    public static final NameProtect INSTANCE = new NameProtect();
    
    private NameProtect() {
    }

    private final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);

    public static String getCustomName() {
        Module module = NameProtect.INSTANCE;
        return module != null && module.isEnabled() ? "ENDLESSDLC" : mc.player.getNameForScoreboard();
    }

    public static String getCustomName(String originalName) {
        Module module = NameProtect.INSTANCE;
        if (module == null || !module.isEnabled() || mc.player == null) {
            return originalName;
        }

        String me = mc.player.getNameForScoreboard();
        if (originalName.contains(me)) {
            return originalName.replace(me, "ENDLESSDLC");
        }

        if (module instanceof NameProtect nameProtect && nameProtect.hideFriends.isEnabled()) {
            var friends = Endless.getInstance().getFriendManager().getItems();
            for (String friend : friends) {
                if (originalName.contains(friend)) {
                    return originalName.replace(friend, "ENDLESSDLC");
                }
            }
        }

        return originalName;
    }
}
