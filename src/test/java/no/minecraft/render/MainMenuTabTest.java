package no.minecraft.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MainMenuTabTest {

    @Test
    public void testDifficultyCycling() {
        MainMenu.Difficulty diff = MainMenu.Difficulty.PEACEFUL;
        diff = diff.next();
        assertEquals(MainMenu.Difficulty.EASY, diff);
        diff = diff.next();
        assertEquals(MainMenu.Difficulty.NORMAL, diff);
        diff = diff.next();
        assertEquals(MainMenu.Difficulty.HARD, diff);
        diff = diff.next();
        assertEquals(MainMenu.Difficulty.PEACEFUL, diff);
    }

    @Test
    public void testDifficultyDisplayName() {
        assertEquals("Fredelig", MainMenu.Difficulty.PEACEFUL.getDisplayName(true));
        assertEquals("Peaceful", MainMenu.Difficulty.PEACEFUL.getDisplayName(false));
        assertEquals("Vanskelig", MainMenu.Difficulty.HARD.getDisplayName(true));
        assertEquals("Hard", MainMenu.Difficulty.HARD.getDisplayName(false));
    }

    @Test
    public void testTabsAndElements() {
        assertEquals(3, MainMenu.Tab.values().length);
        assertNotNull(MainMenu.FocusedElement.GAME_DIFFICULTY);
        assertNotNull(MainMenu.FocusedElement.GAME_NAME);
        assertNotNull(MainMenu.FocusedElement.MORE_SEED);
        assertNotNull(MainMenu.FocusedElement.MORE_BONUSCHEST);
    }
}
