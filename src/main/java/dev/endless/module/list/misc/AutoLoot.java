package dev.endless.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import dev.endless.event.list.EventTick;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.math.StopWatch;

@ModuleInformation(
        moduleName = "Auto Loot",
        moduleDesc = "Автоматически забирает отмычку у Жителя на корабле",
        moduleCategory = ModuleCategory.MISC
)
public class AutoLoot extends Module {

    private final BooleanSetting sendMessage =
            new BooleanSetting("Команда продажи", true);
    private final SliderSetting sellPrice =
            (SliderSetting) new SliderSetting("Цена /ah sell", 30_000_000, 1_000_000, 100_000_000, 1_000_000)
                    .setVisible(sendMessage::getValue);
    private final SliderSetting reach =
            new SliderSetting("Радиус", 4.0, 2.0, 6.0, 0.1);
    private final SliderSetting cooldown =
            new SliderSetting("Кулдаун (мс)", 600, 100, 5000, 50);

    private final StopWatch stopWatch = new StopWatch();

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (!stopWatch.isReached((long) cooldown.getFloatValue())) return;

        final double reachSq = reach.getFloatValue() * reach.getFloatValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof MerchantEntity merchant)) continue;
            if (!merchant.isAlive()) continue;

            // Должен держать предмет в руке (отмычку)
            boolean hasItem = !merchant.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()
                    || !merchant.getEquippedStack(EquipmentSlot.OFFHAND).isEmpty();
            if (!hasItem) continue;

            if (mc.player.squaredDistanceTo(entity) > reachSq) continue;

            // Полная имитация ванильного клика по сущности (interactAt + interact)
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket
                    .interactAt(merchant, mc.player.isSneaking(), Hand.MAIN_HAND,
                            entity.getBoundingBox().getCenter()));
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket
                    .interact(merchant, mc.player.isSneaking(), Hand.MAIN_HAND));
            mc.player.swingHand(Hand.MAIN_HAND);

            if (sendMessage.getValue()) {
                long price = (long) sellPrice.getFloatValue();
                mc.player.networkHandler.sendChatCommand("ah sell " + price);
            }

            stopWatch.reset();
            return;
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        stopWatch.reset();
    }
}
