package no.minecraft.player;

import no.minecraft.world.BlockType;

public class Inventory {
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_INVENTORY_SIZE = 27;
    public static final int TOTAL_SLOTS = HOTBAR_SIZE + MAIN_INVENTORY_SIZE; // 36 slots
    public static final int MAX_STACK_SIZE = 64;

    private final ItemStack[] slots = new ItemStack[TOTAL_SLOTS];

    public Inventory() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slots[i] = new ItemStack(BlockType.AIR, 0);
        }
    }

    public void clear() {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slots[i].setType(BlockType.AIR);
            slots[i].setCount(0);
        }
    }

    public int getSize() {
        return TOTAL_SLOTS;
    }

    public ItemStack getSlot(int index) {
        if (index < 0 || index >= TOTAL_SLOTS) {
            return slots[0];
        }
        return slots[index];
    }

    public int getItemCount(BlockType type) {
        if (type == BlockType.AIR) return 0;
        int total = 0;
        for (ItemStack slot : slots) {
            if (slot.getType() == type) {
                total += slot.getCount();
            }
        }
        return total;
    }

    public boolean hasSpaceFor(BlockType type, int amount) {
        if (type == BlockType.AIR || amount <= 0) return true;
        int remaining = amount;
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) {
                remaining -= MAX_STACK_SIZE;
            } else if (slot.getType() == type) {
                remaining -= (MAX_STACK_SIZE - slot.getCount());
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    public boolean addItem(BlockType type, int amount) {
        if (type == BlockType.AIR || amount <= 0) return false;
        int remaining = amount;

        // 1. Fill existing matching stacks first (up to MAX_STACK_SIZE)
        for (ItemStack slot : slots) {
            if (slot.getType() == type && slot.getCount() < MAX_STACK_SIZE) {
                int space = MAX_STACK_SIZE - slot.getCount();
                int add = Math.min(remaining, space);
                slot.add(add);
                remaining -= add;
                if (remaining <= 0) return true;
            }
        }

        // 2. Put into first available empty slots
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) {
                int add = Math.min(remaining, MAX_STACK_SIZE);
                slot.setType(type);
                slot.setCount(add);
                remaining -= add;
                if (remaining <= 0) return true;
            }
        }

        return remaining < amount;
    }

    public boolean removeItem(BlockType type, int amount) {
        if (type == BlockType.AIR || amount <= 0) return false;
        if (getItemCount(type) < amount) return false;

        int remaining = amount;
        for (ItemStack slot : slots) {
            if (slot.getType() == type) {
                int take = Math.min(remaining, slot.getCount());
                slot.setCount(slot.getCount() - take);
                remaining -= take;
                if (remaining <= 0) break;
            }
        }
        return true;
    }

    public void swapSlots(int from, int to) {
        if (from < 0 || from >= TOTAL_SLOTS || to < 0 || to >= TOTAL_SLOTS) return;
        ItemStack temp = new ItemStack(slots[from].getType(), slots[from].getCount());
        slots[from].setType(slots[to].getType());
        slots[from].setCount(slots[to].getCount());
        slots[to].setType(temp.getType());
        slots[to].setCount(temp.getCount());
    }
}
