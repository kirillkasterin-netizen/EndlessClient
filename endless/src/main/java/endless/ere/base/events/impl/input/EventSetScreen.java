package endless.ere.base.events.impl.input;

import com.darkmagician6.eventapi.events.Event;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.injection.At;
import endless.ere.base.events.callables.EventCancellable;
@AllArgsConstructor
@Data
public class EventSetScreen implements Event {
    private Screen screen;
}
