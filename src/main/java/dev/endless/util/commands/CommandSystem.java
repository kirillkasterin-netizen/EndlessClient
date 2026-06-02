package dev.endless.util.commands;

import dev.endless.util.commands.api.ICommandSystem;
import dev.endless.util.commands.api.argparser.IArgParserManager;
import dev.endless.util.commands.argparser.ArgParserManager;

public enum CommandSystem implements ICommandSystem {
    INSTANCE;

    @Override
    public IArgParserManager getParserManager() {
        return ArgParserManager.INSTANCE;
    }
}
