package dev.endless.module.list.misc;

import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.SliderSetting;

/**
 * Lets the player rapidly shift-click items inside any inventory by holding
 * LMB + Shift and sweeping the cursor over slots. The actual click-driving
 * happens inside {@link dev.endless.mixin.HandledScreenMixin}, which calls
 * {@link #canQuickMove()} on every render frame.
 *
 * The {@code delay} setting throttles how often QUICK_MOVE actions are
 * dispatched (in milliseconds). 0 means as fast as possible.
 */
@ModuleInformation(moduleName = "ItemScroller", moduleDesc = "Быстрое перемещение предметов с зажатым Shift", moduleCategory = ModuleCategory.MISC)
public class ItemScroller extends Module {

    public final SliderSetting delay = new SliderSetting("Задержка, мс", 50, 0, 200, 1);

    private long lastQuickMoveAt;

    /**
     * Polled by the GUI render hook on each frame. Returns true if enough
     * time has passed since the previous quick-move, advancing the internal
     * cooldown when true is returned.
     */
    public boolean canQuickMove() {
        long now = System.currentTimeMillis();
        if (now - lastQuickMoveAt < (long) delay.getValue()) {
            return false;
        }
        lastQuickMoveAt = now;
        return true;
    }

    /**
     * Resets the cooldown timer. Called when the user lets go of the
     * shift+click combination so the next sweep starts without lag.
     */
    public void resetTimer() {
        lastQuickMoveAt = 0L;
    }

    @Override
    public void onDisable() {
        resetTimer();
        super.onDisable();
    }
}
