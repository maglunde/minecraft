package no.minecraft.render;

import no.minecraft.player.Player;
import no.minecraft.world.World;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * GuiScreen adapter that puts the legacy {@link MainMenu} on the
 * {@link ScreenStack}. The menu still renders itself through its own GL path
 * (see Main's render loop), so this adapter only maps the stack lifecycle onto
 * the menu's open/close state and forwards input to it. It consumes every
 * click while the menu is open, mirroring the legacy Main loop, which never let
 * game input through while the menu was shown.
 */
public class MainMenuScreen implements GuiScreen {

    private final MainMenu menu;
    private final IntSupplier width;
    private final IntSupplier height;
    private final Supplier<World> world;
    private final Supplier<Player> player;
    private boolean open = false;

    public MainMenuScreen(MainMenu menu, IntSupplier width, IntSupplier height,
                          Supplier<World> world, Supplier<Player> player) {
        this.menu = menu;
        this.width = width;
        this.height = height;
        this.world = world;
        this.player = player;
    }

    /**
     * Whether this screen is currently on the stack (i.e. the main menu is shown).
     */
    public boolean isOpen() {
        return open;
    }

    @Override
    public void onOpen() {
        open = true;
        if (!menu.isInMenu()) {
            // Resets the menu to the title screen and refreshes the world list,
            // exactly as the legacy open paths (M key, quit-to-title) did.
            menu.setInMenu(true);
        }
    }

    @Override
    public void onClose() {
        open = false;
        if (menu.isInMenu()) {
            menu.setInMenu(false);
        }
    }

    @Override
    public boolean handleMouseClick(double mx, double my, int button, boolean shiftDown) {
        if (!menu.isInMenu()) {
            return false;
        }
        menu.handleClick(mx, my, button, width.getAsInt(), height.getAsInt(), world.get(), player.get());
        return true;
    }

    @Override
    public boolean handleKey(int key, int action) {
        return menu.handleKey(key, action);
    }

    @Override
    public void handleScroll(double xoffset, double yoffset) {
        menu.handleScroll(yoffset);
    }

    @Override
    public void handleChar(char c) {
        menu.handleChar(c);
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
