package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;

import endless.ere.Endless;
import endless.ere.base.events.impl.render.EventHudRender;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.ColorSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.render.display.base.color.ColorRGBA;
import endless.ere.utility.render.display.shader.hands.ShaderHandsRenderer;

/**
 * Красивый шейдер на руки и предметы. Перенесено из WraithClient.
 * Сам пайплайн (захват кадров до/после рук, маска, эффекты) живёт в
 * {@link ShaderHandsRenderer}; модуль хранит настройки и финальную композицию
 * вызывает в HUD-проходе.
 */
@ModuleAnnotation(name = "ShaderHands", category = Category.RENDER,
        description = "Красивый шейдер на руки и предметы")
public final class ShaderHands extends Module {

    public static final ShaderHands INSTANCE = new ShaderHands();

    private static final ShaderHandsRenderer RENDERER = ShaderHandsRenderer.getInstance();

    private final ModeSetting mode = new ModeSetting("Режим", "Свечение", "Красивый");
    private final ModeSetting colorSource = new ModeSetting("Источник цвета", "Тема", "Свой");
    private final ColorSetting customColor = new ColorSetting("Цвет", new ColorRGBA(0xFF8A6BFF),
            () -> colorSource.is("Свой"));
    private final NumberSetting waveSpeed = new NumberSetting("Скорость волн", 1.2f, 0.1f, 5.0f, 0.1f,
            () -> mode.is("Красивый"));
    private final NumberSetting waveScale = new NumberSetting("Частота волн", 1.0f, 1.0f, 3.0f, 0.1f,
            () -> mode.is("Красивый"));
    private final NumberSetting outline = new NumberSetting("Ширина обводки", 1.2f, 0.1f, 5.0f, 0.1f);
    private final NumberSetting glow = new NumberSetting("Сила свечения", 1.0f, 0.0f, 5.0f, 0.1f);
    private final NumberSetting fill = new NumberSetting("Заливка", 0.6f, 0.0f, 1.0f, 0.01f);
    private final NumberSetting alpha = new NumberSetting("Прозрачность", 1.0f, 0.0f, 1.0f, 0.05f);

    private ShaderHands() {
    }

    @Override
    public void onDisable() {
        RENDERER.invalidateState();
        super.onDisable();
    }

    @EventTarget
    public void onHudRender(EventHudRender event) {
        if (!isEnabled()) return;
        RENDERER.renderOverlayIfPending();
    }

    // ── Доступ для рендерера ─────────────────────────────────────────────────

    public boolean isPrettyMode() {
        return mode.is("Красивый");
    }

    public float getWaveSpeedValue() {
        return waveSpeed.getCurrent();
    }

    public float getWaveScaleValue() {
        return waveScale.getCurrent();
    }

    public float getOutlineValue() {
        return outline.getCurrent();
    }

    public float getGlowValue() {
        return glow.getCurrent();
    }

    public float getFillValue() {
        return fill.getCurrent();
    }

    public float getAlphaValue() {
        return alpha.getCurrent();
    }

    public int resolveColor() {
        if (colorSource.is("Свой")) return customColor.getIntColor();
        return Endless.getInstance().getThemeManager().getCurrentTheme().getColor().getRGB();
    }
}
