package no.minecraft.entity;

import no.minecraft.world.BlockType;

public enum MobType {
    ZOMBIE("Zombie", 20, 0.6f, 1.95f, 2.3f, 3, BlockType.ROTTEN_FLESH),
    CREEPER("Creeper", 20, 0.6f, 1.7f, 2.6f, 0, BlockType.GUNPOWDER),
    SPIDER("Spider", 16, 1.4f, 0.9f, 3.8f, 2, BlockType.STRING),
    SKELETON("Skeleton", 20, 0.6f, 1.95f, 2.4f, 2, BlockType.BONE);

    private final String name;
    private final int maxHealth;
    private final float width;
    private final float height;
    private final float moveSpeed;
    private final int attackDamage;
    private final BlockType dropItem;

    MobType(String name, int maxHealth, float width, float height, float moveSpeed, int attackDamage, BlockType dropItem) {
        this.name = name;
        this.maxHealth = maxHealth;
        this.width = width;
        this.height = height;
        this.moveSpeed = moveSpeed;
        this.attackDamage = attackDamage;
        this.dropItem = dropItem;
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

    public BlockType getDropItem() {
        return dropItem;
    }
}
