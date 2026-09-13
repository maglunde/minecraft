package no.minecraft.world.gen.noise;

import java.util.Random;

/**
 * Fractal Brownian Motion (fBm) over flere oktaver med konfigurerbar
 * lacunarity (frekvensøkning) og persistence (amplitudefall).
 */
public class OctaveNoiseSampler {
    private final PerlinNoiseSampler[] samplers;
    private final double persistence;
    private final double lacunarity;

    public OctaveNoiseSampler(long seed, int octaves, double persistence, double lacunarity) {
        this.samplers = new PerlinNoiseSampler[octaves];
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        Random r = new Random(seed);
        for (int i = 0; i < octaves; i++) {
            samplers[i] = new PerlinNoiseSampler(r.nextLong());
        }
    }

    public double sample3D(double x, double y, double z) {
        double total = 0.0;
        double freq = 1.0;
        double amp = 1.0;
        double maxAmp = 0.0;

        for (PerlinNoiseSampler sampler : samplers) {
            total += sampler.sample(x * freq, y * freq, z * freq) * amp;
            maxAmp += amp;
            freq *= lacunarity;
            amp *= persistence;
        }
        return total / maxAmp;
    }

    public double sample2D(double x, double z) {
        return sample3D(x, 0.0, z);
    }
}
