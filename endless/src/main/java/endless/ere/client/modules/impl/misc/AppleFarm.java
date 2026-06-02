package endless.ere.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.ModeSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.game.other.MessageUtil;
import endless.ere.utility.math.Timer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AppleFarm - автоматическая ферма яблок/дерева.
 * Сажает саженцы, удобряет костной мукой, рубит дерево (вручную или через Baritone).
 */
@ModuleAnnotation(name = "AppleFarm", category = Category.MISC, description = "Автоферма дерева/яблок")
public final class AppleFarm extends Module {

    public static final AppleFarm INSTANCE = new AppleFarm();
    private AppleFarm() {}

    // --- Настройки ---
    private final ModeSetting breakMode = new ModeSetting("Режим рубки", "На месте", "Baritone");
    private final BooleanSetting autoStop = new BooleanSetting("Авто-стоп", "Останавливать Baritone после рубки", true,
            () -> breakMode.is("Baritone"));

    private final NumberSetting actionDelay = new NumberSetting("Задержка действий", 1f, 0f, 500f, 5f);
    private final NumberSetting commandDelay = new NumberSetting("Задержка команд", 600f, 0f, 2000f, 50f,
            () -> breakMode.is("Baritone"));
    private final NumberSetting breakTickDelay = new NumberSetting("Задержка ломания", 1f, 0f, 250f, 1f);
    private final NumberSetting toolSwitchDelay = new NumberSetting("Задержка смены инструмента", 80f, 0f, 1000f, 10f);

    private final NumberSetting scanRadius = new NumberSetting("Радиус сканирования", 6f, 1f, 10f, 1f);
    private final NumberSetting scanHeight = new NumberSetting("Высота сканирования", 20f, 1f, 40f, 1f);
    private final NumberSetting maxFarmDistance = new NumberSetting("Макс. дистанция", 5f, 1f, 12f, 0.5f);
    private final NumberSetting reachDistance = new NumberSetting("Дистанция ломания", 5.5f, 1f, 6f, 0.1f);

    private final NumberSetting rotationYawStep = new NumberSetting("Поворот Yaw", 180f, 1f, 180f, 1f);
    private final NumberSetting rotationPitchStep = new NumberSetting("Поворот Pitch", 180f, 1f, 180f, 1f);
    private final NumberSetting breakTolerance = new NumberSetting("Допуск ломания", 20f, 1f, 45f, 1f);
    private final NumberSetting interactTolerance = new NumberSetting("Допуск взаимодействия", 16f, 1f, 45f, 1f);

    // --- Таймеры ---
    private final Timer actionTimer = new Timer();
    private final Timer commandTimer = new Timer();
    private final Timer notifyTimer = new Timer();
    private final Timer toolSwitchTimer = new Timer();
    private final Timer breakTimer = new Timer();

    // --- Состояние ---
    private BlockPos farmLocation;
    private BlockPos currentTargetBlock;
    private Direction targetBlockSide;
    private boolean isBaritoneMining;
    private boolean hasBaritone;

    @Override
    public void onEnable() {
        isBaritoneMining = false;
        currentTargetBlock = null;
        targetBlockSide = null;
        actionTimer.reset();
        commandTimer.reset();
        notifyTimer.reset();
        toolSwitchTimer.reset();
        breakTimer.reset();

        hasBaritone = detectBaritone();

        if (breakMode.is("Baritone") && !hasBaritone) {
            MessageUtil.displayMessage(MessageUtil.LogLevel.WARN,
                    "Baritone не найден! Установи Baritone или режим 'На месте'.");
            disableSelf();
            return;
        }

        farmLocation = getLookTargetBlock();
        if (farmLocation == null) {
            MessageUtil.displayMessage(MessageUtil.LogLevel.WARN,
                    "Не удалось определить точку фермы. Посмотри на блок земли и включи модуль снова.");
            disableSelf();
            return;
        }

        if (breakMode.is("Baritone")) {
            runBaritoneCommand("#set autoTool true");
            runBaritoneCommand("#set allowBreak true");
        }

        MessageUtil.displayMessage(MessageUtil.LogLevel.INFO,
                "Ферма запущена на " + farmLocation + " режим " + breakMode.get());
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (breakMode.is("Baritone") && hasBaritone && isBaritoneMining && autoStop.isEnabled()) {
            runBaritoneCommand("#stop");
        }
        isBaritoneMining = false;
        currentTargetBlock = null;
        targetBlockSide = null;
        farmLocation = null;
        super.onDisable();
    }

