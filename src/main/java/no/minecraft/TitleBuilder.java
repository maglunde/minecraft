package no.minecraft;

import no.minecraft.i18n.I18n;
import no.minecraft.player.GameMode;
import no.minecraft.player.Player;
import no.minecraft.world.World;

public class TitleBuilder {

    public static String buildTitle(World world, Player player, int fps, boolean sprintActive) {
        String status = player.getDeathFlashTimer() > 0 ? " [💀 " + I18n.get("window.title.status.death") + "]" : "";
        String sprintIndicator = sprintActive ? " [⚡ " + I18n.get("window.title.status.sprint") + "]" : "";
        String countStr = player.getSelectedBlockCount() == -1 ? "∞" : String.valueOf(player.getSelectedBlockCount());
        String modeStr = player.getGameMode().getDisplayName();
        if (player.getGameMode() == GameMode.SURVIVAL) {
            modeStr += " " + I18n.format("window.title.hp", player.getHealth());
        } else if (player.isFlying()) {
            modeStr += " [" + I18n.get("window.title.status.flying") + "]";
        }

        String timeStr = world.isNight() ? "🌙 " + I18n.get("window.title.time.night") : "☀️ " + I18n.get("window.title.time.day");
        return I18n.format("window.title.format",
                world.getSeed(),
                fps,
                timeStr,
                world.getMobs().size(),
                modeStr,
                sprintIndicator,
                player.getSelectedBlock() != null ? player.getSelectedBlock().getName() : I18n.get("window.title.none"),
                countStr,
                world.getDroppedItems().size(),
                status);
    }
}
