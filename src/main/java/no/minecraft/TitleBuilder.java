package no.minecraft;

import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.World;

public class TitleBuilder {

    public static String buildTitle(World world, Player player, int fps, boolean sprintActive) {
        String status = player.getDeathFlashTimer() > 0 ? " [💀 DU DØDE - Falt ut av verden!]" : "";
        String sprintIndicator = sprintActive ? " [⚡ SPRINT]" : "";
        String countStr = player.getSelectedBlockCount() == -1 ? "∞" : String.valueOf(player.getSelectedBlockCount());
        String modeStr = player.getGameMode().getDisplayName();
        if (player.getGameMode() == GameMode.SURVIVAL) {
            modeStr += String.format(" (HP: %d/20)", player.getHealth());
        } else if (player.isFlying()) {
            modeStr += " [Flyvende]";
        }

        String timeStr = world.isNight() ? "🌙 Natt" : "☀️ Dag";
        return String.format("Minecraft Java Clone | Seed: %d | FPS: %d | Tid: %s | Mobs: %d | Modus: %s%s | Valgt: %s (x%s) | Drops: %d | E: Crafting%s",
                world.getSeed(),
                fps,
                timeStr,
                world.getMobs().size(),
                modeStr,
                sprintIndicator,
                player.getSelectedBlock() != null ? player.getSelectedBlock().getName() : "Ingen",
                countStr,
                world.getDroppedItems().size(),
                status);
    }
}
