package dev.endless.module.list.misc;

import com.google.common.eventbus.Subscribe;
import dev.endless.event.list.EventTick;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BindSetting;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.ui.autobuy.AutoBuyScreen;
import dev.endless.util.auction.AutoBuyManager;
import dev.endless.util.auction.AutoBuySettingsManager;

/**
 * Модуль-оболочка для AutoBuy. Сам экран — {@link AutoBuyScreen}.
 *
 * При включении подгружает настройки из json и активирует менеджер.
 * Бинд "Открыть GUI" откроет экран AutoBuy.
 */
@ModuleInformation(moduleName = "AutoBuy",
        moduleDesc = "Автопокупка предметов с аукциона + парсер",
        moduleCategory = ModuleCategory.MISC)
public class AutoBuy extends Module {

    public final BindSetting openGui = new BindSetting("Открыть GUI", -1);
    public final BooleanSetting onlyHolyworld = new BooleanSetting("Только на HolyWorld", true);

    private boolean lastBindPressed;
    private boolean settingsLoaded;

    public AutoBuy() {}

    @Override
    public void onEnable() {
        super.onEnable();
        if (!settingsLoaded) {
            AutoBuyManager.get().rebuildIndex();
            AutoBuySettingsManager.load();
            settingsLoaded = true;
        }
        AutoBuyManager.get().setEnabled(true);
        AutoBuySettingsManager.save();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        AutoBuyManager.get().setEnabled(false);
        AutoBuyManager.get().setAutoParserEnabled(false);
        AutoBuySettingsManager.save();
    }

    @Subscribe
    private void onTick(EventTick e) {
        if (mc.player == null || mc.world == null) return;

        // Бинд: открыть GUI (rising edge)
        long handle = mc.getWindow().getHandle();
        int key = openGui.getValue();
        boolean pressed = key != -1 && key < 8
                ? org.lwjgl.glfw.GLFW.glfwGetMouseButton(handle, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                : (key != -1 && net.minecraft.client.util.InputUtil.isKeyPressed(handle, key));
        if (pressed && !lastBindPressed && mc.currentScreen == null) {
            mc.setScreen(new AutoBuyScreen());
        }
        lastBindPressed = pressed;

        // Тикаем менеджер
        AutoBuyManager.get().tick();
    }
}
