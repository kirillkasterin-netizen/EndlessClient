package dev.endless;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import dev.endless.event.list.EventKeyInput;
import dev.endless.module.Module;
import dev.endless.module.ModuleStorage;
import dev.endless.util.commands.CommandDispatcher;
import dev.endless.util.commands.manager.CommandRepository;
import dev.endless.util.config.ConfigManager;
import dev.endless.util.discord.DiscordRPC;
import dev.endless.util.draggable.DragManager;
import dev.endless.util.friend.FriendRepository;
import dev.endless.util.macro.MacroRepository;
import dev.endless.util.math.TPSGetter;
import dev.endless.util.player.combat.IdealHitUtils;
import dev.endless.util.player.other.ServerManager;
import dev.endless.util.rotation.ComponentManager;
import dev.endless.util.script.ScriptManager;
import dev.endless.util.staff.StaffManager;

import java.io.File;

public class Endless implements ModInitializer {

    private static Endless instance;

    @Getter
    private final EventBus eventBus;

    @Getter
    private final ModuleStorage moduleStorage;
    @Getter
    private final ComponentManager componentManager;
    @Getter
    private final DragManager dragManager;
    @Getter
    private final CommandRepository commandRepository;
    @Getter
    private final MacroRepository macroRepository;
    @Getter
    private final ConfigManager configManager;
    @Getter
    private final CommandDispatcher commandDispatcher;
    @Getter
    private final StaffManager staffManager;
    @Getter
    private final ServerManager serverManager;
    @Getter
    private final TPSGetter tpsGetter;
    @Getter
    private final IdealHitUtils idealHitUtils;
    @Getter
    private final ScriptManager scriptManager;

    public Endless() {
        instance = this;

        eventBus = new EventBus();
        eventBus.register(this);

        moduleStorage = new ModuleStorage();
        componentManager = new ComponentManager();
        dragManager = new DragManager();
        macroRepository = new MacroRepository();
        configManager = new ConfigManager();
        staffManager = new StaffManager();
        staffManager.load();
        commandRepository = new CommandRepository();
        commandDispatcher = new CommandDispatcher();
        serverManager = new ServerManager();
        tpsGetter = new TPSGetter();
        idealHitUtils = new IdealHitUtils();
        scriptManager = new ScriptManager();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.save("autocfg");
            getDragManager().saveDraggables();
            getMacroRepository().save();
            FriendRepository.save();
            staffManager.save();
            DiscordRPC.shutdown();
        }));
        File dir = new File("endless/configs/");
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public static Endless getInstance() {
        return instance == null ? new Endless() : instance;
    }

    @Override
    public void onInitialize() {
        getModuleStorage().injectRegisterModules();
        componentManager.init();
        dragManager.load();
        macroRepository.load();
        FriendRepository.load();
        configManager.load("autocfg");
        DiscordRPC.init();
    }

    @Subscribe
    private void onModuleKeyPressed(EventKeyInput event) {
        for (Module module : getModuleStorage().getModules()) {
            if (event.getAction() == 1 && MinecraftClient.getInstance().currentScreen == null) {
                if (module.getKey() == event.getKey()) {
                    module.toggle();
                }
            }
        }
    }
}
