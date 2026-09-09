package no.minecraft.world.save;

import no.minecraft.player.GameMode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class WorldInfo {
    private String name;
    private String folderName;
    private long seed;
    private GameMode gameMode;
    private long lastPlayed;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public WorldInfo(String name, String folderName, long seed, GameMode gameMode, long lastPlayed) {
        this.name = name;
        this.folderName = folderName;
        this.seed = seed;
        this.gameMode = gameMode;
        this.lastPlayed = lastPlayed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(long lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public String getFormattedDate() {
        if (lastPlayed <= 0) return "";
        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastPlayed), ZoneId.systemDefault());
        return ldt.format(DATE_FORMATTER);
    }

    public String getModeDisplayName(boolean norwegian) {
        if (norwegian) {
            return switch (gameMode) {
                case CREATIVE -> "Kreativ modus";
                case HARDCORE -> "Hardcore-modus";
                default -> "Overlevelsesmodus";
            };
        } else {
            return switch (gameMode) {
                case CREATIVE -> "Creative Mode";
                case HARDCORE -> "Hardcore Mode";
                default -> "Survival Mode";
            };
        }
    }
}
