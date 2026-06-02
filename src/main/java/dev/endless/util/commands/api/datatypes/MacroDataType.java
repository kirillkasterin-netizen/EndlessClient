package dev.endless.util.commands.api.datatypes;

import dev.endless.Endless;
import dev.endless.util.commands.api.exception.CommandException;
import dev.endless.util.commands.api.helpers.TabCompleteHelper;
import dev.endless.util.keyboard.KeyStorage;
import dev.endless.util.macro.Macro;

import java.util.List;
import java.util.stream.Stream;

public enum MacroDataType implements IDatatypeFor<Macro> {
    INSTANCE;

    @Override
    public Stream<String> tabComplete(IDatatypeContext datatypeContext) throws CommandException {
        Stream<String> macros = getMacro()
                .stream()
                .map(macro -> KeyStorage.getKey(macro.key()));

        String context = datatypeContext
                .getConsumer()
                .getString();

        return new TabCompleteHelper()
                .append(macros)
                .filterPrefix(context)
                .sortAlphabetically()
                .stream();
    }

    @Override
    public Macro get(IDatatypeContext datatypeContext) throws CommandException {
        String username = datatypeContext
                .getConsumer()
                .getString();

        return getMacro().stream()
                .filter(s -> KeyStorage.getKey(s.key()).equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    private List<? extends Macro> getMacro() {
        return Endless.getInstance().getMacroRepository().getMacroList();
    }
}
