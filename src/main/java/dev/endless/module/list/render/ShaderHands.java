package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import dev.endless.event.list.EventHUD;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.ColorSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.hands.ShaderHandsRenderer;
import dev.endless.util.render.providers.ColorProvider;

@ModuleInformation(moduleName = "ShaderHands", moduleDesc = "Красивый Шейдер на руки и предметы", moduleCategory = ModuleCategory.RENDER)
public class ShaderHands extends Module {

    private static final ShaderHandsRenderer RENDERER = ShaderHandsRenderer.getInstance();

    public final ModeSetting mode = new ModeSetting("Режим", "Свечение", "Свечение", "Красивый");

    public final ModeSetting colorSource = new ModeSetting("Источник цвета", "Тема", "Тема", "Свой");

    public final ColorSetting customColor = (ColorSetting) new ColorSetting("Цвет", 0xFF8A6BFF)
            .setVisible(() -> colorSource.is("Свой"));

    public final SliderSetting waveSpeed = (SliderSetting) new SliderSetting("Скорость волн", 1.2, 0.1, 5.0, 0.1)
            .setVisible(() -> mode.is("Красивый"));

    public final SliderSetting waveScale = (SliderSetting) new SliderSetting("Частота волн", 1.0, 1.0, 3.0, 0.1)
            .setVisible(() -> mode.is("Красивый"));

    public final SliderSetting outline = new SliderSetting("Ширина обводки", 1.2, 0.1, 5.0, 0.1);

    public final SliderSetting glow = new SliderSetting("Сила свечения", 1.0, 0.0, 5.0, 0.1);

    public final SliderSetting fill = new SliderSetting("Заливка", 0.6, 0.0, 1.0, 0.01);

    public final SliderSetting alpha = new SliderSetting("Прозрачность", 1.0, 0.0, 1.0, 0.05);

    /**
     * Resolves the active shader color according to the selected source.
     *
     * @return Packed ARGB color used by the shader pipeline
     */
    public int resolveColor() {
        if (colorSource.is("Свой")) {
            return customColor.getValue();
        }
        return ColorProvider.getColorClient();
    }

    @Override
    public void onDisable() {
        RENDERER.invalidateState();
        super.onDisable();
    }

    @Subscribe
    public void onRender2D(EventHUD event) {
        if (!isEnabled()) return;
        RENDERER.renderOverlayIfPending();
    }
}
