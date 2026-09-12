package no.minecraft.world;

import no.minecraft.player.Inventory;
import no.minecraft.player.ItemStack;

public class FurnaceData {
    public static final float COOK_TIME_TOTAL = 6.0f; // Seconds to cook 1 item

    private final int x, y, z;
    private final ItemStack input = new ItemStack(BlockType.AIR, 0);
    private final ItemStack fuel = new ItemStack(BlockType.AIR, 0);
    private final ItemStack output = new ItemStack(BlockType.AIR, 0);

    private float cookTime = 0.0f;
    private float burnTime = 0.0f;
    private float maxBurnTime = 0.0f;

    public FurnaceData(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public ItemStack getInput() { return input; }
    public ItemStack getFuel() { return fuel; }
    public ItemStack getOutput() { return output; }

    public float getCookTime() { return cookTime; }
    public void setCookTime(float t) { this.cookTime = t; }
    public float getBurnTime() { return burnTime; }
    public void setBurnTime(float t) { this.burnTime = t; }
    public float getMaxBurnTime() { return maxBurnTime; }
    public void setMaxBurnTime(float t) { this.maxBurnTime = t; }
    public boolean isBurning() { return burnTime > 0.0f; }

    public static BlockType getSmeltingResult(BlockType in) {
        if (in == null) return null;
        return switch (in) {
            // Meats / Food
            case BEEF -> BlockType.COOKED_BEEF;
            case PORKCHOP -> BlockType.COOKED_PORKCHOP;
            case CHICKEN_MEAT -> BlockType.COOKED_CHICKEN;
            case ROTTEN_FLESH -> BlockType.COOKED_BEEF;
            // Ores & Materials
            case IRON_ORE -> BlockType.IRON_INGOT;
            case GOLD_ORE -> BlockType.GOLD_INGOT;
            case COBBLESTONE -> BlockType.STONE;
            case SAND -> BlockType.GLASS;
            case NETHERRACK -> BlockType.NETHER_BRICKS;
            case WOOD -> BlockType.COAL; // Charcoal
            default -> null;
        };
    }

    public static float getFuelBurnTime(BlockType f) {
        if (f == null) return 0.0f;
        return switch (f) {
            case COAL -> 48.0f; // Smelts 8 items
            case WOOD, PLANKS, CRAFTING_TABLE -> 12.0f; // Smelts 2 items
            case FENCE, FENCE_GATE, TRAPDOOR, WOODEN_DOOR, WOODEN_SLAB, WOODEN_STAIRS,
                 WOODEN_BUTTON, WOODEN_PRESSURE_PLATE, BOWL, CHEST -> 9.0f; // Smelts 1.5 items
            case STICK -> 3.0f; // Smelts 0.5 items
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD, WOODEN_HOE, BOAT -> 12.0f;
            case LAVA -> 600.0f; // 100 items
            default -> 0.0f;
        };
    }

    public static boolean isFuel(BlockType type) {
        return getFuelBurnTime(type) > 0.0f;
    }

    public static boolean canSmelt(BlockType type) {
        return getSmeltingResult(type) != null;
    }

    public void update(float dt) {
        if (burnTime > 0.0f) {
            burnTime -= dt;
            if (burnTime < 0.0f) burnTime = 0.0f;
        }

        BlockType result = getSmeltingResult(input.getType());
        boolean canSmeltNow = result != null && canAcceptOutput(result);

        // Consume fuel if not burning and has valid item to cook
        if (burnTime <= 0.0f && canSmeltNow) {
            float fuelDuration = getFuelBurnTime(fuel.getType());
            if (fuelDuration > 0.0f) {
                burnTime = fuelDuration;
                maxBurnTime = fuelDuration;
                fuel.add(-1);
            }
        }

        // Advance cook progress
        if (isBurning() && canSmeltNow) {
            cookTime += dt;
            if (cookTime >= COOK_TIME_TOTAL) {
                cookTime = 0.0f;
                smeltItem(result);
            }
        } else if (!canSmeltNow) {
            cookTime = 0.0f;
        } else if (!isBurning()) {
            cookTime = Math.max(0.0f, cookTime - dt * 1.5f);
        }
    }

    private boolean canAcceptOutput(BlockType result) {
        if (output.isEmpty()) return true;
        if (output.getType() != result) return false;
        return output.getCount() < Inventory.MAX_STACK_SIZE;
    }

    private void smeltItem(BlockType result) {
        if (!canAcceptOutput(result)) return;

        input.add(-1);
        if (output.isEmpty()) {
            output.setType(result);
            output.setCount(1);
        } else {
            output.add(1);
        }
    }
}
