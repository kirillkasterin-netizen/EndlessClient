package dev.endless.util.commands.defaults;

import dev.endless.util.chat.ChatUtil;
import dev.endless.util.commands.api.Command;
import dev.endless.util.commands.api.argument.IArgConsumer;
import dev.endless.util.commands.api.exception.CommandException;
import dev.endless.util.dataset.DatasetRecorder;
import dev.endless.util.dataset.DatasetPlayer;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

public class DatasetCommand extends Command {
    
    public DatasetCommand() {
        super("dataset");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            printHelp();
            return;
        }

        String subcommand = args.getString().toLowerCase();

        switch (subcommand) {
            case "start" -> {
                if (DatasetRecorder.isRecording()) {
                    ChatUtil.send("§eЗапись уже идет!");
                    return;
                }
                
                DatasetRecorder.startRecording();
                ChatUtil.send("§a✓ Запись датасета начата!");
                ChatUtil.send("§c§l⚠ ВЫКЛЮЧИ KillAura!");
                ChatUtil.send("§7Играй §eВРУЧНУЮ§7 и атакуй мобов/игроков");
                ChatUtil.send("§7Используй §f.dataset stop §7для остановки");
            }

            case "stop" -> {
                if (!DatasetRecorder.isRecording()) {
                    ChatUtil.send("§eЗапись не идет!");
                    return;
                }
                
                int frames = DatasetRecorder.stopRecording();
                ChatUtil.send("§a✓ Запись остановлена!");
                ChatUtil.send("§7Записано фреймов: §e" + frames);
                ChatUtil.send("§7Датасет сохранен в §fwraith/datasets/");
                ChatUtil.send("");
                ChatUtil.send("§aТеперь:");
                ChatUtil.send("§71. §f.dataset load <имя файла>");
                ChatUtil.send("§72. Включи KillAura → Ротация → §eCustom");
            }

            case "load" -> {
                if (!args.hasAny()) {
                    ChatUtil.send("§cИспользование: §f.dataset load <имя>");
                    ChatUtil.send("§7Пример: §f.dataset load dataset_2026-04-20_15-30-00.csv");
                    return;
                }
                
                String filename = args.getString();
                String fullPath = "endless/datasets/" + filename;
                
                ChatUtil.send("§7Загрузка датасета...");
                boolean success = DatasetPlayer.loadDataset(fullPath);
                
                if (success) {
                    ChatUtil.send("§a✓ Датасет загружен!");
                    ChatUtil.send("§7Фреймов: §e" + DatasetPlayer.getFrameCount());
                    ChatUtil.send("§7Теперь включи KillAura → Ротация → §eCustom");
                } else {
                    ChatUtil.send("§c✗ Ошибка загрузки датасета!");
                    ChatUtil.send("§7Проверь имя файла: §f" + fullPath);
                }
            }

            case "list" -> {
                File dir = new File("endless/datasets");
                if (!dir.exists() || !dir.isDirectory()) {
                    ChatUtil.send("§cПапка с датасетами не найдена!");
                    ChatUtil.send("§7Сначала запиши датасет: §f.dataset start");
                    return;
                }
                
                File[] files = dir.listFiles((d, name) -> name.endsWith(".csv"));
                if (files == null || files.length == 0) {
                    ChatUtil.send("§eДатасеты не найдены!");
                    ChatUtil.send("§7Запиши датасет: §f.dataset start");
                    return;
                }
                
                ChatUtil.send("§e§l=== Датасеты ===");
                for (File file : files) {
                    ChatUtil.send("§7- §f" + file.getName());
                }
                ChatUtil.send("§7Загрузить: §f.dataset load <имя>");
            }

            case "status" -> {
                if (DatasetRecorder.isRecording()) {
                    ChatUtil.send("§eЗапись идет...");
                    ChatUtil.send("§7Фреймов записано: §f" + DatasetRecorder.getFrameCount());
                } else if (DatasetPlayer.hasDataset()) {
                    ChatUtil.send("§aДатасет загружен!");
                    ChatUtil.send("§7Файл: §f" + DatasetPlayer.getCurrentDatasetName());
                    ChatUtil.send("§7Фреймов: §e" + DatasetPlayer.getFrameCount());
                } else {
                    ChatUtil.send("§7Датасет не загружен");
                    ChatUtil.send("§7Используй §f.dataset load <имя>");
                }
            }

            default -> {
                ChatUtil.send("§cНеизвестная подкоманда: §f" + subcommand);
                printHelp();
            }
        }
    }

    private void printHelp() {
        ChatUtil.send("§e§l=== Dataset Commands ===");
        ChatUtil.send("§f.dataset start §7- Начать запись §c(БЕЗ ЧИТОВ!)");
        ChatUtil.send("§f.dataset stop §7- Остановить запись");
        ChatUtil.send("§f.dataset load <имя> §7- Загрузить датасет");
        ChatUtil.send("§f.dataset list §7- Список датасетов");
        ChatUtil.send("§f.dataset status §7- Статус");
        ChatUtil.send("");
        ChatUtil.send("§a§lБыстрый старт:");
        ChatUtil.send("§71. §cВыключи KillAura!");
        ChatUtil.send("§72. §f.dataset start");
        ChatUtil.send("§73. §7Играй §eВРУЧНУЮ§7 10-15 мин");
        ChatUtil.send("§74. §f.dataset stop");
        ChatUtil.send("§75. §f.dataset load <имя файла>");
        ChatUtil.send("§76. KillAura → Ротация → §eCustom");
    }

    @Override
    public String getShortDesc() {
        return "Управление датасетами ротаций";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                "Команда для записи и воспроизведения датасетов",
                "",
                "Использование:",
                ".dataset start - начать запись (БЕЗ ЧИТОВ!)",
                ".dataset stop - остановить запись",
                ".dataset load <имя> - загрузить датасет",
                ".dataset list - список датасетов",
                ".dataset status - статус"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("start", "stop", "load", "list", "status");
        }
        return Stream.empty();
    }
}
