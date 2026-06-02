package endless.ere.base.rotation.mods;


import endless.ere.base.rotation.mods.api.RotationMode;
import endless.ere.utility.game.player.rotation.Rotation;
import endless.ere.utility.game.player.rotation.RotationDelta;

public class InstantRotationMode extends RotationMode {

    public Rotation process(Rotation target) {

        return rotationManager.getCurrentRotation().add(rotationManager.getCurrentRotation().rotationDeltaTo(target));
    }
}
