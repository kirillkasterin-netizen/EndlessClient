package dev.endless.util.commands.defaults;

import dev.endless.util.chat.ChatUtil;
import dev.endless.util.commands.api.Command;
import dev.endless.util.commands.api.argument.IArgConsumer;
import dev.endless.util.commands.api.exception.CommandException;
import dev.endless.util.rotation.PatternManager;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public class PatternCommand extends Command {
    public PatternCommand() {
        super("pattern");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            ChatUtil.send("§cИспользование:");
            getLongDesc().forEach(ChatUtil::send);
            return;
        }

        String sub = args.getString().toLowerCase();
        PatternManager manager = PatternManager.getInstance();

        switch (sub) {
            case "recode":
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: .pattern recode <имя>");
                    return;
                }
                String recName = args.getString();
                manager.startRecording(recName);
                ChatUtil.send("§aНачата запись паттерна: §f" + recName);
                break;
            case "stop":
                if (!manager.isRecording()) {
                    ChatUtil.send("§cЗапись не запущена!");
                    return;
                }
                manager.stopRecording();
                ChatUtil.send("§aЗапись остановлена и сохранена.");
                break;
            case "load":
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: .pattern load <имя>");
                    return;
                }
                String loadName = args.getString();
                if (manager.loadPattern(loadName)) {
                    ChatUtil.send("§aПаттерн §f" + loadName + " §aуспешно загружен.");
                } else {
                    ChatUtil.send("§cПаттерн §f" + loadName + " §cне найден!");
                }
                break;
            case "active":
                ChatUtil.send("§7Активный паттерн: §f" + manager.getActivePatternName());
                break;
            case "list":
                ChatUtil.send("§eСписок паттернов:");
                manager.getLoadedPatterns().keySet().forEach(name -> ChatUtil.send("§7- §f" + name));
                break;
            case "browse":
                try {
                    Desktop.getDesktop().open(manager.getPatternsDir().toFile());
                    ChatUtil.send("§aПапка с паттернами открыта.");
                } catch (IOException e) {
                    ChatUtil.send("§cОшибка при открытии папки: " + e.getMessage());
                }
                break;
            default:
                ChatUtil.send("§cНеизвестная подкоманда: " + sub);
                break;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return Stream.of("recode", "stop", "load", "active", "list", "browse");
        }
        if (args.has(2)) {
            String sub = args.peekString().toLowerCase();
            if (sub.equals("load")) {
                return PatternManager.getInstance().getLoadedPatterns().keySet().stream();
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление паттернами ротации";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "§7.pattern recode <имя> §8- начать запись",
                "§7.pattern stop §8- остановить запись",
                "§7.pattern load <имя> §8- загрузить паттерн",
                "§7.pattern active §8- текущий активный паттерн",
                "§7.pattern list §8- список всех паттернов",
                "§7.pattern browse §8- открыть папку с паттернами"
        );
    }
}

