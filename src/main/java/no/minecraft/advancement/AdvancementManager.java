package no.minecraft.advancement;

import no.minecraft.i18n.I18n;
import no.minecraft.sound.SoundManager;
import no.minecraft.world.BlockType;

import java.util.*;

public class AdvancementManager {
    private static final AdvancementManager INSTANCE = new AdvancementManager();

    public static AdvancementManager getInstance() {
        return INSTANCE;
    }

    public enum Advancement {
        ROOT(BlockType.GRASS),
        STONE_AGE(BlockType.COBBLESTONE),
        TIME_TO_STRIKE(BlockType.WOODEN_SWORD),
        HOT_TOPIC(BlockType.FURNACE),
        MONSTER_HUNTER(BlockType.BONE),
        WE_NEED_TO_GO_DEEPER(BlockType.NETHERRACK),
        INTO_FIRE(BlockType.BLAZE_ROD),
        EYE_SPY(BlockType.EYE_OF_ENDER),
        THE_END(BlockType.END_PORTAL_FRAME_FILLED),
        FREE_THE_END(BlockType.DRAGON_EGG);

        private final BlockType icon;

        Advancement(BlockType icon) {
            this.icon = icon;
        }

        /** Translation key stem, e.g. "advancement.stone_age" (.title / .description appended). */
        public String key() {
            return "advancement." + name().toLowerCase(Locale.ROOT);
        }

        public String getTitle() {
            return I18n.get(key() + ".title");
        }

        public String getDescription() {
            return I18n.get(key() + ".description");
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
