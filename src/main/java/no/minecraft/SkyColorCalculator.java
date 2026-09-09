package no.minecraft;

import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import no.minecraft.world.World;
import org.joml.Vector3f;

public class SkyColorCalculator {

    // Calculate dynamic day/night sky color & lighting
    public static void calculateSkyColor(World world, Player player, Vector3f out) {
        float sunLight = world.getSunLightLevel();
        Vector3f daySky = new Vector3f(0.53f, 0.81f, 0.98f);    // Minecraft azure sky
        Vector3f nightSky = new Vector3f(0.04f, 0.05f, 0.10f);  // Deep starry night sky
        Vector3f sunsetColor = new Vector3f(0.85f, 0.42f, 0.22f); // Golden sunset orange

        if (world.getCurrentDimension() == no.minecraft.world.Dimension.NETHER) {
            out.set(world.getCurrentDimension().getSkyR(), world.getCurrentDimension().getSkyG(), world.getCurrentDimension().getSkyB());
        } else if (world.getCurrentDimension() == no.minecraft.world.Dimension.THE_END) {
            out.set(world.getCurrentDimension().getSkyR(), world.getCurrentDimension().getSkyG(), world.getCurrentDimension().getSkyB());
        } else {
            // Blend day and night sky
            out.set(
                    nightSky.x + (daySky.x - nightSky.x) * sunLight,
                    nightSky.y + (daySky.y - nightSky.y) * sunLight,
                    nightSky.z + (daySky.z - nightSky.z) * sunLight
            );

            // Add warm sunset / sunrise tint when sun is on the horizon
            float sunsetFactor = 1.0f - Math.abs(sunLight - 0.5f) * 2.0f;
            if (sunsetFactor > 0.0f) {
                out.lerp(sunsetColor, sunsetFactor * 0.45f);
            }

            // Underwater atmosphere if submerged
            int hx = (int) Math.floor(player.getPosition().x);
            int hy = (int) Math.floor(player.getPosition().y + Player.EYE_HEIGHT);
            int hz = (int) Math.floor(player.getPosition().z);
            if (world.getBlock(hx, hy, hz) == BlockType.WATER) {
                out.set(0.06f, 0.22f, 0.55f);
            }
        }
    }
}
