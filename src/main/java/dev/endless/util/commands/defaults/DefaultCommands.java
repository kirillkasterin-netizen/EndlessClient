

package dev.endless.util.commands.defaults;

import dev.endless.Endless;
import dev.endless.util.commands.api.ICommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class DefaultCommands {

    public static List<ICommand> createAll() {
        List<ICommand> commands = new ArrayList<>(Arrays.asList(
                new CfgCommand(),
                new RotationCommand(),
                new HelpCommand(Endless.getInstance()),
                new MacroCommand(Endless.getInstance()),
                new BindCommand(Endless.getInstance()),
                new FriendCommand(Endless.getInstance()),
                new StaffCommand(Endless.getInstance()),
                new VClipCommand(),
                new PartyCommand(),
                new GpsCommand(),
                new PatternCommand(),
                new NeuroCommand(),
                new BotCommand()
        ));
        return Collections.unmodifiableList(commands);
    }
}
