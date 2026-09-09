package no.minecraft.render;

import java.util.ArrayList;
import java.util.List;

/**
 * A LIFO stack of open {@link GuiScreen}s and the single UI exclusivity
 * mechanism: input entering the stack is dispatched to the top-most screen
 * alone, and screens below it receive nothing while covered.
 *
 * <p>Stack semantics:</p>
 * <ul>
 *   <li>{@link #push} calls the new screen's {@link GuiScreen#onOpen()}. The
 *       previous top screen (if any) is <em>not</em> closed — it receives no
 *       {@link GuiScreen#onClose()} call, stays open with its state intact, and
 *       is simply covered.</li>
 *   <li>{@link #pop} calls the popped screen's {@link GuiScreen#onClose()}.
 *       The screen below resumes receiving input (it was never closed).</li>
 *   <li>{@link #closeAll} pops every screen top-most first, each getting its
 *       {@link GuiScreen#onClose()}.</li>
 * </ul>
 *
 * <p>The stack performs no rendering itself and makes no GL calls; screens
 * render through their own geometry lists.</p>
 */
public class ScreenStack {

    /** Open screens, bottom of the stack at index 0, top at the last index. */
    private final List<GuiScreen> screens = new ArrayList<>();

    /**
     * Whether no screens are open.
     */
    public boolean isEmpty() {
        return screens.isEmpty();
    }

    /**
     * The top-most screen, or null if the stack is empty.
     */
    public GuiScreen getTop() {
        return screens.isEmpty() ? null : screens.get(screens.size() - 1);
    }

    /**
     * Opens the given screen on top of the stack. Only the new screen's
     * {@link GuiScreen#onOpen()} is called; the screen below it stays open but
     * stops receiving input until the new screen is popped.
     */
    public void push(GuiScreen screen) {
        screens.add(screen);
        screen.onOpen();
    }

    /**
     * Closes and removes the top-most screen, calling its
     * {@link GuiScreen#onClose()}. The screen below resumes receiving input.
     *
     * @return the popped screen, or null if the stack was empty
     */
    public GuiScreen pop() {
        if (screens.isEmpty()) {
            return null;
        }
        GuiScreen popped = screens.remove(screens.size() - 1);
        popped.onClose();
        return popped;
    }

    /**
     * Pops every open screen, top-most first. Each screen's
     * {@link GuiScreen#onClose()} is called exactly once, in closing order.
     */
    public void closeAll() {
        while (!screens.isEmpty()) {
            pop();
        }
    }

    /**
     * Whether any open screen pauses the game loop while open (see
     * {@link GuiScreen#isPauseGame()}). Screens covered by newer pushes still
     * count, since the game only resumes when all pausing screens are closed.
     */
    public boolean anyPausesGame() {
        for (GuiScreen screen : screens) {
            if (screen.isPauseGame()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Dispatches a mouse click to the top-most screen only.
     *
     * @return true if a screen consumed the click, false if the stack is empty
     *         or the screen did not consume it
     */
    public boolean handleMouseClick(double mx, double my, int button, boolean shiftDown) {
        GuiScreen top = getTop();
        return top != null && top.handleMouseClick(mx, my, button, shiftDown);
    }

    /**
     * Dispatches a keyboard event to the top-most screen only.
     *
     * @return true if a screen consumed the event, false if the stack is empty
     *         or the screen did not consume it
     */
    public boolean handleKey(int key, int action) {
        GuiScreen top = getTop();
        return top != null && top.handleKey(key, action);
    }

    /**
     * Dispatches a mouse scroll event to the top-most screen only.
     */
    public void handleScroll(double xoffset, double yoffset) {
        GuiScreen top = getTop();
        if (top != null) {
            top.handleScroll(xoffset, yoffset);
        }
    }

    /**
     * Dispatches a typed character to the top-most screen only.
     */
    public void handleChar(char c) {
        GuiScreen top = getTop();
        if (top != null) {
            top.handleChar(c);
        }
    }
}
