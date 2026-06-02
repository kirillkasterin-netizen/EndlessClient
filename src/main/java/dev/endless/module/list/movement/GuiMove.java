package dev.endless.module.list.movement;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import dev.endless.event.list.EventCloseInv;
import dev.endless.event.list.EventPacket;
import dev.endless.event.list.EventPlayerUpdate;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.player.move.MoveUtil;
import dev.endless.util.packet.NetworkUtils;
import dev.endless.util.player.other.SlownessManager;

import java.util.ArrayList;
import java.util.List;

@ModuleInformation(moduleName = "Gui Move", moduleDesc = "Позволяет взаимодействовать с инвентарем при движении", moduleCategory = ModuleCategory.MOVEMENT)
public class GuiMove extends Module {

    private final BooleanSetting universal = new BooleanSetting("Универсальный", false);
    private final SliderSetting slownessDuration = new SliderSetting("Длительность замедления", 50, 1, 400, 1).setVisible(universal::getValue);
    private final List<Packet<?>> packets = new ArrayList<>();
    private boolean wasSprinting = false;

    @Override
    public void onDisable() {
        super.onDisable();
        packets.clear();
        wasSprinting = false;
    }

    @Subscribe
    public void onPacket(EventPacket e) {
        if (!universal.getValue()) return;
        if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) return;

        final Packet<?> packet = e.getPacket();

        if (packet instanceof ClickSlotC2SPacket
                && MoveUtil.hasPlayerMovement()
                && mc.currentScreen instanceof InventoryScreen) {
            packets.add(packet);
            e.cancelEvent();
        } else if (packet instanceof CloseHandledScreenC2SPacket
                && MoveUtil.hasPlayerMovement()
                && mc.player.isSprinting()) {
            wasSprinting = true;
            packets.add(packet);
            e.cancelEvent();
        }
    }

    @Subscribe
    public void onCloseInv(EventCloseInv e) {
        if (!universal.getValue()) return;
        if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) return;

        if (!packets.isEmpty()) {
            e.cancelEvent();

            if (wasSprinting) {
                mc.player.setSprinting(false);
            }

            SlownessManager.addTask(new SlownessManager.SlowTask(slownessDuration.getIntValue(), 0, () -> {
                packets.forEach(NetworkUtils::sendSilentPacket);
                packets.clear();
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));

                if (wasSprinting) {
                    Sprint sprint = dev.endless.util.base.Instance.get(Sprint.class);
                    if (sprint != null && sprint.isEnabled()) {
                        mc.player.setSprinting(true);
                    }
                    wasSprinting = false;
                }
            }));
        }
    }

    @Subscribe
    public void onUpdate(EventPlayerUpdate e) {
        if (mc.player == null) return;
        if (mc.currentScreen == null || mc.currentScreen instanceof ChatScreen) return;

        for (KeyBinding key : new KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey,
                mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey
        }) {
            key.setPressed(InputUtil.isKeyPressed(
                    mc.getWindow().getHandle(),
                    InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()).getCode()
            ));
        }
    }
}