    @EventTarget
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        if (farmLocation == null) {
            farmLocation = getLookTargetBlock();
            if (farmLocation == null) return;
        }

        // Проверка дистанции
        if (mc.player.squaredDistanceTo(farmLocation.toCenterPos())
                > Math.pow(maxFarmDistance.getCurrent(), 2)) {
            if (notifyTimer.finished(2500f)) {
                MessageUtil.displayMessage(MessageUtil.LogLevel.WARN,
                        "Слишком далеко от точки фермы! Вернись назад.");
                notifyTimer.reset();
            }
            if (breakMode.is("Baritone") && isBaritoneMining && autoStop.isEnabled()) {
                runBaritoneCommand("#stop");
                isBaritoneMining = false;
            }
            return;
        }

        if (isTreeGrown()) {
            if (breakMode.is("Baritone")) {
                updateBaritoneMining();
            }
            performManualChop();
            return;
        }

        currentTargetBlock = null;
        targetBlockSide = null;
        if (breakMode.is("Baritone") && isBaritoneMining && autoStop.isEnabled()) {
            runBaritoneCommand("#stop");
            isBaritoneMining = false;
        }

        if (!actionTimer.finished(actionDelay.getCurrent())) return;
        actionTimer.reset();

        if (!isSaplingPlanted()) {
            if (!tryPlantSapling()) {
                if (notifyTimer.finished(2500f)) {
                    Item sapling = findSaplingInInventory();
                    if (sapling == Items.AIR) {
                        MessageUtil.displayMessage(MessageUtil.LogLevel.WARN, "В инвентаре нет саженцев!");
                    } else {
                        MessageUtil.displayMessage(MessageUtil.LogLevel.WARN,
                                "Не удалось посадить " + sapling.getName().getString());
                    }
                    notifyTimer.reset();
                }
            }
            return;
        }

