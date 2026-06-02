package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.render.EventRenderScreen;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.screens.morecosmetics.MoreCosmeticPanel;
import endless.ere.client.screens.morecosmetics.MoreCosmeticsBridge;

/**
 * Интеграция мода MoreCosmetics в виде модуля Endless. При активации
 * прокачивает MoreCosmetics API (тики/инициализация) и открывает панель в
 * стиле клиента для управления плащом, никами и официальным меню MoreCosmetics.
 *
 * <p>Если самого мода MoreCosmetics нет на classpath — модуль аккуратно
 * отключается, не валя клиент.
 */
@ModuleAnnotation(name = "MoreCosmetic", category = Category.RENDER,
        description = "Интеграция MoreCosmetics с панелью в стиле клиента")
public final class MoreCosmetic extends Module {

    public static final MoreCosmetic INSTANCE = new MoreCosmetic();

    private MoreCosmeticPanel panel;

    private MoreCosmetic() {
    }

    @Override
    public void onEnable() {
        if (mc.world == null || !MoreCosmeticsBridge.isAvailable()) {
            // Нет мира или MoreCosmetics не установлен — нечего показывать.
            this.setEnabled(false);
            return;
        }
        openPanel();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (panel != null && mc.currentScreen == panel) {
            panel.startFadeOut();
        }
        panel = null;
        super.onDisable();
    }

    private void openPanel() {
        if (panel == null) {
            panel = new MoreCosmeticPanel(() -> {
                if (this.isEnabled()) this.setToggled(false);
            });
        }
        if (mc.currentScreen != panel) {
            mc.setScreen(panel);
        }
    }

    @EventTarget
    public void onTick(EventUpdate event) {
        if (!MoreCosmeticsBridge.isAvailable()) return;
        MoreCosmeticsBridge.onTick();
    }

    @EventTarget
    public void onScreen(EventRenderScreen event) {
        // Если игрок закрыл панель самостоятельно — выключаем модуль.
        if (panel != null && mc.currentScreen != panel && this.isEnabled()) {
            this.setToggled(false);
        }
    }
}
