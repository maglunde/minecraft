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
