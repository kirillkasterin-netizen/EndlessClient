package dev.endless.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.endless.Endless;
import dev.endless.event.list.EventHandledScreen;
import dev.endless.module.list.misc.ItemScroller;
import dev.endless.util.base.Instance;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    @Nullable
    protected Slot focusedSlot;

    @Shadow
    @Nullable
    protected abstract Slot getSlotAt(double x, double y);

    @Shadow
    protected abstract void onMouseClick(@Nullable Slot slot, int slotId, int button, SlotActionType actionType);

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Endless.getInstance().getEventBus().post(new EventHandledScreen(focusedSlot));
        wraith$tickItemScroller(mouseX, mouseY);
    }

    /**
     * Drives the {@link ItemScroller} module. While the user holds LMB +
     * Shift inside any inventory screen, every slot the mouse passes over
     * is shift-clicked according to the configured throttle delay.
     */
    private void wraith$tickItemScroller(int mouseX, int mouseY) {
        ItemScroller scroller = Instance.get(ItemScroller.class);
        if (scroller == null || !scroller.isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        long window = mc.getWindow().getHandle();
        boolean leftMousePressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean shiftPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (!leftMousePressed || !shiftPressed) {
            scroller.resetTimer();
            return;
        }

        Slot slot = getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) return;

        if (!scroller.canQuickMove()) return;

        onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
    }
}
