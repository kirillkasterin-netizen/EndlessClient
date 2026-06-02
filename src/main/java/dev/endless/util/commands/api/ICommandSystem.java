package dev.endless.util.commands.api;

import dev.endless.util.commands.api.argparser.IArgParserManager;

public interface ICommandSystem {
    IArgParserManager getParserManager();
}
