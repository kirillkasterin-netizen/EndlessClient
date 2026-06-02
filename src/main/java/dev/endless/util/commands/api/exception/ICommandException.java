package dev.endless.util.commands.api.exception;

import net.minecraft.util.Formatting;
import dev.endless.util.QuickLogger;
import dev.endless.util.commands.api.ICommand;
import dev.endless.util.commands.api.argument.ICommandArgument;

import java.util.List;

public interface ICommandException extends QuickLogger {

    String getMessage();

    default void handle(ICommand command, List<ICommandArgument> args) {
        logDirect(
                this.getMessage(),
                Formatting.RED
        );
    }
}
