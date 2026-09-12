package no.minecraft.render;

import java.util.List;

/**
 * A user-facing screen (main menu, pause menu, inventory, chat overlay, ...)
 * shown on top of the game and managed by a {@link ScreenStack}.
 *
 * <p>Headless by design: a screen never issues GL calls itself. {@link #render}
 * receives two pre-built geometry lists that the caller batches and draws once
 * afterwards — {@code geom} for solid-color rectangles, {@code texGeom} for
 * textured rectangles, both in the vertex layout produced by
 * {@link UiBatch#addRect}.</p>
 *
 * <p>All lifecycle and input methods have no-op defaults so existing screens can
 * be migrated incrementally; only {@link #render} must be implemented.</p>
 */
public interface GuiScreen {

    /**
     * Called once by the stack when this screen is pushed and becomes the top of
     * the stack. The screen below it (if any) is not closed and stays open.
     */
    default void onOpen() {
    }

    /**
     * Called once by the stack when this screen is popped or the stack is closed
     * (top-most first). A screen that is merely covered by a newer push is not
     * closed and never receives this call.
     */
    default void onClose() {
    }

    /**
     * Handles a mouse click at ({@code mx}, {@code my}) in window pixel
     * coordinates (top-left origin, matching the render geometry).
     *
     * @param button     the mouse button (GLFW_MOUSE_BUTTON_1, ...)
     * @param shiftDown  whether shift was held during the click
     * @return true if the click was consumed and should not be acted on elsewhere
     */
    default boolean handleMouseClick(double mx, double my, int button, boolean shiftDown) {
        return false;
    }

    /**
     * Handles a keyboard event.
     *
     * @param key     the GLFW key code
     * @param action  the GLFW action (GLFW_PRESS, GLFW_RELEASE or GLFW_REPEAT)
     * @return true if the key press was consumed and should not be acted on elsewhere
     */
    default boolean handleKey(int key, int action) {
        return false;
    }

    /**
     * Handles a mouse scroll event.
     *
     * @param xoffset  horizontal scroll offset
     * @param yoffset  vertical scroll offset
     */
    default void handleScroll(double xoffset, double yoffset) {
    }

    /**
     * Handles a typed character (e.g. text fields).
     *
     * @param c  the typed character
     */
    default void handleChar(char c) {
    }

    /**
     * Emits this screen's geometry into the given lists. Both lists hold
     * rectangles in the {@link UiBatch#addRect} vertex format
     * (x, y, w, h, u0, v0, u1, v1, r, g, b, a); {@code geom} is drawn without a
     * texture, {@code texGeom} with one. No GL calls may be made here.
     *
     * @param geom      solid-color rectangle vertices
     * @param texGeom   textured rectangle vertices
     * @param width     window width in pixels
     * @param height    window height in pixels
     */
    void render(List<Float> geom, List<Float> texGeom, int width, int height);

    /**
     * Whether the game loop should be paused while this screen is open
     * (e.g. the pause menu). Screens that overlay live gameplay, such as the
     * chat or inventory, return false.
     */
    default boolean isPauseGame() {
        return false;
    }
}
