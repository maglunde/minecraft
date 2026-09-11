package no.minecraft.entity;

import no.minecraft.world.BlockType;
import no.minecraft.world.DroppedItem;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MobDropTest {

    @Test
    public void dropTableMatchesVanilla() {
        assertEquals("Zombie", MobType.ZOMBIE.getName());
        assertDrops(MobType.ZOMBIE, new BlockType[]{BlockType.ROTTEN_FLESH});
        assertDrops(MobType.CREEPER, new BlockType[]{BlockType.GUNPOWDER});
        assertDrops(MobType.SPIDER, new BlockType[]{BlockType.STRING});
        assertDrops(MobType.SKELETON, new BlockType[]{BlockType.BONE, BlockType.ARROW});
        assertDrops(MobType.BLAZE, new BlockType[]{BlockType.BLAZE_ROD});
        assertDrops(MobType.ENDERMAN, new BlockType[]{BlockType.ENDER_PEARL});
        assertDrops(MobType.ENDER_DRAGON, new BlockType[]{BlockType.DRAGON_EGG});
        assertDrops(MobType.END_CRYSTAL, new BlockType[]{});
        assertDrops(MobType.PIG, new BlockType[]{BlockType.PORKCHOP});
        assertDrops(MobType.COW, new BlockType[]{BlockType.LEATHER, BlockType.BEEF});
        // The bug: sheep used to drop feathers
        assertDrops(MobType.SHEEP, new BlockType[]{BlockType.WOOL, BlockType.MUTTON});
        assertDrops(MobType.CHICKEN, new BlockType[]{BlockType.FEATHER, BlockType.CHICKEN_MEAT});
    }

    private void assertDrops(MobType type, BlockType[] expectedTypes) {
        MobType.MobDrop[] drops = type.getDrops();
        assertEquals(expectedTypes.length, drops.length, type + " skal ha riktig antall drop-typer");
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals(expectedTypes[i], drops[i].type(), type + " dropper feil item på plass " + i);
            assertTrue(drops[i].min() >= 0 && drops[i].min() <= drops[i].max(),
                    type + " har ugyldig range " + drops[i].min() + ".." + drops[i].max());
        }
    }

    @Test
    public void mobDropsRespectRanges() {
        // Sheep killed 50 times must never drop feathers and always stays within declared ranges
        for (int i = 0; i < 50; i++) {
            World world = new World(12345L + i);
            world.spawnMob(MobType.SHEEP, 5.0f, 22.0f, 5.0f);
            Mob sheep = world.getMobs().get(0);
            sheep.takeDamage(1000, 0, 0, world);
            assertTrue(sheep.isDead());

            List<DroppedItem> drops = world.getDroppedItems();
            assertFalse(drops.isEmpty(), "Sau skal droppe noe");
            for (DroppedItem drop : drops) {
                assertNotEquals(BlockType.FEATHER, drop.getType(), "Sau skal IKKE droppe fjær");
                boolean typeOk = drop.getType() == BlockType.WOOL || drop.getType() == BlockType.MUTTON;
                assertTrue(typeOk, "Sau droppet uventet item: " + drop.getType());
            }
            for (MobType.MobDrop declared : MobType.SHEEP.getDrops()) {
                long spawned = drops.stream().filter(d -> d.getType() == declared.type()).count();
                long total = spawned > 0 ? drops.stream().filter(d -> d.getType() == declared.type())
                        .mapToInt(DroppedItem::getCount).sum() : 0;
                if (spawned > 0) {
                    assertTrue(total >= declared.min() && total <= declared.max(),
                            declared.type() + " utenfor range: " + total);
                }
            }
        }
    }

    @Test
    public void endCrystalDropsNothing() {
        World world = new World(12345L);
        world.spawnMob(MobType.END_CRYSTAL, 5.0f, 22.0f, 5.0f);
        Mob crystal = world.getMobs().get(0);
        crystal.takeDamage(1000, 0, 0, world);
        assertTrue(world.getDroppedItems().isEmpty(), "End Crystal skal ikke droppe items");
    }
}
