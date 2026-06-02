package endless.ere.base.events.impl.player;


import net.minecraft.entity.Entity;
import endless.ere.base.events.callables.EventCancellable;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class EventAttack extends EventCancellable {
    private final Entity target;
    private final Action action;

    public enum Action {
        POST,
        PRE
    }
}