package endless.ere.client.modules.impl.movement;

import lombok.Getter;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;


@ModuleAnnotation(name = "ElytraBooster", category = Category.MOVEMENT,description = "Усиливает ваш фейерверк")
@Getter
public final class ElytraBooster extends Module {
    public static final ElytraBooster INSTANCE = new ElytraBooster();

    private final ModeSetting mode = new ModeSetting( "Mode", "");
    @Getter
    private final NumberSetting boost = new NumberSetting( "Boost", 1f,0.1f,5f,0.1f, () -> !mode.getValue().getName().equals("Custom"));
    @Getter
    private ModeSetting.Value custom;
    @Getter
    private ModeSetting.Value auto;

    private ElytraBooster() {
        super();
        
        custom = new ModeSetting.Value(mode, "Custom", "");
        auto = new ModeSetting.Value(mode, "Auto", "");
        mode.setValue(custom);
    }

} 