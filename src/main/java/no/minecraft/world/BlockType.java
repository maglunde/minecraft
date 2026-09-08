package no.minecraft.world;

public enum BlockType {
    AIR((byte) 0, "Air", false, true, -1, -1, -1),
    GRASS((byte) 1, "Grass Block", true, false, 0, 2, 1),
    DIRT((byte) 2, "Dirt", true, false, 2, 2, 2),
    STONE((byte) 3, "Stone", true, false, 3, 3, 3),
    COBBLESTONE((byte) 4, "Cobblestone", true, false, 4, 4, 4),
    WOOD((byte) 5, "Oak Wood", true, false, 6, 6, 5),
    LEAVES((byte) 6, "Oak Leaves", true, true, 7, 7, 7),
    PLANKS((byte) 7, "Oak Planks", true, false, 8, 8, 8),
    BRICKS((byte) 8, "Bricks", true, false, 9, 9, 9),
    SAND((byte) 9, "Sand", true, false, 10, 10, 10),
    GLASS((byte) 10, "Glass", true, true, 11, 11, 11),
    BEDROCK((byte) 11, "Bedrock", true, false, 12, 12, 12),
    CRAFTING_TABLE((byte) 12, "Arbeidsbenk", true, false, 13, 8, 14),
    STICK((byte) 13, "Pinne", false, true, 15, 15, 15),
    WOODEN_PICKAXE((byte) 14, "Trehakke", false, true, 16, 16, 16),
    WOODEN_AXE((byte) 15, "Treøks", false, true, 17, 17, 17),
    WOODEN_SHOVEL((byte) 16, "Trespade", false, true, 18, 18, 18),
    WOODEN_SWORD((byte) 17, "Tresverd", false, true, 19, 19, 19),
    WOODEN_HOE((byte) 18, "Tregrev", false, true, 20, 20, 20),
    BOAT((byte) 19, "Trebåt", false, true, 21, 21, 21),
    CHEST((byte) 20, "Kiste", true, false, 22, 22, 22),
    WOODEN_DOOR((byte) 21, "Tredør", false, true, 23, 23, 23),
    TRAPDOOR((byte) 22, "Fallelem", true, true, 24, 24, 24),
    LADDER((byte) 23, "Stige", false, true, 25, 25, 25),
    FENCE((byte) 24, "Tregjerde", true, true, 26, 26, 26),
    FENCE_GATE((byte) 25, "Gjerdeport", true, true, 27, 27, 27),
    WOODEN_SLAB((byte) 26, "Trehelle", true, false, 28, 28, 28),
    WOODEN_STAIRS((byte) 27, "Tretrapp", true, false, 29, 29, 29),
    WOODEN_PRESSURE_PLATE((byte) 28, "Tre trykkplate", false, true, 30, 30, 30),
    WOODEN_BUTTON((byte) 29, "Treknapp", false, true, 31, 31, 31),
    BOWL((byte) 30, "Trebolle", false, true, 32, 32, 32),
    FURNACE((byte) 31, "Ovn", true, false, 4, 4, 33),
    STONE_PICKAXE((byte) 32, "Steinhakke", false, true, 34, 34, 34),
    STONE_AXE((byte) 33, "Steinøks", false, true, 35, 35, 35),
    STONE_SHOVEL((byte) 34, "Steinspade", false, true, 36, 36, 36),
    STONE_SWORD((byte) 35, "Steinsverd", false, true, 37, 37, 37),
    ROTTEN_FLESH((byte) 36, "Rått Kjøtt", false, true, 38, 38, 38),
    GUNPOWDER((byte) 37, "Krutt", false, true, 39, 39, 39),
    STRING((byte) 38, "Tråd", false, true, 40, 40, 40),
    BONE((byte) 39, "Bein", false, true, 41, 41, 41);

    public enum ToolType {
        NONE, PICKAXE, AXE, SHOVEL, SWORD
    }

    private final byte id;
    private final String name;
    private final boolean solid;
    private final boolean transparent;
    private final int topTexture;
    private final int bottomTexture;
    private final int sideTexture;

    BlockType(byte id, String name, boolean solid, boolean transparent, int topTexture, int bottomTexture, int sideTexture) {
        this.id = id;
        this.name = name;
        this.solid = solid;
        this.transparent = transparent;
        this.topTexture = topTexture;
        this.bottomTexture = bottomTexture;
        this.sideTexture = sideTexture;
    }

