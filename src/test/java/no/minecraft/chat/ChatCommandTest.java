package no.minecraft.chat;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChatCommandTest {

    private World world;
    private Player player;
    private ChatManager chat;

    @BeforeEach
    public void setUp() {
        world = new World(12345L);
        player = new Player(world, 0, 22, 0);
        chat = new ChatManager();
    }

    @Test
    public void testHealRestoresFullHealth() {
        player.damage(10);
        chat.executeCommand("heal", world, player);
        assertEquals(Player.MAX_HEALTH, player.getHealth());
    }

    @Test
    public void testGiveAddsItem() {
        chat.executeCommand("give diamond 5", world, player);
        assertEquals(5, player.getInventory().getItemCount(BlockType.DIAMOND));
    }

    @Test
    public void testGiveUnknownItemErrors() {
        chat.executeCommand("give tulleblokk 1", world, player);
        assertFalse(chat.getMessages().isEmpty());
        assertTrue(chat.getMessages().get(chat.getMessages().size() - 1).getText().contains("Ukjent item"));
    }

    @Test
    public void testTimeSetDay() {
        chat.executeCommand("time set day", world, player);
        assertEquals(0.10f, world.getDayFraction(), 0.001f);
    }

    @Test
    public void testUnknownCommandErrors() {
        chat.executeCommand("xyzzy", world, player);
        assertFalse(chat.getMessages().isEmpty());
        assertTrue(chat.getMessages().get(chat.getMessages().size() - 1).getText().contains("Ukjent kommando"));
    }

    @Test
    public void testKillKillsPlayer() {
        chat.executeCommand("kill", world, player);
        // Player dies and respawns at spawn with full health; the death flash proves the kill
        assertTrue(player.getDeathFlashTimer() > 0.0f, "Kill må utløse døds-respawn");
        assertEquals(Player.MAX_HEALTH, player.getHealth());
    }
}
