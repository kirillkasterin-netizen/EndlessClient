package endless.ere.base.modules;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import lombok.Getter;
import org.lwjgl.glfw.GLFW;


import endless.ere.base.events.impl.input.EventKey;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.impl.combat.*;
import endless.ere.client.modules.impl.misc.*;
import endless.ere.client.modules.impl.movement.*;
import endless.ere.client.modules.impl.player.FastBreak;
import endless.ere.client.modules.impl.player.NoDelay;
import endless.ere.client.modules.impl.render.*;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.client.modules.impl.player.AutoTool;
import endless.ere.client.modules.impl.player.AutoArmor;
import endless.ere.client.modules.impl.player.Blink;


import java.util.*;

@Getter
public final class ModuleManager implements IMinecraft {

    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        init();
        EventManager.register(this);
    }

    private void init() {
        registerCombat();
        registerMovement();
        registerRender();
        registerPlayer();
        registerMisc();
    }

    private void registerCombat() {

        registerModule(AntiBot.INSTANCE);
        registerModule(Aura.INSTANCE);
        registerModule(AutoSwap.INSTANCE);

        registerModule(AutoTotem.INSTANCE);
        registerModule(AimAssist.INSTANCE);
        registerModule(KillAura.INSTANCE);
    }

    private void registerMovement() {

        registerModule(AutoSprint.INSTANCE);
        registerModule(ElytraBooster.INSTANCE);
        registerModule(GuiWalk.INSTANCE);
        registerModule(NoSlow.INSTANCE);
    }

    private void registerRender() {
        registerModule(Interface.INSTANCE);
        registerModule(AntiInvisible.INSTANCE);

        registerModule(Menu.INSTANCE);
        registerModule(NoRender.INSTANCE);
        registerModule(Predictions.INSTANCE);
        registerModule(BlockESP.INSTANCE);
        registerModule(BlockGlow.INSTANCE);
        registerModule(ShaderJump.INSTANCE);
        registerModule(JumpCircles.INSTANCE);
        registerModule(SkyShader.INSTANCE);
        registerModule(ShaderHands.INSTANCE);
        registerModule(SwingAnimation.INSTANCE);
        registerModule(Crosshair.INSTANCE);
        registerModule(ViewModel.INSTANCE);
        registerModule(WorldTweaks.INSTANCE);
        registerModule(EntityESP.INSTANCE);
        registerModule(Cosmetics.INSTANCE);
        registerModule(MoreCosmetic.INSTANCE);
        registerModule(TargetESP.INSTANCE);
    }

    private void registerPlayer() {
        registerModule(AutoTool.INSTANCE);
        registerModule(AutoArmor.INSTANCE);
        registerModule(Blink.INSTANCE);
        registerModule(NoDelay.INSTANCE);
        registerModule(FastBreak.INSTANCE);
    }

    private void registerMisc() {
        registerModule(ServerHelper.INSTANCE);
        registerModule(ElytraHelper.INSTANCE);
        registerModule(ItemScroller.INSTANCE);
        registerModule(ClickAction.INSTANCE);
        registerModule(FreeCam.INSTANCE);
        registerModule(CameraTweaks.INSTANCE);
        registerModule(AutoAuth.INSTANCE);
        registerModule(AutoDuels.INSTANCE);
        registerModule(AHHelper.INSTANCE);
        registerModule(AutoSbor.INSTANCE);
        registerModule(NoInteract.INSTANCE);
        registerModule(AutoAccept.INSTANCE);
        registerModule(AutoRespawn.INSTANCE);
        registerModule(NameProtect.INSTANCE);
        registerModule(ClanUpgrade.INSTANCE);
        registerModule(WardenHelper.INSTANCE);
        registerModule(AutoBuy.INSTANCE);
        registerModule(AppleFarm.INSTANCE);
    }

    private void registerModule(Module module) {
        modules.add(module);
    }


    public Module getModule(String name) {
        return modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public Set<Module> getActiveModules() {
        Set<Module> active = new HashSet<>();
        for (Module module : modules) {
            if (module.isEnabled()) active.add(module);
        }
        return active;
    }


    @EventTarget
    public void onKey(EventKey event) {

        if (mc.currentScreen != null || event.getAction() != GLFW.GLFW_PRESS) return;

        for (Module module : modules) {
            if (module.getKeyCode() == event.getKeyCode()
                    && module.getKeyCode() != GLFW.GLFW_KEY_UNKNOWN) {
                module.toggle();
            }
        }
    }
}
