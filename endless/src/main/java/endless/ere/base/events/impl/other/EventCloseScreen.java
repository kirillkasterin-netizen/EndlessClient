package endless.ere.base.events.impl.other;

import lombok.AllArgsConstructor;
import net.minecraft.client.gui.screen.Screen;
import endless.ere.base.events.callables.EventCancellable;
@AllArgsConstructor
public class EventCloseScreen extends EventCancellable {
   private final Screen screen;
}
