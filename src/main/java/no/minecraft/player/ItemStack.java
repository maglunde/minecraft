package no.minecraft.player;

import no.minecraft.world.BlockType;

public class ItemStack {
    private BlockType type;
    private int count;
    private int damage = 0; // 0 = undamaged, maxDurability = broken

    public ItemStack(BlockType type, int count) {
        this.type = type;
        this.count = count;
        this.damage = 0;
    }

    public ItemStack(BlockType type, int count, int damage) {
        this.type = type;
        this.count = count;
        this.damage = damage;
    }

    public BlockType getType() {
        return type;
    }

    public void setType(BlockType type) {
        this.type = type;
        this.damage = 0;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
        if (this.count <= 0) {
            this.type = BlockType.AIR;
            this.count = 0;
            this.damage = 0;
        }
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public boolean damageTool(int amount) {
        if (!type.isDamageable()) return false;
        damage += amount;
        if (damage >= type.getMaxDurability()) {
            clear();
            return true; // Item broke
        }
        return false;
    }

    public float getDurabilityRatio() {
        if (!type.isDamageable()) return 1.0f;
        int max = type.getMaxDurability();
        return Math.max(0.0f, (float) (max - damage) / (float) max);
    }

    public void add(int amount) {
        setCount(this.count + amount);
    }

    public boolean isEmpty() {
        return type == BlockType.AIR || count <= 0;
    }

    public void clear() {
        this.type = BlockType.AIR;
        this.count = 0;
        this.damage = 0;
    }
}
