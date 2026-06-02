package endless.ere.base.rotation;

import com.darkmagician6.eventapi.EventManager;
import com.darkmagician6.eventapi.EventTarget;
import com.darkmagician6.eventapi.types.Priority;

import lombok.Getter;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRotationS2CPacket;
import net.minecraft.util.math.MathHelper;
import endless.ere.base.events.impl.other.EventSpawnEntity;
import endless.ere.base.events.impl.player.EventDirection;
import endless.ere.base.events.impl.player.EventRotate;
import endless.ere.base.events.impl.player.EventUpdate;
import endless.ere.base.events.impl.server.EventPacket;
import endless.ere.base.request.RequestHandler;
import endless.ere.client.modules.impl.render.Predictions;
import endless.ere.utility.game.other.MessageUtil;
import endless.ere.utility.game.player.rotation.Rotation;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.client.modules.api.Module;

@Getter
public class RotationManager implements IMinecraft {

    private Rotation currentRotation = new Rotation(0, 0);
    private Rotation previousRotation = new Rotation(0, 0);
    private final RequestHandler<RotationTarget> requestHandler = new RequestHandler<>();
    private final AimManager aimManager = new AimManager();
    private RotationTarget previousRotationTarget = new RotationTarget(currentRotation, () -> currentRotation, aimManager.getInstantSetup());

    private boolean setRotation = true;

    public RotationManager() {
        EventManager.register(this);

    }

    /**
     * Проверяет, активна ли кастомная ротация от модулей
     */
    public boolean hasActiveRotation() {
        // Ротация активна только если есть запрос от модулей И setRotation = false
        // setRotation = true означает, что ротация вернулась к оригинальной
        return requestHandler.getActiveRequestValue() != null && !setRotation;
    }
    
    /**
     * Проверяет, есть ли активный запрос на ротацию (без учета setRotation)
     */
    public boolean hasActiveRequest() {
        return requestHandler.getActiveRequestValue() != null;
    }

    @EventTarget
    public void addLocalPlayer(EventSpawnEntity eventSpawnLocalPlayer) {
        if (eventSpawnLocalPlayer.getEntity() instanceof ClientPlayerEntity player) {


            currentRotation = new Rotation(player.getYaw(), player.getPitch());
            previousRotation = new Rotation(player.getYaw(), player.getPitch());
            previousRotationTarget = new RotationTarget(currentRotation, () -> currentRotation, aimManager.getInstantSetup());
            setRotation = true;
        }
    }

    /**
     * Принудительно сбрасывает currentRotation на ванильный yaw/pitch игрока без интерполяции.
     * Используется модулями при отключении чтобы не было долгого "хвоста" Simulation flags.
     */
    public void forceResetToVanilla() {
        if (mc.player == null) return;
        // НЕ wrap - оставляем continuous yaw как в ванили
        float yaw = mc.player.lastYaw;
        float pitch = MathHelper.clamp(mc.player.lastPitch, -90f, 90f);
        currentRotation = new Rotation(yaw, pitch);
        previousRotation = currentRotation;
        previousRotationTarget = new RotationTarget(currentRotation, () -> currentRotation, aimManager.getInstantSetup());
        setRotation = true;
        hasLastSent = false;
        interactPendingForMove = false;
        suppressRotationTicks = 0;
    }

    @EventTarget(Priority.LOW)
    public void update(EventUpdate event) {

//        mc.player.prevHeadYaw = previousRotation.getYaw();
//        mc.player.prevPitch = previousRotation.getPitch();
//        mc.player.prevBodyYaw = previousRotation.getYaw();

        EventManager.call(new EventRotate());

        RotationTarget targetRotation = requestHandler.getActiveRequestValue();
        if (targetRotation != null) {


            Rotation newRot = targetRotation.rotation().get();
            previousRotation = currentRotation;
            currentRotation = newRot;
            setRotation = false;
            this.previousRotationTarget = targetRotation;
        } else {
            if (setRotation) {
                previousRotation = currentRotation;

                currentRotation = aimManager.rotate(aimManager.getInstantSetup(), new Rotation(mc.player.getYaw(),mc.player.getPitch()));

            } else {
                Rotation back = new Rotation(mc.player.getYaw(), mc.player.getPitch());

                if (currentRotation.rotationDeltaTo(back).isInRange(5)) {
                    previousRotation = currentRotation;
                    currentRotation = aimManager.rotate(aimManager.getInstantSetup(), back);
                    setRotation = true;

                } else {

                    Rotation newRot = aimManager.rotate(previousRotationTarget.rotationConfigBack(), back);

                    previousRotation = currentRotation;
                    currentRotation = newRot;


                }

            }
        }




       if(!setRotation) {
            float delta = currentRotation.getYaw() - mc.player.lastYaw;
            {
                Rotation validing = new Rotation(currentRotation.getYaw(), currentRotation.getPitch());
                if (delta > 320)
                    validing = new Rotation(mc.player.lastYaw + 300, currentRotation.getPitch()).normalize(new Rotation(mc.player.lastYaw, mc.player.lastPitch));
                if (delta < -320)
                    validing = new Rotation(mc.player.lastYaw - 300, currentRotation.getPitch()).normalize(new Rotation(mc.player.lastYaw, mc.player.lastPitch));

                currentRotation = validing;
            }
        }

        //  MessageUtil.displayMessage(MessageUtil.LogLevel.WARN, "valid " + (MathHelper.wrapDegrees(mc.player.getYaw()) + "  " + MathHelper.wrapDegrees(currentRotation.getYaw())));

        currentRotation = new Rotation(currentRotation.getYaw(), MathHelper.clamp(currentRotation.getPitch(), -90, 90));


        requestHandler.tick();

//        mc.player.setYaw(currentRotation.getYaw());
//        mc.player.setPitch(currentRotation.getPitch()


    }

