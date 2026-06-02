package dev.endless.module.list.render;

import org.lwjgl.glfw.GLFW;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.render.hud.Interface;
import dev.endless.ui.CSGOClickGui;
import dev.endless.ui.ClickGuiFrame;
import dev.endless.ui.Panel;
import dev.endless.util.base.Instance;

@ModuleInformation(moduleName = "Click Gui", moduleCategory = ModuleCategory.RENDER, moduleKeybind = GLFW.GLFW_KEY_RIGHT_SHIFT)
public class ClickGui extends Module {

    private ClickGuiFrame clickGuiFrame;
    private CSGOClickGui csgoClickGui;

    @Override
    public void onEnable() {
        Interface iface = Instance.get(Interface.class);
        
        if (iface != null && iface.getGuiStyle().is("КС ГУИ")) {
            // Открываем CS:GO стиль
            if (csgoClickGui == null) csgoClickGui = new CSGOClickGui();
            mc.setScreen(csgoClickGui);
        } else {
            // Открываем стандартный стиль
            if (clickGuiFrame == null) clickGuiFrame = new ClickGuiFrame();
            mc.setScreen(clickGuiFrame);
            for (Panel panel : clickGuiFrame.getPanels()) {
                panel.getAnimationAlpha().setValue(0);
                panel.getAnimationAlpha().setStartValue(0);
                panel.getAnimationAlpha().reset();
            }
        }
        
        toggle();
    }
}
