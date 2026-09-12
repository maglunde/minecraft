package no.minecraft.render;

import no.minecraft.settings.GameSettings;
import no.minecraft.testutil.LanguageTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MainMenuTabTest {

    @AfterEach
    public void restoreLanguage() {
        LanguageTestSupport.restore();
    }

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
        LanguageTestSupport.pin(GameSettings.Language.NORWEGIAN);
        assertEquals("Fredelig", MainMenu.Difficulty.PEACEFUL.getDisplayName());
        assertEquals("Vanskelig", MainMenu.Difficulty.HARD.getDisplayName());

        LanguageTestSupport.pin(GameSettings.Language.ENGLISH);
        assertEquals("Peaceful", MainMenu.Difficulty.PEACEFUL.getDisplayName());
        assertEquals("Hard", MainMenu.Difficulty.HARD.getDisplayName());
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
