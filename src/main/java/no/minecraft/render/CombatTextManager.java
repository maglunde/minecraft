package no.minecraft.render;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class CombatTextManager {
    private static final CombatTextManager INSTANCE = new CombatTextManager();
    private static final Random RANDOM = new Random();

    public static class CombatText {
        public final Vector3f worldPos;
        public final float hearts;
        public final boolean isCrit;
        public float age = 0.0f;
        public final float maxLifetime = 1.2f;
        public final float driftX;
        public final float driftZ;

        public CombatText(float x, float y, float z, float hearts, boolean isCrit) {
            this.worldPos = new Vector3f(x, y, z);
            this.hearts = hearts;
            this.isCrit = isCrit;
            this.driftX = (RANDOM.nextFloat() - 0.5f) * 0.35f;
            this.driftZ = (RANDOM.nextFloat() - 0.5f) * 0.35f;
        }
    }

    private final List<CombatText> texts = new ArrayList<>();

    public static CombatTextManager getInstance() {
        return INSTANCE;
    }

    private CombatTextManager() {
    }

    public void add(float x, float y, float z, float hearts) {
        add(x, y, z, hearts, false);
    }

    public void add(float x, float y, float z, float hearts, boolean isCrit) {
        texts.add(new CombatText(x, y, z, hearts, isCrit));
    }

    public void update(float dt) {
        Iterator<CombatText> it = texts.iterator();
        while (it.hasNext()) {
            CombatText ct = it.next();
            ct.age += dt;
            if (ct.age >= ct.maxLifetime) {
                it.remove();
            }
        }
    }

    public List<CombatText> getTexts() {
        return texts;
    }
}
