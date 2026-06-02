

package dev.endless.util.commands.api.argparser;

import dev.endless.util.commands.api.argument.ICommandArgument;
import dev.endless.util.commands.api.exception.CommandInvalidTypeException;
import dev.endless.util.commands.api.registry.Registry;

public interface  IArgParserManager {

    
    <T> IArgParser.Stateless<T> getParserStateless(Class<T> type);

    
    <T, S> IArgParser.Stated<T, S> getParserStated(Class<T> type, Class<S> stateKlass);

    
    <T> T parseStateless(Class<T> type, ICommandArgument arg) throws CommandInvalidTypeException;

    
    <T, S> T parseStated(Class<T> type, Class<S> stateKlass, ICommandArgument arg, S state) throws CommandInvalidTypeException;

    Registry<IArgParser> getRegistry();
}
