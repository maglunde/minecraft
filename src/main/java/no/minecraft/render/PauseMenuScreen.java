package no.minecraft.render;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * GuiScreen adapter that puts the legacy {@link PauseMenu} on the
 * {@link ScreenStack}. The menu still renders itself through its own GL path
 * (see Main's render loop), so this adapter only maps the stack lifecycle onto
 * the menu's own open/close state and forwards input to it. It consumes every
 * click while the pause menu is open, mirroring the legacy Main loop.
 */
public class PauseMenuScreen implements GuiScreen {

    private final PauseMenu menu;
    private final IntSupplier width;
    private final IntSupplier height;
    private boolean open = false;

    public PauseMenuScreen(PauseMenu menu, IntSupplier width, IntSupplier height) {
        this.menu = menu;
        this.width = width;
        this.height = height;
    }

    /**
     * Whether this screen is currently on the stack (i.e. the pause menu is shown).
     */
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onOpen() {
        open = true;
        if (!menu.isOpen()) {
            // Normal pause-menu open (ESC in game). The options-from-title flow
            // overrides this right after pushing (openOptionsFromTitle).
            menu.open();
        }
    }

    @Override
    public void onClose() {
        open = false;
        if (menu.isOpen()) {
            menu.close();
        }
    }

    @Override
    public boolean handleMouseClick(double mx, double my, int button, boolean shiftDown) {
        if (!menu.isOpen()) {
            return false;
        }
        menu.handleClick(mx, my, button, width.getAsInt(), height.getAsInt());
        return true;
    }

    @Override
    public boolean handleKey(int key, int action) {
        return menu.handleKey(key, action);
    }

    @Override
    public void render(List<Float> geom, List<Float> texGeom, int width, int height) {
        // The legacy menu draws itself with its own shader/VAO; nothing is emitted here.
    }

    @Override
    public boolean isPauseGame() {
        return true;
    }
}
