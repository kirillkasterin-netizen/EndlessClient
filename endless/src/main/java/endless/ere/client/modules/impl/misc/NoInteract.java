package endless.ere.client.modules.impl.misc;

import endless.ere.client.modules.api.Module;
import endless.ere.client.modules.api.ModuleAnnotation;
import endless.ere.client.modules.api.Category;

@ModuleAnnotation(name = "NoInteract", category = Category.MISC, description = "Не дает открыть контейнера")
public final class NoInteract extends Module {
    public static final NoInteract INSTANCE = new NoInteract();
    
    private NoInteract() {
    }
}
