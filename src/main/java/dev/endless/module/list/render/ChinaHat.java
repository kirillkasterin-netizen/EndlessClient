package dev.endless.module.list.render;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityType;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;

@ModuleInformation(moduleName = "China Hat", moduleCategory = ModuleCategory.RENDER)
public class ChinaHat extends Module {

    private static ChinaHat instance;

    @SuppressWarnings("unchecked")
    public ChinaHat() {
        instance = this;

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityType == EntityType.PLAYER) {
                registrationHelper.register(new ChinaHatRenderer((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) entityRenderer));
            }
        });
    }

    public static ChinaHat getInstance() {
        return instance;
    }
}
