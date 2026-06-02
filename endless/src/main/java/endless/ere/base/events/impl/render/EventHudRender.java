package endless.ere.base.events.impl.render;

import com.darkmagician6.eventapi.events.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import endless.ere.utility.render.display.base.CustomDrawContext;

@Getter
@RequiredArgsConstructor
public class EventHudRender implements Event {

    private final CustomDrawContext context;
    private final float tickDelta;

}