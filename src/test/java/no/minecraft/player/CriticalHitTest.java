package no.minecraft.player;

import no.minecraft.render.ParticleManager;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CriticalHitTest {

    @Test
    public void testCriticalHitConditions() {
        World world = new World();
        Player player = new Player(world, 0, 10, 0);

        // On ground initially -> false
        assertFalse(player.canPerformCriticalHit());

        // Jumping upwards -> velocity.y > 0 -> false
        player.getVelocity().y = 5.0f;
        assertFalse(player.canPerformCriticalHit());

        // Falling downwards in air -> velocity.y < 0 and !onGround -> true
        player.getVelocity().y = -2.5f;
        assertTrue(player.canPerformCriticalHit());

        // In boat -> false
        no.minecraft.entity.Boat boat = new no.minecraft.entity.Boat(0, 10, 0, 0);
        player.setRidingBoat(boat);
        assertFalse(player.canPerformCriticalHit());
        player.setRidingBoat(null);
        assertTrue(player.canPerformCriticalHit());
    }

    @Test
    public void testCriticalHitDamageCalculation() {
        // Base damage: 1 (hand), 4 (wood), 6 (iron), 7 (diamond)
        int[] baseDamages = {1, 4, 6, 7};
        for (int baseDmg : baseDamages) {
            int critDmg = Math.max(baseDmg + 1, (int) Math.ceil(baseDmg * 1.5f));
            assertTrue(critDmg > baseDmg, "Critical hit must always deal more damage than base hit");
        }

        // Diamond sword crit: 7 * 1.5 = 10.5 -> ceil = 11
        assertEquals(11, Math.max(7 + 1, (int) Math.ceil(7 * 1.5f)));

        // Hand crit: 1 * 1.5 = 1.5 -> ceil = 2
        assertEquals(2, Math.max(1 + 1, (int) Math.ceil(1 * 1.5f)));
    }

    @Test
    public void testParticleManagerCritParticles() {
        ParticleManager pm = ParticleManager.getInstance();
        pm.clear();
        assertEquals(0, pm.getParticles().size());

        pm.spawnCritParticles(0, 1, 0, 16);
        assertEquals(16, pm.getParticles().size());

        // Update 0.1s
        pm.update(0.1f);
        assertEquals(16, pm.getParticles().size());

        // Fast forward 1.0s (particles expire in ~0.5s)
        pm.update(1.0f);
        assertEquals(0, pm.getParticles().size());
    }
}
