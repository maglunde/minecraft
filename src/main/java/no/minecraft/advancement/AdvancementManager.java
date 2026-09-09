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
        MONSTER_HUNTER("Monster Hunter", "Monsterjeger", "Attack and destroy a dangerous monster", BlockType.BONE),
        WE_NEED_TO_GO_DEEPER("We Need to Go Deeper", "Vi ma dypere", "Build and enter a Nether Portal", BlockType.NETHERRACK),
        INTO_FIRE("Into Fire", "Inn i ilden", "Relieve a Blaze of its rod", BlockType.BLAZE_ROD),
        EYE_SPY("Eye Spy", "Oye for en portal", "Follow an Eye of Ender to a Stronghold", BlockType.EYE_OF_ENDER),
        THE_END("The End?", "Slutten?", "Enter the End Portal", BlockType.END_PORTAL_FRAME_FILLED),
        FREE_THE_END("Free the End", "Frigjor Enden", "Defeat the Ender Dragon", BlockType.DRAGON_EGG);

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

    public void setUnlocked(java.util.Collection<String> names) {
        unlocked.clear();
        unlock(Advancement.ROOT, false);
        if (names != null) {
            for (String name : names) {
                try {
                    Advancement adv = Advancement.valueOf(name);
                    unlocked.add(adv);
                } catch (IllegalArgumentException ignored) {}
            }
        }
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
