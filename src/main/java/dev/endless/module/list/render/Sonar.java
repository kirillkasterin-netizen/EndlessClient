package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.math.Vec3d;
import dev.endless.event.EventGameUpdate;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ColorSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.sonar.SonarRenderer;

/**
 * Sonar pulse module — emits an expanding scan ring around the player.
 *
 * The ring grows outwards from the configurable trigger point and is rendered
 * by {@link SonarRenderer} through a fragment shader that reads the depth
 * buffer to project the ring onto world geometry.
 *
 * Trigger options:
 *   • {@code Auto} — pulse periodically while enabled
 *   • {@code On move} — pulse when the player moves more than X blocks
 *   • {@code Manual} — only when the user re-enables the module
 */
@ModuleInformation(moduleName = "Sonar", moduleDesc = "Сканирующая волна вокруг игрока", moduleCategory = ModuleCategory.RENDER)
public class Sonar extends Module {

    public final SliderSetting duration = new SliderSetting("Длительность", 5.6, 0.8, 10.0, 0.1);
    public final SliderSetting alpha = new SliderSetting("Яркость", 1.0, 0.1, 1.0, 0.01);
    public final SliderSetting widthMul = new SliderSetting("Ширина", 1.0, 0.35, 2.2, 0.05);
    public final SliderSetting sharpness = new SliderSetting("Резкость", 24, 4, 80, 1);

    public final ModeSetting trigger = new ModeSetting(
            "Триггер", "Авто", "Авто", "При движении", "Вручную"
    );

    public final SliderSetting autoInterval = (SliderSetting) new SliderSetting(
            "Интервал, сек", 6.0, 1.0, 30.0, 0.5
    ).setVisible(() -> trigger.is("Авто"));

    public final SliderSetting moveThreshold = (SliderSetting) new SliderSetting(
            "Дистанция движения", 12.0, 4.0, 64.0, 1.0
    ).setVisible(() -> trigger.is("При движении"));

    public final ModeSetting colorSource = new ModeSetting(
            "Источник цвета", "Тема", "Тема", "Свой"
    );

    public final ColorSetting customColor = (ColorSetting) new ColorSetting(
            "Цвет", 0xFF67D7FF
    ).setVisible(() -> colorSource.is("Свой"));

    private long currentStart;
    private Vec3d center = Vec3d.ZERO;
    private long nextAutoAt;
    private Vec3d lastPingPos;

    @Override
    public void onEnable() {
        if (mc.player != null) {
            ping(mc.player.getPos());
        }
        nextAutoAt = System.currentTimeMillis();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        currentStart = 0L;
        SonarRenderer.getInstance().deleteDepthCopyFramebuffer();
        super.onDisable();
    }

    @Subscribe
    public void onUpdate(EventGameUpdate event) {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        Vec3d pos = mc.player.getPos();

        switch (trigger.getValue()) {
            case "Авто" -> {
                if (now >= nextAutoAt) {
                    ping(pos);
                    nextAutoAt = now + (long) (autoInterval.getValue() * 1000.0);
                }
            }
            case "При движении" -> {
                if (lastPingPos == null || lastPingPos.distanceTo(pos) >= moveThreshold.getValue()) {
                    ping(pos);
                }
            }
            // "Вручную" → no automatic re-trigger
        }
    }

    /**
     * Triggers a sonar pulse at the given world position.
     */
    public void ping(Vec3d pos) {
        currentStart = System.currentTimeMillis();
        center = pos;
        lastPingPos = pos;
    }

    /**
     * Resets the active pulse without triggering a new one.
     */
    public void clearPulse() {
        currentStart = 0L;
    }

    public long getCurrentStart() {
        return currentStart;
    }

    public Vec3d getCenter() {
        return center;
    }

    /**
     * Resolves the active ring color according to the user-selected source.
     */
    public int resolveColor() {
        if (colorSource.is("Свой")) return customColor.getValue();
        return ColorProvider.getColorClient();
    }
}
