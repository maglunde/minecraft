package no.minecraft.render;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ParticleManager {
    private static final ParticleManager INSTANCE = new ParticleManager();
    private static final Random RANDOM = new Random();

    public static class Particle {
        public final Vector3f pos;
        public final Vector3f vel;
        public float age = 0.0f;
        public final float maxLifetime;
        public final float r, g, b;
        public final float size;

        public Particle(float x, float y, float z, float vx, float vy, float vz, float r, float g, float b, float size, float lifetime) {
            this.pos = new Vector3f(x, y, z);
            this.vel = new Vector3f(vx, vy, vz);
            this.r = r;
            this.g = g;
            this.b = b;
            this.size = size;
            this.maxLifetime = lifetime;
        }
    }

    private final List<Particle> particles = new ArrayList<>();

    public static ParticleManager getInstance() {
        return INSTANCE;
    }

    private ParticleManager() {
    }

    public void spawnCritParticles(float x, float y, float z, int count) {
        for (int i = 0; i < count; i++) {
            // Outward burst in sphere with bias upwards
            float theta = RANDOM.nextFloat() * ((float) Math.PI * 2.0f);
            float phi = RANDOM.nextFloat() * (float) Math.PI;
            float speed = 1.8f + RANDOM.nextFloat() * 2.2f;

            float vx = (float) (Math.sin(phi) * Math.cos(theta)) * speed;
            float vy = (float) Math.abs(Math.cos(phi)) * speed * 0.85f + 0.5f;
            float vz = (float) (Math.sin(phi) * Math.sin(theta)) * speed;

            // Minecraft crit particles have cyan/blue and bronze/gold sparks
            float r, g, b;
            if (RANDOM.nextBoolean()) {
                // Cyan / magic crit
                r = 0.35f + RANDOM.nextFloat() * 0.25f;
                g = 0.85f + RANDOM.nextFloat() * 0.15f;
                b = 0.95f;
            } else {
                // Golden spark
                r = 0.98f;
                g = 0.80f + RANDOM.nextFloat() * 0.18f;
                b = 0.25f + RANDOM.nextFloat() * 0.20f;
            }

            float size = 0.07f + RANDOM.nextFloat() * 0.05f;
            float lifetime = 0.40f + RANDOM.nextFloat() * 0.30f;
            particles.add(new Particle(x, y, z, vx, vy, vz, r, g, b, size, lifetime));
        }
    }

    public void spawnEatingParticles(float x, float y, float z, no.minecraft.world.BlockType food, int count) {
        float baseR = 0.8f, baseG = 0.5f, baseB = 0.2f;
        if (food != null) {
            switch (food) {
                case APPLE -> { baseR = 0.9f; baseG = 0.15f; baseB = 0.15f; }
                case BREAD -> { baseR = 0.85f; baseG = 0.60f; baseB = 0.25f; }
                case PORKCHOP -> { baseR = 0.95f; baseG = 0.65f; baseB = 0.65f; }
                case COOKED_PORKCHOP, COOKED_BEEF -> { baseR = 0.55f; baseG = 0.30f; baseB = 0.15f; }
                case BEEF -> { baseR = 0.75f; baseG = 0.20f; baseB = 0.20f; }
                case CHICKEN_MEAT -> { baseR = 0.90f; baseG = 0.75f; baseB = 0.70f; }
                case COOKED_CHICKEN -> { baseR = 0.75f; baseG = 0.55f; baseB = 0.25f; }
                case ROTTEN_FLESH -> { baseR = 0.40f; baseG = 0.45f; baseB = 0.20f; }
                default -> {}
            }
        }
        for (int i = 0; i < count; i++) {
            float vx = (RANDOM.nextFloat() - 0.5f) * 1.0f;
            float vy = RANDOM.nextFloat() * 0.8f + 0.2f;
            float vz = (RANDOM.nextFloat() - 0.5f) * 1.0f;

            float r = Math.clamp(baseR + (RANDOM.nextFloat() - 0.5f) * 0.15f, 0.0f, 1.0f);
            float g = Math.clamp(baseG + (RANDOM.nextFloat() - 0.5f) * 0.15f, 0.0f, 1.0f);
            float b = Math.clamp(baseB + (RANDOM.nextFloat() - 0.5f) * 0.15f, 0.0f, 1.0f);

            float size = 0.04f + RANDOM.nextFloat() * 0.03f;
            float lifetime = 0.25f + RANDOM.nextFloat() * 0.20f;
            particles.add(new Particle(x + (RANDOM.nextFloat() - 0.5f) * 0.2f,
                                       y + (RANDOM.nextFloat() - 0.5f) * 0.1f,
                                       z + (RANDOM.nextFloat() - 0.5f) * 0.2f,
                                       vx, vy, vz, r, g, b, size, lifetime));
        }
    }

    public void update(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.age += dt;
            if (p.age >= p.maxLifetime) {
                it.remove();
                continue;
            }
            p.pos.add(p.vel.x * dt, p.vel.y * dt, p.vel.z * dt);
            p.vel.y -= 7.5f * dt; // Gravity
            p.vel.x *= (float) Math.pow(0.5, dt); // Air drag
            p.vel.z *= (float) Math.pow(0.5, dt);
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }

    public void clear() {
        particles.clear();
    }
}
