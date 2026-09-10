package no.minecraft.world;

import no.minecraft.player.Inventory;
import no.minecraft.player.ItemStack;

public class ChestData {
    public static final int CHEST_SIZE = 27;

    private final int x, y, z;
    private final ItemStack[] items;

    public ChestData(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.items = new ItemStack[CHEST_SIZE];
        for (int i = 0; i < CHEST_SIZE; i++) {
            this.items[i] = new ItemStack(BlockType.AIR, 0);
        }
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public int getSize() { return CHEST_SIZE; }

    public ItemStack getSlot(int index) {
        if (index >= 0 && index < CHEST_SIZE) {
            return items[index];
        }
        return null;
    }

    public void setSlot(int index, ItemStack stack) {
        if (index >= 0 && index < CHEST_SIZE) {
            if (stack == null) {
                items[index].clear();
            } else {
                items[index].setType(stack.getType());
                items[index].setCount(stack.getCount());
            }
        }
    }

    public ItemStack[] getItems() {
        return items;
    }

    public boolean isEmpty() {
        for (ItemStack is : items) {
            if (!is.isEmpty()) return false;
        }
        return true;
    }

    public void clear() {
        for (ItemStack is : items) {
            is.clear();
        }
    }

    public int addItem(BlockType type, int count) {
        if (type == null || type == BlockType.AIR || count <= 0) return 0;
        int remaining = count;

        // 1. Merge into existing stacks of same type
        for (ItemStack slot : items) {
            if (!slot.isEmpty() && slot.getType() == type) {
                int canAdd = Math.min(remaining, Inventory.MAX_STACK_SIZE - slot.getCount());
                if (canAdd > 0) {
                    slot.add(canAdd);
                    remaining -= canAdd;
                    if (remaining <= 0) return 0;
                }
            }
        }

        // 2. Place in first empty slot
        for (ItemStack slot : items) {
            if (slot.isEmpty()) {
                int addCount = Math.min(remaining, Inventory.MAX_STACK_SIZE);
                slot.setType(type);
                slot.setCount(addCount);
                remaining -= addCount;
                if (remaining <= 0) return 0;
            }
        }

        return remaining;
    }
}
