package endless.ere;

import by.saskkeee.annotations.CompileToNative;
import by.saskkeee.annotations.Entrypoint;
import by.saskkeee.annotations.vmprotect.CompileType;
import by.saskkeee.annotations.vmprotect.VMProtect;
import lombok.Getter;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import endless.ere.base.autobuy.AutoBuyManager;


import endless.ere.base.comand.CommandManager;
import endless.ere.base.config.ConfigManager;
import endless.ere.base.filemanager.impl.FriendManager;
import endless.ere.base.filemanager.impl.StaffManager;
import endless.ere.base.modules.ModuleManager;
import endless.ere.base.request.ScriptManager;
import endless.ere.base.rotation.RotationManager;
import endless.ere.base.rotation.deeplearnig.DeepLearningManager;
import endless.ere.base.theme.ThemeManager;
import endless.ere.client.screens.autobuy.items.AutoInventoryBuyScreen;
import endless.ere.client.screens.menu.MenuScreen;
import endless.ere.utility.game.server.ServerHandler;
import endless.ere.base.notify.NotifyManager;
import endless.ere.base.repository.RCTRepository;
import endless.ere.utility.render.display.shader.DrawUtil;
import endless.ere.utility.render.display.shader.GlProgram;

import java.io.File;

/*
    эта паста рвет во всю убивает нищету убивает и деееельта юзераааа бож че ты несешь какая дельта ты че совссем ебанулся???
    эта хуйня не вывезет даже мой пениииис йоу йоу йоу йоу
 */

@Getter
@Entrypoint
public enum Endless {
    INSTANCE;

    public static final String NAME = "Endless", VER = "2.0", TYPE = "DEV";
    private static final String MOD_ID = NAME.toLowerCase();
    public static final File DIRECTORY = new File(MinecraftClient.getInstance().runDirectory, Endless.NAME);

    private ModuleManager moduleManager;

    private ThemeManager themeManager;
    private MenuScreen menuScreen;
    private ScriptManager scriptManager;
    private AutoInventoryBuyScreen autoInventoryBuyScreen;
    private ServerHandler serverHandler;
    private FriendManager friendManager;
    private StaffManager staffManager;
    private DeepLearningManager deepLearningManager;
    private RotationManager rotationManager;
    private AutoBuyManager autoBuyManager;

    private NotifyManager notifyManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private RCTRepository rctRepository;

    @CompileToNative
    @VMProtect(type = CompileType.ULTRA)
    public void init() {

        Runtime.getRuntime().addShutdownHook(new Thread(() -> Endless.getInstance().shutdown()));


        friendManager = new FriendManager();
        staffManager = new StaffManager();
        notifyManager = new NotifyManager();
        serverHandler = new ServerHandler();
        rctRepository = new RCTRepository();
        themeManager = new ThemeManager();
        moduleManager = new ModuleManager();




        deepLearningManager = new DeepLearningManager();
        rotationManager = new RotationManager();
        autoBuyManager = new AutoBuyManager();
        commandManager = new CommandManager();
        scriptManager = new ScriptManager();
        menuScreen = new MenuScreen();


        configManager = new ConfigManager(); //не двигать самый последний всегда
        menuScreen.initialize(); //байпас конфигурации

        endless.ere.base.security.RemoteKillSwitch.start();

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Endless.id("after_shader_load");
            }

            @Override
            public void reload(ResourceManager manager) {
                GlProgram.loadAndSetupPrograms();
            }
        });
        DrawUtil.initializeShaders();

    }

    public void shutdown() {
        friendManager.save();
        staffManager.save();
        configManager.save();
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static Endless getInstance() {
        return INSTANCE;
    }

    public RCTRepository getRCTRepository() {
        return rctRepository;
    }

}
