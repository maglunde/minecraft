package no.minecraft.render;

import no.minecraft.player.CraftingRecipe;
import no.minecraft.player.GameMode;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class HUD {
    private final Shader hudShader;
    private final int vaoId;
    private final int vboId;

    private boolean inventoryOpen = false;
    private boolean craftingTableOpen = false;
    private boolean recipeBookOpen = false;
    private boolean showDebugInfo = false;
    private int lastFps = 60;
    private no.minecraft.player.Raycast.HitResult lastTargetedHit = null;
    private final List<CraftingRecipe> recipes = CraftingRecipe.getDefaultRecipes();

    private final ItemStack[] craftSlots = new ItemStack[4];
    private final ItemStack[] benchSlots = new ItemStack[9];
    private final ItemStack carriedItem = new ItemStack(BlockType.AIR, 0);
    private float mouseX, mouseY;

    private static final String VERTEX_SHADER = """
            #version 330 core
            layout (location = 0) in vec2 aPos;
            layout (location = 1) in vec2 aTexCoord;
            layout (location = 2) in vec4 aColor;

            uniform mat4 uOrtho;

            out vec2 vTexCoord;
            out vec4 vColor;

            void main() {
                gl_Position = uOrtho * vec4(aPos, 0.0, 1.0);
                vTexCoord = aTexCoord;
                vColor = aColor;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            in vec2 vTexCoord;
            in vec4 vColor;

            uniform sampler2D uTexture;
            uniform int uUseTexture;

            out vec4 FragColor;

            void main() {
                if (uUseTexture == 1) {
                    vec4 texColor = texture(uTexture, vTexCoord);
                    FragColor = texColor * vColor;
                } else {
                    FragColor = vColor;
                }
            }
            """;

    public HUD() {
        hudShader = new Shader(VERTEX_SHADER, FRAGMENT_SHADER);
        vaoId = glGenVertexArrays();
        vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        int stride = (2 + 2 + 4) * Float.BYTES;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        for (int i = 0; i < 4; i++) {
            craftSlots[i] = new ItemStack(BlockType.AIR, 0);
        }
        for (int i = 0; i < 9; i++) {
            benchSlots[i] = new ItemStack(BlockType.AIR, 0);
        }
    }

    public boolean isDebugInfoOpen() {
        return showDebugInfo;
    }

    public void setDebugInfoOpen(boolean open) {
        this.showDebugInfo = open;
    }

    public void toggleDebugInfo() {
        this.showDebugInfo = !this.showDebugInfo;
    }

    public boolean isInventoryOpen() {
        return inventoryOpen || craftingTableOpen;
    }

    public boolean isCraftingTableOpen() {
        return craftingTableOpen;
    }

    public void openCraftingTable() {
        this.inventoryOpen = false;
        this.craftingTableOpen = true;
    }

    public void setInventoryOpen(boolean open) {
        this.inventoryOpen = open;
    }

    public void setInventoryOpen(boolean open, Player player) {
        if (!open && (inventoryOpen || craftingTableOpen)) {
            closeInventory(player);
        } else {
            this.inventoryOpen = open;
        }
    }

    public void toggleInventory() {
        this.inventoryOpen = !this.inventoryOpen;
    }

    public void toggleInventory(Player player) {
        if (inventoryOpen || craftingTableOpen) {
            closeInventory(player);
        } else {
            this.inventoryOpen = true;
        }
    }

    public void returnCraftSlotsToInventory(Player player) {
        for (ItemStack s : craftSlots) {
            if (!s.isEmpty()) {
                player.getInventory().addItem(s.getType(), s.getCount());
                s.clear();
            }
        }
    }

    public void returnBenchSlotsToInventory(Player player) {
        for (ItemStack s : benchSlots) {
            if (!s.isEmpty()) {
                player.getInventory().addItem(s.getType(), s.getCount());
                s.clear();
            }
        }
    }

    public void closeInventory(Player player) {
        if (inventoryOpen || craftingTableOpen) {
            inventoryOpen = false;
            craftingTableOpen = false;
            if (!carriedItem.isEmpty()) {
                player.getInventory().addItem(carriedItem.getType(), carriedItem.getCount());
                carriedItem.clear();
            }
            returnCraftSlotsToInventory(player);
            returnBenchSlotsToInventory(player);
        }
    }

    public ItemStack getCraftingResult() {
        int countNonEmpty = 0;
        for (ItemStack s : craftSlots) {
            if (!s.isEmpty()) countNonEmpty++;
        }
        if (countNonEmpty == 0) return null; // EMPTY GRID -> NO RESULT!

        // 1 item in grid
        if (countNonEmpty == 1) {
            for (ItemStack s : craftSlots) {
                if (!s.isEmpty()) {
                    if (s.getType() == BlockType.WOOD) {
                        return new ItemStack(BlockType.PLANKS, 4);
                    }
                    if (s.getType() == BlockType.PLANKS) {
                        return new ItemStack(BlockType.WOODEN_BUTTON, 1);
                    }
                }
            }
        }

        // 2 items in grid
        if (countNonEmpty == 2) {
            // Vertical 2 planks -> Sticks (slots 0 & 2 or 1 & 3)
            if ((!craftSlots[0].isEmpty() && !craftSlots[2].isEmpty() && craftSlots[0].getType() == BlockType.PLANKS && craftSlots[2].getType() == BlockType.PLANKS) ||
                (!craftSlots[1].isEmpty() && !craftSlots[3].isEmpty() && craftSlots[1].getType() == BlockType.PLANKS && craftSlots[3].getType() == BlockType.PLANKS)) {
                return new ItemStack(BlockType.STICK, 4);
            }
            // Horizontal 2 planks -> Pressure Plate (slots 0 & 1 or 2 & 3)
            if ((!craftSlots[0].isEmpty() && !craftSlots[1].isEmpty() && craftSlots[0].getType() == BlockType.PLANKS && craftSlots[1].getType() == BlockType.PLANKS) ||
                (!craftSlots[2].isEmpty() && !craftSlots[3].isEmpty() && craftSlots[2].getType() == BlockType.PLANKS && craftSlots[3].getType() == BlockType.PLANKS)) {
                return new ItemStack(BlockType.WOODEN_PRESSURE_PLATE, 1);
            }
        }

        // 4 items in grid
        if (countNonEmpty == 4) {
            // 4 Planks -> Crafting Table
            boolean allPlanks = true;
            for (ItemStack s : craftSlots) {
                if (s.getType() != BlockType.PLANKS) {
                    allPlanks = false;
                    break;
                }
            }
            if (allPlanks) {
                return new ItemStack(BlockType.CRAFTING_TABLE, 1);
            }
        }

        return null;
    }

    private void handleSlotClick(ItemStack slot, int button) {
        no.minecraft.sound.SoundManager.getInstance().play("click");
        if (carriedItem.isEmpty()) {
            if (!slot.isEmpty()) {
                if (button == GLFW_MOUSE_BUTTON_RIGHT && slot.getCount() > 1) {
                    int half = (slot.getCount() + 1) / 2;
                    carriedItem.setType(slot.getType());
                    carriedItem.setCount(half);
                    slot.add(-half);
                } else {
                    carriedItem.setType(slot.getType());
                    carriedItem.setCount(slot.getCount());
                    slot.clear();
                }
            }
        } else {
            if (slot.isEmpty()) {
                if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    slot.setType(carriedItem.getType());
                    slot.setCount(1);
                    carriedItem.add(-1);
                } else {
                    slot.setType(carriedItem.getType());
                    slot.setCount(carriedItem.getCount());
                    carriedItem.clear();
                }
            } else if (slot.getType() == carriedItem.getType()) {
                if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                    if (slot.getCount() < no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                        slot.add(1);
                        carriedItem.add(-1);
                    }
                } else {
                    int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - slot.getCount();
                    int add = Math.min(space, carriedItem.getCount());
                    slot.add(add);
                    carriedItem.add(-add);
                }
            } else {
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    BlockType tempType = slot.getType();
                    int tempCount = slot.getCount();
                    slot.setType(carriedItem.getType());
                    slot.setCount(carriedItem.getCount());
                    carriedItem.setType(tempType);
                    carriedItem.setCount(tempCount);
                }
            }
        }
    }

    private boolean isPlank(int idx) {
        return !benchSlots[idx].isEmpty() && benchSlots[idx].getType() == BlockType.PLANKS;
    }

    private boolean isStick(int idx) {
        return !benchSlots[idx].isEmpty() && benchSlots[idx].getType() == BlockType.STICK;
    }

    private boolean isCobble(int idx) {
        return !benchSlots[idx].isEmpty() && benchSlots[idx].getType() == BlockType.COBBLESTONE;
    }

    public ItemStack get3x3CraftingResult() {
        int countNonEmpty = 0;
        int woodCount = 0;
        int plankCount = 0;
        int stickCount = 0;
        int cobbleCount = 0;

        for (ItemStack s : benchSlots) {
            if (!s.isEmpty()) {
                countNonEmpty++;
                if (s.getType() == BlockType.WOOD) woodCount++;
                else if (s.getType() == BlockType.PLANKS) plankCount++;
                else if (s.getType() == BlockType.STICK) stickCount++;
                else if (s.getType() == BlockType.COBBLESTONE) cobbleCount++;
            }
        }

        if (countNonEmpty == 0) return null; // EMPTY GRID -> NO RESULT!

        // --- Cobblestone Recipes ---
        // 8 Cobblestone: Furnace (outer ring)
        if (cobbleCount == 8 && countNonEmpty == 8) {
            if (isCobble(0) && isCobble(1) && isCobble(2) && isCobble(3) && isCobble(5) && isCobble(6) && isCobble(7) && isCobble(8) && benchSlots[4].isEmpty()) {
                return new ItemStack(BlockType.FURNACE, 1);
            }
        }

        // 3 Cobblestone + 2 Sticks: Stone Pickaxe
        if (cobbleCount == 3 && stickCount == 2 && countNonEmpty == 5) {
            if (isCobble(0) && isCobble(1) && isCobble(2) && isStick(4) && isStick(7)) {
                return new ItemStack(BlockType.STONE_PICKAXE, 1);
            }
        }

        // 3 Cobblestone + 2 Sticks: Stone Axe
        if (cobbleCount == 3 && stickCount == 2 && countNonEmpty == 5) {
            if (((isCobble(0) && isCobble(1) && isCobble(3)) || (isCobble(1) && isCobble(2) && isCobble(5))) && isStick(4) && isStick(7)) {
                return new ItemStack(BlockType.STONE_AXE, 1);
            }
        }

        // 1 Cobblestone + 2 Sticks: Stone Shovel
        if (cobbleCount == 1 && stickCount == 2 && countNonEmpty == 3) {
            if ((isCobble(1) && isStick(4) && isStick(7)) ||
                (isCobble(0) && isStick(3) && isStick(6)) ||
                (isCobble(2) && isStick(5) && isStick(8))) {
                return new ItemStack(BlockType.STONE_SHOVEL, 1);
            }
        }

        // 2 Cobblestone + 1 Stick: Stone Sword
        if (cobbleCount == 2 && stickCount == 1 && countNonEmpty == 3) {
            if ((isCobble(1) && isCobble(4) && isStick(7)) ||
                (isCobble(0) && isCobble(3) && isStick(6)) ||
                (isCobble(2) && isCobble(5) && isStick(8))) {
                return new ItemStack(BlockType.STONE_SWORD, 1);
            }
        }

        // --- Wood Recipes ---
        // 1 Wood -> 4 Planks
        if (woodCount == 1 && countNonEmpty == 1) {
            return new ItemStack(BlockType.PLANKS, 4);
        }

        // 1 Plank -> 1 Button
        if (plankCount == 1 && countNonEmpty == 1) {
            return new ItemStack(BlockType.WOODEN_BUTTON, 1);
        }

        // 2 Planks:
        if (plankCount == 2 && countNonEmpty == 2) {
            // Vertical -> Sticks
            if ((isPlank(0) && isPlank(3)) || (isPlank(1) && isPlank(4)) || (isPlank(2) && isPlank(5)) ||
                (isPlank(3) && isPlank(6)) || (isPlank(4) && isPlank(7)) || (isPlank(5) && isPlank(8))) {
                return new ItemStack(BlockType.STICK, 4);
            }
            // Horizontal -> Pressure Plate
            if ((isPlank(0) && isPlank(1)) || (isPlank(1) && isPlank(2)) ||
                (isPlank(3) && isPlank(4)) || (isPlank(4) && isPlank(5)) ||
                (isPlank(6) && isPlank(7)) || (isPlank(7) && isPlank(8))) {
                return new ItemStack(BlockType.WOODEN_PRESSURE_PLATE, 1);
            }
        }

        // 3 Planks:
        if (plankCount == 3 && countNonEmpty == 3) {
            // Horizontal row -> 6 Slabs
            if ((isPlank(0) && isPlank(1) && isPlank(2)) ||
                (isPlank(3) && isPlank(4) && isPlank(5)) ||
                (isPlank(6) && isPlank(7) && isPlank(8))) {
                return new ItemStack(BlockType.WOODEN_SLAB, 6);
            }
            // V-shape -> 4 Bowls
            if ((isPlank(3) && isPlank(5) && isPlank(7)) ||
                (isPlank(0) && isPlank(2) && isPlank(4))) {
                return new ItemStack(BlockType.BOWL, 4);
            }
        }

        // 4 Planks:
        if (plankCount == 4 && countNonEmpty == 4) {
            // 2x2 Square -> Crafting Table
            if ((isPlank(0) && isPlank(1) && isPlank(3) && isPlank(4)) ||
                (isPlank(1) && isPlank(2) && isPlank(4) && isPlank(5)) ||
                (isPlank(3) && isPlank(4) && isPlank(6) && isPlank(7)) ||
                (isPlank(4) && isPlank(5) && isPlank(7) && isPlank(8))) {
                return new ItemStack(BlockType.CRAFTING_TABLE, 1);
            }
        }

        // 1 Plank + 2 Sticks: Shovel
        if (plankCount == 1 && stickCount == 2 && countNonEmpty == 3) {
            if ((isPlank(1) && isStick(4) && isStick(7)) ||
                (isPlank(0) && isStick(3) && isStick(6)) ||
                (isPlank(2) && isStick(5) && isStick(8))) {
                return new ItemStack(BlockType.WOODEN_SHOVEL, 1);
            }
        }

        // 2 Planks + 1 Stick: Sword
        if (plankCount == 2 && stickCount == 1 && countNonEmpty == 3) {
            if ((isPlank(1) && isPlank(4) && isStick(7)) ||
                (isPlank(0) && isPlank(3) && isStick(6)) ||
                (isPlank(2) && isPlank(5) && isStick(8))) {
                return new ItemStack(BlockType.WOODEN_SWORD, 1);
            }
        }

        // 2 Planks + 2 Sticks: Hoe
        if (plankCount == 2 && stickCount == 2 && countNonEmpty == 4) {
            if (((isPlank(0) && isPlank(1)) || (isPlank(1) && isPlank(2))) && isStick(4) && isStick(7)) {
                return new ItemStack(BlockType.WOODEN_HOE, 1);
            }
        }

        // 3 Planks + 2 Sticks: Pickaxe or Axe
        if (plankCount == 3 && stickCount == 2 && countNonEmpty == 5) {
            // Pickaxe: Planks across top (0, 1, 2)
            if (isPlank(0) && isPlank(1) && isPlank(2) && isStick(4) && isStick(7)) {
                return new ItemStack(BlockType.WOODEN_PICKAXE, 1);
            }
            // Axe: 2 top, 1 mid side
            if (((isPlank(0) && isPlank(1) && isPlank(3)) || (isPlank(1) && isPlank(2) && isPlank(5))) && isStick(4) && isStick(7)) {
                return new ItemStack(BlockType.WOODEN_AXE, 1);
            }
        }

        // 5 Planks: Boat
        if (plankCount == 5 && countNonEmpty == 5) {
            if ((isPlank(3) && isPlank(5) && isPlank(6) && isPlank(7) && isPlank(8)) ||
                (isPlank(0) && isPlank(2) && isPlank(3) && isPlank(4) && isPlank(5))) {
                return new ItemStack(BlockType.BOAT, 1);
            }
        }

        // 6 Planks: Door, Trapdoor, or Stairs
        if (plankCount == 6 && countNonEmpty == 6) {
            // Door (2 columns of 3)
            if ((isPlank(0) && isPlank(1) && isPlank(3) && isPlank(4) && isPlank(6) && isPlank(7)) ||
                (isPlank(1) && isPlank(2) && isPlank(4) && isPlank(5) && isPlank(7) && isPlank(8))) {
                return new ItemStack(BlockType.WOODEN_DOOR, 3);
            }
            // Trapdoor (2 rows of 3)
            if ((isPlank(0) && isPlank(1) && isPlank(2) && isPlank(3) && isPlank(4) && isPlank(5)) ||
                (isPlank(3) && isPlank(4) && isPlank(5) && isPlank(6) && isPlank(7) && isPlank(8))) {
                return new ItemStack(BlockType.TRAPDOOR, 2);
            }
            // Stairs
            if ((isPlank(0) && isPlank(3) && isPlank(4) && isPlank(6) && isPlank(7) && isPlank(8)) ||
                (isPlank(2) && isPlank(4) && isPlank(5) && isPlank(6) && isPlank(7) && isPlank(8))) {
                return new ItemStack(BlockType.WOODEN_STAIRS, 4);
            }
        }

        // 7 Sticks: Ladder
        if (stickCount == 7 && countNonEmpty == 7) {
            if (isStick(0) && isStick(2) && isStick(3) && isStick(4) && isStick(5) && isStick(6) && isStick(8)) {
                return new ItemStack(BlockType.LADDER, 3);
            }
        }

        // 4 Planks + 2 Sticks: Fence
        if (plankCount == 4 && stickCount == 2 && countNonEmpty == 6) {
            if (isPlank(3) && isPlank(5) && isPlank(6) && isPlank(8) && isStick(4) && isStick(7)) {
                return new ItemStack(BlockType.FENCE, 3);
            }
        }

        // 2 Planks + 4 Sticks: Fence Gate
        if (plankCount == 2 && stickCount == 4 && countNonEmpty == 6) {
            if (isStick(3) && isStick(5) && isStick(6) && isStick(8) && isPlank(4) && isPlank(7)) {
                return new ItemStack(BlockType.FENCE_GATE, 1);
            }
        }

        // 8 Planks: Chest
        if (plankCount == 8 && countNonEmpty == 8) {
            if (isPlank(0) && isPlank(1) && isPlank(2) && isPlank(3) && isPlank(5) && isPlank(6) && isPlank(7) && isPlank(8) && benchSlots[4].isEmpty()) {
                return new ItemStack(BlockType.CHEST, 1);
            }
        }

        return null;
    }

    private void placeInBench(Player player, BlockType type, int... slots) {
        for (int s : slots) {
            if (player.getInventory().getItemCount(type) >= 1) {
                player.getInventory().removeItem(type, 1);
                benchSlots[s].setType(type);
                benchSlots[s].setCount(1);
            }
        }
    }

    private void populate3x3Recipe(CraftingRecipe r, Player player) {
        if (!r.canCraft(player.getInventory())) return;
        returnBenchSlotsToInventory(player);

        BlockType out = r.getOutput().getType();
        if (out == BlockType.WOODEN_PICKAXE) {
            placeInBench(player, BlockType.PLANKS, 0, 1, 2);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.WOODEN_AXE) {
            placeInBench(player, BlockType.PLANKS, 0, 1, 3);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.WOODEN_SHOVEL) {
            placeInBench(player, BlockType.PLANKS, 1);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.WOODEN_SWORD) {
            placeInBench(player, BlockType.PLANKS, 1, 4);
            placeInBench(player, BlockType.STICK, 7);
        } else if (out == BlockType.WOODEN_HOE) {
            placeInBench(player, BlockType.PLANKS, 0, 1);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.BOAT) {
            placeInBench(player, BlockType.PLANKS, 3, 5, 6, 7, 8);
        } else if (out == BlockType.CHEST) {
            placeInBench(player, BlockType.PLANKS, 0, 1, 2, 3, 5, 6, 7, 8);
        } else if (out == BlockType.WOODEN_DOOR) {
            placeInBench(player, BlockType.PLANKS, 0, 1, 3, 4, 6, 7);
        } else if (out == BlockType.TRAPDOOR) {
            placeInBench(player, BlockType.PLANKS, 3, 4, 5, 6, 7, 8);
        } else if (out == BlockType.LADDER) {
            placeInBench(player, BlockType.STICK, 0, 2, 3, 4, 5, 6, 8);
        } else if (out == BlockType.FENCE) {
            placeInBench(player, BlockType.PLANKS, 3, 5, 6, 8);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.FENCE_GATE) {
            placeInBench(player, BlockType.STICK, 3, 5, 6, 8);
            placeInBench(player, BlockType.PLANKS, 4, 7);
        } else if (out == BlockType.WOODEN_SLAB) {
            placeInBench(player, BlockType.PLANKS, 6, 7, 8);
        } else if (out == BlockType.WOODEN_STAIRS) {
            placeInBench(player, BlockType.PLANKS, 0, 3, 4, 6, 7, 8);
        } else if (out == BlockType.BOWL) {
            placeInBench(player, BlockType.PLANKS, 3, 5, 7);
        } else if (out == BlockType.CRAFTING_TABLE) {
            placeInBench(player, BlockType.PLANKS, 0, 1, 3, 4);
        } else if (out == BlockType.PLANKS) {
            placeInBench(player, BlockType.WOOD, 4);
        } else if (out == BlockType.STICK) {
            placeInBench(player, BlockType.PLANKS, 1, 4);
        } else if (out == BlockType.WOODEN_PRESSURE_PLATE) {
            placeInBench(player, BlockType.PLANKS, 3, 4);
        } else if (out == BlockType.STONE_PICKAXE) {
            placeInBench(player, BlockType.COBBLESTONE, 0, 1, 2);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.STONE_AXE) {
            placeInBench(player, BlockType.COBBLESTONE, 0, 1, 3);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.STONE_SHOVEL) {
            placeInBench(player, BlockType.COBBLESTONE, 1);
            placeInBench(player, BlockType.STICK, 4, 7);
        } else if (out == BlockType.STONE_SWORD) {
            placeInBench(player, BlockType.COBBLESTONE, 1, 4);
            placeInBench(player, BlockType.STICK, 7);
        } else if (out == BlockType.FURNACE) {
            placeInBench(player, BlockType.COBBLESTONE, 0, 1, 2, 3, 5, 6, 7, 8);
        } else if (out == BlockType.WOODEN_BUTTON) {
            placeInBench(player, BlockType.PLANKS, 4);
        }
    }

    public boolean handleMouseClick(double mx, double my, Player player, int windowWidth, int windowHeight) {
        return handleMouseClick(mx, my, GLFW_MOUSE_BUTTON_LEFT, player, windowWidth, windowHeight);
    }

    public boolean handleMouseClick(double mx, double my, int button, Player player, int windowWidth, int windowHeight) {
        if (!inventoryOpen && !craftingTableOpen) return false;

        float scale = 2.4f;
        float invW = 176.0f * scale;
        float invH = 166.0f * scale;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        // --- Handle Crafting Table (3x3) clicks ---
        if (craftingTableOpen) {
            // 1. Recipe book toggle button
            float rbX = ix + 8.0f * scale;
            float rbY = iy + 35.0f * scale;
            float rbSize = 20.0f * scale;
            if (mx >= rbX && mx <= rbX + rbSize && my >= rbY && my <= rbY + rbSize) {
                recipeBookOpen = !recipeBookOpen;
                return true;
            }

            // 2. Recipe book clicks (if open)
            if (recipeBookOpen) {
                float popW = 126.0f * scale;
                float popX = ix - popW - 6.0f;
                float popY = iy;
                float slotW = 22.0f * scale;
                float slotStep = 26.0f * scale;
                float startGridX = popX + 11.0f * scale;
                float startGridY = popY + 20.0f * scale;

                for (int i = 0; i < recipes.size(); i++) {
                    int row = i / 4;
                    int col = i % 4;
                    float sx = startGridX + col * slotStep;
                    float sy = startGridY + row * slotStep;

                    if (mx >= sx && mx <= sx + slotW && my >= sy && my <= sy + slotW) {
                        CraftingRecipe r = recipes.get(i);
                        populate3x3Recipe(r, player);
                        return true;
                    }
                }
            }

            // 3. 3x3 Crafting grid slots
            float gridX = ix + 30.0f * scale;
            float gridY = iy + 17.0f * scale;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slotIdx = r * 3 + c;
                    float sx = gridX + c * 18.0f * scale;
                    float sy = gridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        handleSlotClick(benchSlots[slotIdx], button);
                        return true;
                    }
                }
            }

            // 4. Result Slot
            float resX = ix + 124.0f * scale;
            float resY = iy + 31.0f * scale;
            float resSize = 24.0f * scale;
            if (mx >= resX && mx <= resX + resSize && my >= resY && my <= resY + resSize) {
                ItemStack res = get3x3CraftingResult();
                if (res != null && !res.isEmpty()) {
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    if (res.getType() == BlockType.WOODEN_SWORD || res.getType() == BlockType.STONE_SWORD) {
                        no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.TIME_TO_STRIKE);
                    } else if (res.getType() == BlockType.FURNACE) {
                        no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                    }

                    if (carriedItem.isEmpty()) {
                        carriedItem.setType(res.getType());
                        carriedItem.setCount(res.getCount());
                        for (ItemStack s : benchSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                    } else if (carriedItem.getType() == res.getType() && carriedItem.getCount() + res.getCount() <= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                        carriedItem.add(res.getCount());
                        for (ItemStack s : benchSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                    }
                }
                return true;
            }

            // 5. Main Inventory Grid (3 rows x 9 columns)
            float mainInvX = ix + 8.0f * scale;
            float mainInvY = iy + 84.0f * scale;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    float sx = mainInvX + col * 18.0f * scale;
                    float sy = mainInvY + row * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        handleSlotClick(player.getInventory().getSlot(slotIndex), button);
                        return true;
                    }
                }
            }

            // 6. Hotbar Grid in Inventory (1 row x 9 columns)
            float hotbarInvY = iy + 142.0f * scale;
            for (int col = 0; col < 9; col++) {
                float sx = mainInvX + col * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                    handleSlotClick(player.getInventory().getSlot(col), button);
                    return true;
                }
            }

            return true;
        }

        // Recipe book toggle button (under crafting grid)
        float rbX = ix + 104.0f * scale;
        float rbY = iy + 61.0f * scale;
        float rbSize = 20.0f * scale;

        if (mx >= rbX && mx <= rbX + rbSize && my >= rbY && my <= rbY + rbSize) {
            recipeBookOpen = !recipeBookOpen;
            return true;
        }

        // Crafting Recipe Book clicks if open
        if (recipeBookOpen) {
            float popW = 126.0f * scale;
            float popX = ix - popW - 6.0f;
            float popY = iy;

            float slotW = 22.0f * scale;
            float slotStep = 26.0f * scale;
            float startGridX = popX + 11.0f * scale;
            float startGridY = popY + 20.0f * scale;

            for (int i = 0; i < recipes.size(); i++) {
                int row = i / 4;
                int col = i % 4;
                float sx = startGridX + col * slotStep;
                float sy = startGridY + row * slotStep;

                if (mx >= sx && mx <= sx + slotW && my >= sy && my <= sy + slotW) {
                    CraftingRecipe r = recipes.get(i);
                    // 2x2 recipes: populate the 2x2 grid from inventory
                    if (r.getOutput().getType() == BlockType.PLANKS) {
                        if (player.getInventory().getItemCount(BlockType.WOOD) >= 1) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.WOOD, 1);
                            craftSlots[0].setType(BlockType.WOOD);
                            craftSlots[0].setCount(1);
                        }
                    } else if (r.getOutput().getType() == BlockType.CRAFTING_TABLE) {
                        if (player.getInventory().getItemCount(BlockType.PLANKS) >= 4) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.PLANKS, 4);
                            for (int k = 0; k < 4; k++) {
                                craftSlots[k].setType(BlockType.PLANKS);
                                craftSlots[k].setCount(1);
                            }
                        }
                    } else if (r.getOutput().getType() == BlockType.STICK) {
                        if (player.getInventory().getItemCount(BlockType.PLANKS) >= 2) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.PLANKS, 2);
                            craftSlots[0].setType(BlockType.PLANKS);
                            craftSlots[0].setCount(1);
                            craftSlots[2].setType(BlockType.PLANKS);
                            craftSlots[2].setCount(1);
                        }
                    } else if (r.getOutput().getType() == BlockType.WOODEN_PRESSURE_PLATE) {
                        if (player.getInventory().getItemCount(BlockType.PLANKS) >= 2) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.PLANKS, 2);
                            craftSlots[0].setType(BlockType.PLANKS);
                            craftSlots[0].setCount(1);
                            craftSlots[1].setType(BlockType.PLANKS);
                            craftSlots[1].setCount(1);
                        }
                    } else if (r.getOutput().getType() == BlockType.WOODEN_BUTTON) {
                        if (player.getInventory().getItemCount(BlockType.PLANKS) >= 1) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.PLANKS, 1);
                            craftSlots[0].setType(BlockType.PLANKS);
                            craftSlots[0].setCount(1);
                        }
                    } else {
                        // 3x3 recipe (tools, boat, chest, door, etc.)
                        if (r.canCraft(player.getInventory())) {
                            r.craft(player.getInventory());
                        }
                    }
                    return true;
                }
            }
        }

        // 2x2 Crafting Grid Slots
        float craftGridX = ix + 98.0f * scale;
        float craftGridY = iy + 18.0f * scale;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                int slotIdx = r * 2 + c;
                float sx = craftGridX + c * 18.0f * scale;
                float sy = craftGridY + r * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                    handleSlotClick(craftSlots[slotIdx], button);
                    return true;
                }
            }
        }

        // Crafting Result Click
        float resultSlotX = ix + 152.0f * scale;
        float resultSlotY = iy + 26.0f * scale;
        float resSize = 20.0f * scale;

        if (mx >= resultSlotX && mx <= resultSlotX + resSize && my >= resultSlotY && my <= resultSlotY + resSize) {
            ItemStack res = getCraftingResult();
            if (res != null && !res.isEmpty()) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                if (carriedItem.isEmpty()) {
                    carriedItem.setType(res.getType());
                    carriedItem.setCount(res.getCount());
                    for (ItemStack s : craftSlots) {
                        if (!s.isEmpty()) s.add(-1);
                    }
                } else if (carriedItem.getType() == res.getType() && carriedItem.getCount() + res.getCount() <= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                    carriedItem.add(res.getCount());
                    for (ItemStack s : craftSlots) {
                        if (!s.isEmpty()) s.add(-1);
                    }
                }
            }
            return true;
        }

        // Main Inventory Grid (3 rows x 9 columns)
        float mainInvX = ix + 8.0f * scale;
        float mainInvY = iy + 84.0f * scale;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                float sx = mainInvX + col * 18.0f * scale;
                float sy = mainInvY + row * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                    handleSlotClick(player.getInventory().getSlot(slotIndex), button);
                    return true;
                }
            }
        }

        // Hotbar Grid in Inventory (1 row x 9 columns)
        float hotbarInvY = iy + 142.0f * scale;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * scale;
            if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                handleSlotClick(player.getInventory().getSlot(col), button);
                return true;
            }
        }

        return true;
    }

    public void render(int windowWidth, int windowHeight, float mouseX, float mouseY, Player player, TextureAtlas atlas, no.minecraft.world.World world, no.minecraft.chat.ChatManager chatManager) {
        render(windowWidth, windowHeight, mouseX, mouseY, player, atlas, world, chatManager, this.lastFps, this.lastTargetedHit);
    }

    public void render(int windowWidth, int windowHeight, float mouseX, float mouseY, Player player, TextureAtlas atlas, no.minecraft.world.World world, no.minecraft.chat.ChatManager chatManager, int fps, no.minecraft.player.Raycast.HitResult targetedHit) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.lastFps = fps;
        this.lastTargetedHit = targetedHit;
        render(windowWidth, windowHeight, player, atlas, world, chatManager);
    }

    public void render(int windowWidth, int windowHeight, Player player, TextureAtlas atlas, no.minecraft.world.World world, no.minecraft.chat.ChatManager chatManager) {
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Matrix4f ortho = new Matrix4f().ortho(0, windowWidth, windowHeight, 0, -1, 1);

        hudShader.bind();
        hudShader.setUniform("uOrtho", ortho);

        List<Float> geom = new ArrayList<>();
        List<Float> tex = new ArrayList<>();
        List<Float> overlayGeom = new ArrayList<>();

        // 1. Red damage/death flash overlay
        if (player.getDeathFlashTimer() > 0) {
            float alpha = Math.min(player.getDeathFlashTimer() / 1.5f, 0.55f);
            addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0.85f, 0.08f, 0.08f, alpha);
        }

        // 2. Minecraft Crosshair in center (only when inventory is closed)
        if (!isInventoryOpen()) {
            float cx = windowWidth / 2.0f;
            float cy = windowHeight / 2.0f;
            if (showDebugInfo) {
                drawDebugCrosshair(geom, cx, cy);
            } else {
                drawMinecraftCrosshair(geom, cx, cy);
            }
        }

        // 3. Minecraft HUD (Hotbar + Hearts + Hunger Bar + XP Bar) as shown in reference image 2
        renderMinecraftHUD(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);

        // 3b. Ender Dragon Boss Bar in The End
        if (world != null && world.getCurrentDimension() == no.minecraft.world.Dimension.THE_END) {
            renderBossBar(geom, overlayGeom, windowWidth, world);
        }

        // 3c. Victory / Game Over screen if Ender Dragon is defeated
        if (world != null && world.isGameWon()) {
            renderVictoryOverlay(geom, overlayGeom, windowWidth, windowHeight);
        }

        // 3d. Chat Log and Chat Input field (Minecraft style bottom-left)
        if (chatManager != null) {
            renderChat(geom, overlayGeom, windowWidth, windowHeight, chatManager);
        }

        // 3e. F3 Debug Screen (authentic Minecraft Java Edition)
        if (showDebugInfo) {
            renderDebugMenu(geom, overlayGeom, windowWidth, windowHeight, player, world);
        }

        // 4. Minecraft Inventory & Crafting GUI
        if (craftingTableOpen) {
            renderCraftingTableGUI(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);
        } else if (inventoryOpen) {
            renderMinecraftInventoryGUI(geom, tex, overlayGeom, windowWidth, windowHeight, player, atlas);
        }

        // Render Geometry pass (background panels, health, hotbar base)
        hudShader.setUniform("uUseTexture", 0);
        drawVertices(geom);

        // Render Texture pass (item icons)
        hudShader.setUniform("uUseTexture", 1);
        atlas.bind();
        drawVertices(tex);
        atlas.unbind();

        // Render Overlay Geometry pass (stack count numbers, active selection on top of icons)
        hudShader.setUniform("uUseTexture", 0);
        drawVertices(overlayGeom);

        hudShader.unbind();

        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    private void renderMinecraftHUD(List<Float> geom, List<Float> tex, List<Float> overlayGeom, int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        float pScale = 2.4f; // Pixel scale for authentic UI
        float hotbarW = 182.0f * pScale;
        float hotbarH = 22.0f * pScale;
        float hx = (windowWidth - hotbarW) / 2.0f;
        float hy = windowHeight - hotbarH - 8.0f;

        // --- A. Health Bar (10 Hearts on left) ---
        if (player.getGameMode() != GameMode.CREATIVE) {
            float heartY = hy - 18.0f * pScale;
            int hp = player.getHealth();

            for (int i = 0; i < 10; i++) {
                float heartX = hx + i * (8.0f * pScale);
                int heartVal = (i + 1) * 2;
                int state = 0; // 0=empty, 1=half, 2=full
                if (hp >= heartVal) {
                    state = 2;
                } else if (hp == heartVal - 1) {
                    state = 1;
                }
                drawPixelHeart(geom, heartX, heartY, pScale, state);
            }

            // --- B. Hunger Bar (10 Drumsticks on right) ---
            float hungerY = hy - 18.0f * pScale;
            for (int i = 0; i < 10; i++) {
                float drumX = hx + hotbarW - (10 - i) * (8.0f * pScale) - 1.0f * pScale;
                drawPixelDrumstick(geom, drumX, hungerY, pScale);
            }

            // --- C. Experience (XP) Bar in center ---
            float xpY = hy - 7.0f * pScale;
            drawPixelXPBar(geom, hx, xpY, hotbarW, 5.0f * pScale);
        }

        // --- D. Minecraft Hotbar Frame ---
        drawPixelHotbar(geom, hx, hy, hotbarW, hotbarH, pScale);

        // --- E. Items in Hotbar & Count Display nede til høyre ---
        float slotInnerSize = 16.0f * pScale;
        for (int i = 0; i < 9; i++) {
            float slotX = hx + (3.0f + i * 20.0f) * pScale;
            float slotY = hy + 3.0f * pScale;

            BlockType block = (player.getGameMode() == GameMode.CREATIVE)
                    ? Player.CREATIVE_HOTBAR_BLOCKS[i]
                    : player.getInventory().getSlot(i).getType();

            int count = (player.getGameMode() == GameMode.CREATIVE)
                    ? -1
                    : player.getInventory().getSlot(i).getCount();

            if (block != null && block != BlockType.AIR && (count > 0 || count == -1)) {
                int tileId = block.getTexture(BlockType.Face.TOP);
                float[] uv = TextureAtlas.getUVs(tileId);
                addRect(tex, slotX, slotY, slotInnerSize, slotInnerSize, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);

                // Draw stack count number directly in the bottom-right corner of the slot on TOP of texture
                if (count > 0 && !block.isDamageable()) {
                    float numRight = hx + (i * 20.0f + 18.5f) * pScale;
                    float numBottom = hy + 18.5f * pScale;
                    drawMinecraftNumber(overlayGeom, count, numRight, numBottom, pScale * 0.95f);
                }

                // Draw durability bar if damaged tool
                if (player.getGameMode() != GameMode.CREATIVE) {
                    ItemStack st = player.getInventory().getSlot(i);
                    if (st.getType().isDamageable() && st.getDamage() > 0) {
                        float barW = 12.0f * pScale;
                        float barH = 1.2f * pScale;
                        float bx = slotX + 2.0f * pScale;
                        float by = slotY + 13.0f * pScale;
                        float ratio = st.getDurabilityRatio();
                        // Dark background
                        addRect(overlayGeom, bx, by, barW, barH, 0, 0, 0, 0, 0, 0, 0, 1.0f);
                        // Health color: Green (high) -> Yellow (mid) -> Red (low)
                        float r = ratio < 0.5f ? 1.0f : (1.0f - ratio) * 2.0f;
                        float g = ratio > 0.5f ? 1.0f : ratio * 2.0f;
                        addRect(overlayGeom, bx, by, barW * ratio, barH, 0, 0, 0, 0, r, g, 0.0f, 1.0f);
                    }
                }
            }

            // Active Slot Selection Box (Uthevet hvit 3D ramme)
            if (i == player.getSelectedSlot()) {
                float selX = hx + (i * 20.0f - 1.0f) * pScale;
                float selY = hy - 1.0f * pScale;
                float selW = 24.0f * pScale;
                float selH = 24.0f * pScale;
                drawPixelActiveSelection(overlayGeom, selX, selY, selW, selH, pScale);
            }
        }
    }

    private void renderMinecraftInventoryGUI(List<Float> geom, List<Float> tex, List<Float> overlayGeom, int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        float p = 2.4f; // Scale
        float invW = 176.0f * p;
        float invH = 166.0f * p;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        // 1. Dark background overlay
        addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0, 0, 0, 0.65f);

        // 2. Main Window Panel (Gray with 3D Bevel)
        drawMinecraftWindowFrame(geom, ix, iy, invW, invH, p);

        // 3. Armor Slots (4 vertical slots on left)
        float armorX = ix + 8.0f * p;
        for (int i = 0; i < 4; i++) {
            float armorY = iy + (8.0f + i * 18.0f) * p;
            drawPixelSlot(geom, armorX, armorY, 18.0f * p, p);
            drawArmorSilhouette(geom, armorX + 2.0f * p, armorY + 2.0f * p, i, p);
        }

        // 4. Player Preview Box (Black box with 2D Steve character)
        float playerBoxX = ix + 26.0f * p;
        float playerBoxY = iy + 8.0f * p;
        float playerBoxW = 51.0f * p;
        float playerBoxH = 70.0f * p;

        // Dark player viewport with inset border
        addRect(geom, playerBoxX, playerBoxY, playerBoxW, playerBoxH, 0, 0, 0, 0, 0.05f, 0.05f, 0.05f, 1.0f);
        drawInsetBorder(geom, playerBoxX, playerBoxY, playerBoxW, playerBoxH, p);

        // Draw Steve figure inside viewport
        drawPixelSteve(geom, playerBoxX + 16.0f * p, playerBoxY + 8.0f * p, p);

        // 5. Shield Slot (Off-hand)
        float shieldX = ix + 77.0f * p;
        float shieldY = iy + 62.0f * p;
        drawPixelSlot(geom, shieldX, shieldY, 18.0f * p, p);
        drawShieldSilhouette(geom, shieldX + 3.0f * p, shieldY + 3.0f * p, p);

        // 6. Crafting Title
        drawPixelCraftingTitle(geom, ix + 97.0f * p, iy + 6.0f * p, p);

        // 7. 2x2 Crafting Grid
        float craftGridX = ix + 98.0f * p;
        float craftGridY = iy + 18.0f * p;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                int slotIdx = r * 2 + c;
                float sx = craftGridX + c * 18.0f * p;
                float sy = craftGridY + r * 18.0f * p;
                drawPixelSlot(geom, sx, sy, 18.0f * p, p);

                ItemStack stack = craftSlots[slotIdx];
                if (!stack.isEmpty()) {
                    int tId = stack.getType().getTexture(BlockType.Face.TOP);
                    float[] uv = TextureAtlas.getUVs(tId);
                    addRect(tex, sx + 2.0f * p, sy + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                    if (stack.getCount() > 0) {
                        drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, sy + 17.0f * p, p * 0.95f);
                    }
                }
            }
        }

        // 8. Crafting Arrow ➔
        float arrowX = ix + 135.0f * p;
        float arrowY = iy + 28.0f * p;
        drawPixelCraftingArrow(geom, arrowX, arrowY, p);

        // 9. Crafting Result Slot
        float resultSlotX = ix + 152.0f * p;
        float resultSlotY = iy + 26.0f * p;
        drawPixelSlot(geom, resultSlotX, resultSlotY, 20.0f * p, p);

        // ONLY show result if 2x2 grid contains a valid recipe!
        ItemStack craftResult = getCraftingResult();
        if (craftResult != null && !craftResult.isEmpty()) {
            BlockType outBlock = craftResult.getType();
            int tileId = outBlock.getTexture(BlockType.Face.TOP);
            float[] uv = TextureAtlas.getUVs(tileId);
            addRect(tex, resultSlotX + 3.0f * p, resultSlotY + 3.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
            if (craftResult.getCount() > 0) {
                drawMinecraftNumber(overlayGeom, craftResult.getCount(), resultSlotX + 19.0f * p, resultSlotY + 19.0f * p, p * 0.95f);
            }
        }

        // 10. Recipe Book Button (Green Book Icon)
        float rbX = ix + 104.0f * p;
        float rbY = iy + 61.0f * p;
        drawPixelRecipeBookButton(geom, rbX, rbY, 20.0f * p, p);

        // 11. Main Inventory Grid (3 rows x 9 columns)
        float mainInvX = ix + 8.0f * p;
        float mainInvY = iy + 84.0f * p;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                float sx = mainInvX + col * 18.0f * p;
                float sy = mainInvY + row * 18.0f * p;
                drawPixelSlot(geom, sx, sy, 18.0f * p, p);

                ItemStack stack = player.getInventory().getSlot(slotIndex);
                if (!stack.isEmpty()) {
                    int tId = stack.getType().getTexture(BlockType.Face.TOP);
                    float[] uv = TextureAtlas.getUVs(tId);
                    addRect(tex, sx + 2.0f * p, sy + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                    if (stack.getCount() > 0) {
                        drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, sy + 17.0f * p, p * 0.95f);
                    }
                }
            }
        }

        // 12. Hotbar Grid in Inventory (1 row x 9 columns)
        float hotbarInvY = iy + 142.0f * p;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * p;
            drawPixelSlot(geom, sx, hotbarInvY, 18.0f * p, p);

            ItemStack stack = player.getInventory().getSlot(col);
            if (!stack.isEmpty()) {
                int tId = stack.getType().getTexture(BlockType.Face.TOP);
                float[] uv = TextureAtlas.getUVs(tId);
                addRect(tex, sx + 2.0f * p, hotbarInvY + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                if (stack.getCount() > 0) {
                    drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, hotbarInvY + 17.0f * p, p * 0.95f);
                }
            }
        }

        // 13. Pop-out Recipe Book panel if toggled
        if (recipeBookOpen) {
            float popW = 126.0f * p;
            float popX = ix - popW - 6.0f;
            float popY = iy;
            drawMinecraftWindowFrame(geom, popX, popY, popW, invH, p);

            // Small header bar for recipe book
            addRect(geom, popX + 8.0f * p, popY + 8.0f * p, popW - 16.0f * p, 6.0f * p, 0, 0, 0, 0, 0.28f, 0.28f, 0.28f, 1.0f);
            drawInsetBorder(geom, popX + 8.0f * p, popY + 8.0f * p, popW - 16.0f * p, 6.0f * p, p);

            float slotW = 22.0f * p;
            float slotStep = 26.0f * p;
            float startGridX = popX + 11.0f * p;
            float startGridY = popY + 20.0f * p;

            for (int i = 0; i < recipes.size(); i++) {
                int row = i / 4;
                int col = i % 4;
                float sx = startGridX + col * slotStep;
                float sy = startGridY + row * slotStep;

                CraftingRecipe r = recipes.get(i);
                boolean canCraft = r.canCraft(player.getInventory());

                // Slot background
                drawPixelSlot(geom, sx, sy, slotW, p);

                // Highlight border if craftable
                if (canCraft) {
                    addRect(geom, sx, sy, slotW, p, 0, 0, 0, 0, 0.30f, 0.88f, 0.30f, 1.0f);
                    addRect(geom, sx, sy + slotW - p, slotW, p, 0, 0, 0, 0, 0.20f, 0.65f, 0.20f, 1.0f);
                    addRect(geom, sx, sy, p, slotW, 0, 0, 0, 0, 0.30f, 0.88f, 0.30f, 1.0f);
                    addRect(geom, sx + slotW - p, sy, p, slotW, 0, 0, 0, 0, 0.20f, 0.65f, 0.20f, 1.0f);
                }

                // Item icon
                int tId = r.getOutput().getType().getTexture(BlockType.Face.TOP);
                float[] uv = TextureAtlas.getUVs(tId);
                float bright = canCraft ? 1.0f : 0.45f;
                float alpha = canCraft ? 1.0f : 0.55f;
                addRect(tex, sx + 3.0f * p, sy + 3.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], bright, bright, bright, alpha);

                // Stack count if > 1
                if (r.getOutput().getCount() > 1) {
                    drawMinecraftNumber(overlayGeom, r.getOutput().getCount(), sx + 20.5f * p, sy + 20.5f * p, p * 0.95f);
                }
            }
        }

        // 14. Carried item on mouse cursor
        if (!carriedItem.isEmpty()) {
            int tId = carriedItem.getType().getTexture(BlockType.Face.TOP);
            float[] uv = TextureAtlas.getUVs(tId);
            float cx = mouseX - 8.0f * p;
            float cy = mouseY - 8.0f * p;
            addRect(tex, cx, cy, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1.0f, 1.0f, 1.0f, 1.0f);
            if (carriedItem.getCount() > 0) {
                drawMinecraftNumber(overlayGeom, carriedItem.getCount(), cx + 16.0f * p, cy + 16.0f * p, p * 0.95f);
            }
        }
    }

    private void renderCraftingTableGUI(List<Float> geom, List<Float> tex, List<Float> overlayGeom, int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        float p = 2.4f; // Scale
        float invW = 176.0f * p;
        float invH = 166.0f * p;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        // 1. Dark background overlay
        addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0, 0, 0, 0.65f);

        // 2. Main Window Panel
        drawMinecraftWindowFrame(geom, ix, iy, invW, invH, p);

        // 3. Title: "Arbeidsbenk" / Crafting Table
        drawPixelCraftingTitle(geom, ix + 28.0f * p, iy + 6.0f * p, p);

        // 4. 3x3 Crafting Grid
        float gridX = ix + 30.0f * p;
        float gridY = iy + 17.0f * p;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int slotIdx = r * 3 + c;
                float sx = gridX + c * 18.0f * p;
                float sy = gridY + r * 18.0f * p;
                drawPixelSlot(geom, sx, sy, 18.0f * p, p);

                ItemStack stack = benchSlots[slotIdx];
                if (!stack.isEmpty()) {
                    int tId = stack.getType().getTexture(BlockType.Face.TOP);
                    float[] uv = TextureAtlas.getUVs(tId);
                    addRect(tex, sx + 2.0f * p, sy + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                    if (stack.getCount() > 0) {
                        drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, sy + 17.0f * p, p * 0.95f);
                    }
                }
            }
        }

        // 5. Crafting Arrow ➔
        float arrowX = ix + 90.0f * p;
        float arrowY = iy + 35.0f * p;
        drawPixelCraftingArrow(geom, arrowX, arrowY, p);

        // 6. Crafting Result Slot
        float resX = ix + 124.0f * p;
        float resY = iy + 31.0f * p;
        drawPixelSlot(geom, resX, resY, 24.0f * p, p);

        // ONLY show result when 3x3 grid ingredients form a valid recipe!
        ItemStack craftResult = get3x3CraftingResult();
        if (craftResult != null && !craftResult.isEmpty()) {
            BlockType outBlock = craftResult.getType();
            int tileId = outBlock.getTexture(BlockType.Face.TOP);
            float[] uv = TextureAtlas.getUVs(tileId);
            addRect(tex, resX + 4.0f * p, resY + 4.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
            if (craftResult.getCount() > 0) {
                drawMinecraftNumber(overlayGeom, craftResult.getCount(), resX + 22.0f * p, resY + 22.0f * p, p * 0.95f);
            }
        }

        // 7. Recipe Book Button (Green Book Icon)
        float rbX = ix + 8.0f * p;
        float rbY = iy + 35.0f * p;
        drawPixelRecipeBookButton(geom, rbX, rbY, 20.0f * p, p);

        // 8. Main Inventory Grid (3 rows x 9 columns)
        float mainInvX = ix + 8.0f * p;
        float mainInvY = iy + 84.0f * p;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                float sx = mainInvX + col * 18.0f * p;
                float sy = mainInvY + row * 18.0f * p;
                drawPixelSlot(geom, sx, sy, 18.0f * p, p);

                ItemStack stack = player.getInventory().getSlot(slotIndex);
                if (!stack.isEmpty()) {
                    int tId = stack.getType().getTexture(BlockType.Face.TOP);
                    float[] uv = TextureAtlas.getUVs(tId);
                    addRect(tex, sx + 2.0f * p, sy + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                    if (stack.getCount() > 0) {
                        drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, sy + 17.0f * p, p * 0.95f);
                    }
                }
            }
        }

        // 9. Hotbar Grid in Inventory (1 row x 9 columns)
        float hotbarInvY = iy + 142.0f * p;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * p;
            drawPixelSlot(geom, sx, hotbarInvY, 18.0f * p, p);

            ItemStack stack = player.getInventory().getSlot(col);
            if (!stack.isEmpty()) {
                int tId = stack.getType().getTexture(BlockType.Face.TOP);
                float[] uv = TextureAtlas.getUVs(tId);
                addRect(tex, sx + 2.0f * p, hotbarInvY + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
                if (stack.getCount() > 0) {
                    drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, hotbarInvY + 17.0f * p, p * 0.95f);
                }
            }
        }

        // 10. Pop-out Recipe Book panel if toggled
        if (recipeBookOpen) {
            float popW = 126.0f * p;
            float popX = ix - popW - 6.0f;
            float popY = iy;
            drawMinecraftWindowFrame(geom, popX, popY, popW, invH, p);

            // Small header bar for recipe book
            addRect(geom, popX + 8.0f * p, popY + 8.0f * p, popW - 16.0f * p, 6.0f * p, 0, 0, 0, 0, 0.28f, 0.28f, 0.28f, 1.0f);
            drawInsetBorder(geom, popX + 8.0f * p, popY + 8.0f * p, popW - 16.0f * p, 6.0f * p, p);

            float slotW = 22.0f * p;
            float slotStep = 26.0f * p;
            float startGridX = popX + 11.0f * p;
            float startGridY = popY + 20.0f * p;

            for (int i = 0; i < recipes.size(); i++) {
                int row = i / 4;
                int col = i % 4;
                float sx = startGridX + col * slotStep;
                float sy = startGridY + row * slotStep;

                CraftingRecipe r = recipes.get(i);
                boolean canCraft = r.canCraft(player.getInventory());

                // Slot background
                drawPixelSlot(geom, sx, sy, slotW, p);

                // Highlight border if craftable
                if (canCraft) {
                    addRect(geom, sx, sy, slotW, p, 0, 0, 0, 0, 0.30f, 0.88f, 0.30f, 1.0f);
                    addRect(geom, sx, sy + slotW - p, slotW, p, 0, 0, 0, 0, 0.20f, 0.65f, 0.20f, 1.0f);
                    addRect(geom, sx, sy, p, slotW, 0, 0, 0, 0, 0.30f, 0.88f, 0.30f, 1.0f);
                    addRect(geom, sx + slotW - p, sy, p, slotW, 0, 0, 0, 0, 0.20f, 0.65f, 0.20f, 1.0f);
                }

                // Item icon
                int tId = r.getOutput().getType().getTexture(BlockType.Face.TOP);
                float[] uv = TextureAtlas.getUVs(tId);
                float bright = canCraft ? 1.0f : 0.45f;
                float alpha = canCraft ? 1.0f : 0.55f;
                addRect(tex, sx + 3.0f * p, sy + 3.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], bright, bright, bright, alpha);

                // Stack count if > 1
                if (r.getOutput().getCount() > 1) {
                    drawMinecraftNumber(overlayGeom, r.getOutput().getCount(), sx + 20.5f * p, sy + 20.5f * p, p * 0.95f);
                }
            }
        }

        // 11. Carried item on mouse cursor
        if (!carriedItem.isEmpty()) {
            int tId = carriedItem.getType().getTexture(BlockType.Face.TOP);
            float[] uv = TextureAtlas.getUVs(tId);
            float cx = mouseX - 8.0f * p;
            float cy = mouseY - 8.0f * p;
            addRect(tex, cx, cy, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1.0f, 1.0f, 1.0f, 1.0f);
            if (carriedItem.getCount() > 0) {
                drawMinecraftNumber(overlayGeom, carriedItem.getCount(), cx + 16.0f * p, cy + 16.0f * p, p * 0.95f);
            }
        }
    }

    // --- Pixel Helper Rendering Methods ---

    private void drawPixelHotbar(List<Float> g, float x, float y, float w, float h, float p) {
        // Outer dark border
        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.12f, 0.12f, 0.12f, 1.0f);
        // Inner base fill
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, 0.54f, 0.54f, 0.54f, 1.0f);

        // Slots grid inside hotbar
        for (int i = 0; i < 9; i++) {
            float sx = x + (1.0f + i * 20.0f) * p;
            // Slot background
            addRect(g, sx + p, y + p, 18.0f * p, 18.0f * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
            // Inset bevel
            addRect(g, sx + p, y + p, 18.0f * p, p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
            addRect(g, sx + p, y + p, p, 18.0f * p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
            addRect(g, sx + p, y + 19.0f * p, 18.0f * p, p, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
            addRect(g, sx + 19.0f * p, y + p, p, 18.0f * p, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
        }
    }

    private void drawPixelActiveSelection(List<Float> g, float x, float y, float w, float h, float p) {
        // Minecraft white 3D highlight frame
        addRect(g, x, y, w, p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        addRect(g, x, y, p, h, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        addRect(g, x + w - p, y, p, h, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f, 1.0f);
        addRect(g, x, y + h - p, w, p, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f, 1.0f);

        // Inner frame
        addRect(g, x + 2 * p, y + 2 * p, w - 4 * p, p, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f, 1.0f);
        addRect(g, x + 2 * p, y + 2 * p, p, h - 4 * p, 0, 0, 0, 0, 0.4f, 0.4f, 0.4f, 1.0f);
        addRect(g, x + w - 3 * p, y + 2 * p, p, h - 4 * p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        addRect(g, x + 2 * p, y + h - 3 * p, w - 4 * p, p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawPixelSlot(List<Float> g, float x, float y, float size, float p) {
        // Slot border & inset bevel
        addRect(g, x, y, size, size, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x, y, size, p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
        addRect(g, x, y, p, size, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
        addRect(g, x, y + size - p, size, p, 0, 0, 0, 0, 0.95f, 0.95f, 0.95f, 1.0f);
        addRect(g, x + size - p, y, p, size, 0, 0, 0, 0, 0.95f, 0.95f, 0.95f, 1.0f);
    }

    private void drawMinecraftWindowFrame(List<Float> g, float x, float y, float w, float h, float p) {
        // Outer border
        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.12f, 0.12f, 0.12f, 1.0f);
        // Base panel
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, 0.776f, 0.776f, 0.776f, 1.0f);
        // Light Bevel (Top & Left)
        addRect(g, x + p, y + p, w - 2 * p, 2 * p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        addRect(g, x + p, y + p, 2 * p, h - 2 * p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
        // Dark Bevel (Bottom & Right)
        addRect(g, x + p, y + h - 3 * p, w - 2 * p, 2 * p, 0, 0, 0, 0, 0.33f, 0.33f, 0.33f, 1.0f);
        addRect(g, x + w - 3 * p, y + p, 2 * p, h - 2 * p, 0, 0, 0, 0, 0.33f, 0.33f, 0.33f, 1.0f);
    }

    private void drawInsetBorder(List<Float> g, float x, float y, float w, float h, float p) {
        addRect(g, x, y, w, p, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);
        addRect(g, x, y, p, h, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);
        addRect(g, x, y + h - p, w, p, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
        addRect(g, x + w - p, y, p, h, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
    }

    private void drawPixelSteve(List<Float> g, float x, float y, float p) {
        // Head (8x8 px)
        addRect(g, x + 4 * p, y, 8 * p, 8 * p, 0, 0, 0, 0, 0.72f, 0.48f, 0.34f, 1.0f); // Skin
        addRect(g, x + 4 * p, y, 8 * p, 3 * p, 0, 0, 0, 0, 0.29f, 0.18f, 0.09f, 1.0f); // Hair
        // Eyes
        addRect(g, x + 5 * p, y + 4 * p, p, p, 0, 0, 0, 0, 1, 1, 1, 1);
        addRect(g, x + 6 * p, y + 4 * p, p, p, 0, 0, 0, 0, 0.1f, 0.2f, 0.8f, 1);
        addRect(g, x + 9 * p, y + 4 * p, p, p, 0, 0, 0, 0, 1, 1, 1, 1);
        addRect(g, x + 10 * p, y + 4 * p, p, p, 0, 0, 0, 0, 0.1f, 0.2f, 0.8f, 1);

        // Body (Torso: Cyan shirt 8x12 px)
        addRect(g, x + 4 * p, y + 8 * p, 8 * p, 12 * p, 0, 0, 0, 0, 0.0f, 0.65f, 0.65f, 1.0f);

        // Arms (4x12 px each)
        addRect(g, x, y + 8 * p, 4 * p, 4 * p, 0, 0, 0, 0, 0.0f, 0.65f, 0.65f, 1.0f);
        addRect(g, x, y + 12 * p, 4 * p, 8 * p, 0, 0, 0, 0, 0.72f, 0.48f, 0.34f, 1.0f);
        addRect(g, x + 12 * p, y + 8 * p, 4 * p, 4 * p, 0, 0, 0, 0, 0.0f, 0.65f, 0.65f, 1.0f);
        addRect(g, x + 12 * p, y + 12 * p, 4 * p, 8 * p, 0, 0, 0, 0, 0.72f, 0.48f, 0.34f, 1.0f);

        // Legs (Blue pants 8x12 px)
        addRect(g, x + 4 * p, y + 20 * p, 8 * p, 12 * p, 0, 0, 0, 0, 0.16f, 0.16f, 0.48f, 1.0f);
        // Shoes (Gray 8x2 px)
        addRect(g, x + 4 * p, y + 32 * p, 8 * p, 2 * p, 0, 0, 0, 0, 0.25f, 0.25f, 0.25f, 1.0f);
    }

    private void drawArmorSilhouette(List<Float> g, float x, float y, int type, float p) {
        // Outline silhouette of armor in empty slots
        float c = 0.42f;
        if (type == 0) { // Helmet
            addRect(g, x + 3 * p, y + 2 * p, 8 * p, 7 * p, 0, 0, 0, 0, c, c, c, 0.5f);
            addRect(g, x + 5 * p, y + 5 * p, 4 * p, 4 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        } else if (type == 1) { // Chestplate
            addRect(g, x + 2 * p, y + 2 * p, 10 * p, 9 * p, 0, 0, 0, 0, c, c, c, 0.5f);
        } else if (type == 2) { // Leggings
            addRect(g, x + 3 * p, y + 2 * p, 8 * p, 10 * p, 0, 0, 0, 0, c, c, c, 0.5f);
            addRect(g, x + 6 * p, y + 5 * p, 2 * p, 7 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        } else if (type == 3) { // Boots
            addRect(g, x + 3 * p, y + 4 * p, 3 * p, 6 * p, 0, 0, 0, 0, c, c, c, 0.5f);
            addRect(g, x + 8 * p, y + 4 * p, 3 * p, 6 * p, 0, 0, 0, 0, c, c, c, 0.5f);
        }
    }

    private void drawShieldSilhouette(List<Float> g, float x, float y, float p) {
        addRect(g, x + 2 * p, y + 2 * p, 8 * p, 8 * p, 0, 0, 0, 0, 0.42f, 0.42f, 0.42f, 0.5f);
    }

    private void drawPixelCraftingTitle(List<Float> g, float x, float y, float p) {
        // "Crafting" text label in Minecraft font style
        addRect(g, x, y, 42 * p, 6 * p, 0, 0, 0, 0, 0.25f, 0.25f, 0.25f, 0.8f);
    }

    private void drawPixelCraftingArrow(List<Float> g, float x, float y, float p) {
        // ➔ Crafting arrow with 3D bevel
        addRect(g, x, y + 4 * p, 12 * p, 4 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 8 * p, y + 1 * p, 3 * p, 3 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 8 * p, y + 8 * p, 3 * p, 3 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 11 * p, y + 3 * p, 2 * p, 6 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
    }

    private void drawPixelRecipeBookButton(List<Float> g, float x, float y, float size, float p) {
        // Minecraft Green Recipe Book Button
        addRect(g, x, y, size, size, 0, 0, 0, 0, 0.12f, 0.12f, 0.12f, 1.0f);
        addRect(g, x + p, y + p, size - 2 * p, size - 2 * p, 0, 0, 0, 0, 0.776f, 0.776f, 0.776f, 1.0f);
        // Green book graphic
        addRect(g, x + 4 * p, y + 4 * p, 12 * p, 11 * p, 0, 0, 0, 0, 0.18f, 0.65f, 0.25f, 1.0f);
        addRect(g, x + 5 * p, y + 14 * p, 10 * p, 2 * p, 0, 0, 0, 0, 0.95f, 0.95f, 0.95f, 1.0f); // pages
    }

    private void drawMinecraftButton(List<Float> g, float x, float y, float w, float h, boolean enabled, float p) {
        float r = enabled ? 0.35f : 0.25f;
        float gr = enabled ? 0.65f : 0.25f;
        float b = enabled ? 0.35f : 0.25f;

        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.12f, 0.12f, 0.12f, 1.0f);
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, r, gr, b, 1.0f);
        addRect(g, x + p, y + p, w - 2 * p, p, 0, 0, 0, 0, r + 0.2f, gr + 0.2f, b + 0.2f, 1.0f);
        addRect(g, x + p, y + h - 2 * p, w - 2 * p, p, 0, 0, 0, 0, r - 0.15f, gr - 0.15f, b - 0.15f, 1.0f);
    }

    private void drawPixelHeart(List<Float> g, float x, float y, float p, int state) {
        // 9x9 Pixel Heart matching Minecraft reference image 2
        int[][] pat = {
                {0,1,1,0,0,0,1,1,0},
                {1,3,2,1,0,1,2,2,1},
                {1,2,2,2,1,2,2,4,1},
                {1,2,2,2,2,2,4,4,1},
                {0,1,2,2,2,4,4,1,0},
                {0,0,1,2,2,4,1,0,0},
                {0,0,0,1,2,1,0,0,0},
                {0,0,0,0,1,0,0,0,0}
        };

        for (int py = 0; py < pat.length; py++) {
            for (int px = 0; px < pat[py].length; px++) {
                int c = pat[py][px];
                if (c == 0) continue;

                float rx = x + px * p;
                float ry = y + py * p;

                if (c == 1) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f); // Black border
                } else {
                    if (state == 0 || (state == 1 && px >= 5)) {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.22f, 0.12f, 0.12f, 1.0f); // Empty
                    } else if (c == 3) {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f); // White sheen
                    } else if (c == 4) {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.65f, 0.05f, 0.05f, 1.0f); // Dark shadow
                    } else {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.95f, 0.12f, 0.12f, 1.0f); // Red
                    }
                }
            }
        }
    }

    private void drawPixelDrumstick(List<Float> g, float x, float y, float p) {
        // 9x9 Pixel Drumstick matching Minecraft reference image 2
        int[][] pat = {
                {0,0,0,0,1,1,1,0,0},
                {0,0,0,1,3,2,2,1,0},
                {0,0,1,3,2,2,2,2,1},
                {0,1,3,2,2,2,2,4,1},
                {0,1,2,2,2,2,4,1,0},
                {1,1,1,1,1,1,1,0,0},
                {0,1,5,5,1,0,0,0,0},
                {1,5,0,0,5,1,0,0,0},
                {1,0,0,0,0,1,0,0,0}
        };

        for (int py = 0; py < pat.length; py++) {
            for (int px = 0; px < pat[py].length; px++) {
                int c = pat[py][px];
                if (c == 0) continue;

                float rx = x + px * p;
                float ry = y + py * p;

                if (c == 1) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f); // Border
                } else if (c == 2) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.58f, 0.35f, 0.18f, 1.0f); // Meat brown
                } else if (c == 3) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.88f, 0.35f, 0.35f, 1.0f); // Meat sheen / red
                } else if (c == 4) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.38f, 0.22f, 0.10f, 1.0f); // Dark shadow
                } else if (c == 5) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.95f, 0.90f, 0.82f, 1.0f); // Bone white
                }
            }
        }
    }

    private void drawPixelXPBar(List<Float> g, float x, float y, float w, float h) {
        // Dark XP background frame
        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f);
        addRect(g, x + 1, y + 1, w - 2, h - 2, 0, 0, 0, 0, 0.12f, 0.25f, 0.15f, 1.0f);
        // Slices of green XP bar
        addRect(g, x + 1, y + 1, w - 2, 2.0f, 0, 0, 0, 0, 0.35f, 0.78f, 0.22f, 1.0f);
        addRect(g, x + 1, y + 3.0f, w - 2, 2.0f, 0, 0, 0, 0, 0.22f, 0.55f, 0.15f, 1.0f);
    }

    private void drawMinecraftCrosshair(List<Float> g, float cx, float cy) {
        float size = 8.0f;
        float th = 2.0f;
        addRect(g, cx - size - 1, cy - th / 2 - 1, size * 2 + 2, th + 2, 0, 0, 0, 0, 0, 0, 0, 0.6f);
        addRect(g, cx - th / 2 - 1, cy - size - 1, th + 2, size * 2 + 2, 0, 0, 0, 0, 0, 0, 0, 0.6f);
        addRect(g, cx - size, cy - th / 2, size * 2, th, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.9f);
        addRect(g, cx - th / 2, cy - size, th, size * 2, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.9f);
    }

    private void drawDebugCrosshair(List<Float> g, float cx, float cy) {
        float arm = 10.0f;
        float th = 2.0f;

        // Dark outline for contrast
        addRect(g, cx - 1.0f, cy - 1.0f, arm + 2.0f, th + 2.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.7f);
        addRect(g, cx - 1.0f, cy - arm - 1.0f, th + 2.0f, arm + 2.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.7f);
        addRect(g, cx - 1.0f, cy - 1.0f, th + 2.0f, arm + 2.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.7f);

        // Center white dot
        addRect(g, cx, cy, th, th, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);

        // Red arm (+X: right)
        addRect(g, cx + th, cy, arm - th, th, 0, 0, 0, 0, 0.95f, 0.15f, 0.15f, 1.0f);

        // Green arm (+Y: up)
        addRect(g, cx, cy - arm + th, th, arm - th, 0, 0, 0, 0, 0.15f, 0.95f, 0.15f, 1.0f);

        // Blue arm (+Z: down)
        addRect(g, cx, cy + th, th, arm - th, 0, 0, 0, 0, 0.25f, 0.45f, 1.0f, 1.0f);
    }

    private void drawMinecraftNumber(List<Float> g, int number, float rightX, float bottomY, float s) {
        String numStr = String.valueOf(number);
        int charWidth = 5;
        int charHeight = 7;
        int charSpacing = 1;

        float totalWidth = numStr.length() * (charWidth * s) + (numStr.length() - 1) * (charSpacing * s);
        float startX = rightX - totalWidth;
        float startY = bottomY - charHeight * s;

        int[][][] font = {
                // 0
                {{1,1,1,1,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,1,1,1,1}},
                // 1
                {{0,0,1,1,0},
                 {0,1,1,1,0},
                 {0,0,1,1,0},
                 {0,0,1,1,0},
                 {0,0,1,1,0},
                 {0,0,1,1,0},
                 {1,1,1,1,1}},
                // 2
                {{1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1},
                 {1,1,1,1,1},
                 {1,0,0,0,0},
                 {1,0,0,0,0},
                 {1,1,1,1,1}},
                // 3
                {{1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1},
                 {1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1},
                 {1,1,1,1,1}},
                // 4
                {{1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1}},
                // 5
                {{1,1,1,1,1},
                 {1,0,0,0,0},
                 {1,0,0,0,0},
                 {1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1},
                 {1,1,1,1,1}},
                // 6
                {{1,1,1,1,1},
                 {1,0,0,0,0},
                 {1,0,0,0,0},
                 {1,1,1,1,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,1,1,1,1}},
                // 7
                {{1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,1,0},
                 {0,0,1,0,0},
                 {0,1,0,0,0},
                 {0,1,0,0,0},
                 {0,1,0,0,0}},
                // 8
                {{1,1,1,1,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,1,1,1,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,1,1,1,1}},
                // 9
                {{1,1,1,1,1},
                 {1,0,0,0,1},
                 {1,0,0,0,1},
                 {1,1,1,1,1},
                 {0,0,0,0,1},
                 {0,0,0,0,1},
                 {1,1,1,1,1}}
        };

        float curX = startX;
        for (int i = 0; i < numStr.length(); i++) {
            char c = numStr.charAt(i);
            int digit = c - '0';
            if (digit < 0 || digit > 9) continue;

            int[][] glyph = font[digit];

            for (int r = 0; r < charHeight; r++) {
                for (int col = 0; col < charWidth; col++) {
                    if (glyph[r][col] == 1) {
                        float px = curX + col * s;
                        float py = startY + r * s;
                        // Drop shadow (Dark gray #3F3F3F)
                        addRect(g, px + s, py + s, s, s, 0, 0, 0, 0, 0.24f, 0.24f, 0.24f, 1.0f);
                        // Main text (Pure White #FFFFFF)
                        addRect(g, px, py, s, s, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
                    }
                }
            }
            curX += (charWidth + charSpacing) * s;
        }
    }

    private void addRect(List<Float> v, float x, float y, float w, float h,
                         float u0, float v0, float u1, float v1,
                         float r, float g, float b, float a) {
        addVertex(v, x, y, u0, v0, r, g, b, a);
        addVertex(v, x, y + h, u0, v1, r, g, b, a);
        addVertex(v, x + w, y + h, u1, v1, r, g, b, a);

        addVertex(v, x, y, u0, v0, r, g, b, a);
        addVertex(v, x + w, y + h, u1, v1, r, g, b, a);
        addVertex(v, x + w, y, u1, v0, r, g, b, a);
    }

    private void addVertex(List<Float> v, float x, float y, float u, float valV, float r, float g, float b, float a) {
        v.add(x);
        v.add(y);
        v.add(u);
        v.add(valV);
        v.add(r);
        v.add(g);
        v.add(b);
        v.add(a);
    }

    private void drawVertices(List<Float> vertices) {
        if (vertices.isEmpty()) return;

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float f : vertices) {
            buffer.put(f);
        }
        buffer.flip();

        glBufferData(GL_ARRAY_BUFFER, buffer, GL_DYNAMIC_DRAW);
        glDrawArrays(GL_TRIANGLES, 0, vertices.size() / 8);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void renderBossBar(List<Float> geom, List<Float> overlayGeom, int windowWidth, no.minecraft.world.World world) {
        no.minecraft.entity.Mob dragon = null;
        for (no.minecraft.entity.Mob m : world.getMobs()) {
            if (m.getType() == no.minecraft.entity.MobType.ENDER_DRAGON) {
                dragon = m;
                break;
            }
        }
        if (dragon == null) return;

        float barW = 360.0f;
        float barH = 12.0f;
        float bx = (windowWidth - barW) / 2.0f;
        float by = 18.0f;

        // Boss Title text: "Ender Dragon"
        String title = "ENDER DRAGON";
        float scale = 1.8f;
        float textW = title.length() * (6.0f * scale);
        float tx = (windowWidth - textW) / 2.0f;
        drawHudText(overlayGeom, title, tx, by - 12.0f, scale, 0.95f, 0.4f, 0.95f);

        // Background dark bar
        addRect(geom, bx - 2, by - 2, barW + 4, barH + 4, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 0.85f);
        addRect(geom, bx, by, barW, barH, 0, 0, 0, 0, 0.22f, 0.08f, 0.25f, 1.0f);

        // Purple Boss Health Fill
        float hpRatio = Math.clamp((float) dragon.getHealth() / dragon.getType().getMaxHealth(), 0.0f, 1.0f);
        float fillW = barW * hpRatio;
        if (fillW > 0) {
            addRect(geom, bx, by, fillW, barH, 0, 0, 0, 0, 0.82f, 0.18f, 0.88f, 1.0f);
            addRect(geom, bx, by, fillW, 3.0f, 0, 0, 0, 0, 0.95f, 0.45f, 1.0f, 1.0f);
        }
    }

    private void renderVictoryOverlay(List<Float> geom, List<Float> overlayGeom, int windowWidth, int windowHeight) {
        // Dark translucent overlay
        addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.75f);

        // Game Over / Victory banners
        String title = "SPILLET ER VUNNET";
        String titleSub = "FREE THE END!";
        float scale = 3.6f;
        float textW = title.length() * (6.0f * scale);
        float tx = (windowWidth - textW) / 2.0f;
        float ty = windowHeight * 0.30f;

        drawHudText(overlayGeom, title, tx, ty, scale, 1.0f, 0.85f, 0.15f);

        float scale2 = 2.2f;
        float textW2 = titleSub.length() * (6.0f * scale2);
        drawHudText(overlayGeom, titleSub, (windowWidth - textW2) / 2.0f, ty + 42.0f, scale2, 0.85f, 0.45f, 0.95f);

        String desc1 = "DRAGEN ER BESEIRET";
        float scale3 = 1.8f;
        drawHudText(overlayGeom, desc1, (windowWidth - desc1.length() * (6.0f * scale3)) / 2.0f, ty + 85.0f, scale3, 0.9f, 0.9f, 0.9f);

        String hint = "TRYKK ESC FOR MENY";
        drawHudText(overlayGeom, hint, (windowWidth - hint.length() * (6.0f * scale3)) / 2.0f, ty + 125.0f, scale3, 0.7f, 0.7f, 0.7f);
    }

    private void drawHudText(List<Float> g, String text, float startX, float startY, float s, float r, float gr, float b) {
        for (int i = 0; i < text.length(); i++) {
            float px = startX + i * (6.0f * s);
            char c = Character.toUpperCase(text.charAt(i));
            drawHudChar(g, c, px + s * 0.5f, startY + s * 0.5f, s, 0.12f, 0.12f, 0.12f);
            drawHudChar(g, c, px, startY, s, r, gr, b);
        }
    }

    private void drawHudChar(List<Float> g, char ch, float x, float y, float s, float r, float gr, float b) {
        int[][] glyph = getGlyph(ch);
        if (glyph == null) return;
        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length; col++) {
                if (glyph[row][col] == 1) {
                    addRect(g, x + col * s, y + row * s, s, s, 0, 0, 0, 0, r, gr, b, 1.0f);
                }
            }
        }
    }

    private void renderChat(List<Float> geom, List<Float> overlayGeom, int windowWidth, int windowHeight, no.minecraft.chat.ChatManager chat) {
        float scale = 1.4f;
        float chatX = 10.0f;
        float chatBottom = windowHeight - (chat.isOpen() ? 32.0f : 80.0f);
        float lineHeight = 12.0f * scale;

        // Render recent messages (up to 8 lines)
        List<no.minecraft.chat.ChatManager.ChatMessage> msgs = chat.getMessages();
        int maxLines = chat.isOpen() ? 12 : 8;
        int startIdx = Math.max(0, msgs.size() - maxLines);

        for (int i = startIdx; i < msgs.size(); i++) {
            no.minecraft.chat.ChatManager.ChatMessage m = msgs.get(i);
            int linePos = i - startIdx;
            float lineY = chatBottom - (msgs.size() - startIdx - linePos) * lineHeight;

            float alpha = chat.isOpen() ? 0.9f : Math.min(1.0f, m.getTimeRemaining() / 2.0f);
            if (alpha <= 0.01f) continue;

            // Translucent dark line background
            float textWidth = m.getText().length() * (6.0f * scale);
            addRect(geom, chatX - 2.0f, lineY - 1.0f, textWidth + 6.0f, lineHeight, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.45f * alpha);

            // Message text
            drawHudText(overlayGeom, m.getText(), chatX, lineY, scale, m.getR(), m.getG(), m.getB());
        }

        // Render Chat Input Box if Chat is Open
        if (chat.isOpen()) {
            float boxY = windowHeight - 24.0f;
            float boxW = windowWidth - 20.0f;
            float boxH = 18.0f;

            // Dark input background with white/gray border
            addRect(geom, chatX - 2.0f, boxY, boxW, boxH, 0, 0, 0, 0, 0.05f, 0.05f, 0.05f, 0.85f);
            addRect(geom, chatX - 2.0f, boxY, boxW, 1.0f, 0, 0, 0, 0, 0.6f, 0.6f, 0.6f, 1.0f);
            addRect(geom, chatX - 2.0f, boxY + boxH, boxW, 1.0f, 0, 0, 0, 0, 0.6f, 0.6f, 0.6f, 1.0f);

            // Prompt cursor text
            boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;
            String prompt = "> " + chat.getInputText() + (blink ? "_" : "");
            drawHudText(overlayGeom, prompt, chatX + 2.0f, boxY + 3.0f, scale, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderDebugMenu(List<Float> geom, List<Float> overlayGeom, int windowWidth, int windowHeight,
                                Player player, no.minecraft.world.World world) {
        if (world == null || player == null) return;

        float scale = 2.7f;
        float lineHeight = 10.0f * scale;
        float startX = 8.0f;
        float startY = 8.0f;

        org.joml.Vector3f pos = player.getPosition();
        int bx = (int) Math.floor(pos.x);
        int by = (int) Math.floor(pos.y);
        int bz = (int) Math.floor(pos.z);

        int cx = Math.floorDiv(bx, 16);
        int cy = Math.floorDiv(by, 16);
        int cz = Math.floorDiv(bz, 16);

        int inCx = Math.floorMod(bx, 16);
        int inCy = Math.floorMod(by, 16);
        int inCz = Math.floorMod(bz, 16);

        org.joml.Vector3f fwd = player.getCamera().getForward();
        String facing;
        String toward;
        if (Math.abs(fwd.x) > Math.abs(fwd.z)) {
            if (fwd.x > 0) {
                facing = "east";
                toward = "Towards positive X (+X)";
            } else {
                facing = "west";
                toward = "Towards negative X (-X)";
            }
        } else {
            if (fwd.z > 0) {
                facing = "south";
                toward = "Towards positive Z (+Z)";
            } else {
                facing = "north";
                toward = "Towards negative Z (-Z)";
            }
        }

        float yaw = player.getCamera().getYaw();
        float pitch = player.getCamera().getPitch();

        int skyLight = (int) (world.getSunLightLevel() * 15.0f);
        boolean openToSky = !world.isDarkAt(bx, by, bz);
        int light = openToSky ? skyLight : Math.max(0, skyLight - 8);

        int day = (int) (world.getWorldTime() / no.minecraft.world.World.DAY_LENGTH_SECONDS) + 1;
        String timeStr = world.isNight() ? "Night" : "Day";

        List<String> leftLines = new ArrayList<>();
        leftLines.add(String.format("Minecraft 1.20 Clone (%d fps)", lastFps));
        leftLines.add(String.format(java.util.Locale.ROOT, "XYZ: %.3f / %.3f / %.3f", pos.x, pos.y, pos.z));
        leftLines.add(String.format(java.util.Locale.ROOT, "Block: %d %d %d", bx, by, bz));
        leftLines.add(String.format(java.util.Locale.ROOT, "Chunk: %d %d %d [%d %d %d in chunk]", cx, cy, cz, inCx, inCy, inCz));
        leftLines.add(String.format(java.util.Locale.ROOT, "Facing: %s (%s) (%.1f / %.1f)", facing, toward, yaw, pitch));
        leftLines.add(String.format("Dimension: %s", world.getCurrentDimension().name().toLowerCase()));
        leftLines.add(String.format("Biome: %s", world.getBiomeName(bx, by, bz)));
        leftLines.add(String.format(java.util.Locale.ROOT, "Light: %d (%d sky, %d block)", light, skyLight, 0));
        leftLines.add(String.format(java.util.Locale.ROOT, "Day %d (%s, sun: %.2f)", day, timeStr, world.getSunLightLevel()));
        leftLines.add(String.format(java.util.Locale.ROOT, "Chunks: %d loaded | Mobs: %d | Drops: %d",
                world.getLoadedChunkCount(), world.getMobs().size(), world.getDroppedItems().size()));

        for (int i = 0; i < leftLines.size(); i++) {
            String line = leftLines.get(i);
            float y = startY + i * lineHeight;
            float textW = line.length() * (6.0f * scale);
            addRect(geom, startX - 2.0f, y - 1.0f, textW + 4.0f, lineHeight - 1.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.55f);
            drawHudText(overlayGeom, line, startX, y, scale, 0.90f, 0.90f, 0.90f);
        }

        // Right panel
        List<String> rightLines = new ArrayList<>();
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory() / (1024 * 1024);
        long totalMem = rt.totalMemory() / (1024 * 1024);
        long freeMem = rt.freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        long memPct = totalMem > 0 ? (usedMem * 100 / totalMem) : 0;
        String javaVer = System.getProperty("java.version");
        String arch = System.getProperty("os.arch").contains("64") ? "64bit" : "32bit";

        rightLines.add(String.format("Java: %s %s", javaVer, arch));
        rightLines.add(String.format(java.util.Locale.ROOT, "Mem: %d%% %d/%dMB", memPct, usedMem, totalMem));
        rightLines.add(String.format(java.util.Locale.ROOT, "Allocated: %dMB (Max: %dMB)", totalMem, maxMem));
        rightLines.add(String.format("Display: %dx%d", windowWidth, windowHeight));
        rightLines.add(String.format("GameMode: %s%s", player.getGameMode().name(), player.isFlying() ? " [FLY]" : ""));

        if (lastTargetedHit != null) {
            rightLines.add(""); // spacer
            rightLines.add(String.format(java.util.Locale.ROOT, "Targeted Block: %d, %d, %d",
                    lastTargetedHit.hitX, lastTargetedHit.hitY, lastTargetedHit.hitZ));
            BlockType tb = world.getBlock(lastTargetedHit.hitX, lastTargetedHit.hitY, lastTargetedHit.hitZ);
            rightLines.add("Block: " + tb.name());
            rightLines.add("Solid: " + tb.isSolid());
        }

        for (int i = 0; i < rightLines.size(); i++) {
            String line = rightLines.get(i);
            if (line.isEmpty()) continue;
            float y = startY + i * lineHeight;
            float textW = line.length() * (6.0f * scale);
            float rx = windowWidth - 8.0f - textW;
            addRect(geom, rx - 2.0f, y - 1.0f, textW + 4.0f, lineHeight - 1.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.55f);
            drawHudText(overlayGeom, line, rx, y, scale, 0.90f, 0.90f, 0.90f);
        }
    }

    private int[][] getGlyph(char c) {
        return switch (c) {
            case 'A' -> new int[][]{{0,1,1,0},{1,0,0,1},{1,1,1,1},{1,0,0,1},{1,0,0,1}};
            case 'B' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,0,1},{1,1,1,0}};
            case 'C' -> new int[][]{{0,1,1,1},{1,0,0,0},{1,0,0,0},{1,0,0,0},{0,1,1,1}};
            case 'D' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,1,1,0}};
            case 'E' -> new int[][]{{1,1,1,1},{1,0,0,0},{1,1,1,0},{1,0,0,0},{1,1,1,1}};
            case 'F' -> new int[][]{{1,1,1,1},{1,0,0,0},{1,1,1,0},{1,0,0,0},{1,0,0,0}};
            case 'G' -> new int[][]{{0,1,1,1},{1,0,0,0},{1,0,1,1},{1,0,0,1},{0,1,1,1}};
            case 'H' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,1,1,1},{1,0,0,1},{1,0,0,1}};
            case 'I' -> new int[][]{{1,1,1},{0,1,0},{0,1,0},{0,1,0},{1,1,1}};
            case 'J' -> new int[][]{{0,0,1,1},{0,0,0,1},{0,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'K' -> new int[][]{{1,0,0,1},{1,0,1,0},{1,1,0,0},{1,0,1,0},{1,0,0,1}};
            case 'L' -> new int[][]{{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,0,0,0},{1,1,1,1}};
            case 'M' -> new int[][]{{1,0,0,0,1},{1,1,0,1,1},{1,0,1,0,1},{1,0,0,0,1},{1,0,0,0,1}};
            case 'N' -> new int[][]{{1,0,0,1},{1,1,0,1},{1,0,1,1},{1,0,0,1},{1,0,0,1}};
            case 'O' -> new int[][]{{0,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'P' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,0,0},{1,0,0,0}};
            case 'Q' -> new int[][]{{0,1,1,0},{1,0,0,1},{1,0,0,1},{1,0,1,0},{0,1,0,1}};
            case 'R' -> new int[][]{{1,1,1,0},{1,0,0,1},{1,1,1,0},{1,0,1,0},{1,0,0,1}};
            case 'S' -> new int[][]{{0,1,1,1},{1,0,0,0},{0,1,1,0},{0,0,0,1},{1,1,1,0}};
            case 'T' -> new int[][]{{1,1,1,1,1},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0},{0,0,1,0,0}};
            case 'U' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0}};
            case 'V' -> new int[][]{{1,0,0,1},{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,0,0}};
            case 'W' -> new int[][]{{1,0,0,0,1},{1,0,0,0,1},{1,0,1,0,1},{1,1,0,1,1},{1,0,0,0,1}};
            case 'X' -> new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{1,0,0,1},{1,0,0,1}};
            case 'Y' -> new int[][]{{1,0,0,1},{1,0,0,1},{0,1,1,0},{0,0,1,0},{0,0,1,0}};
            case 'Z' -> new int[][]{{1,1,1,1},{0,0,0,1},{0,1,1,0},{1,0,0,0},{1,1,1,1}};
            case '0' -> new int[][]{{1,1,1},{1,0,1},{1,0,1},{1,0,1},{1,1,1}};
            case '1' -> new int[][]{{0,1,0},{1,1,0},{0,1,0},{0,1,0},{1,1,1}};
            case '2' -> new int[][]{{1,1,1},{0,0,1},{1,1,1},{1,0,0},{1,1,1}};
            case '3' -> new int[][]{{1,1,1},{0,0,1},{1,1,1},{0,0,1},{1,1,1}};
            case '4' -> new int[][]{{1,0,1},{1,0,1},{1,1,1},{0,0,1},{0,0,1}};
            case '5' -> new int[][]{{1,1,1},{1,0,0},{1,1,1},{0,0,1},{1,1,1}};
            case '6' -> new int[][]{{1,1,1},{1,0,0},{1,1,1},{1,0,1},{1,1,1}};
            case '7' -> new int[][]{{1,1,1},{0,0,1},{0,1,0},{0,1,0},{0,1,0}};
            case '8' -> new int[][]{{1,1,1},{1,0,1},{1,1,1},{1,0,1},{1,1,1}};
            case '9' -> new int[][]{{1,1,1},{1,0,1},{1,1,1},{0,0,1},{1,1,1}};
            case '/' -> new int[][]{{0,0,1},{0,0,1},{0,1,0},{1,0,0},{1,0,0}};
            case '-' -> new int[][]{{0,0,0},{0,0,0},{1,1,1},{0,0,0},{0,0,0}};
            case '_' -> new int[][]{{0,0,0},{0,0,0},{0,0,0},{0,0,0},{1,1,1}};
            case ':' -> new int[][]{{0,0},{1,0},{0,0},{1,0},{0,0}};
            case '.' -> new int[][]{{0},{0},{0},{0},{1}};
            case ',' -> new int[][]{{0},{0},{0},{1},{1}};
            case '<' -> new int[][]{{0,0,1},{0,1,0},{1,0,0},{0,1,0},{0,0,1}};
            case '>' -> new int[][]{{1,0,0},{0,1,0},{0,0,1},{0,1,0},{1,0,0}};
            case '[' -> new int[][]{{1,1},{1,0},{1,0},{1,0},{1,1}};
            case ']' -> new int[][]{{1,1},{0,1},{0,1},{0,1},{1,1}};
            case '@' -> new int[][]{{1,1,1},{1,0,1},{1,1,1},{1,0,0},{1,1,1}};
            case '!' -> new int[][]{{1},{1},{1},{0},{1}};
            case '?' -> new int[][]{{1,1,1},{0,0,1},{0,1,0},{0,0,0},{0,1,0}};
            case ' ' -> new int[][]{{0,0,0},{0,0,0},{0,0,0},{0,0,0},{0,0,0}};
            case '(' -> new int[][]{{0,1},{1,0},{1,0},{1,0},{0,1}};
            case ')' -> new int[][]{{1,0},{0,1},{0,1},{0,1},{1,0}};
            case '+' -> new int[][]{{0,0,0},{0,1,0},{1,1,1},{0,1,0},{0,0,0}};
            case '%' -> new int[][]{{1,0,1},{0,0,1},{0,1,0},{1,0,0},{1,0,1}};
            case '=' -> new int[][]{{0,0,0},{1,1,1},{0,0,0},{1,1,1},{0,0,0}};
            case '|' -> new int[][]{{1},{1},{1},{1},{1}};
            case 'Æ' -> new int[][]{{0,1,1,1},{1,0,1,0},{1,1,1,0},{1,0,1,0},{1,0,1,1}};
            case 'Ø' -> new int[][]{{0,1,1,1},{1,0,0,1},{1,0,1,1},{1,1,0,1},{1,1,1,0}};
            case 'Å' -> new int[][]{{0,1,0},{1,0,1},{1,1,1},{1,0,1},{1,0,1}};
            default -> null;
        };
    }

    public void cleanup() {
        hudShader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
