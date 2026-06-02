package dev.endless.util.commands.api.datatypes;

import dev.endless.util.IMinecraft;
import dev.endless.util.commands.api.exception.CommandException;

import java.util.stream.Stream;

public interface IDatatype extends IMinecraft {
    Stream<String> tabComplete(IDatatypeContext ctx) throws CommandException;
}
