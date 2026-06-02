package endless.ere.base.events.impl.player;


import net.minecraft.util.math.Vec3d;
import endless.ere.base.events.callables.EventCancellable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;



@AllArgsConstructor
@Getter
@Setter
public class EventMove extends EventCancellable {
    private Vec3d movePos;
}
