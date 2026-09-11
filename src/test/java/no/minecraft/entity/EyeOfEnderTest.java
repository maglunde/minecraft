package no.minecraft.entity;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.DroppedItem;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class EyeOfEnderTest {

    @Test
    public void eyeHomesTowardStrongholdAndDropsItem() {
        World world = new World(12345L);
        Player player = new Player(world, 0.5f, 20.9f, 0.5f);
        player.setHealth(20);

        EyeOfEnder eye = new EyeOfEnder(0.5f, 21.6f, 0.5f, 16.0f, 6.0f, 0.0f);
        for (int i = 0; i < 1200 && !eye.isDead(); i++) {
            eye.update(1.0f / 60.0f, world, player);
        }

        assertTrue(eye.isDead(), "Øyet skal ankomme strongholden innen 10 sekunder");
        List<DroppedItem> drops = world.getDroppedItems();
        assertEquals(1, drops.size(), "Øyet skal droppe seg selv som item");
        assertEquals(BlockType.EYE_OF_ENDER, drops.get(0).getType());
        float dx = drops.get(0).getPosition().x - (World.STRONGHOLD_X + 0.5f);
        float dz = drops.get(0).getPosition().z - (World.STRONGHOLD_Z + 0.5f);
        assertTrue(Math.sqrt(dx * dx + dz * dz) <= 2.0f,
                "Droppen skal ligge ved strongholden (dx=" + dx + ", dz=" + dz + ")");
    }

    @Test
    public void eyeNeverDamagesPlayer() {
        World world = new World(12345L);
        Player player = new Player(world, 0.5f, 20.9f, 0.5f);
        player.setHealth(20);

        EyeOfEnder eye = new EyeOfEnder(player.getEyePosition().x, player.getEyePosition().y, player.getEyePosition().z,
                16.0f, 6.0f, 0.0f);
        for (int i = 0; i < 1200; i++) {
            eye.update(1.0f / 60.0f, world, player);
        }

        assertEquals(20, player.getHealth(), "Eye of Ender skal aldri skade spilleren");
        assertTrue(eye.isDead());
    }
}
