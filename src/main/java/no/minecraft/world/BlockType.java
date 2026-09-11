package no.minecraft.world;

import no.minecraft.i18n.I18n;

import java.util.Locale;

public enum BlockType {
    AIR((byte) 0, false, true, -1, -1, -1),
    GRASS((byte) 1, true, false, 0, 2, 1),
    DIRT((byte) 2, true, false, 2, 2, 2),
    STONE((byte) 3, true, false, 3, 3, 3),
    COBBLESTONE((byte) 4, true, false, 4, 4, 4),
    WOOD((byte) 5, true, false, 6, 6, 5),
    LEAVES((byte) 6, true, true, 7, 7, 7),
    PLANKS((byte) 7, true, false, 8, 8, 8),
    BRICKS((byte) 8, true, false, 9, 9, 9),
    SAND((byte) 9, true, false, 10, 10, 10),
    GLASS((byte) 10, true, true, 11, 11, 11),
    BEDROCK((byte) 11, true, false, 12, 12, 12),
    CRAFTING_TABLE((byte) 12, true, false, 13, 8, 14),
    STICK((byte) 13, false, true, 15, 15, 15),
    WOODEN_PICKAXE((byte) 14, false, true, 16, 16, 16),
    WOODEN_AXE((byte) 15, false, true, 17, 17, 17),
    WOODEN_SHOVEL((byte) 16, false, true, 18, 18, 18),
    WOODEN_SWORD((byte) 17, false, true, 19, 19, 19),
    WOODEN_HOE((byte) 18, false, true, 20, 20, 20),
    BOAT((byte) 19, false, true, 21, 21, 21),
    CHEST((byte) 20, true, true, 112, 113, 111),
    WOODEN_DOOR((byte) 21, false, true, 23, 23, 23),
    TRAPDOOR((byte) 22, true, true, 24, 24, 24),
    LADDER((byte) 23, false, true, 25, 25, 25),
    FENCE((byte) 24, true, true, 8, 8, 8),
    FENCE_GATE((byte) 25, true, true, 8, 8, 8),
    WOODEN_SLAB((byte) 26, true, false, 8, 8, 8),
    WOODEN_STAIRS((byte) 27, true, false, 8, 8, 8),
    WOODEN_PRESSURE_PLATE((byte) 28, false, true, 8, 8, 8),
    WOODEN_BUTTON((byte) 29, false, true, 8, 8, 8),
    BOWL((byte) 30, false, true, 32, 32, 32),
    FURNACE((byte) 31, true, false, 114, 114, 33),
    STONE_PICKAXE((byte) 32, false, true, 34, 34, 34),
    STONE_AXE((byte) 33, false, true, 35, 35, 35),
    STONE_SHOVEL((byte) 34, false, true, 36, 36, 36),
    STONE_SWORD((byte) 35, false, true, 37, 37, 37),
    ROTTEN_FLESH((byte) 36, false, true, 38, 38, 38),
    GUNPOWDER((byte) 37, false, true, 39, 39, 39),
    STRING((byte) 38, false, true, 40, 40, 40),
    BONE((byte) 39, false, true, 41, 41, 41),
    OBSIDIAN((byte) 40, true, false, 52, 52, 52),
    NETHERRACK((byte) 41, true, false, 53, 53, 53),
    NETHER_BRICKS((byte) 42, true, false, 54, 54, 54),
    NETHER_PORTAL((byte) 43, false, true, 55, 55, 55),
    END_STONE((byte) 44, true, false, 56, 56, 56),
    END_PORTAL_FRAME((byte) 45, true, false, 57, 3, 58),
    END_PORTAL_FRAME_FILLED((byte) 46, true, false, 59, 3, 58),
    END_PORTAL((byte) 47, false, true, 60, 60, 60),
    DRAGON_EGG((byte) 48, true, false, 61, 61, 61),
    BLAZE_ROD((byte) 49, false, true, 62, 62, 62),
    BLAZE_POWDER((byte) 50, false, true, 63, 63, 63),
    ENDER_PEARL((byte) 51, false, true, 64, 64, 64),
    EYE_OF_ENDER((byte) 52, false, true, 65, 65, 65),
    BOW((byte) 53, false, true, 66, 66, 66),
    ARROW((byte) 54, false, true, 67, 67, 67),
    FLINT_AND_STEEL((byte) 55, false, true, 68, 68, 68),
    LAVA((byte) 56, false, false, 69, 69, 69),
    NETHER_QUARTZ_ORE((byte) 57, true, false, 70, 70, 70),
    GLOWSTONE((byte) 58, true, false, 71, 71, 71),
    SOUL_SAND((byte) 59, true, false, 72, 72, 72),
    BASALT((byte) 60, true, false, 74, 74, 73),
    SPAWNER((byte) 61, true, true, 75, 75, 75),
    WATER((byte) 62, false, true, 76, 76, 76),
    SANDSTONE((byte) 63, true, false, 77, 79, 78),
    SNOW_BLOCK((byte) 64, true, false, 80, 80, 80),
    GRAVEL((byte) 65, true, false, 81, 81, 81),
    CACTUS((byte) 66, true, true, 82, 82, 83),
    COAL_ORE((byte) 67, true, false, 84, 84, 84),
    COAL((byte) 68, false, true, 85, 85, 85),
    IRON_ORE((byte) 69, true, false, 86, 86, 86),
    IRON_INGOT((byte) 70, false, true, 87, 87, 87),
    GOLD_ORE((byte) 71, true, false, 88, 88, 88),
    GOLD_INGOT((byte) 72, false, true, 89, 89, 89),
    DIAMOND_ORE((byte) 73, true, false, 90, 90, 90),
    DIAMOND((byte) 74, false, true, 91, 91, 91),
    TORCH((byte) 75, false, true, 92, 92, 92),
    IRON_PICKAXE((byte) 76, false, true, 93, 93, 93),
    IRON_SWORD((byte) 77, false, true, 94, 94, 94),
    IRON_AXE((byte) 78, false, true, 95, 95, 95),
    IRON_SHOVEL((byte) 79, false, true, 96, 96, 96),
    DIAMOND_PICKAXE((byte) 80, false, true, 97, 97, 97),
    DIAMOND_SWORD((byte) 81, false, true, 98, 98, 98),
    DIAMOND_AXE((byte) 82, false, true, 99, 99, 99),
    DIAMOND_SHOVEL((byte) 83, false, true, 100, 100, 100),
    PORKCHOP((byte) 84, false, true, 101, 101, 101),
    COOKED_PORKCHOP((byte) 85, false, true, 102, 102, 102),
    BEEF((byte) 86, false, true, 103, 103, 103),
    COOKED_BEEF((byte) 87, false, true, 104, 104, 104),
    CHICKEN_MEAT((byte) 88, false, true, 105, 105, 105),
    COOKED_CHICKEN((byte) 89, false, true, 106, 106, 106),
    APPLE((byte) 90, false, true, 107, 107, 107),
    BREAD((byte) 91, false, true, 108, 108, 108),
    LEATHER((byte) 92, false, true, 109, 109, 109),
    FEATHER((byte) 93, false, true, 110, 110, 110);

