package dev.endless.util.commands.defaults;

import net.minecraft.client.MinecraftClient;
import dev.endless.ui.neuro.NeuroTrainerScreen;
import dev.endless.util.commands.api.Command;
import dev.endless.util.commands.api.argument.IArgConsumer;
import dev.endless.util.commands.api.exception.CommandException;
import dev.endless.util.neuro.trainer.NeuroProfile;
import dev.endless.util.neuro.trainer.NeuroProfileManager;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Управление Neuro профилями ротации.
 *
 * <pre>
 *  .neuro train          — открыть aim-тренажёр
 *  .neuro list           — список сохранённых профилей
 *  .neuro use &lt;name&gt;     — установить активный профиль
 *  .neuro current        — какой сейчас активный
 *  .neuro reload         — перечитать профили из диска
 *  .neuro delete &lt;name&gt;  — удалить профиль
 * </pre>
 */
public class NeuroCommand extends Command {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public NeuroCommand() {
        super("neuro");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            help();
            return;
        }
        String sub = args.getString().toLowerCase();
        switch (sub) {
            case "train" -> {
                mc.send(() -> mc.setScreen(new NeuroTrainerScreen()));
                msg("§aОткрываю aim-тренажёр…");
            }
            case "list" -> {
                List<NeuroProfile> all = NeuroProfileManager.get().getAll();
                if (all.isEmpty()) {
                    msg("§eПрофилей пока нет. Используй §6.neuro train");
                    return;
                }
                NeuroProfile active = NeuroProfileManager.get().getActive();
                msg("§aДоступные профили (" + all.size() + "):");
                for (NeuroProfile p : all) {
                    String marker = (active != null && p.name.equals(active.name)) ? "§b▶ " : "§7  ";
                    msg(marker + "§f" + p.name + " §7| попаданий: " + p.targetsHit
                            + ", скорость " + (int) p.avgSpeed + "°/с");
                }
            }
            case "use" -> {
                if (!args.hasAny()) {
                    msg("§cУкажи имя: §7.neuro use <name>");
                    return;
                }
                String name = args.getString();
                NeuroProfile p = NeuroProfileManager.get().get(name);
                if (p == null) {
                    msg("§cПрофиль «" + name + "» не найден");
                    return;
                }
                NeuroProfileManager.get().setActive(p);
                msg("§aАктивный профиль: §f" + name);
            }
            case "current" -> {
                NeuroProfile active = NeuroProfileManager.get().getActive();
                if (active == null) msg("§eНет активного профиля");
                else msg("§aАктивный: §f" + active.name);
            }
            case "reload" -> {
                NeuroProfileManager.get().reload();
                msg("§aПрофили перечитаны (" + NeuroProfileManager.get().getNames().size() + ")");
            }
            case "delete" -> {
                if (!args.hasAny()) {
                    msg("§cУкажи имя: §7.neuro delete <name>");
                    return;
                }
                String name = args.getString();
                if (NeuroProfileManager.get().delete(name)) {
                    msg("§aПрофиль «" + name + "» удалён");
                } else {
                    msg("§cНе удалось удалить «" + name + "»");
                }
            }
            default -> help();
        }
    }

    private void help() {
        msg("§a=== Neuro Commands ===");
        msg("§7.neuro train §f— открыть aim-тренажёр");
        msg("§7.neuro list §f— список профилей");
        msg("§7.neuro use <name> §f— активировать профиль");
        msg("§7.neuro current §f— текущий активный");
        msg("§7.neuro reload §f— перечитать профили");
        msg("§7.neuro delete <name> §f— удалить");
    }

    private void msg(String text) {
        if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.literal(text), false);
        }
    }

    @Override
    public String getShortDesc() {
        return "Neuro профили ротации (aim-тренажёр)";
    }

    @Override
    public List<String> getLongDesc() {
        return List.of(
                ".neuro train — открыть aim-тренажёр и записать новый профиль",
                ".neuro list — список профилей",
                ".neuro use <name> — выбрать активный",
                ".neuro current — показать текущий",
                ".neuro reload — перечитать с диска",
                ".neuro delete <name> — удалить"
        );
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("train", "list", "use", "current", "reload", "delete");
        }
        if (args.has(2)) {
            try {
                String sub = args.peekString();
                if (sub.equalsIgnoreCase("use") || sub.equalsIgnoreCase("delete")) {
                    return NeuroProfileManager.get().getNames().stream();
                }
            } catch (Exception ignored) {}
        }
        return Stream.empty();
    }
}
