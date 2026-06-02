package endless.ere.base.rotation;



import endless.ere.base.rotation.mods.config.api.RotationConfig;
import endless.ere.utility.game.player.rotation.Rotation;

import java.util.function.Supplier;


public record RotationTarget(Rotation targetRotation, Supplier<Rotation> rotation, RotationConfig rotationConfigBack) {
}
