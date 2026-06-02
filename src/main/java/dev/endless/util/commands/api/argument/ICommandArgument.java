

package dev.endless.util.commands.api.argument;

import dev.endless.util.commands.api.argparser.IArgParser;
import dev.endless.util.commands.api.exception.CommandInvalidTypeException;

public interface ICommandArgument {

    
    int getIndex();

    
    String getValue();

    
    String getRawRest();

    
    <E extends Enum<?>> E getEnum(Class<E> enumClass) throws CommandInvalidTypeException;

    
    <T> T getAs(Class<T> type) throws CommandInvalidTypeException;

    
    <T> boolean is(Class<T> type);

    
    <T, S> T getAs(Class<T> type, Class<S> stateType, S state) throws CommandInvalidTypeException;

    
    <T, S> boolean is(Class<T> type, Class<S> stateType, S state);
}
