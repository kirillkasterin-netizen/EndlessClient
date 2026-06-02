package endless.ere.client.modules.impl.player;

import net.minecraft.util.Hand;
import com.darkmagician6.eventapi.EventTarget;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.NumberSetting;

@ModuleAnnotation(name = "NoDelay", category = Category.PLAYER, description = "Убирает задержку при использовании предметов")
public final class NoDelay extends Module {
    public static final NoDelay INSTANCE = new NoDelay();

    private NoDelay() {

    }

    // через сколько тиков повторять interactItem (не каждый тик - GrimAC ловит как BadPacketsJ)
    private final NumberSetting interval = new NumberSetting("Интервал", 4, 2, 10, 1, "Тиков между использованиями");

    private int tickCounter = 0;

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (!this.isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (!mc.options.useKey.isPressed()) {
            tickCounter = 0;
            return;
        }

        // повторяем interact только если игрок реально использует предмет (ест, пьет, заряжает лук и т.д.)
        // НЕ работаем если игрок просто держит ПКМ на блоке или в воздухе
        if (!mc.player.isUsingItem() || mc.player.getItemUseTime() <= 0) {
            tickCounter = 0;
            return;
        }

        tickCounter++;
        if (tickCounter < interval.getCurrent()) return;
        tickCounter = 0;

        Hand activeHand = mc.player.getActiveHand();
        if (activeHand != null) {
            mc.interactionManager.interactItem(mc.player, activeHand);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        tickCounter = 0;
    }
}
