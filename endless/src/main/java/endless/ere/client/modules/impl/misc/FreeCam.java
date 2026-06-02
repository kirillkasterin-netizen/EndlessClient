package endless.ere.client.modules.impl.misc;

import com.darkmagician6.eventapi.EventTarget;
import net.minecraft.client.option.Perspective;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.util.math.Vec3d;
import endless.ere.base.events.impl.player.EventMove;
import endless.ere.base.events.impl.player.EventMoveInput;
import endless.ere.base.events.impl.render.EventCameraPosition;
import endless.ere.base.events.impl.render.EventRender3D;
import endless.ere.base.events.impl.server.EventPacket;
import endless.ere.client.modules.api.Category;
import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.setting.impl.BooleanSetting;
import endless.ere.client.modules.api.setting.impl.NumberSetting;
import endless.ere.utility.game.player.MovingUtil;
import endless.ere.utility.game.player.PlayerInventoryComponent;
import endless.ere.utility.math.MathUtil;
import endless.ere.utility.render.level.Render3DUtil;

@ModuleAnnotation(name = "FreeCam",description = "Свободный обзор камеры летать можно",category = Category.MISC)
public final class FreeCam extends Module {
    public static final FreeCam INSTANCE = new FreeCam();
    private final NumberSetting speedSetting = new NumberSetting("Скорость",2.0F,0.5F, 5.0F,0.5f);
    private final BooleanSetting freezeSetting = new BooleanSetting("Зависать", false);
    public Vec3d pos, prevPos;

    private FreeCam() {

    }

    
    @Override
    public void onEnable() {
        prevPos = pos = new Vec3d(mc.getEntityRenderDispatcher().camera.getPos().toVector3f());
        super.onEnable();
    }

    
    @EventTarget
    public void onPacket(EventPacket e) {
        switch (e.getPacket()) {
            case PlayerMoveC2SPacket move when freezeSetting.isEnabled() -> e.cancel();
            case PlayerRespawnS2CPacket respawn -> this.toggle();
            case GameJoinS2CPacket join -> this.toggle();
            default -> {}
        }
    }

    
    @EventTarget
    public void onWorldRender(EventRender3D e) {
        Render3DUtil.drawBox(mc.player.getBoundingBox().offset(MathUtil.interpolate(mc.player).subtract(mc.player.getPos())), endless.getThemeManager().getClientColor(90).getRGB(), 1);
    }

    
    @EventTarget
    public void onMove(EventMove e) {
        if (!this.isEnabled()) return;
        
        if (freezeSetting.isEnabled()) {
            e.setMovePos(Vec3d.ZERO);
        }
    }

    
    @EventTarget
    public void onInput(EventMoveInput e) {
        float speed = speedSetting.getCurrent();
        double[] motion = MovingUtil.calculateDirection(speed);

        prevPos = pos;
        pos = pos.add(motion[0], e.getInput().jump() ? speed : e.getInput().sneak() ? -speed : 0, motion[1]);


    }

    
    @EventTarget
    public void onCameraPosition(EventCameraPosition e) {
        e.setPos(MathUtil.interpolate(prevPos, pos));
        mc.options.setPerspective(Perspective.FIRST_PERSON);
    }
}
