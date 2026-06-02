package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import endless.ere.Endless;
import endless.ere.base.events.impl.render.EventFog;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.ColorSetting;
import endless.ere.client.modules.api.setting.impl.MultiBooleanSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.render.display.base.color.ColorRGBA;

import java.util.List;

@ModuleAnnotation(name = "WorldTweaks",description = "", category =Category.RENDER)
public  final  class WorldTweaks extends Module {

    public final MultiBooleanSetting modeSetting =  MultiBooleanSetting.create("Настройки", List.of("Освещение", "Туман","Время"));


    public final NumberSetting brightSetting = new NumberSetting("Освещение",1.0F,0.0F, 1.0F,0.1f,() -> modeSetting.isEnable(0));

    private final ColorSetting colorFog = new ColorSetting("Цвет тумана", Endless.getInstance().getThemeManager().getCurrentTheme().getColor());
    public final NumberSetting distanceSetting = new NumberSetting("Дистанция туманна",80,10,255,5,() -> modeSetting.isEnable(1) );

    public final NumberSetting timeSetting = new NumberSetting("Время суток",12,0,24,1,() -> modeSetting.isEnable(2) );
    public static final WorldTweaks INSTANCE = new WorldTweaks();
    private WorldTweaks() {}
    @EventTarget
    public void onFog(EventFog e) {
        if (modeSetting.isEnable(1)) {
            e.setDistance(distanceSetting.getCurrent());
            e.setColor(colorFog.getIntColor());
            e.setCancelled(true);
        }
    }
}