    public void setRotation(RotationTarget targetRotation, int priority, Module module) {
        requestHandler.request(new RequestHandler.Request<>(2, priority, module, targetRotation));
    }

    /**
     * Render-ротация: обновляется ровно один раз за кадр в render-проходе,
     * независимо от того, сколько раз геттер был вызван (raycast, hud, ...).
     * tau — постоянная времени экспоненциального сглаживания в миллисекундах:
     * чем меньше — тем резче камера тянется к цели.
     */
    private Rotation renderRotation = new Rotation(0, 0);
    private long lastFrameNs = 0L;
    private static final float SMOOTH_TAU_MS = 70f;

    /** Тикает сглаживание ровно один раз на кадр. Должно вызываться из render-потока. */
    public void tickRenderRotation() {
        long nowNs = System.nanoTime();
        if (lastFrameNs == 0L) {
            lastFrameNs = nowNs;
            renderRotation = currentRotation;
            return;
        }
        float dtMs = Math.min(120f, (nowNs - lastFrameNs) / 1_000_000f);
        lastFrameNs = nowNs;

        float t = 1f - (float) Math.exp(-dtMs / SMOOTH_TAU_MS);
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;

        float yaw   = MathHelper.lerpAngleDegrees(t, renderRotation.getYaw(),   currentRotation.getYaw());
        float pitch = MathHelper.lerp           (t, renderRotation.getPitch(),  currentRotation.getPitch());
        renderRotation = new Rotation(yaw, pitch);
    }

    public Rotation getInterpolatedRotation() {
        return renderRotation;
    }

    @EventTarget
    public void direction(EventDirection direction) {
        if (suppressRotationTicks > 0) {
            suppressRotationTicks--;
            return;
        }
        if (hasActiveRequest()) {
            float yaw, pitch;
            if (interactPendingForMove) {
                yaw = lastSentYaw;
                pitch = lastSentPitch;
                interactPendingForMove = false;
            } else {
                float lastYaw = mc.player != null ? mc.player.lastYaw : 0f;
                float targetYaw = currentRotation.getYaw();
                // wrapDegrees ТОЛЬКО для расчёта дельты по короткому пути.
                // Финальный yaw НЕ wrap'аем - оставляем continuous как в ванили
                // (Grim AimModulo360 палит когда yaw кратен 0/360 что бывает после wrap).
                float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
                if (deltaYaw > 80f) deltaYaw = 80f;
                if (deltaYaw < -80f) deltaYaw = -80f;
                yaw = lastYaw + deltaYaw;
                pitch = MathHelper.clamp(currentRotation.getPitch(), -90f, 90f);
                lastSentYaw = yaw;
                lastSentPitch = pitch;
            }
            direction.setYaw(yaw);
            direction.setPitch(pitch);
            hasLastSent = true;
        } else {
            hasLastSent = false;
            interactPendingForMove = false;
        }
    }

    private float lastSentYaw, lastSentPitch;
    private boolean hasLastSent = false;
    private boolean interactPendingForMove = false;

    @EventTarget
    public void packet(EventPacket eventPacket) {


        switch (eventPacket.getPacket()) {
            case PlayerRotationS2CPacket player -> {
                currentRotation = new Rotation(player.xRot(), player.yRot());
                previousRotationTarget = new RotationTarget(currentRotation, () -> currentRotation, aimManager.getInstantSetup());
                setRotation = true;
            }

            case PlayerPositionLookS2CPacket player -> {
                currentRotation = new Rotation(player.change().yaw(), player.change().pitch());

                previousRotationTarget = new RotationTarget(currentRotation, () -> currentRotation, aimManager.getInstantSetup());
                setRotation = true;
            }
            case PlayerInteractItemC2SPacket packetItem -> {
                if (eventPacket.getAction() == EventPacket.Action.SENT && hasActiveRequest()) {
                    // Используем ту же формулу что в ClientPlayerEntityMixin.replaceMovePacketYaw
                    // чтобы interact и move в одном тике имели идентичный yaw.
                    float lastYaw = mc.player != null ? mc.player.lastYaw : 0f;
                    float deltaYaw = MathHelper.wrapDegrees(currentRotation.getYaw() - lastYaw);
                    if (deltaYaw > 80f) deltaYaw = 80f;
                    if (deltaYaw < -80f) deltaYaw = -80f;
                    float yaw = lastYaw + deltaYaw;
                    float pitch = MathHelper.clamp(currentRotation.getPitch(), -90f, 90f);
                    eventPacket.setPacket(new PlayerInteractItemC2SPacket(
                            packetItem.getHand(), packetItem.getSequence(), yaw, pitch));
                }
            }
            default -> {
            }
        }
    }

    private int suppressRotationTicks = 0;

}
