package endless.ere.base.rotation.mods.config;


import endless.ere.base.rotation.mods.config.api.RotationConfig;
import endless.ere.base.rotation.mods.config.api.RotationModeType;

public class InstantRotationConfig extends RotationConfig {
    @Override
    public RotationModeType getType() {
        return RotationModeType.INSTANT;
    }
}