    public float getHardness() {
        return switch (this) {
            case BEDROCK -> -1.0f; // Unbreakable
            case STONE -> 1.5f;
            case COBBLESTONE, BRICKS, FURNACE -> 2.0f;
            case WOOD, PLANKS, CHEST, CRAFTING_TABLE, WOODEN_DOOR, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_STAIRS, WOODEN_SLAB -> 1.0f;
            case DIRT, GRASS -> 0.5f;
            case SAND -> 0.4f;
            case GLASS, LEAVES -> 0.2f;
            default -> 0.1f;
        };
    }

    public ToolType getEffectiveTool() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, FURNACE -> ToolType.PICKAXE;
            case WOOD, PLANKS, CHEST, CRAFTING_TABLE, WOODEN_DOOR, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_STAIRS, WOODEN_SLAB -> ToolType.AXE;
            case DIRT, GRASS, SAND -> ToolType.SHOVEL;
            case LEAVES -> ToolType.SWORD;
            default -> ToolType.NONE;
        };
    }

    public boolean requiresToolForDrop() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, FURNACE -> true;
            default -> false;
        };
    }

    public ToolType getItemToolType() {
        return switch (this) {
            case WOODEN_PICKAXE, STONE_PICKAXE -> ToolType.PICKAXE;
            case WOODEN_AXE, STONE_AXE -> ToolType.AXE;
            case WOODEN_SHOVEL, STONE_SHOVEL -> ToolType.SHOVEL;
            case WOODEN_SWORD, STONE_SWORD -> ToolType.SWORD;
            default -> ToolType.NONE;
        };
    }

    public float getMiningSpeedMultiplier(BlockType block) {
        ToolType needed = block.getEffectiveTool();
        ToolType has = getItemToolType();
        if (needed != ToolType.NONE && needed == has) {
            if (this == STONE_PICKAXE || this == STONE_AXE || this == STONE_SHOVEL || this == STONE_SWORD) {
                return 4.0f;
            }
            return 2.5f; // Wooden tools
        }
        return 1.0f;
    }

    public int getMaxDurability() {
        return switch (this) {
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD, WOODEN_HOE -> 59;
            case STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD -> 131;
            default -> 0;
        };
    }

    public boolean isDamageable() {
        return getMaxDurability() > 0;
    }

    public int getAttackDamage() {
        return switch (this) {
            case WOODEN_SWORD -> 4;
            case STONE_SWORD -> 5;
            case WOODEN_AXE -> 3;
            case STONE_AXE -> 4;
            case WOODEN_PICKAXE -> 2;
            case STONE_PICKAXE -> 3;
            case WOODEN_SHOVEL -> 1;
            case STONE_SHOVEL -> 2;
            default -> 1; // Fists
        };
    }

    public byte getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isSolid() {
        return solid;
    }

    public boolean isTransparent() {
        return transparent;
    }

    public int getTexture(Face face) {
        return switch (face) {
            case TOP -> topTexture;
            case BOTTOM -> bottomTexture;
            case NORTH, SOUTH, EAST, WEST -> sideTexture;
        };
    }

    public BlockType getDrop() {
        return switch (this) {
            case GRASS -> DIRT;
            case STONE -> COBBLESTONE;
            default -> this;
        };
    }

    public String getDigSound() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, BEDROCK, FURNACE -> "dig_stone";
            case WOOD, PLANKS, CRAFTING_TABLE, CHEST, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_SLAB, WOODEN_STAIRS -> "dig_wood";
            case SAND -> "dig_sand";
            case DIRT, GRASS, LEAVES -> "dig_grass";
            default -> "dig_stone";
        };
    }

    public String getBreakSound() {
        return switch (this) {
            case STONE, COBBLESTONE, BRICKS, BEDROCK, FURNACE -> "break_stone";
            case WOOD, PLANKS, CRAFTING_TABLE, CHEST, TRAPDOOR, FENCE, FENCE_GATE, WOODEN_SLAB, WOODEN_STAIRS -> "break_wood";
            case DIRT, GRASS, LEAVES, SAND -> "break_grass";
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
