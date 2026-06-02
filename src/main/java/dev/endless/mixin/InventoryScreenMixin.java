package dev.endless.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет кнопку «Выкинуть всё» над инвентарём — выкидывает весь инвентарь
 * (хотбар, основной инвентарь, броня, оффхенд) одним кликом.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen<PlayerScreenHandler> {

    public InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void wraith$addDropAllButton(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        InventoryScreen self = (InventoryScreen) (Object) this;

        int x = self.width / 2 - 40;
        int y = self.height / 2 - 110;

        ButtonWidget dropAllButton = ButtonWidget.builder(
                Text.of("Выкинуть всё"),
                button -> wraith$dropAllItems(mc)
        ).position(x, y).size(80, 16).build();

        self.addDrawableChild(dropAllButton);
    }

    private static void wraith$dropAllItems(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null || mc.interactionManager == null) return;
        int syncId = player.currentScreenHandler.syncId;

        // Броня (slots 5–8 = шлем, нагрудник, поножи, ботинки в InventoryScreen)
        for (int i = 5; i <= 8; i++) {
            mc.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, player);
        }

        // Оффхенд (slot 45)
        mc.interactionManager.clickSlot(syncId, 45, 1, SlotActionType.THROW, player);

        // Основной инвентарь (slots 9..35)
        for (int i = 9; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            mc.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, player);
        }

        // Хотбар (slots 0..8 в инвентаре игрока, в screen-handler это slots 36..44)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            mc.interactionManager.clickSlot(syncId, i + 36, 1, SlotActionType.THROW, player);
        }
    }
}
