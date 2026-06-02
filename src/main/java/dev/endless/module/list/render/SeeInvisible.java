package dev.endless.module.list.render;

import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.settings.SliderSetting;

/**
 * SeeInvisible — рендерит сущности с эффектом INVISIBILITY как полупрозрачные,
 * вместо полного скрытия. Работает через LivingEntityRendererMixin.
 *
 * Адаптировано из mytheria SeeInvisible.
 */
@ModuleInformation(
        moduleName = "SeeInvisible",
        moduleDesc = "Показывает невидимых сущностей с настраиваемой прозрачностью",
        moduleCategory = ModuleCategory.RENDER)
public class SeeInvisible extends Module {

    public final SliderSetting alpha = new SliderSetting(
            "Альфа", 0.5f, 0.1f, 1.0f, 0.01f);

    /** Сила альфы в виде значения 0..1. Берётся из mixin'а. */
    public float getAlpha() {
        return alpha.getFloatValue();
    }
}