    public enum ToolType {
        NONE, PICKAXE, AXE, SHOVEL, SWORD
    }

    private final byte id;
    private final boolean solid;
    private final boolean transparent;
    private final int topTexture;
    private final int bottomTexture;
    private final int sideTexture;

    BlockType(byte id, boolean solid, boolean transparent, int topTexture, int bottomTexture, int sideTexture) {
        this.id = id;
        this.solid = solid;
        this.transparent = transparent;
        this.topTexture = topTexture;
        this.bottomTexture = bottomTexture;
        this.sideTexture = sideTexture;
    }

    public float getHardness() {
        return 3 *  switch (this) {
            case BEDROCK, END_PORTAL, NETHER_PORTAL, LAVA, WATER -> -1.0f; // Unbreakable
            case OBSIDIAN -> 50.0f;
            case END_PORTAL_FRAME, END_PORTAL_FRAME_FILLED -> -1.0f;
            case END_STONE -> 3.0f;
            case STONE -> 1.5f;
            case BASALT -> 1.25f;
            case COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, NETHER_QUARTZ_ORE -> 3.0f;
            case COBBLESTONE, BRICKS, WOOD, PLANKS, FENCE, FENCE_GATE, WOODEN_SLAB, WOODEN_STAIRS, NETHER_BRICKS -> 2.0f;
            case FURNACE -> 3.5f;
            case SPAWNER -> 5.0f;
            case SANDSTONE -> 0.8f;
            case GRAVEL -> 0.6f;
            case NETHERRACK -> 0.4f;
            case SOUL_SAND -> 0.5f;
            case GLOWSTONE -> 0.3f;
            case DRAGON_EGG -> 3.0f;
            case CHEST, CRAFTING_TABLE -> 2.5f;
            case WOODEN_DOOR, TRAPDOOR -> 3.0f;
            case WOODEN_PRESSURE_PLATE, WOODEN_BUTTON -> 0.5f;
            case LADDER -> 0.4f;
            case DIRT -> 0.5f;
            case GRASS -> 0.6f;
            case SAND -> 0.5f;
            case CACTUS -> 0.4f;
            case GLASS, LEAVES, SNOW_BLOCK -> 0.2f;
            case TORCH -> 0.0f;
            default -> 0.0f;
        };
    }

