package no.minecraft.world;

/**
 * Deterministic 2D value noise and fractal helpers for terrain generation.
 * Pure functions of (seed, x, y) — same seed always gives the same world,
 * and different seeds give completely different shapes.
 */
public final class Noise {

    private Noise() {
    }

    /** 2D value noise in [-1, 1] with smooth (quintic) lattice interpolation. */
    public static double value2D(long seed, double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        double fx = x - ix;
        double fy = y - iy;
        double v00 = hash2D(seed, ix, iy);
        double v10 = hash2D(seed, ix + 1, iy);
        double v01 = hash2D(seed, ix, iy + 1);
        double v11 = hash2D(seed, ix + 1, iy + 1);
        double sx = smooth(fx);
        double sy = smooth(fy);
        double v0 = v00 + (v10 - v00) * sx;
        double v1 = v01 + (v11 - v01) * sx;
        return v0 + (v1 - v0) * sy;
    }

    /** Fractal Brownian motion: octaves of value2D, frequency x2 and amplitude x gain per octave. */
    public static double fbm2D(long seed, double x, double y, int octaves, double gain) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += value2D(seed + i * 1013904223L, x * freq, y * freq) * amp;
            norm += amp;
            amp *= gain;
            freq *= 2.0;
        }
        return sum / norm;
    }

    /** Ridged fBm in [0, 1] — sharp crests and valleys, good for mountain ranges. */
    public static double ridged2D(long seed, double x, double y, int octaves) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            double v = 1.0 - Math.abs(value2D(seed + i * 1013904223L, x * freq, y * freq));
            sum += v * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.0;
        }
        return sum / norm;
    }

    /** Hermite smoothstep for soft blending between terrain zones instead of hard cutoffs. */
    public static double smoothstep(double edge0, double edge1, double v) {
        double t = Math.clamp((v - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /** SplitMix64-style hash: (ix, iy, seed) -> [-1, 1]. */
    private static double hash2D(long seed, int ix, int iy) {
        long h = seed ^ ((long) ix * 0x9E3779B97F4A7C15L) ^ ((long) iy * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * (1.0 / 9007199254740992.0) * 2.0 - 1.0;
    }

    /** Quintic fade curve: 6t^5 - 15t^4 + 10t^3. */
    private static double smooth(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }
}
