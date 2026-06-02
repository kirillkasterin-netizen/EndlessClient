

package dev.endless.util.commands;

import com.google.common.eventbus.Subscribe;
import net.minecraft.util.Pair;
import dev.endless.Endless;
import dev.endless.event.list.ChatEvent;
import dev.endless.event.list.TabCompleteEvent;
import dev.endless.util.commands.api.argument.ICommandArgument;
import dev.endless.util.commands.api.exception.CommandNotEnoughArgumentsException;
import dev.endless.util.commands.api.exception.CommandNotFoundException;
import dev.endless.util.commands.api.helpers.TabCompleteHelper;
import dev.endless.util.commands.api.manager.ICommandManager;
import dev.endless.util.commands.argument.ArgConsumer;
import dev.endless.util.commands.argument.CommandArguments;
import dev.endless.util.commands.manager.CommandRepository;

import java.util.List;
import java.util.stream.Stream;

import static dev.endless.util.commands.api.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class CommandDispatcher {

    private final ICommandManager manager;

    public CommandDispatcher() {
        this.manager = Endless.getInstance().getCommandRepository();
        Endless.getInstance().getEventBus().register(this);
    }

    @Subscribe
    public void onSendChatMessage(ChatEvent event) {
        String msg = event.getMessage();
        String prefix = ".";
        boolean forceRun = msg.startsWith(FORCE_COMMAND_PREFIX);
        if ((msg.startsWith(prefix)) || forceRun) {
            event.setCancelled(true);
            String commandStr = msg.substring(forceRun ? FORCE_COMMAND_PREFIX.length() : prefix.length());
            if (!runCommand(commandStr) && !commandStr.trim().isEmpty()) {
                new CommandNotFoundException(CommandRepository.expand(commandStr).getLeft()).handle(null, null);
            }
        }
    }

    public boolean runCommand(String msg) {
        if (msg.isEmpty()) {
            return this.runCommand("help");
        }
        Pair<String, List<ICommandArgument>> pair = CommandRepository.expand(msg);
        return this.manager.execute(pair);
    }

    @Subscribe
    public void onPreTabComplete(TabCompleteEvent event) {
        String prefix = event.getPrefix();
        String commandPrefix = ".";
        if (!prefix.startsWith(commandPrefix)) {
            return;
        }
        String msg = prefix.substring(commandPrefix.length());
        List<ICommandArgument> args = CommandArguments.from(msg, true);
        Stream<String> stream = tabComplete(msg);
        if (args.size() == 1) {
            stream = stream.map(x -> commandPrefix + x);
        }
        event.completions = stream.toArray(String[]::new);
    }

    public Stream<String> tabComplete(String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(this.manager, args);
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                            .addCommands(this.manager)
                            .filterPrefix(argc.getString())
                            .stream();
                }
          
            }
            return this.manager.tabComplete(msg);
        } catch (CommandNotEnoughArgumentsException ignored) { 
            return Stream.empty();
        }
    }
}
