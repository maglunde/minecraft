package no.minecraft.render;

import no.minecraft.entity.Mob;
import no.minecraft.entity.MobType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MobAnimationTest {

    @Test
    public void testLimbSwingDeterministic() {
        Mob mob = new Mob(MobType.ZOMBIE, 0, 0, 0);

        // Advance walkTime
        mob.setWalkTime(1.0f);
        assertEquals(1.0f, mob.getWalkTime(), 0.001f);

        float angle1 = (float) Math.sin(mob.getWalkTime() * 4.5f);
        mob.setWalkTime(1.0f);
        float angle2 = (float) Math.sin(mob.getWalkTime() * 4.5f);

        assertEquals(angle1, angle2, 0.0001f, "Animasjonsvinkler skal være deterministiske");
    }

    @Test
    public void testCreeperFuseScaling() {
        Mob creeper = new Mob(MobType.CREEPER, 0, 0, 0);
        creeper.setIgnited(true);
        creeper.setFuseTime(0.75f);

        float ratio = creeper.getFuseRatio();
        assertEquals(0.5f, ratio, 0.01f);

        float scale = 1.0f + ratio * 0.22f;
        assertTrue(scale > 1.0f && scale <= 1.25f, "Creeper skal ekspandere under antenning");
    }
}