        if (!tryApplyBoneMeal()) {
            if (notifyTimer.finished(2500f)) {
                MessageUtil.displayMessage(MessageUtil.LogLevel.INFO,
                        "Нет костной муки, ждём естественного роста...");
                notifyTimer.reset();
            }
        }
    }

    // ==================== Baritone ====================

    private void updateBaritoneMining() {
        if (!commandTimer.finished(commandDelay.getCurrent())) return;
        String targets = anyLeavesNearby()
                ? "oak_leaves spruce_leaves birch_leaves jungle_leaves acacia_leaves dark_oak_leaves cherry_leaves mangrove_leaves"
                : "oak_log spruce_log birch_log jungle_log acacia_log dark_oak_log cherry_log mangrove_log";
        runBaritoneCommand("#mine 1 " + targets);
        isBaritoneMining = true;
        commandTimer.reset();
    }

    private void runBaritoneCommand(String cmd) {
        if (mc.player != null && mc.player.networkHandler != null) {
            mc.player.networkHandler.sendChatCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        }
    }

    private boolean detectBaritone() {
        try {
            Class.forName("baritone.api.BaritoneAPI");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // ==================== Manual chopping ====================

    private void performManualChop() {
        if (!breakTimer.finished(breakTickDelay.getCurrent())) return;

        if (currentTargetBlock == null || !isValidChopTarget(currentTargetBlock)) {
            BlockInfo best = findBestBlockToChop();
            if (best == null) {
                currentTargetBlock = null;
                targetBlockSide = null;
                return;
            }
            currentTargetBlock = best.pos;
            targetBlockSide = best.side;
        }

        if (currentTargetBlock == null || targetBlockSide == null) return;
        if (!aimAtBlock(currentTargetBlock, targetBlockSide)) return;
        switchToBestTool(currentTargetBlock);
        mc.interactionManager.attackBlock(currentTargetBlock, targetBlockSide);
        mc.player.swingHand(Hand.MAIN_HAND);
        breakTimer.reset();
    }

    // ==================== Planting / Bone meal ====================

    private boolean tryPlantSapling() {
        Item sapling = findSaplingInInventory();
        if (sapling == Items.AIR) return false;
        if (mc.world.getBlockState(farmLocation).isIn(BlockTags.SAPLINGS)) return true;
        if (!canPlantAt(farmLocation)) return false;
        BlockPos plantPos = farmLocation.up();
        BlockHitResult hit = new BlockHitResult(plantPos.toCenterPos(), Direction.UP, plantPos, false);
        return interactWithItem(sapling, hit);
    }

    private boolean tryApplyBoneMeal() {
        int oldSlot = mc.player.getInventory().selectedSlot;
        int mealSlot = getHotbarSlot(Items.BONE_MEAL);
        if (mealSlot == -1) {
            int invSlot = getInventorySlot(Items.BONE_MEAL);
            if (invSlot == -1) return false;
            int swapTo = findTrashHotbarSlot();
            if (swapTo == -1) return false;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, invSlot, swapTo,
                    SlotActionType.SWAP, mc.player);
            mealSlot = swapTo;
        }

        switchSlot(mealSlot);
        BlockHitResult hit = new BlockHitResult(farmLocation.toCenterPos(), Direction.UP, farmLocation, false);
        if (!rotateAndCheckAim(hit.getPos(), interactTolerance.getCurrent())) {
            if (oldSlot != mealSlot) switchSlot(oldSlot);
            return false;
        }
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (oldSlot != mealSlot) switchSlot(oldSlot);
        return result.isAccepted();
    }

    private boolean interactWithItem(Item item, BlockHitResult hit) {
        if (!rotateAndCheckAim(hit.getPos(), interactTolerance.getCurrent())) return false;
        int slot = getHotbarSlot(item);
        if (slot == -1) return false;
        int oldSlot = mc.player.getInventory().selectedSlot;
        if (oldSlot != slot) switchSlot(slot);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        if (oldSlot != slot) switchSlot(oldSlot);
        return result.isAccepted();
    }

    private void switchSlot(int slot) {
        if (slot < 0 || slot > 8) return;
        mc.player.getInventory().selectedSlot = slot;
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    // ==================== Inventory helpers ====================

    private int findTrashHotbarSlot() {
        for (int i = 0; i < 9; ++i) {
            Item item = mc.player.getInventory().getStack(i).getItem();
            if (item == Items.AIR) return i;
            if (isTool(item)) continue;
            return i;
        }
        return -1;
    }

    private boolean isTool(Item item) {
        String name = item.getName().getString();
        return name.contains("Axe") || name.contains("Pickaxe")
                || item == Items.WOODEN_AXE || item == Items.STONE_AXE
                || item == Items.IRON_AXE || item == Items.GOLDEN_AXE
                || item == Items.DIAMOND_AXE || item == Items.NETHERITE_AXE
                || item == Items.BONE_MEAL;
    }

    private int getHotbarSlot(Item target) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == target) return i;
        }
        return -1;
    }

    private int getInventorySlot(Item target) {
        for (int i = 9; i < 36; ++i) {
            if (mc.player.getInventory().getStack(i).getItem() == target) return i;
        }
        return -1;
    }

    private Item findSaplingInInventory() {
        List<Item> types = List.of(
                Items.OAK_SAPLING, Items.SPRUCE_SAPLING, Items.BIRCH_SAPLING,
                Items.JUNGLE_SAPLING, Items.ACACIA_SAPLING, Items.DARK_OAK_SAPLING,
                Items.CHERRY_SAPLING);
        for (int i = 0; i < 36; i++) {
            Item it = mc.player.getInventory().getStack(i).getItem();
            if (types.contains(it)) return it;
        }
        return Items.AIR;
    }

    private void switchToBestTool(BlockPos pos) {
        if (!toolSwitchTimer.finished(toolSwitchDelay.getCurrent())) return;
        BlockState state = mc.world.getBlockState(pos);
        int bestSlot = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            float speed = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        if (bestSlot != -1 && bestSlot != mc.player.getInventory().selectedSlot) {
            switchSlot(bestSlot);
            toolSwitchTimer.reset();
        }
    }

    // ==================== Scanning ====================

    private boolean isTreeGrown() {
        int r = (int) scanRadius.getCurrent();
        int h = (int) scanHeight.getCurrent();
        for (int x = -r; x <= r; ++x) {
            for (int z = -r; z <= r; ++z) {
                for (int y = 0; y <= h; ++y) {
                    BlockPos pos = farmLocation.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) return true;
                }
            }
        }
        return false;
    }

    private boolean isSaplingPlanted() {
        return mc.world.getBlockState(farmLocation.up()).isIn(BlockTags.SAPLINGS);
    }

    private boolean canPlantAt(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isFullCube(mc.world, pos) && !state.isOf(Blocks.GRASS_BLOCK)) return false;
        BlockPos up = pos.up();
        return mc.world.isAir(up) || mc.world.getBlockState(up).isOf(Blocks.SHORT_GRASS);
    }

    private BlockPos getLookTargetBlock() {
        if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (canPlantAt(pos)) return pos;
            if (canPlantAt(pos.down())) return pos.down();
        }
        return null;
    }

    private boolean isValidChopTarget(BlockPos pos) {
        if (pos == null) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isIn(BlockTags.LOGS) && !state.isIn(BlockTags.LEAVES)) return false;
        Direction side = getVisibleSide(pos);
        if (side == null) return false;
        targetBlockSide = side;
        return mc.player.squaredDistanceTo(pos.toCenterPos()) <= Math.pow(reachDistance.getCurrent(), 2);
    }

    private BlockInfo findBestBlockToChop() {
        List<BlockInfo> logs = new ArrayList<>();
        List<BlockInfo> leaves = new ArrayList<>();
        int r = (int) scanRadius.getCurrent();
        int h = (int) scanHeight.getCurrent();
        for (int x = -r; x <= r; ++x) {
            for (int z = -r; z <= r; ++z) {
                for (int y = 0; y <= h; ++y) {
                    BlockPos pos = farmLocation.add(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    double distSq = mc.player.squaredDistanceTo(pos.toCenterPos());
                    if (distSq > Math.pow(reachDistance.getCurrent(), 2)) continue;
                    Direction side = getVisibleSide(pos);
                    if (side == null) continue;
                    BlockInfo info = new BlockInfo(pos, side, distSq);
                    if (state.isIn(BlockTags.LOGS)) logs.add(info);
                    else if (state.isIn(BlockTags.LEAVES)) leaves.add(info);
                }
            }
        }
        Comparator<BlockInfo> byDist = Comparator.comparingDouble(b -> b.distSq);
        if (!leaves.isEmpty()) return leaves.stream().min(byDist).orElse(null);
        return logs.stream().min(byDist).orElse(null);
    }

    private boolean anyLeavesNearby() {
        int r = (int) scanRadius.getCurrent();
        int h = (int) scanHeight.getCurrent();
        for (int x = -r; x <= r; ++x) {
            for (int z = -r; z <= r; ++z) {
                for (int y = 0; y <= h; ++y) {
                    if (mc.world.getBlockState(farmLocation.add(x, y, z)).isIn(BlockTags.LEAVES)) return true;
                }
            }
        }
        return false;
    }

    private Direction getVisibleSide(BlockPos pos) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d center = pos.toCenterPos();
        for (Direction side : Direction.values()) {
            Vec3d point = center.add(side.getOffsetX() * 0.49,
                    side.getOffsetY() * 0.49, side.getOffsetZ() * 0.49);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(eyes, point,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                return hit.getSide();
            }
        }
        return null;
    }

    // ==================== Rotation ====================

    private boolean aimAtBlock(BlockPos pos, Direction side) {
        Vec3d hitVec = pos.toCenterPos().add(
                side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        return rotateAndCheckAim(hitVec, breakTolerance.getCurrent());
    }

    private boolean rotateAndCheckAim(Vec3d target, float tolerance) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float diffYaw = MathHelper.wrapDegrees(yaw - currentYaw);
        float diffPitch = MathHelper.wrapDegrees(pitch - currentPitch);

        float stepYaw = rotationYawStep.getCurrent();
        float stepPitch = rotationPitchStep.getCurrent();
        float nextYaw = currentYaw + MathHelper.clamp(diffYaw, -stepYaw, stepYaw);
        float nextPitch = MathHelper.clamp(currentPitch + MathHelper.clamp(diffPitch, -stepPitch, stepPitch), -90f, 90f);

        mc.player.setYaw(nextYaw);
        mc.player.setPitch(nextPitch);
        mc.player.setHeadYaw(nextYaw);
        mc.player.setBodyYaw(nextYaw);

        return Math.abs(diffYaw) <= tolerance && Math.abs(diffPitch) <= tolerance;
    }

    // ==================== Helpers ====================

    private void disableSelf() {
        if (isEnabled()) toggle();
    }

    private static class BlockInfo {
        final BlockPos pos;
        final Direction side;
        final double distSq;

        BlockInfo(BlockPos pos, Direction side, double distSq) {
            this.pos = pos;
            this.side = side;
            this.distSq = distSq;
        }
    }
}

