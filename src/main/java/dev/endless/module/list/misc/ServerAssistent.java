package dev.endless.module.list.misc;

import com.google.common.eventbus.Subscribe;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import org.lwjgl.glfw.GLFW;
import dev.endless.Endless;
import dev.endless.event.list.EventKeyInput;
import dev.endless.event.list.EventTick;
import dev.endless.module.Module;
import dev.endless.module.ModuleCategory;
import dev.endless.module.ModuleInformation;
import dev.endless.module.list.movement.Sprint;
import dev.endless.module.settings.BindSetting;
import dev.endless.module.settings.ModeSetting;
import dev.endless.util.base.Instance;
import dev.endless.util.packet.NetworkUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@ModuleInformation(
        moduleName = "Server Assistent",
        moduleDesc = "Помощник для серверов (HolyWorld / FunTime / ReallyWorld)",
        moduleCategory = ModuleCategory.MISC
)
public class ServerAssistent extends Module {

    private final ModeSetting server = new ModeSetting("Сервер", "HolyWorld",
            "ReallyWorld", "HolyWorld", "FunTime");

    // ==== HolyWorld ====
    private final BindSetting kHFireTrap   = (BindSetting) new BindSetting("Взрывная Трапка", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHTrap       = (BindSetting) new BindSetting("Обычная Трапка", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHStun       = (BindSetting) new BindSetting("Стан", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHFireball   = (BindSetting) new BindSetting("Взрывная Штучка", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHSnowball   = (BindSetting) new BindSetting("Ком Снега", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHJack       = (BindSetting) new BindSetting("Светильник Джека", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHExp        = (BindSetting) new BindSetting("Пузырь Опыта", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHBackpack1  = (BindSetting) new BindSetting("Рюкзак 1", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHBackpack2  = (BindSetting) new BindSetting("Рюкзак 2", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHBackpack3  = (BindSetting) new BindSetting("Рюкзак 3", -1).setVisible(() -> server.getValue().equals("HolyWorld"));
    private final BindSetting kHBackpack4  = (BindSetting) new BindSetting("Рюкзак 4", -1).setVisible(() -> server.getValue().equals("HolyWorld"));

    // ==== FunTime ====
    private final BindSetting kFSnowball   = (BindSetting) new BindSetting("Снежок Замароска", -1).setVisible(() -> server.getValue().equals("FunTime"));
    private final BindSetting kFAura       = (BindSetting) new BindSetting("Божья Аура", -1).setVisible(() -> server.getValue().equals("FunTime"));
    private final BindSetting kFTrap       = (BindSetting) new BindSetting("Трапка", -1).setVisible(() -> server.getValue().equals("FunTime"));
    private final BindSetting kFPlast      = (BindSetting) new BindSetting("Пласт", -1).setVisible(() -> server.getValue().equals("FunTime"));
    private final BindSetting kFSugar      = (BindSetting) new BindSetting("Явная Пыль", -1).setVisible(() -> server.getValue().equals("FunTime"));
    private final BindSetting kFFireCharge = (BindSetting) new BindSetting("Огненный Заряд", -1).setVisible(() -> server.getValue().equals("FunTime"));
    private final BindSetting kFDisorient  = (BindSetting) new BindSetting("Дезориентация", -1).setVisible(() -> server.getValue().equals("FunTime"));

    // ==== ReallyWorld ====
    private final BindSetting kRAnti       = (BindSetting) new BindSetting("Анти Полет", -1).setVisible(() -> server.getValue().equals("ReallyWorld"));
    private final BindSetting kRScroll     = (BindSetting) new BindSetting("Свиток Опыта", -1).setVisible(() -> server.getValue().equals("ReallyWorld"));

    // === Internal ===
    private record ItemBind(BindSetting setting, Item item, String nameLc, String[] excludes, Supplier<Boolean> visible) {}

    private final List<ItemBind> binds = new ArrayList<>();
    private final Map<BindSetting, Boolean> lastKey = new HashMap<>();
    private final List<ItemBind> queue = new ArrayList<>();

    /**
     * Многотиковый state machine.
     * Если предмет в хотбаре → сразу SELECT → USE → RESTORE_SELECT → DONE.
     * Если в инвентаре → SWAP_IN → SELECT → USE → RESTORE_SELECT → SWAP_OUT → DONE.
     * Между шагами — задержка в пару тиков, чтобы сервер успел обработать.
     */
    private enum State { IDLE, SWAP_IN, SELECT, USE, RESTORE_SELECT, SWAP_OUT, DONE }
    private State state = State.IDLE;
    private int tickWait = 0;       // сколько тиков ждём перед следующим шагом
    private static final int STEP_DELAY = 1;   // 1 тик = ~50мс

    private int sourceSlot = -1;    // изначальный слот предмета (0..8 хотбар, 9..35 инвентарь)
    private int safeHotbar = -1;    // куда положили предмет на хотбар (0..8)
    private int previousSelected = -1;
    private boolean fromInventory = false;
    private boolean wasSprinting = false;

    // === FunTime state machine (новая логика на 5 тиков, как в onetap FtHelper) ===
    private ItemBind ftPending = null;
    private int ftTickTimer = 0;
    private int ftItemSlot = -1;
    private int ftOriginalSlot = -1;
    private boolean ftF, ftB, ftL, ftR, ftJ;

    public ServerAssistent() {
        // HolyWorld
        addBind(kHFireTrap,  Items.PRISMARINE_SHARD,    "взрывная трапка",    null);
        addBind(kHTrap,      Items.POPPED_CHORUS_FRUIT, "трапка",             new String[]{"взрывная трапка"});
        addBind(kHStun,      Items.NETHER_STAR,         "стан",               new String[]{"взрывная штучка", "трапка", "снега", "рюкзак"});
        addBind(kHFireball,  Items.FIRE_CHARGE,         "взрывная штучка",    null);
        addBind(kHSnowball,  Items.SNOWBALL,            "ком снега",          null);
        addBind(kHJack,      Items.JACK_O_LANTERN,      "светильник",         null);
        addBind(kHExp,       Items.EXPERIENCE_BOTTLE,   "пузырь опыта",       null);
        addBind(kHBackpack1, Items.PINK_SHULKER_BOX,    "рюкзак (i уровень)", null);
        addBind(kHBackpack2, Items.BLUE_SHULKER_BOX,    "рюкзак (ii уровень)",null);
        addBind(kHBackpack3, Items.RED_SHULKER_BOX,     "рюкзак (iii уровень)",null);
        addBind(kHBackpack4, Items.PINK_SHULKER_BOX,    "рюкзак (iv уровень)",null);

        // FunTime — точные предметы как в onetap FtHelper
        addBind(kFSnowball,   Items.SNOWBALL,            "снежок",             null);
        addBind(kFAura,       Items.PHANTOM_MEMBRANE,    "божья аура",         null);
        addBind(kFTrap,       Items.NETHERITE_SCRAP,     "трапка",             null);
        addBind(kFPlast,      Items.DRIED_KELP,          "пласт",              null);
        addBind(kFSugar,      Items.SUGAR,               "явная",              null);
        addBind(kFFireCharge, Items.FIRE_CHARGE,         "огненный заряд",     null);
        addBind(kFDisorient,  Items.ENDER_EYE,           "дезориентация",      null);

        // ReallyWorld
        addBind(kRAnti,      Items.FIREWORK_STAR,        "анти полет",        null);
        addBind(kRScroll,    Items.FLOWER_BANNER_PATTERN,"свиток опыта",      null);
    }

    private void addBind(BindSetting setting, Item item, String nameLc, String[] excludes) {
        binds.add(new ItemBind(setting, item, nameLc, excludes, () -> setting.visible.get()));
        lastKey.put(setting, false);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    private void resetState() {
        state = State.IDLE;
        tickWait = 0;
        sourceSlot = -1;
        safeHotbar = -1;
        previousSelected = -1;
        fromInventory = false;
        wasSprinting = false;
        queue.clear();
        lastKey.replaceAll((k, v) -> false);

        // FunTime
        ftPending = null;
        ftItemSlot = -1;
        ftTickTimer = 0;
    }

    @Subscribe
    public void onTick(EventTick e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;

        // FunTime использует отдельный быстрый state machine (5 тиков) — обрабатывается через EventKeyInput
        if (server.is("FunTime")) {
            stepFunTime();
            return;
        }

        // === detect rising edge на биндах (HW / RW) ===
        long handle = mc.getWindow().getHandle();
        for (ItemBind b : binds) {
            if (!b.visible.get()) continue;
            int key = b.setting.getValue();
            boolean pressed = key != -1 && (key >= GLFW.GLFW_MOUSE_BUTTON_1 && key <= GLFW.GLFW_MOUSE_BUTTON_8
                    ? GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS
                    : InputUtil.isKeyPressed(handle, key));
            boolean wasPressed = lastKey.getOrDefault(b.setting, false);
            if (pressed && !wasPressed) {
                if (!queue.contains(b)) queue.add(b);
            }
            lastKey.put(b.setting, pressed);
        }

        runStateMachine();
    }

    /**
     * Обработчик нажатия клавиши специально для FunTime ветки.
     * Логика 1-в-1 из onetap FtHelper: при нажатии бинда находим слот предмета по имени,
     * сохраняем оригинальный hotbar-слот и стартуем 5-тиковый state machine.
     */
    @Subscribe
    public void onKeyInput(EventKeyInput e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) return;
        if (e.getAction() != 1) return; // только нажатие
        if (!server.is("FunTime")) return;
        if (ftPending != null) return; // уже что-то выполняется

        int key = e.getKey();
        ItemBind matched = null;
        for (ItemBind b : binds) {
            if (!b.visible.get()) continue;
            if (b.setting.getValue() == -1) continue;
            if (b.setting.getValue() == key) {
                matched = b;
                break;
            }
        }
        if (matched == null) return;

        int slot = findSlotByName(matched.nameLc, matched.excludes);
        if (slot == -1) return;
        if (mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(slot))) return;

        ftOriginalSlot = mc.player.getInventory().selectedSlot;
        ftPending = matched;
        ftItemSlot = -1;
        ftTickTimer = 0;
    }

    /** FunTime: один шаг 5-тикового state machine. */
    private void stepFunTime() {
        if (ftPending == null) return;
        ftTickTimer++;

        int selected = mc.player.getInventory().selectedSlot;
        int syncId = mc.player.currentScreenHandler.syncId;

        if (ftTickTimer == 1) {
            // Сохраняем и гасим движение
            ftF = mc.options.forwardKey.isPressed();
            ftB = mc.options.backKey.isPressed();
            ftL = mc.options.leftKey.isPressed();
            ftR = mc.options.rightKey.isPressed();
            ftJ = mc.options.jumpKey.isPressed();

            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);

            if (mc.player.input != null) {
                mc.player.input.movementForward = 0;
                mc.player.input.movementSideways = 0;
            }

            ftItemSlot = findSlotByName(ftPending.nameLc, ftPending.excludes);
            if (ftItemSlot == -1) {
                ftReset();
                return;
            }
            return;
        }

        switch (ftTickTimer) {
            case 2 -> {
                if (ftItemSlot >= 9) {
                    mc.interactionManager.clickSlot(syncId, ftItemSlot, selected, SlotActionType.SWAP, mc.player);
                } else {
                    mc.player.getInventory().selectedSlot = ftItemSlot;
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(ftItemSlot));
                }
            }
            case 3 -> {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            case 4 -> {
                if (ftItemSlot >= 9) {
                    mc.interactionManager.clickSlot(syncId, ftItemSlot, selected, SlotActionType.SWAP, mc.player);
                } else {
                    mc.player.getInventory().selectedSlot = ftOriginalSlot;
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(ftOriginalSlot));
                }
            }
            case 5 -> ftReset();
        }
    }

    private void ftReset() {
        if (mc.player != null) {
            mc.options.forwardKey.setPressed(ftF);
            mc.options.backKey.setPressed(ftB);
            mc.options.leftKey.setPressed(ftL);
            mc.options.rightKey.setPressed(ftR);
            mc.options.jumpKey.setPressed(ftJ);
        }
        ftPending = null;
        ftItemSlot = -1;
        ftTickTimer = 0;
    }

    private void runStateMachine() {
        // Пока идёт юз — каждый тик гасим движение/спринт чтобы он не возобновился
        if (state != State.IDLE) {
            mc.player.setSprinting(false);
            if (mc.player.input != null) {
                mc.player.input.movementForward = 0;
                mc.player.input.movementSideways = 0;
            }
            mc.options.sprintKey.setPressed(false);
            mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(
                    new PlayerInput(false, false, false, false, false, false, false)));
            if (Endless.getInstance().getServerManager().isServerSprinting()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                        mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
        }
        if (tickWait > 0) {
            tickWait--;
            return;
        }
        stepOnce();
    }

    /** Делает один шаг state machine. */
    private void stepOnce() {

        switch (state) {
            case IDLE -> {
                if (queue.isEmpty()) return;
                ItemBind b = queue.remove(0);

                int slot = findSlotByName(b.nameLc, b.excludes);
                if (slot == -1) return;
                if (mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(slot))) return;

                sourceSlot = slot;
                previousSelected = mc.player.getInventory().selectedSlot;
                fromInventory = (slot >= 9);

                // Сбрасываем спринт сервер-сайд: пустой инпут + STOP_SPRINTING.
                wasSprinting = mc.player.isSprinting() || Endless.getInstance().getServerManager().isServerSprinting();
                mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(
                        new PlayerInput(false, false, false, false, false, false, false)));
                if (wasSprinting) {
                    mc.player.setSprinting(false);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                            mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    Sprint sprintMod = Instance.get(Sprint.class);
                    if (sprintMod == null || !sprintMod.isEnabled()) {
                        mc.options.sprintKey.setPressed(false);
                    }
                }

                if (fromInventory) {
                    safeHotbar = findSafeHotbarSlot();
                    if (safeHotbar == -1) safeHotbar = 8;
                    mc.interactionManager.clickSlot(0, sourceSlot, safeHotbar, SlotActionType.SWAP, mc.player);
                    NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                    state = State.SWAP_IN;
                } else {
                    safeHotbar = sourceSlot;
                    state = State.SELECT;
                }
                tickWait = STEP_DELAY;
            }
            case SWAP_IN -> {
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(safeHotbar));
                mc.player.getInventory().selectedSlot = safeHotbar;
                state = State.USE;
                tickWait = STEP_DELAY;
            }
            case SELECT -> {
                if (!fromInventory) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(safeHotbar));
                    mc.player.getInventory().selectedSlot = safeHotbar;
                }
                state = State.USE;
                tickWait = STEP_DELAY;
            }
            case USE -> {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                mc.player.swingHand(Hand.MAIN_HAND);
                state = State.RESTORE_SELECT;
                tickWait = STEP_DELAY;
            }
            case RESTORE_SELECT -> {
                if (previousSelected != -1 && previousSelected != safeHotbar) {
                    mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSelected));
                    mc.player.getInventory().selectedSlot = previousSelected;
                }
                state = fromInventory ? State.SWAP_OUT : State.DONE;
                tickWait = STEP_DELAY;
            }
            case SWAP_OUT -> {
                mc.interactionManager.clickSlot(0, sourceSlot, safeHotbar, SlotActionType.SWAP, mc.player);
                NetworkUtils.sendSilentPacket(new CloseHandledScreenC2SPacket(0));
                state = State.DONE;
                tickWait = STEP_DELAY;
            }
            case DONE -> {
                if (mc.player.input != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(mc.player.input.playerInput));
                }
                if (wasSprinting) {
                    mc.player.setSprinting(true);
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                            mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                }
                wasSprinting = false;
                sourceSlot = -1;
                safeHotbar = -1;
                previousSelected = -1;
                fromInventory = false;
                state = State.IDLE;
            }
        }
    }

    // === Slot finding ===
    private int findSlotByName(String nameLc, String[] excludes) {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (matches(s, nameLc, excludes)) return i;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (matches(s, nameLc, excludes)) return i;
        }
        return -1;
    }

    private static boolean matches(ItemStack stack, String nameLc, String[] excludes) {
        if (stack.isEmpty()) return false;
        String n = stack.getName().getString().toLowerCase();
        if (!n.contains(nameLc)) return false;
        if (excludes != null) {
            for (String ex : excludes) {
                if (ex != null && n.contains(ex)) return false;
            }
        }
        return true;
    }

    private int findSafeHotbarSlot() {
        int sel = mc.player.getInventory().selectedSlot;
        // 1) пустой
        for (int i = 0; i < 9; i++) {
            if (i == sel) continue;
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        // 2) безопасный (не еда/щит/лук — UseAction.NONE)
        for (int i = 0; i < 9; i++) {
            if (i == sel) continue;
            if (mc.player.getInventory().getStack(i).getUseAction() == UseAction.NONE) return i;
        }
        // 3) фолбэк
        return -1;
    }
}
