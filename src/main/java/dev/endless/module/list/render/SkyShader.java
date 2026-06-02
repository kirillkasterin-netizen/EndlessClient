package dev.endless.module.list.render;

import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.ColorSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;

import java.util.List;

/**
 * Animated procedural sky shader module.
 * Replaces the vanilla sky with a stylized fragment-shader pattern.
 */
@ModuleInformation(moduleName = "SkyShader", moduleDesc = "Шейдерное небо", moduleCategory = ModuleCategory.RENDER)
public class SkyShader extends Module {

    /**
     * Sky preset names. Order matches the {@code iMode} integer used by the shader,
     * so the index of the chosen mode is sent directly to the GPU.
     */
    private static final List<String> MODES = List.of(
            "Сияние",     // 0 — aurora
            "Космос",     // 1 — cosmos
            "Магма",      // 2 — magma
            "Океан",      // 3 — ocean
            "Закат",      // 4 — sunset
            "Гроза",      // 5 — storm
            "Неон",       // 6 — neon
            "Сумерки",    // 7 — twilight
            "Рассвет",    // 8 — dawn
            "Чужой мир"   // 9 — alien
    );

    public final ModeSetting mode = new ModeSetting(
            "Режим",
            MODES.get(0),
            MODES.toArray(new String[0])
    );

    public final SliderSetting speed = new SliderSetting("Скорость", 1.0, 0.0, 5.0, 0.05);

    public final ModeSetting colorSource = new ModeSetting(
            "Источник цвета", "Тема", "Тема", "Свой"
    );

    public final ColorSetting primaryColor = (ColorSetting) new ColorSetting(
            "Основной цвет", 0xFFFF3358
    ).setVisible(() -> colorSource.is("Свой"));

    public final ColorSetting secondaryColor = (ColorSetting) new ColorSetting(
            "Вторичный цвет", 0xFF6600AA
    ).setVisible(() -> colorSource.is("Свой"));

    /**
     * Returns the index of the currently selected mode. The shader expects this
     * exact integer for {@code iMode}.
     */
    public int getModeIndex() {
        int idx = MODES.indexOf(mode.getValue());
        return idx < 0 ? 0 : idx;
    }

    /**
     * Resolves the primary color used by the shader.
     */
    public int resolvePrimaryColor() {
        if (colorSource.is("Свой")) return primaryColor.getValue();
        return ColorProvider.getColorClient();
    }

    /**
     * Resolves the secondary color used by the shader.
     * When the theme is selected, the secondary color is derived from the
     * primary by darkening, giving a sensible default gradient.
     */
    public int resolveSecondaryColor() {
        if (colorSource.is("Свой")) return secondaryColor.getValue();
        int themed = ColorProvider.getColorClient();
        return darken(themed, 0.55f);
    }

    private static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = Math.round(((color >> 16) & 0xFF) * factor);
        int g = Math.round(((color >> 8) & 0xFF) * factor);
        int b = Math.round((color & 0xFF) * factor);
        return (a << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
