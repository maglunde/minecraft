package no.minecraft.advancement;

import no.minecraft.sound.SoundManager;
import no.minecraft.world.BlockType;

import java.util.*;

public class AdvancementManager {
    private static final AdvancementManager INSTANCE = new AdvancementManager();

    public static AdvancementManager getInstance() {
        return INSTANCE;
    }

    public enum Advancement {
        ROOT("Minecraft", "Gruppas forste steg", "The heart and story of the game", BlockType.GRASS),
        STONE_AGE("Stone Age", "Steinlalder", "Mine stone with your new pickaxe", BlockType.COBBLESTONE),
        TIME_TO_STRIKE("Time to Strike!", "Tid for strid!", "Craft a sword to defend yourself", BlockType.WOODEN_SWORD),
        HOT_TOPIC("Hot Topic", "Et hett tema", "Construct a furnace out of cobblestone", BlockType.FURNACE),
        MONSTER_HUNTER("Monster Hunter", "Monsterjeger", "Attack and destroy a dangerous monster", BlockType.BONE);

        private final String englishTitle;
        private final String norwegianTitle;
        private final String description;
        private final BlockType icon;

        Advancement(String englishTitle, String norwegianTitle, String description, BlockType icon) {
            this.englishTitle = englishTitle;
            this.norwegianTitle = norwegianTitle;
            this.description = description;
            this.icon = icon;
        }

        public String getTitle(boolean norwegian) {
            return norwegian ? norwegianTitle : englishTitle;
        }

        public String getDescription() {
            return description;
        }

        public BlockType getIcon() {
            return icon;
        }
    }

    private final Set<Advancement> unlocked = new HashSet<>();
    private Advancement recentUnlock = null;
    private float popupTimer = 0.0f;

    private AdvancementManager() {
        // Unlock root by default on starting the game
        unlock(Advancement.ROOT, false);
    }

    public void unlock(Advancement adv) {
        unlock(adv, true);
    }

    public void unlock(Advancement adv, boolean playSound) {
        if (!unlocked.contains(adv)) {
            unlocked.add(adv);
            recentUnlock = adv;
            popupTimer = 4.0f;
            if (playSound) {
                SoundManager.getInstance().play("pop");
            }
        }
    }

    public boolean isUnlocked(Advancement adv) {
        return unlocked.contains(adv);
    }

    public Set<Advancement> getUnlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public void update(float dt) {
        if (popupTimer > 0.0f) {
            popupTimer -= dt;
            if (popupTimer <= 0.0f) {
                recentUnlock = null;
            }
        }
    }

    public Advancement getRecentUnlock() {
        return recentUnlock;
    }

    public float getPopupTimer() {
        return popupTimer;
    }

    public void reset() {
        unlocked.clear();
        recentUnlock = null;
        popupTimer = 0.0f;
        unlock(Advancement.ROOT, false);
    }
}
