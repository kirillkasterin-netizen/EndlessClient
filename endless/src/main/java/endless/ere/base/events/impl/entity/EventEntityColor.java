package endless.ere.base.events.impl.entity;

import lombok.*;
import endless.ere.base.events.callables.EventCancellable;
@Getter
@Setter
@AllArgsConstructor
public class EventEntityColor extends EventCancellable {
    private int color;
}
