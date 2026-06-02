package endless.ere.base.comand.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;
import endless.ere.Endless;
import endless.ere.base.comand.api.CommandAbstract;
import endless.ere.base.notify.NotifyManager;
import endless.ere.base.repository.RCTRepository;
import endless.ere.utility.game.server.ServerHandler;
import endless.ere.utility.interfaces.IClient;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class RCTCommand extends CommandAbstract implements IClient {
    private final RCTRepository repository;

    public RCTCommand() {
        super("rct");
        repository = Endless.getInstance().getRCTRepository();
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            ServerHandler serverHandler = Endless.getInstance().getServerHandler();
            
            if (!serverHandler.isHolyWorld()) {
                NotifyManager.getInstance().addNotification("[RCT]", net.minecraft.text.Text.literal(" Не работает на этом " + Formatting.RED + "сервере"));
                return SINGLE_SUCCESS;
            }

            if (serverHandler.isPvp()) {
                NotifyManager.getInstance().addNotification("️[RCT]", net.minecraft.text.Text.literal(" Вы находитесь в режиме " + Formatting.RED + "пвп"));
                return SINGLE_SUCCESS;
            }

            repository.reconnect(serverHandler.getAnarchy());
            return SINGLE_SUCCESS;
        });

        builder.then(CommandAbstract.arg("anarchy", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 63)).executes(context -> {
            ServerHandler serverHandler = Endless.getInstance().getServerHandler();
            
            if (!serverHandler.isHolyWorld()) {
                NotifyManager.getInstance().addNotification("[RCT]", net.minecraft.text.Text.literal(" Не работает на этом " + Formatting.RED + "сервере"));
                return SINGLE_SUCCESS;
            }

            if (serverHandler.isPvp()) {
                NotifyManager.getInstance().addNotification("[RCT]️", net.minecraft.text.Text.literal(" Вы находитесь в режиме " + Formatting.RED + "пвп"));
                return SINGLE_SUCCESS;
            }

            int anarchy = context.getArgument("anarchy", Integer.class);
            repository.reconnect(anarchy);
            return SINGLE_SUCCESS;
        }));
    }
}
