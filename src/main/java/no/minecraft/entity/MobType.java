package no.minecraft.entity;

import no.minecraft.world.BlockType;

public enum MobType {
    ZOMBIE("Zombie", 20, 0.6f, 1.95f, 2.3f, 3, new MobDrop(BlockType.ROTTEN_FLESH, 0, 2)),
    CREEPER("Creeper", 20, 0.6f, 1.7f, 2.6f, 0, new MobDrop(BlockType.GUNPOWDER, 0, 2)),
    SPIDER("Spider", 16, 1.4f, 0.9f, 3.8f, 2, new MobDrop(BlockType.STRING, 0, 2)),
    SKELETON("Skeleton", 20, 0.6f, 1.95f, 2.4f, 2, new MobDrop(BlockType.BONE, 0, 2), new MobDrop(BlockType.ARROW, 0, 2)),
    BLAZE("Blaze", 20, 0.6f, 1.8f, 2.0f, 4, new MobDrop(BlockType.BLAZE_ROD, 0, 1)),
    ENDERMAN("Enderman", 40, 0.6f, 2.9f, 4.2f, 5, new MobDrop(BlockType.ENDER_PEARL, 0, 1)),
    ENDER_DRAGON("Ender Dragon", 200, 4.0f, 2.5f, 5.5f, 8, new MobDrop(BlockType.DRAGON_EGG, 1, 1)),
    END_CRYSTAL("End Crystal", 1, 1.0f, 1.5f, 0.0f, 0),
    PIG("Gris", 10, 0.9f, 0.9f, 1.8f, 0, new MobDrop(BlockType.PORKCHOP, 1, 3)),
    COW("Ku", 10, 0.9f, 1.4f, 1.6f, 0, new MobDrop(BlockType.LEATHER, 0, 2), new MobDrop(BlockType.BEEF, 1, 3)),
    SHEEP("Sau", 8, 0.9f, 1.3f, 1.8f, 0, new MobDrop(BlockType.WOOL, 1, 1), new MobDrop(BlockType.MUTTON, 1, 2)),
    CHICKEN("Kylling", 4, 0.4f, 0.7f, 2.0f, 0, new MobDrop(BlockType.FEATHER, 0, 2), new MobDrop(BlockType.CHICKEN_MEAT, 1, 1));

    public record MobDrop(BlockType type, int min, int max) {}

    private final String name;
    private final int maxHealth;
    private final float width;
    private final float height;
    private final float moveSpeed;
    private final int attackDamage;
    private final MobDrop[] drops;

    MobType(String name, int maxHealth, float width, float height, float moveSpeed, int attackDamage, MobDrop... drops) {
        this.name = name;
        this.maxHealth = maxHealth;
        this.width = width;
        this.height = height;
        this.moveSpeed = moveSpeed;
        this.attackDamage = attackDamage;
        this.drops = drops;
    }

    public String getName() {
        return name;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getMoveSpeed() {
        return moveSpeed;
    }

    public int getAttackDamage() {
        return attackDamage;
    }

    public MobDrop[] getDrops() {
        return drops;
    }

    public boolean isPassive() {
        return attackDamage == 0 && this != END_CRYSTAL && this != CREEPER;
    }

    public boolean isHostile() {
        return attackDamage > 0 || this == CREEPER;
    }
}