    public ToolType getEffectiveTool() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, FURNACE, OBSIDIAN, NETHERRACK, NETHER_BRICKS, END_STONE, NETHER_QUARTZ_ORE, BASALT, SPAWNER, SANDSTONE, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE -> ToolType.PICKAXE;
            case WOOD, PLANKS, CHEST, CRAFTING_TABLE, WOODEN_DOOR, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_STAIRS, WOODEN_SLAB, CACTUS -> ToolType.AXE;
            case DIRT, GRASS, SAND, SOUL_SAND, GRAVEL, SNOW_BLOCK -> ToolType.SHOVEL;
            case LEAVES -> ToolType.SWORD;
            default -> ToolType.NONE;
        };
    }

    public boolean requiresToolForDrop() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, FURNACE, OBSIDIAN, NETHERRACK, NETHER_BRICKS, END_STONE, NETHER_QUARTZ_ORE, BASALT, SANDSTONE, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE -> true;
            default -> false;
        };
    }

    public boolean canHarvest(BlockType tool) {
        if (!requiresToolForDrop()) return true;
        if (tool == null) return false;
        if (tool.getItemToolType() != getEffectiveTool()) return false;
        return switch (this) {
            case OBSIDIAN -> tool == DIAMOND_PICKAXE;
            case DIAMOND_ORE, GOLD_ORE -> tool == IRON_PICKAXE || tool == DIAMOND_PICKAXE;
            case IRON_ORE -> tool == STONE_PICKAXE || tool == IRON_PICKAXE || tool == DIAMOND_PICKAXE;
            default -> true;
        };
    }

    public ToolType getItemToolType() {
        return switch (this) {
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, DIAMOND_PICKAXE -> ToolType.PICKAXE;
            case WOODEN_AXE, STONE_AXE, IRON_AXE, DIAMOND_AXE -> ToolType.AXE;
            case WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL, DIAMOND_SHOVEL -> ToolType.SHOVEL;
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, DIAMOND_SWORD -> ToolType.SWORD;
            default -> ToolType.NONE;
        };
    }

    public float getMiningSpeedMultiplier(BlockType block) {
        ToolType needed = block.getEffectiveTool();
        ToolType has = getItemToolType();
        if (needed != ToolType.NONE && needed == has) {
            if (this == DIAMOND_PICKAXE || this == DIAMOND_AXE || this == DIAMOND_SHOVEL || this == DIAMOND_SWORD) {
                return 8.0f;
            }
            if (this == IRON_PICKAXE || this == IRON_AXE || this == IRON_SHOVEL || this == IRON_SWORD) {
                return 6.0f;
            }
            if (this == STONE_PICKAXE || this == STONE_AXE || this == STONE_SHOVEL || this == STONE_SWORD) {
                return 4.0f;
            }
            return 2.5f; // Wooden tools
        }
        return 1.0f;
    }

    public int getMaxDurability() {
        return switch (this) {
            case DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_SWORD -> 1561;
            case IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD -> 250;
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD -> 131;
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD, WOODEN_HOE -> 59;
            default -> 0;
        };
    }

    public boolean isDamageable() {
        return getMaxDurability() > 0;
    }

    public int getAttackDamage() {
        return switch (this) {
            case DIAMOND_SWORD -> 7;
            case IRON_SWORD -> 6;
            case STONE_SWORD -> 5;
            case WOODEN_SWORD -> 4;
            case DIAMOND_AXE -> 6;
            case IRON_AXE -> 5;
            case STONE_AXE -> 4;
            case WOODEN_AXE -> 3;
            case DIAMOND_PICKAXE -> 5;
            case IRON_PICKAXE -> 4;
            case STONE_PICKAXE -> 3;
            case WOODEN_PICKAXE -> 2;
            case DIAMOND_SHOVEL -> 4;
            case IRON_SHOVEL -> 3;
            case STONE_SHOVEL, WOODEN_SHOVEL -> 1;
            default -> 1; // Fists
        };
    }

    public byte getId() {
        return id;
    }

    /** Bundle key of the display name, e.g. "block.crafting_table". */
    public String getTranslationKey() {
        return "block." + name().toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return I18n.get(getTranslationKey());
    }

    public boolean isSolid() {
        return solid;
    }

    public boolean isTransparent() {
        return transparent;
    }

    public int getTexture(Face face) {
        if (this == CHEST) {
            return switch (face) {
                case TOP -> topTexture; // 112
                case BOTTOM -> bottomTexture; // 113
                case NORTH -> 22; // 22: chest_front (with latch)
                case SOUTH, EAST, WEST -> sideTexture; // 111 (chest_side)
            };
        }
        if (this == FURNACE) {
            return switch (face) {
                case TOP, BOTTOM -> 114;
                case NORTH -> 33;
                case SOUTH, EAST, WEST -> 115;
            };
        }
        return switch (face) {
            case TOP -> topTexture;
            case BOTTOM -> bottomTexture;
            case NORTH, SOUTH, EAST, WEST -> sideTexture;
        };
    }

    public int getItemTexture() {
        return switch (this) {
            case FURNACE -> sideTexture;
            case CHEST -> 22; // Front face with latch for inventory/HUD icon
            default -> topTexture;
        };
    }

    public boolean isPlaceable() {
        return switch (this) {
            case AIR, WATER, LAVA, END_PORTAL, NETHER_PORTAL, BOAT -> false;
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD, WOODEN_HOE,
                 STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD,
                 IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD,
                 DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_SWORD,
                 BOW, ARROW, FLINT_AND_STEEL -> false;
            case STICK, BOWL, ROTTEN_FLESH, GUNPOWDER, STRING, BONE,
                 BLAZE_ROD, BLAZE_POWDER, ENDER_PEARL, EYE_OF_ENDER,
                 COAL, IRON_INGOT, GOLD_INGOT, DIAMOND, LEATHER, FEATHER -> false;
            case APPLE, BREAD, PORKCHOP, COOKED_PORKCHOP, BEEF, COOKED_BEEF,
                 CHICKEN_MEAT, COOKED_CHICKEN -> false;
            default -> true;
        };
    }

    public BlockType getDrop() {
        return switch (this) {
            case GRASS -> DIRT;
            case STONE -> COBBLESTONE;
            case COAL_ORE -> COAL;
            case DIAMOND_ORE -> DIAMOND;
            case LAVA, WATER -> AIR;
            default -> this;
        };
    }

    public int getFoodValue() {
        return switch (this) {
            case COOKED_PORKCHOP, COOKED_BEEF -> 8;
            case COOKED_CHICKEN -> 6;
            case BREAD -> 5;
            case APPLE, ROTTEN_FLESH -> 4;
            case PORKCHOP, BEEF -> 3;
            case CHICKEN_MEAT -> 2;
            default -> 0;
        };
    }

    public float getSaturationValue() {
        return switch (this) {
            case COOKED_PORKCHOP, COOKED_BEEF -> 12.8f;
            case COOKED_CHICKEN -> 7.2f;
            case BREAD -> 6.0f;
            case APPLE -> 2.4f;
            case PORKCHOP, BEEF -> 1.8f;
            case CHICKEN_MEAT -> 1.2f;
            case ROTTEN_FLESH -> 0.8f;
            default -> 0.0f;
        };
    }

    public boolean isFood() {
        return getFoodValue() > 0;
    }

    public String getDigSound() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, BEDROCK, FURNACE, OBSIDIAN, NETHERRACK, NETHER_BRICKS, END_STONE, DRAGON_EGG, END_PORTAL_FRAME, END_PORTAL_FRAME_FILLED, NETHER_QUARTZ_ORE, BASALT, SANDSTONE, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE -> "dig_stone";
            case WOOD, PLANKS, CRAFTING_TABLE, CHEST, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_SLAB, WOODEN_STAIRS, CACTUS, TORCH -> "dig_wood";
            case SAND, SOUL_SAND, GRAVEL -> "dig_sand";
            case DIRT, GRASS, LEAVES, SNOW_BLOCK -> "dig_grass";
            case GLOWSTONE -> "dig_stone";
            default -> "dig_stone";
        };
    }

    public String getBreakSound() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, BEDROCK, FURNACE, OBSIDIAN, NETHERRACK, NETHER_BRICKS, END_STONE, DRAGON_EGG, END_PORTAL_FRAME, END_PORTAL_FRAME_FILLED, NETHER_QUARTZ_ORE, BASALT, SANDSTONE, COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE -> "break_stone";
            case WOOD, PLANKS, CRAFTING_TABLE, CHEST, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_SLAB, WOODEN_STAIRS, CACTUS, TORCH -> "break_wood";
            case DIRT, GRASS, LEAVES, SAND, SOUL_SAND, GRAVEL, SNOW_BLOCK -> "break_grass";
            case GLOWSTONE -> "break_stone";
            default -> "break_stone";
        };
    }

    private static final BlockType[] BY_ID = new BlockType[256];
    static {
        for (BlockType type : values()) {
            BY_ID[type.id & 0xFF] = type;
        }
    }

    public static BlockType getById(byte id) {
        BlockType type = BY_ID[id & 0xFF];
        return type != null ? type : AIR;
    }

    public enum Face {
        TOP(0, 1, 0),
        BOTTOM(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        public final int dx, dy, dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }
}
