package dev.endless.module.list.render;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.hit.HitResult;

import dev.endless.event.list.EventHUD;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.BooleanSetting;
import dev.endless.module.settings.ColorSetting;
import dev.endless.module.settings.SliderSetting;
import dev.endless.util.render.providers.ColorProvider;
import dev.endless.util.render.renderers.DrawUtil;

@ModuleInformation(moduleName = "Crosshair", moduleDesc = "Кастомный прицел", moduleCategory = ModuleCategory.RENDER)
public final class Crosshair extends Module {

    private final SliderSetting  thickness      = new SliderSetting("Толщина", 1.0, 0.5, 3.0, 0.1);
    private final SliderSetting  length         = new SliderSetting("Длина",   3.0, 1.0, 8.0, 0.5);
    private final SliderSetting  gap            = new SliderSetting("Разрыв",  2.0, 0.0, 5.0, 0.5);
    private final BooleanSetting dynamicGap     = new BooleanSetting("Динамический разрыв", false);
    private final BooleanSetting useEntityColor = new BooleanSetting("Цвет при наведении",  false);
    private final ColorSetting   entityColor    = (ColorSetting) new ColorSetting("Цвет наведения", ColorProvider.rgba(255, 0, 0, 255))
            .setVisible(useEntityColor::getValue);

    @Subscribe
    public void onRender(EventHUD event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) return;

        float x = mc.getWindow().getScaledWidth()  / 2.0F;
        float y = mc.getWindow().getScaledHeight() / 2.0F;

        float currentGap       = gap.getFloatValue();
        float currentThickness = thickness.getFloatValue();
        float currentLength    = length.getFloatValue();

        if (dynamicGap.getValue()) {
            float cooldown = 1.0F - mc.player.getAttackCooldownProgress(0.0F);
            currentGap += 8.0F * cooldown;
        }

        boolean hoveringEntity = mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.ENTITY;
        int color = (useEntityColor.getValue() && hoveringEntity)
                ? entityColor.getValue()
                : ColorProvider.rgba(255, 255, 255, 255);

        // Top
        drawLine(x - currentThickness / 2.0F, y - currentGap - currentLength, currentThickness, currentLength, color);
        // Bottom
        drawLine(x - currentThickness / 2.0F, y + currentGap,                 currentThickness, currentLength, color);
        // Left
        drawLine(x - currentGap - currentLength, y - currentThickness / 2.0F, currentLength, currentThickness, color);
        // Right
        drawLine(x + currentGap,                 y - currentThickness / 2.0F, currentLength, currentThickness, color);
    }

    private void drawLine(float x, float y, float width, float height, int color) {
        DrawUtil.drawRound(x, y, width, height, 0f, color);
    }
}
