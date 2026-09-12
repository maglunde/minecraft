package no.minecraft.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenStackTest {

    /**
     * Headless test double that records every lifecycle and input call it
     * receives into a shared event log, prefixed with the screen's name.
     */
    private static class FakeScreen implements GuiScreen {
        private final String name;
        private final boolean pauses;
        private final List<String> events;
        private boolean consumeClick = false;
        private boolean consumeKey = false;

        FakeScreen(String name, boolean pauses, List<String> events) {
            this.name = name;
            this.pauses = pauses;
            this.events = events;
        }

        FakeScreen consumeClick() {
            this.consumeClick = true;
            return this;
        }

        FakeScreen consumeKey() {
            this.consumeKey = true;
            return this;
        }

        @Override
        public void onOpen() {
            events.add(name + ":open");
        }

        @Override
        public void onClose() {
            events.add(name + ":close");
        }

        @Override
        public boolean handleMouseClick(double mx, double my, int button, boolean shiftDown) {
            events.add(name + ":click(" + mx + "," + my + "," + button + "," + shiftDown + ")");
            return consumeClick;
        }

        @Override
        public boolean handleKey(int key, int action) {
            events.add(name + ":key(" + key + "," + action + ")");
            return consumeKey;
        }

        @Override
        public void handleScroll(double xoffset, double yoffset) {
            events.add(name + ":scroll(" + xoffset + "," + yoffset + ")");
        }

        @Override
        public void handleChar(char c) {
            events.add(name + ":char(" + c + ")");
        }

        @Override
        public void render(List<Float> geom, List<Float> texGeom, int width, int height) {
        }

        @Override
        public boolean isPauseGame() {
            return pauses;
        }
    }

    @Test
    public void testPushOpensOnlyTheNewScreen() {
        ScreenStack stack = new ScreenStack();
        List<String> events = new ArrayList<>();
        FakeScreen menu = new FakeScreen("menu", false, events);
        FakeScreen inventory = new FakeScreen("inventory", false, events);

        stack.push(menu);
        stack.push(inventory);

        // Covering a screen does not close it: no "menu:close" event.
        assertEquals(List.of("menu:open", "inventory:open"), events);
        assertSame(inventory, stack.getTop());
        assertFalse(stack.isEmpty());
    }

    @Test
    public void testPopClosesOnlyThePoppedScreenAndReturnsIt() {
        ScreenStack stack = new ScreenStack();
        List<String> events = new ArrayList<>();
        FakeScreen menu = new FakeScreen("menu", false, events);
        FakeScreen inventory = new FakeScreen("inventory", false, events);
        stack.push(menu);
        stack.push(inventory);

        assertSame(inventory, stack.pop());
        assertSame(menu, stack.getTop());
        assertEquals(List.of("menu:open", "inventory:open", "inventory:close"), events);

        // The screen below resumes being the top; it was never closed and
        // closing it now works as usual.
        assertSame(menu, stack.pop());
        assertTrue(stack.isEmpty());
        assertEquals(List.of("menu:open", "inventory:open", "inventory:close", "menu:close"), events);
    }

    @Test
    public void testEmptyStack() {
        ScreenStack stack = new ScreenStack();

        assertTrue(stack.isEmpty());
        assertNull(stack.getTop());
        assertNull(stack.pop());
        assertFalse(stack.handleMouseClick(1.0, 2.0, 0, false));
        assertFalse(stack.handleKey(1, 1));
        // Void input on an empty stack must be a safe no-op.
        stack.handleScroll(1.0, -1.0);
        stack.handleChar('a');
        stack.closeAll();
        assertTrue(stack.isEmpty());
    }

    @Test
    public void testOnlyTopScreenReceivesInput() {
        ScreenStack stack = new ScreenStack();
        List<String> events = new ArrayList<>();
        FakeScreen menu = new FakeScreen("menu", false, events);
        FakeScreen inventory = new FakeScreen("inventory", false, events);
        stack.push(menu);
        stack.push(inventory);
        events.clear();

        stack.handleMouseClick(10.0, 20.0, 0, true);
        stack.handleKey(42, 1);
        stack.handleScroll(0.5, 1.5);
        stack.handleChar('x');
        assertEquals(List.of(
                "inventory:click(10.0,20.0,0,true)",
                "inventory:key(42,1)",
                "inventory:scroll(0.5,1.5)",
                "inventory:char(x)"
        ), events);

        // After popping the top, the screen below receives input again.
        stack.pop();
        events.clear();
        stack.handleMouseClick(1.0, 1.0, 1, false);
        assertEquals(List.of("menu:click(1.0,1.0,1,false)"), events);
    }

    @Test
    public void testConsumedInputIsReportedToTheCaller() {
        ScreenStack stack = new ScreenStack();
        List<String> events = new ArrayList<>();
        stack.push(new FakeScreen("passive", false, events));
        stack.push(new FakeScreen("consuming", false, events).consumeClick().consumeKey());

        assertTrue(stack.handleMouseClick(0.0, 0.0, 0, false));
        assertTrue(stack.handleKey(1, 1));

        stack.pop();
        // The screen below consumes nothing, so the stack reports false.
        assertFalse(stack.handleMouseClick(0.0, 0.0, 0, false));
        assertFalse(stack.handleKey(1, 1));
    }

    @Test
    public void testAnyPausesGame() {
        ScreenStack stack = new ScreenStack();
        assertFalse(stack.anyPausesGame());

        stack.push(new FakeScreen("options", true, new ArrayList<>()));
        stack.push(new FakeScreen("chat", false, new ArrayList<>()));
        // A pausing screen below a non-pausing top still pauses the game.
        assertTrue(stack.anyPausesGame());

        stack.pop();
        assertTrue(stack.anyPausesGame());

        stack.pop();
        assertFalse(stack.anyPausesGame());
    }

    @Test
    public void testCloseAllClosesTopMostFirst() {
        ScreenStack stack = new ScreenStack();
        List<String> events = new ArrayList<>();
        stack.push(new FakeScreen("a", false, events));
        stack.push(new FakeScreen("b", false, events));
        stack.push(new FakeScreen("c", false, events));

        stack.closeAll();

        assertEquals(List.of("a:open", "b:open", "c:open", "c:close", "b:close", "a:close"), events);
        assertTrue(stack.isEmpty());
        assertNull(stack.getTop());
    }
}
