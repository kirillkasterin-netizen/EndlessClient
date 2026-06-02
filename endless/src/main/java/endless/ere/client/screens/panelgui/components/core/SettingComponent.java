package endless.ere.client.screens.panelgui.components.core;

import net.minecraft.client.gui.DrawContext;
import endless.ere.client.modules.api.setting.Setting;
import endless.ere.utility.interfaces.IMinecraft;
import endless.ere.utility.interfaces.IMinecraft;

/**
 * @author chuppachups1337
 * @since 04/10/2025
 */

public abstract class SettingComponent<T extends Setting> implements IMinecraft {

    protected final T setting;
    protected float x, y, width, height;

    public SettingComponent(T setting) {
        this.setting = setting;
        this.height = 15f;
    }

    /**
     * Метод для отрисовки компонента.
     * @param g Графический контекст Minecraft.
     * @param mouseX Текущая координата X курсора.
     * @param mouseY Текущая координата Y курсора.
     * @param partialTicks Частичные тики, используемые для сглаженной анимации.
     * @param parentAlpha Фактор прозрачности, переданный от родительского компонента (0.0f до 1.0f).
     */

    public abstract void draw(DrawContext g, float mouseX, float mouseY, float partialTicks, float parentAlpha);

    /**
     * Обрабатывает клик мыши.
     * @param mouseX Текущая координата X курсора.
     * @param mouseY Текущая координата Y курсора.
     * @param button Нажатая кнопка мыши (0 - левая, 1 - правая, 2 - средняя).
     * @return true, если событие было обработано, иначе false.
     */

    public abstract boolean mouseClicked(float mouseX, float mouseY, int button);

    /**
     * Обрабатывает перетаскивание мыши (мышь нажата и двигается).
     */

    public void mouseDragged(double mouseX, double mouseY, int button) {}

    /**
     * Обрабатывает отпускание кнопки мыши.
     */

    public void mouseReleased(float mouseX, float mouseY, int button) {}

    /**
     * Утилитарный метод для проверки, находится ли курсор над компонентом.
     */

    protected boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /**
     * Обрабатывает нажатия клавиш клавиатуры.
     * Вызывается из GUI при вводе, если компонент активен.
     * @param keyCode Код клавиши GLFW.
     * @param scanCode Код скан-клавиши.
     * @param modifiers Модификаторы (Shift, Ctrl и т.д.).
     * @return true, если событие было обработано.
     */

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }


    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setWidth(float width) {
        this.width = width;

    }
    public float getHeight() {
        return height;
    }

    public T getSetting() {
        return setting;
    }
}