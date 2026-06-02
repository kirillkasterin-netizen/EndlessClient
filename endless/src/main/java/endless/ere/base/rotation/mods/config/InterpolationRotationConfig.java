package endless.ere.base.rotation.mods.config;


import lombok.AllArgsConstructor;
import lombok.Getter;
import endless.ere.base.rotation.mods.config.api.RotationConfig;
import endless.ere.base.rotation.mods.config.api.RotationModeType;
import endless.ere.utility.math.IntRange;

@Getter
@AllArgsConstructor
public class InterpolationRotationConfig extends RotationConfig {

    private final IntRange horizontalSpeedSetting;
    private final IntRange verticalSpeedSetting  ;
    private final IntRange directionChangeFactor ;
    private final float midPoint ;

    @Override
    public RotationModeType getType() {
        return RotationModeType.INTERPOLATION;
    }
}
