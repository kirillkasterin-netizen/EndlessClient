package endless.ere.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import org.lwjgl.glfw.GLFW;
import endless.ere.Endless;

import endless.ere.base.events.impl.input.EventSetScreen;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.render.EventRender2D;
import endless.ere.base.events.impl.render.EventRenderScreen;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.screens.menu.MenuScreen;
import endless.ere.client.screens.menu.settings.impl.MenuSliderSetting;
import endless.ere.utility.render.display.base.UIContext;

import java.awt.event.KeyEvent;

@ModuleAnnotation(name = "Menu", category = Category.RENDER, description = "Меню чита")
public final class Menu extends Module {
    public static final Menu INSTANCE = new Menu();

    private endless.ere.client.screens.panelgui.Panel panelScreen;

    private Menu() {
        this.setKeyCode(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onEnable() {
        if (mc.world == null) {
            this.setEnabled(false);
            return;
        }

        if (panelScreen == null) {
            panelScreen = new endless.ere.client.screens.panelgui.Panel(net.minecraft.text.Text.literal("Panel"));
        }

        if (mc.currentScreen == panelScreen) return;

        mc.setScreen(panelScreen);

        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }

    @Override
    public void setKeyCode(int keyCode) {
        if(keyCode == -1) return;
        super.setKeyCode(keyCode);
    }

    @EventTarget
    public void render2d(EventRenderScreen eventRender2D){
        // Old menu rendering is hidden
        /*
        UIContext uiContext =eventRender2D.getContext();
        Endless.getInstance().getMenuScreen().renderTop(uiContext,uiContext.getMouseX(),uiContext.getMouseY());
        if(Endless.getInstance().getMenuScreen().isFinish()){
            this.toggle();
        }
        */
        if (mc.currentScreen != panelScreen && this.isEnabled()) {
             // Panel closed itself or was closed by user
             this.setEnabled(false);
        }
    }

}
