package endless.ere.utility.interfaces;

import endless.ere.Endless;
import endless.ere.base.rotation.AimManager;
import endless.ere.base.rotation.RotationManager;
import endless.ere.base.rotation.deeplearnig.DeepLearningManager;

public interface IClient extends IWindow{
    Endless endless = Endless.getInstance();
    DeepLearningManager deepLearningManager = Endless.getInstance().getDeepLearningManager();
    RotationManager rotationManager = Endless.getInstance().getRotationManager();
    AimManager aimManager = rotationManager.getAimManager();

}
