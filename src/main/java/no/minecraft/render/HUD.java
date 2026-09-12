package no.minecraft.render;

import no.minecraft.i18n.I18n;
import no.minecraft.player.CraftingRecipe;
import no.minecraft.player.GameMode;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static no.minecraft.render.UiBatch.addRect;
import static no.minecraft.render.UiBatch.addVertex;

public class HUD {
    private final Shader hudShader;
    private final int vaoId;
    private final int vboId;

    private boolean inventoryOpen = false;
    private boolean craftingTableOpen = false;
    private boolean furnaceOpen = false;
    private boolean chestOpen = false;
    private no.minecraft.world.FurnaceData activeFurnace = null;
    private no.minecraft.world.ChestData activeChest = null;
    boolean recipeBookOpen = false;
    int recipeScrollRow = 0;
    String recipeSearchText = "";
    boolean recipeSearchFocused = false;
    private boolean showDebugInfo = false;
    private int lastFps = 60;
    private no.minecraft.player.Raycast.HitResult lastTargetedHit = null;
    private final List<CraftingRecipe> recipes = CraftingRecipe.getDefaultRecipes();

    final ItemStack[] craftSlots = new ItemStack[4];
    final ItemStack[] benchSlots = new ItemStack[9];
    final ItemStack carriedItem = new ItemStack(BlockType.AIR, 0);
    float mouseX, mouseY;

    // Mouse drag distribution state
    boolean isLeftDragging = false;
    boolean isRightDragging = false;
    final Set<ItemStack> draggedSlots = new LinkedHashSet<>();
    private ItemStack startDragSlot = null;

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
        this(true);
    }

    public HUD(boolean initGl) {
        if (initGl) {
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
        } else {
            hudShader = null;
            vaoId = 0;
            vboId = 0;
        }

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
        return inventoryOpen || craftingTableOpen || furnaceOpen || chestOpen;
    }

    public boolean isCraftingTableOpen() {
        return craftingTableOpen;
    }

    public boolean isFurnaceOpen() {
        return furnaceOpen;
    }

    public boolean isChestOpen() {
        return chestOpen;
    }

    public void openCraftingTable() {
        this.inventoryOpen = false;
        this.furnaceOpen = false;
        this.chestOpen = false;
        this.activeChest = null;
        this.craftingTableOpen = true;
        this.isLeftDragging = false;
        this.isRightDragging = false;
        this.draggedSlots.clear();
        this.startDragSlot = null;
    }

    public void openRecipeBook() {
        this.recipeBookOpen = true;
    }

    public void openFurnace(no.minecraft.world.FurnaceData furnace) {
        this.inventoryOpen = false;
        this.craftingTableOpen = false;
        this.chestOpen = false;
        this.activeChest = null;
        this.furnaceOpen = true;
        this.activeFurnace = furnace;
        this.isLeftDragging = false;
        this.isRightDragging = false;
        this.draggedSlots.clear();
        this.startDragSlot = null;
    }

    public no.minecraft.world.FurnaceData getActiveFurnace() {
        return activeFurnace;
    }

    public void openChest(no.minecraft.world.ChestData chest) {
        this.inventoryOpen = false;
        this.craftingTableOpen = false;
        this.furnaceOpen = false;
        this.activeFurnace = null;
        this.chestOpen = true;
        this.activeChest = chest;
        this.isLeftDragging = false;
        this.isRightDragging = false;
        this.draggedSlots.clear();
        this.startDragSlot = null;
    }

    public no.minecraft.world.ChestData getActiveChest() {
        return activeChest;
    }

    public boolean isRecipeSearchFocused() {
        return (inventoryOpen || craftingTableOpen) && recipeBookOpen && recipeSearchFocused;
    }

    public void setRecipeSearchFocused(boolean focused) {
        this.recipeSearchFocused = focused;
    }

    public void addRecipeSearchChar(char c) {
        if (!isRecipeSearchFocused()) return;
        if (Character.isISOControl(c)) return;
        if (recipeSearchText.length() < 24) {
            recipeSearchText += c;
            recipeScrollRow = 0;
        }
    }

    public void recipeSearchBackspace() {
        if (!isRecipeSearchFocused()) return;
        if (!recipeSearchText.isEmpty()) {
            recipeSearchText = recipeSearchText.substring(0, recipeSearchText.length() - 1);
            recipeScrollRow = 0;
        }
    }

    public void clearRecipeSearch() {
        recipeSearchText = "";
        recipeScrollRow = 0;
    }

    public String getRecipeSearchText() {
        return recipeSearchText;
    }

    public List<CraftingRecipe> getFilteredRecipes() {
        if (recipeSearchText == null || recipeSearchText.trim().isEmpty()) {
            return recipes;
        }
        String q = recipeSearchText.trim().toLowerCase(java.util.Locale.ROOT);
        List<CraftingRecipe> list = new ArrayList<>();
        for (CraftingRecipe r : recipes) {
            String name = r.getName().toLowerCase(java.util.Locale.ROOT);
            String disp = r.getOutput().getType().getName().toLowerCase(java.util.Locale.ROOT);
            String enumName = r.getOutput().getType().name().toLowerCase(java.util.Locale.ROOT);
            String localized = I18n.get(r.getNameKey()).toLowerCase(java.util.Locale.ROOT);
            if (name.contains(q) || disp.contains(q) || enumName.contains(q) || localized.contains(q)) {
                list.add(r);
            }
        }
        return list;
    }

    public void handleScroll(double xoffset, double yoffset) {
        if (recipeBookOpen) {
            List<CraftingRecipe> activeRecipes = getFilteredRecipes();
            int totalRows = (activeRecipes.size() + 3) / 4;
            int maxScroll = Math.max(0, totalRows - 5);
            if (yoffset > 0) {
                recipeScrollRow = Math.max(0, recipeScrollRow - 1);
            } else if (yoffset < 0) {
                recipeScrollRow = Math.min(maxScroll, recipeScrollRow + 1);
            }
        }
    }

    public void setInventoryOpen(boolean open) {
        this.inventoryOpen = open;
    }

    public void setInventoryOpen(boolean open, Player player) {
        if (!open && (inventoryOpen || craftingTableOpen || furnaceOpen || chestOpen)) {
            closeInventory(player);
        } else {
            this.inventoryOpen = open;
        }
    }

    public void toggleInventory() {
        this.inventoryOpen = !this.inventoryOpen;
    }

    public void toggleInventory(Player player) {
        if (inventoryOpen || craftingTableOpen || furnaceOpen || chestOpen) {
            closeInventory(player);
        } else {
            this.inventoryOpen = true;
            this.isLeftDragging = false;
            this.isRightDragging = false;
            this.draggedSlots.clear();
            this.startDragSlot = null;
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
        if (inventoryOpen || craftingTableOpen || furnaceOpen || chestOpen) {
            inventoryOpen = false;
            craftingTableOpen = false;
            furnaceOpen = false;
            activeFurnace = null;
            chestOpen = false;
            activeChest = null;
            if (!carriedItem.isEmpty()) {
                player.getInventory().addItem(carriedItem.getType(), carriedItem.getCount());
                carriedItem.clear();
            }
            returnCraftSlotsToInventory(player);
            returnBenchSlotsToInventory(player);
            recipeSearchFocused = false;
            isLeftDragging = false;
            isRightDragging = false;
            draggedSlots.clear();
            startDragSlot = null;
        }
    }

    public ItemStack getCraftingResult() {
        return calculateCraftingResult(craftSlots);
    }

    public static ItemStack calculateCraftingResult(ItemStack[] craftSlots) {
        return CraftingRecipe.matchGrid(craftSlots, 2, 2);
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
                no.minecraft.player.Inventory.mergeStacks(carriedItem, slot,
                        button == GLFW_MOUSE_BUTTON_RIGHT ? 1 : carriedItem.getCount());
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

    public static void distributeLeftDrag(ItemStack carriedItem, Collection<ItemStack> draggedSlots) {
        if (carriedItem == null || carriedItem.isEmpty() || draggedSlots == null || draggedSlots.isEmpty()) {
            return;
        }
        int slotsCount = draggedSlots.size();
        int perSlot = carriedItem.getCount() / slotsCount;
        if (perSlot <= 0) return;

        for (ItemStack s : draggedSlots) {
            if (s.isEmpty()) {
                s.setType(carriedItem.getType());
                s.setCount(perSlot);
                carriedItem.add(-perSlot);
            } else if (s.getType() == carriedItem.getType()) {
                no.minecraft.player.Inventory.mergeStacks(carriedItem, s, perSlot);
            }
        }
    }

    public static boolean distributeRightDragSlot(ItemStack carriedItem, ItemStack slot) {
        if (carriedItem == null || carriedItem.isEmpty() || slot == null) {
            return false;
        }
        if (slot.isEmpty()) {
            slot.setType(carriedItem.getType());
            slot.setCount(1);
            carriedItem.add(-1);
            return true;
        } else if (no.minecraft.player.Inventory.mergeStacks(carriedItem, slot, 1)) {
            return true;
        }
        return false;
    }

    private double lastSlotClickTime = 0.0;
    private ItemStack lastClickedSlotRef = null;

    private void onSlotClicked(ItemStack slot, int button, Player player) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            double now = org.lwjgl.glfw.GLFW.glfwGetTime();
            if (!carriedItem.isEmpty() && (now - lastSlotClickTime < 0.35)
                    && (slot == lastClickedSlotRef || (slot != null && slot.getType() == carriedItem.getType()))) {
                if (collectMatchingItemsToCarried(player)) {
                    lastSlotClickTime = 0.0;
                    isLeftDragging = false;
                    draggedSlots.clear();
                    return;
                }
            }
            lastSlotClickTime = now;
            lastClickedSlotRef = slot;

            if (!carriedItem.isEmpty() && (slot.isEmpty() || (slot.getType() == carriedItem.getType() && slot.getCount() < no.minecraft.player.Inventory.MAX_STACK_SIZE))) {
                isLeftDragging = true;
                draggedSlots.clear();
                draggedSlots.add(slot);
                startDragSlot = slot;
                return;
            }
            handleSlotClick(slot, button);
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            lastSlotClickTime = 0.0;
            if (!carriedItem.isEmpty() && (slot.isEmpty() || (slot.getType() == carriedItem.getType() && slot.getCount() < no.minecraft.player.Inventory.MAX_STACK_SIZE))) {
                if (distributeRightDragSlot(carriedItem, slot)) {
                    draggedSlots.clear();
                    draggedSlots.add(slot);
                    isRightDragging = true;
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    return;
                }
            }
            handleSlotClick(slot, button);
        } else {
            lastSlotClickTime = 0.0;
            handleSlotClick(slot, button);
        }
    }

    private boolean collectMatchingItemsToCarried(Player player) {
        if (carriedItem.isEmpty() || carriedItem.getCount() >= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
            return false;
        }
        BlockType targetType = carriedItem.getType();
        boolean collectedAny = false;

        // 1. Collect from player inventory slots (0..35)
        if (player != null) {
            for (int i = 0; i < no.minecraft.player.Inventory.TOTAL_SLOTS; i++) {
                ItemStack slot = player.getInventory().getSlot(i);
                if (!slot.isEmpty() && slot.getType() == targetType) {
                    int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                    if (space <= 0) break;
                    int take = Math.min(space, slot.getCount());
                    carriedItem.add(take);
                    slot.add(-take);
                    collectedAny = true;
                }
            }

            // Offhand slot
            ItemStack offhand = player.getOffhandItem();
            if (offhand != null && !offhand.isEmpty() && offhand.getType() == targetType) {
                int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                if (space > 0) {
                    int take = Math.min(space, offhand.getCount());
                    carriedItem.add(take);
                    offhand.add(-take);
                    collectedAny = true;
                }
            }
        }

        // 2. Collect from open container slots
        if (inventoryOpen) {
            for (ItemStack s : craftSlots) {
                if (!s.isEmpty() && s.getType() == targetType) {
                    int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                    if (space <= 0) break;
                    int take = Math.min(space, s.getCount());
                    carriedItem.add(take);
                    s.add(-take);
                    collectedAny = true;
                }
            }
        } else if (craftingTableOpen) {
            for (ItemStack s : benchSlots) {
                if (!s.isEmpty() && s.getType() == targetType) {
                    int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                    if (space <= 0) break;
                    int take = Math.min(space, s.getCount());
                    carriedItem.add(take);
                    s.add(-take);
                    collectedAny = true;
                }
            }
        } else if (chestOpen && activeChest != null) {
            for (int i = 0; i < activeChest.getSize(); i++) {
                ItemStack s = activeChest.getSlot(i);
                if (s != null && !s.isEmpty() && s.getType() == targetType) {
                    int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                    if (space <= 0) break;
                    int take = Math.min(space, s.getCount());
                    carriedItem.add(take);
                    s.add(-take);
                    collectedAny = true;
                }
            }
        } else if (furnaceOpen && activeFurnace != null) {
            ItemStack in = activeFurnace.getInput();
            if (!in.isEmpty() && in.getType() == targetType) {
                int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                if (space > 0) {
                    int take = Math.min(space, in.getCount());
                    carriedItem.add(take);
                    in.add(-take);
                    collectedAny = true;
                }
            }
            ItemStack fuel = activeFurnace.getFuel();
            if (!fuel.isEmpty() && fuel.getType() == targetType) {
                int space = no.minecraft.player.Inventory.MAX_STACK_SIZE - carriedItem.getCount();
                if (space > 0) {
                    int take = Math.min(space, fuel.getCount());
                    carriedItem.add(take);
                    fuel.add(-take);
                    collectedAny = true;
                }
            }
        }

        if (collectedAny) {
            no.minecraft.sound.SoundManager.getInstance().play("click");
            return true;
        }
        return false;
    }

    public float getGuiScale(int windowWidth, int windowHeight) {
        return no.minecraft.settings.GameSettings.getInstance().calculateGuiScale(windowWidth, windowHeight);
    }

    public float getMouseX() {
        return mouseX;
    }

    public float getMouseY() {
        return mouseY;
    }

    public ItemStack getSlotAt(double mx, double my, Player player, int windowWidth, int windowHeight) {
        if (!isInventoryOpen()) return null;
        float scale = getGuiScale(windowWidth, windowHeight);
        float invW = 176.0f * scale;
        float invH = 166.0f * scale;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        if (craftingTableOpen) {
            float gridX = ix + 30.0f * scale;
            float gridY = iy + 17.0f * scale;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    float sx = gridX + c * 18.0f * scale;
                    float sy = gridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        return benchSlots[r * 3 + c];
                    }
                }
            }
        } else if (furnaceOpen && activeFurnace != null) {
            float inX = ix + 56.0f * scale;
            float inY = iy + 17.0f * scale;
            if (mx >= inX && mx <= inX + 18.0f * scale && my >= inY && my <= inY + 18.0f * scale) {
                return activeFurnace.getInput();
            }
            float fuelX = ix + 56.0f * scale;
            float fuelY = iy + 53.0f * scale;
            if (mx >= fuelX && mx <= fuelX + 18.0f * scale && my >= fuelY && my <= fuelY + 18.0f * scale) {
                return activeFurnace.getFuel();
            }
        } else if (chestOpen && activeChest != null) {
            float chestGridX = ix + 8.0f * scale;
            float chestGridY = iy + 18.0f * scale;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    float sx = chestGridX + c * 18.0f * scale;
                    float sy = chestGridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        return activeChest.getSlot(r * 9 + c);
                    }
                }
            }
        } else if (inventoryOpen) {
            // Armor Slots (4 vertical slots on left: 0=Helmet, 1=Chestplate, 2=Leggings, 3=Boots)
            float armorX = ix + 8.0f * scale;
            for (int i = 0; i < 4; i++) {
                float armorY = iy + (8.0f + i * 18.0f) * scale;
                if (mx >= armorX && mx <= armorX + 18.0f * scale && my >= armorY && my <= armorY + 18.0f * scale) {
                    return player != null ? player.getArmorSlot(i) : null;
                }
            }

            // Shield / Offhand slot
            float shieldX = ix + 77.0f * scale;
            float shieldY = iy + 62.0f * scale;
            if (mx >= shieldX && mx <= shieldX + 18.0f * scale && my >= shieldY && my <= shieldY + 18.0f * scale) {
                return player != null ? player.getOffhandItem() : null;
            }

            float craftGridX = ix + 98.0f * scale;
            float craftGridY = iy + 18.0f * scale;
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 2; c++) {
                    float sx = craftGridX + c * 18.0f * scale;
                    float sy = craftGridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        return craftSlots[r * 2 + c];
                    }
                }
            }
        }

        // Main Inventory Grid (3x9)
        float mainInvX = ix + 8.0f * scale;
        float mainInvY = iy + 84.0f * scale;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = 9 + row * 9 + col;
                float sx = mainInvX + col * 18.0f * scale;
                float sy = mainInvY + row * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                    return player != null ? player.getInventory().getSlot(slotIndex) : null;
                }
            }
        }

        // Hotbar (1x9)
        float hotbarY = iy + 142.0f * scale;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * scale;
            if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarY && my <= hotbarY + 18.0f * scale) {
                return player != null ? player.getInventory().getSlot(col) : null;
            }
        }

        return null;
    }

    public void handleMouseMove(double mx, double my, Player player, int windowWidth, int windowHeight) {
        if (!isInventoryOpen()) return;
        this.mouseX = (float) mx;
        this.mouseY = (float) my;

        if (isRightDragging) {
            if (carriedItem.isEmpty()) return;
            ItemStack slot = getSlotAt(mx, my, player, windowWidth, windowHeight);
            if (slot != null && !draggedSlots.contains(slot)) {
                if (distributeRightDragSlot(carriedItem, slot)) {
                    draggedSlots.add(slot);
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                }
            }
        } else if (isLeftDragging) {
            if (carriedItem.isEmpty()) return;
            ItemStack slot = getSlotAt(mx, my, player, windowWidth, windowHeight);
            if (slot != null && !draggedSlots.contains(slot)) {
                if (slot.isEmpty() || (slot.getType() == carriedItem.getType() && slot.getCount() < no.minecraft.player.Inventory.MAX_STACK_SIZE)) {
                    draggedSlots.add(slot);
                }
            }
        }
    }

    public void handleMouseRelease(double mx, double my, int button, Player player, int windowWidth, int windowHeight) {
        if (!isInventoryOpen()) return;

        float scale = getGuiScale(windowWidth, windowHeight);
        float invW = 176.0f * scale;
        float invH = 166.0f * scale;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;
        float minGuiX = recipeBookOpen ? (ix - 126.0f * scale - 6.0f) : ix;
        float maxGuiX = ix + invW;
        float minGuiY = iy;
        float maxGuiY = iy + invH;

        if (mx < minGuiX || mx > maxGuiX || my < minGuiY || my > maxGuiY) {
            if (!carriedItem.isEmpty()) {
                int dropCount = (button == GLFW_MOUSE_BUTTON_RIGHT) ? 1 : carriedItem.getCount();
                BlockType dropType = carriedItem.getType();
                carriedItem.add(-dropCount);
                Vector3f eye = player.getEyePosition();
                Vector3f fwd = player.getCamera().getForward();
                player.getWorld().spawnItemDrop(eye.x, eye.y - 0.2f, eye.z, fwd.x * 4.5f, fwd.y * 4.5f + 1.5f, fwd.z * 4.5f, dropType, dropCount);
                no.minecraft.sound.SoundManager.getInstance().play("pop", 0.8f);
            }
            isLeftDragging = false;
            isRightDragging = false;
            draggedSlots.clear();
            startDragSlot = null;
            return;
        }

        if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            isRightDragging = false;
            draggedSlots.clear();
        } else if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (isLeftDragging) {
                if (draggedSlots.size() <= 1) {
                    if (startDragSlot != null) {
                        handleSlotClick(startDragSlot, GLFW_MOUSE_BUTTON_LEFT);
                    }
                } else {
                    int countBefore = carriedItem.getCount();
                    distributeLeftDrag(carriedItem, draggedSlots);
                    if (carriedItem.getCount() < countBefore) {
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                }
                isLeftDragging = false;
                draggedSlots.clear();
                startDragSlot = null;
            }
        }
    }

    public int getCarriedDisplayCount() {
        if (isLeftDragging && draggedSlots.size() > 1 && !carriedItem.isEmpty()) {
            int perSlot = carriedItem.getCount() / draggedSlots.size();
            return carriedItem.getCount() - (perSlot * draggedSlots.size());
        }
        return carriedItem.getCount();
    }

    public ItemStack getCarriedItem() {
        return carriedItem;
    }

    public ItemStack[] getBenchSlots() {
        return benchSlots;
    }

    public ItemStack[] getCraftSlots() {
        return craftSlots;
    }

    public boolean isLeftDragging() {
        return isLeftDragging;
    }

    public boolean isRightDragging() {
        return isRightDragging;
    }

    public Set<ItemStack> getDraggedSlots() {
        return draggedSlots;
    }


    public ItemStack get3x3CraftingResult() {
        return CraftingRecipe.matchGrid(benchSlots, 3, 3);
    }

    void populate3x3Recipe(CraftingRecipe r, Player player) {
        if (!r.canCraft(player.getInventory())) return;
        returnBenchSlotsToInventory(player);

        List<String[]> patterns = r.getPatterns();
        if (patterns.isEmpty()) return; // Non-grid recipes (smelting etc.) are not placeable

        // Place the first pattern variant, one item per cell
        String[] pattern = patterns.get(0);
        Map<Character, BlockType> key = r.getPatternKey();
        for (int row = 0; row < pattern.length; row++) {
            for (int col = 0; col < pattern[row].length(); col++) {
                char c = pattern[row].charAt(col);
                if (c == ' ') continue;
                BlockType type = key.get(c);
                if (player.getInventory().getItemCount(type) >= 1) {
                    player.getInventory().removeItem(type, 1);
                    benchSlots[row * 3 + col].setType(type);
                    benchSlots[row * 3 + col].setCount(1);
                }
            }
        }
    }

    private void handleFurnaceShiftClick(ItemStack slot, Player player) {
        if (slot.isEmpty() || activeFurnace == null) return;
        // Try smelting input first
        if (no.minecraft.world.FurnaceData.canSmelt(slot.getType())) {
            ItemStack in = activeFurnace.getInput();
            if (in.isEmpty()) {
                in.setType(slot.getType());
                in.setCount(slot.getCount());
                slot.clear();
                no.minecraft.sound.SoundManager.getInstance().play("click");
                return;
            } else if (no.minecraft.player.Inventory.mergeStacks(slot, in, slot.getCount())) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                return;
            }
        }
        // Try fuel next
        if (no.minecraft.world.FurnaceData.isFuel(slot.getType())) {
            ItemStack fl = activeFurnace.getFuel();
            if (fl.isEmpty()) {
                fl.setType(slot.getType());
                fl.setCount(slot.getCount());
                slot.clear();
                no.minecraft.sound.SoundManager.getInstance().play("click");
                return;
            } else if (no.minecraft.player.Inventory.mergeStacks(slot, fl, slot.getCount())) {
                no.minecraft.sound.SoundManager.getInstance().play("click");
                return;
            }
        }
        // Move between hotbar and main inventory if not accepted by furnace
        int currentIdx = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (player.getInventory().getSlot(i) == slot) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx != -1) {
            if (currentIdx < 9) {
                for (int i = 9; i < 36; i++) {
                    ItemStack target = player.getInventory().getSlot(i);
                    if (no.minecraft.player.Inventory.mergeStacks(slot, target, slot.getCount()) && slot.isEmpty()) break;
                }
            } else {
                for (int i = 0; i < 9; i++) {
                    ItemStack target = player.getInventory().getSlot(i);
                    if (no.minecraft.player.Inventory.mergeStacks(slot, target, slot.getCount()) && slot.isEmpty()) break;
                }
            }
            no.minecraft.sound.SoundManager.getInstance().play("click");
        }
    }

    private void handleChestSlotShiftClick(ItemStack slot, Player player) {
        if (slot == null || slot.isEmpty()) return;
        boolean moved = false;
        // 1. Try to merge into player matching stacks first
        for (int i = 0; i < no.minecraft.player.Inventory.TOTAL_SLOTS; i++) {
            ItemStack pSlot = player.getInventory().getSlot(i);
            if (!pSlot.isEmpty() && pSlot.getType() == slot.getType()) {
                if (no.minecraft.player.Inventory.mergeStacks(slot, pSlot, slot.getCount())) {
                    moved = true;
                    if (slot.isEmpty()) break;
                }
            }
        }
        // 2. Try to move into empty player slots
        if (!slot.isEmpty()) {
            for (int i = 0; i < no.minecraft.player.Inventory.TOTAL_SLOTS; i++) {
                ItemStack pSlot = player.getInventory().getSlot(i);
                if (pSlot.isEmpty()) {
                    if (no.minecraft.player.Inventory.mergeStacks(slot, pSlot, slot.getCount())) {
                        moved = true;
                        if (slot.isEmpty()) break;
                    }
                }
            }
        }
        if (moved) {
            no.minecraft.sound.SoundManager.getInstance().play("click");
        }
    }

    private void handlePlayerToChestShiftClick(ItemStack slot, no.minecraft.world.ChestData chest) {
        if (slot == null || slot.isEmpty() || chest == null) return;
        boolean moved = false;
        // 1. Try to merge into chest matching stacks first
        for (int i = 0; i < no.minecraft.world.ChestData.CHEST_SIZE; i++) {
            ItemStack cSlot = chest.getSlot(i);
            if (cSlot != null && !cSlot.isEmpty() && cSlot.getType() == slot.getType()) {
                if (no.minecraft.player.Inventory.mergeStacks(slot, cSlot, slot.getCount())) {
                    moved = true;
                    if (slot.isEmpty()) break;
                }
            }
        }
        // 2. Try to move into empty chest slots
        if (!slot.isEmpty()) {
            for (int i = 0; i < no.minecraft.world.ChestData.CHEST_SIZE; i++) {
                ItemStack cSlot = chest.getSlot(i);
                if (cSlot != null && cSlot.isEmpty()) {
                    if (no.minecraft.player.Inventory.mergeStacks(slot, cSlot, slot.getCount())) {
                        moved = true;
                        if (slot.isEmpty()) break;
                    }
                }
            }
        }
        if (moved) {
            no.minecraft.sound.SoundManager.getInstance().play("click");
        }
    }

    public boolean handleMouseClick(double mx, double my, Player player, int windowWidth, int windowHeight) {
        return handleMouseClick(mx, my, GLFW_MOUSE_BUTTON_LEFT, player, windowWidth, windowHeight);
    }

    public boolean handleMouseClick(double mx, double my, int button, Player player, int windowWidth, int windowHeight) {
        return handleMouseClick(mx, my, button, false, player, windowWidth, windowHeight);
    }

    public boolean handleMouseClick(double mx, double my, int button, boolean isShiftDown, Player player, int windowWidth, int windowHeight) {
        if (!inventoryOpen && !craftingTableOpen && !furnaceOpen && !chestOpen) return false;

        float scale = getGuiScale(windowWidth, windowHeight);
        float invW = 176.0f * scale;
        float invH = 166.0f * scale;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        float minGuiX = recipeBookOpen ? (ix - 126.0f * scale - 6.0f) : ix;
        float maxGuiX = ix + invW;
        float minGuiY = iy;
        float maxGuiY = iy + invH;

        // Dropping item when dragging outside inventory area
        if (mx < minGuiX || mx > maxGuiX || my < minGuiY || my > maxGuiY) {
            if (!carriedItem.isEmpty()) {
                int dropCount = (button == GLFW_MOUSE_BUTTON_RIGHT) ? 1 : carriedItem.getCount();
                BlockType dropType = carriedItem.getType();
                carriedItem.add(-dropCount);
                Vector3f eye = player.getEyePosition();
                Vector3f fwd = player.getCamera().getForward();
                player.getWorld().spawnItemDrop(eye.x, eye.y - 0.2f, eye.z, fwd.x * 4.5f, fwd.y * 4.5f + 1.5f, fwd.z * 4.5f, dropType, dropCount);
                no.minecraft.sound.SoundManager.getInstance().play("pop", 0.8f);
            }
            return true;
        }

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
                float slotW = 22.0f * scale;
                float slotStep = 26.0f * scale;
                float startGridX = popX + 11.0f * scale;
                float startGridY = iy + 20.0f * scale;

                // Check search bar click
                float searchX = popX + 10.0f * scale;
                float searchY = iy + 6.0f * scale;
                float searchW = popW - 20.0f * scale;
                float searchH = 11.0f * scale;
                if (mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + searchH) {
                    float clearBtnX = searchX + searchW - 10.0f * scale;
                    if (!recipeSearchText.isEmpty() && mx >= clearBtnX - 2.0f * scale) {
                        clearRecipeSearch();
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        return true;
                    }
                    recipeSearchFocused = true;
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    return true;
                } else if (mx >= popX && mx <= popX + popW && my >= iy && my <= iy + invH) {
                    recipeSearchFocused = false;
                } else {
                    recipeSearchFocused = false;
                }

                List<CraftingRecipe> activeRecipes = getFilteredRecipes();
                int totalRows = (activeRecipes.size() + 3) / 4;
                int maxScroll = Math.max(0, totalRows - 5);

                // Scrollbar click
                if (maxScroll > 0) {
                    float sbX = popX + popW - 10.0f * scale;
                    float sbY = startGridY;
                    float sbW = 6.0f * scale;
                    float sbH = 5 * slotStep - 4.0f * scale;
                    if (mx >= sbX - 3.0f * scale && mx <= sbX + sbW + 3.0f * scale && my >= sbY && my <= sbY + sbH) {
                        float frac = Math.clamp((float) (my - sbY) / sbH, 0.0f, 1.0f);
                        recipeScrollRow = Math.round(frac * maxScroll);
                        return true;
                    }
                }

                int startIdx = recipeScrollRow * 4;
                int endIdx = Math.min(activeRecipes.size(), startIdx + 5 * 4);

                for (int i = startIdx; i < endIdx; i++) {
                    int visualRow = (i - startIdx) / 4;
                    int col = (i - startIdx) % 4;
                    float sx = startGridX + col * slotStep;
                    float sy = startGridY + visualRow * slotStep;

                    if (mx >= sx && mx <= sx + slotW && my >= sy && my <= sy + slotW) {
                        CraftingRecipe r = activeRecipes.get(i);
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
                        onSlotClicked(benchSlots[slotIdx], button, player);
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
                    if (isShiftDown) {
                        int craftedTotal = 0;
                        BlockType targetType = res.getType();
                        while (true) {
                            ItemStack craftRes = get3x3CraftingResult();
                            if (craftRes == null || craftRes.isEmpty() || craftRes.getType() != targetType) break;
                            if (!player.getInventory().hasSpaceFor(craftRes.getType(), craftRes.getCount())) break;
                            player.getInventory().addItem(craftRes.getType(), craftRes.getCount());
                            craftedTotal += craftRes.getCount();
                            for (ItemStack s : benchSlots) {
                                if (!s.isEmpty()) s.add(-1);
                            }
                        }
                        if (craftedTotal > 0) {
                            no.minecraft.sound.SoundManager.getInstance().play("click");
                            if (res.getType() == BlockType.WOODEN_SWORD || res.getType() == BlockType.STONE_SWORD) {
                                no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.TIME_TO_STRIKE);
                            } else if (res.getType() == BlockType.FURNACE) {
                                no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                            }
                        }
                    } else {
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
                        onSlotClicked(player.getInventory().getSlot(slotIndex), button, player);
                        return true;
                    }
                }
            }

            // 6. Hotbar Grid in Inventory (1 row x 9 columns)
            float hotbarInvY = iy + 142.0f * scale;
            for (int col = 0; col < 9; col++) {
                float sx = mainInvX + col * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                    onSlotClicked(player.getInventory().getSlot(col), button, player);
                    return true;
                }
            }

            return true;
        }

        // --- Handle Furnace GUI clicks ---
        if (furnaceOpen && activeFurnace != null) {
            float inX = ix + 56.0f * scale;
            float inY = iy + 17.0f * scale;
            float fuelX = ix + 56.0f * scale;
            float fuelY = iy + 53.0f * scale;
            float outX = ix + 116.0f * scale;
            float outY = iy + 31.0f * scale;

            // 1. Input slot click
            if (mx >= inX && mx <= inX + 18.0f * scale && my >= inY && my <= inY + 18.0f * scale) {
                if (isShiftDown) {
                    ItemStack in = activeFurnace.getInput();
                    if (!in.isEmpty() && player.getInventory().hasSpaceFor(in.getType(), in.getCount())) {
                        player.getInventory().addItem(in.getType(), in.getCount());
                        in.clear();
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                } else {
                    onSlotClicked(activeFurnace.getInput(), button, player);
                }
                return true;
            }

            // 2. Fuel slot click
            if (mx >= fuelX && mx <= fuelX + 18.0f * scale && my >= fuelY && my <= fuelY + 18.0f * scale) {
                if (isShiftDown) {
                    ItemStack fl = activeFurnace.getFuel();
                    if (!fl.isEmpty() && player.getInventory().hasSpaceFor(fl.getType(), fl.getCount())) {
                        player.getInventory().addItem(fl.getType(), fl.getCount());
                        fl.clear();
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                } else {
                    onSlotClicked(activeFurnace.getFuel(), button, player);
                }
                return true;
            }

            // 3. Output slot click
            if (mx >= outX && mx <= outX + 24.0f * scale && my >= outY && my <= outY + 24.0f * scale) {
                ItemStack out = activeFurnace.getOutput();
                if (!out.isEmpty()) {
                    if (isShiftDown) {
                        if (player.getInventory().hasSpaceFor(out.getType(), out.getCount())) {
                            player.getInventory().addItem(out.getType(), out.getCount());
                            out.clear();
                            no.minecraft.sound.SoundManager.getInstance().play("click");
                            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                        }
                    } else {
                        if (carriedItem.isEmpty()) {
                            carriedItem.setType(out.getType());
                            carriedItem.setCount(out.getCount());
                            out.clear();
                            no.minecraft.sound.SoundManager.getInstance().play("click");
                            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                        } else if (carriedItem.getType() == out.getType() && carriedItem.getCount() + out.getCount() <= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                            carriedItem.add(out.getCount());
                            out.clear();
                            no.minecraft.sound.SoundManager.getInstance().play("click");
                            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                        }
                    }
                }
                return true;
            }

            // 4. Main Inventory Grid (3 rows x 9 columns)
            float mainInvX = ix + 8.0f * scale;
            float mainInvY = iy + 84.0f * scale;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    float sx = mainInvX + col * 18.0f * scale;
                    float sy = mainInvY + row * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        ItemStack slot = player.getInventory().getSlot(slotIndex);
                        if (isShiftDown) {
                            handleFurnaceShiftClick(slot, player);
                        } else {
                            onSlotClicked(slot, button, player);
                        }
                        return true;
                    }
                }
            }

            // 5. Hotbar Grid (1 row x 9 columns)
            float hotbarInvY = iy + 142.0f * scale;
            for (int col = 0; col < 9; col++) {
                float sx = mainInvX + col * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                    ItemStack slot = player.getInventory().getSlot(col);
                    if (isShiftDown) {
                        handleFurnaceShiftClick(slot, player);
                    } else {
                        onSlotClicked(slot, button, player);
                    }
                    return true;
                }
            }
            return true;
        }

        // --- Handle Chest GUI clicks ---
        if (chestOpen && activeChest != null) {
            float chestGridX = ix + 8.0f * scale;
            float chestGridY = iy + 18.0f * scale;

            // 1. Chest Slots Grid (3 rows x 9 columns = 27 slots)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = row * 9 + col;
                    float sx = chestGridX + col * 18.0f * scale;
                    float sy = chestGridY + row * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        ItemStack slot = activeChest.getSlot(slotIndex);
                        if (slot != null) {
                            if (isShiftDown) {
                                handleChestSlotShiftClick(slot, player);
                            } else {
                                onSlotClicked(slot, button, player);
                            }
                        }
                        return true;
                    }
                }
            }

            // 2. Main Inventory Grid (3 rows x 9 columns)
            float mainInvX = ix + 8.0f * scale;
            float mainInvY = iy + 84.0f * scale;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int slotIndex = 9 + row * 9 + col;
                    float sx = mainInvX + col * 18.0f * scale;
                    float sy = mainInvY + row * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        ItemStack slot = player.getInventory().getSlot(slotIndex);
                        if (isShiftDown) {
                            handlePlayerToChestShiftClick(slot, activeChest);
                        } else {
                            onSlotClicked(slot, button, player);
                        }
                        return true;
                    }
                }
            }

            // 3. Hotbar Grid (1 row x 9 columns)
            float hotbarInvY = iy + 142.0f * scale;
            for (int col = 0; col < 9; col++) {
                float sx = mainInvX + col * 18.0f * scale;
                if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                    ItemStack slot = player.getInventory().getSlot(col);
                    if (isShiftDown) {
                        handlePlayerToChestShiftClick(slot, activeChest);
                    } else {
                        onSlotClicked(slot, button, player);
                    }
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
            float slotW = 22.0f * scale;
            float slotStep = 26.0f * scale;
            float startGridX = popX + 11.0f * scale;
            float startGridY = iy + 20.0f * scale;

            // Check search bar click
            float searchX = popX + 10.0f * scale;
            float searchY = iy + 6.0f * scale;
            float searchW = popW - 20.0f * scale;
            float searchH = 11.0f * scale;
            if (mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + searchH) {
                float clearBtnX = searchX + searchW - 10.0f * scale;
                if (!recipeSearchText.isEmpty() && mx >= clearBtnX - 2.0f * scale) {
                    clearRecipeSearch();
                    no.minecraft.sound.SoundManager.getInstance().play("click");
                    return true;
                }
                recipeSearchFocused = true;
                no.minecraft.sound.SoundManager.getInstance().play("click");
                return true;
            } else if (mx >= popX && mx <= popX + popW && my >= iy && my <= iy + invH) {
                recipeSearchFocused = false;
            } else {
                recipeSearchFocused = false;
            }

            List<CraftingRecipe> activeRecipes = getFilteredRecipes();
            int totalRows = (activeRecipes.size() + 3) / 4;
            int maxScroll = Math.max(0, totalRows - 5);

            // Scrollbar click
            if (maxScroll > 0) {
                float sbX = popX + popW - 10.0f * scale;
                float sbY = startGridY;
                float sbW = 6.0f * scale;
                float sbH = 5 * slotStep - 4.0f * scale;
                if (mx >= sbX - 3.0f * scale && mx <= sbX + sbW + 3.0f * scale && my >= sbY && my <= sbY + sbH) {
                    float frac = Math.clamp((float) (my - sbY) / sbH, 0.0f, 1.0f);
                    recipeScrollRow = Math.round(frac * maxScroll);
                    return true;
                }
            }

            int startIdx = recipeScrollRow * 4;
            int endIdx = Math.min(activeRecipes.size(), startIdx + 5 * 4);

            for (int i = startIdx; i < endIdx; i++) {
                int visualRow = (i - startIdx) / 4;
                int col = (i - startIdx) % 4;
                float sx = startGridX + col * slotStep;
                float sy = startGridY + visualRow * slotStep;

                if (mx >= sx && mx <= sx + slotW && my >= sy && my <= sy + slotW) {
                    CraftingRecipe r = activeRecipes.get(i);
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
                    } else if (r.getOutput().getType() == BlockType.TORCH) {
                        if (player.getInventory().getItemCount(BlockType.COAL) >= 1 && player.getInventory().getItemCount(BlockType.STICK) >= 1) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.COAL, 1);
                            player.getInventory().removeItem(BlockType.STICK, 1);
                            craftSlots[0].setType(BlockType.COAL);
                            craftSlots[0].setCount(1);
                            craftSlots[2].setType(BlockType.STICK);
                            craftSlots[2].setCount(1);
                        }
                    } else if (r.getOutput().getType() == BlockType.BLAZE_POWDER) {
                        if (player.getInventory().getItemCount(BlockType.BLAZE_ROD) >= 1) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.BLAZE_ROD, 1);
                            craftSlots[0].setType(BlockType.BLAZE_ROD);
                            craftSlots[0].setCount(1);
                        }
                    } else if (r.getOutput().getType() == BlockType.EYE_OF_ENDER) {
                        if (player.getInventory().getItemCount(BlockType.ENDER_PEARL) >= 1 && player.getInventory().getItemCount(BlockType.BLAZE_POWDER) >= 1) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.ENDER_PEARL, 1);
                            player.getInventory().removeItem(BlockType.BLAZE_POWDER, 1);
                            craftSlots[0].setType(BlockType.ENDER_PEARL);
                            craftSlots[0].setCount(1);
                            craftSlots[1].setType(BlockType.BLAZE_POWDER);
                            craftSlots[1].setCount(1);
                        }
                    } else if (r.getOutput().getType() == BlockType.FLINT_AND_STEEL) {
                        if (player.getInventory().getItemCount(BlockType.IRON_INGOT) >= 1 && player.getInventory().getItemCount(BlockType.GUNPOWDER) >= 1) {
                            returnCraftSlotsToInventory(player);
                            player.getInventory().removeItem(BlockType.IRON_INGOT, 1);
                            player.getInventory().removeItem(BlockType.GUNPOWDER, 1);
                            craftSlots[0].setType(BlockType.IRON_INGOT);
                            craftSlots[0].setCount(1);
                            craftSlots[1].setType(BlockType.GUNPOWDER);
                            craftSlots[1].setCount(1);
                        }
                    }
                    return true;
                }
            }
        }

        // Armor Slots (4 vertical slots on left: 0=Helmet, 1=Chestplate, 2=Leggings, 3=Boots)
        float armorX = ix + 8.0f * scale;
        for (int i = 0; i < 4; i++) {
            float armorY = iy + (8.0f + i * 18.0f) * scale;
            if (mx >= armorX && mx <= armorX + 18.0f * scale && my >= armorY && my <= armorY + 18.0f * scale) {
                handleArmorSlotClick(i, button, isShiftDown, player);
                return true;
            }
        }

        // Shield / Offhand slot
        float shieldX = ix + 77.0f * scale;
        float shieldY = iy + 62.0f * scale;
        if (mx >= shieldX && mx <= shieldX + 18.0f * scale && my >= shieldY && my <= shieldY + 18.0f * scale) {
            onSlotClicked(player.getOffhandItem(), button, player);
            return true;
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
                    onSlotClicked(craftSlots[slotIdx], button, player);
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
                if (isShiftDown) {
                    int craftedCount = 0;
                    BlockType targetType = res.getType();
                    while (true) {
                        ItemStack craftRes = getCraftingResult();
                        if (craftRes == null || craftRes.isEmpty() || craftRes.getType() != targetType) break;
                        if (!player.getInventory().hasSpaceFor(craftRes.getType(), craftRes.getCount())) break;
                        player.getInventory().addItem(craftRes.getType(), craftRes.getCount());
                        craftedCount += craftRes.getCount();
                        for (ItemStack s : craftSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                    }
                    if (craftedCount > 0) {
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                    }
                } else {
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
                    handleInventorySlotClick(player.getInventory().getSlot(slotIndex), button, isShiftDown, player);
                    return true;
                }
            }
        }

        // Hotbar Grid in Inventory (1 row x 9 columns)
        float hotbarInvY = iy + 142.0f * scale;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * scale;
            if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                handleInventorySlotClick(player.getInventory().getSlot(col), button, isShiftDown, player);
                return true;
            }
        }

        return true;
    }

    private void handleInventorySlotClick(ItemStack slot, int button, boolean isShiftDown, Player player) {
        if (isShiftDown && slot != null && !slot.isEmpty() && slot.getType().isArmor()) {
            int targetArmorSlot = slot.getType().getArmorSlot().getIndex();
            ItemStack armorSlot = player.getArmorSlot(targetArmorSlot);
            if (armorSlot.isEmpty()) {
                armorSlot.setType(slot.getType());
                armorSlot.setCount(1);
                armorSlot.setDamage(slot.getDamage());
                if (slot.getCount() > 1) {
                    slot.add(-1);
                } else {
                    slot.clear();
                }
                no.minecraft.sound.SoundManager.getInstance().play("click", 0.9f);
                return;
            }
        }
        onSlotClicked(slot, button, player);
    }

    private void handleArmorSlotClick(int armorIndex, int button, boolean isShiftDown, Player player) {
        ItemStack slot = player.getArmorSlot(armorIndex);
        if (slot == null) return;

        if (isShiftDown) {
            if (!slot.isEmpty()) {
                if (player.getInventory().hasSpaceFor(slot.getType(), slot.getCount())) {
                    player.getInventory().addItem(slot.getType(), slot.getCount(), slot.getDamage());
                    slot.clear();
                    no.minecraft.sound.SoundManager.getInstance().play("click", 0.9f);
                }
            }
            return;
        }

        if (carriedItem.isEmpty()) {
            if (!slot.isEmpty()) {
                carriedItem.setType(slot.getType());
                carriedItem.setCount(slot.getCount());
                carriedItem.setDamage(slot.getDamage());
                slot.clear();
                no.minecraft.sound.SoundManager.getInstance().play("click", 0.8f);
            }
        } else {
            if (carriedItem.getType().isArmor() && carriedItem.getType().getArmorSlot().getIndex() == armorIndex) {
                BlockType tempType = slot.getType();
                int tempCount = slot.getCount();
                int tempDamage = slot.getDamage();

                slot.setType(carriedItem.getType());
                slot.setCount(1);
                slot.setDamage(carriedItem.getDamage());

                if (carriedItem.getCount() > 1) {
                    carriedItem.add(-1);
                    if (tempType != BlockType.AIR && tempCount > 0) {
                        player.getInventory().addItem(tempType, tempCount, tempDamage);
                    }
                } else {
                    carriedItem.setType(tempType);
                    carriedItem.setCount(tempCount);
                    carriedItem.setDamage(tempDamage);
                }
                no.minecraft.sound.SoundManager.getInstance().play("click", 0.8f);
            }
        }
    }

    public boolean handleInventoryKeyPress(int key, double mx, double my, Player player, int windowWidth, int windowHeight) {
        if (!isInventoryOpen() || player == null) return false;
        if (key < GLFW_KEY_1 || key > GLFW_KEY_9) return false;
        int hotbarIndex = key - GLFW_KEY_1;
        ItemStack hotbarSlot = player.getInventory().getSlot(hotbarIndex);

        float scale = getGuiScale(windowWidth, windowHeight);
        float invW = 176.0f * scale;
        float invH = 166.0f * scale;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        // 1. Check crafting results and furnace outputs
        if (furnaceOpen && activeFurnace != null) {
            float outX = ix + 116.0f * scale;
            float outY = iy + 31.0f * scale;
            float outSize = 24.0f * scale;
            if (mx >= outX && mx <= outX + outSize && my >= outY && my <= outY + outSize) {
                ItemStack res = activeFurnace.getOutput();
                if (!res.isEmpty()) {
                    if (hotbarSlot.isEmpty()) {
                        hotbarSlot.setType(res.getType());
                        hotbarSlot.setCount(res.getCount());
                        res.clear();
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                        return true;
                    } else if (hotbarSlot.getType() == res.getType() && hotbarSlot.getCount() + res.getCount() <= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                        hotbarSlot.add(res.getCount());
                        res.clear();
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                        return true;
                    }
                }
                return false;
            }
        } else if (craftingTableOpen) {
            float resX = ix + 124.0f * scale;
            float resY = iy + 31.0f * scale;
            float resSize = 24.0f * scale;
            if (mx >= resX && mx <= resX + resSize && my >= resY && my <= resY + resSize) {
                ItemStack res = get3x3CraftingResult();
                if (res != null && !res.isEmpty()) {
                    if (hotbarSlot.isEmpty()) {
                        hotbarSlot.setType(res.getType());
                        hotbarSlot.setCount(res.getCount());
                        for (ItemStack s : benchSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        if (res.getType() == BlockType.WOODEN_SWORD || res.getType() == BlockType.STONE_SWORD) {
                            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.TIME_TO_STRIKE);
                        } else if (res.getType() == BlockType.FURNACE) {
                            no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                        }
                        return true;
                    } else if (hotbarSlot.getType() == res.getType() && hotbarSlot.getCount() + res.getCount() <= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                        hotbarSlot.add(res.getCount());
                        for (ItemStack s : benchSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        return true;
                    }
                }
                return false;
            }
        } else if (inventoryOpen) {
            float resultSlotX = ix + 152.0f * scale;
            float resultSlotY = iy + 26.0f * scale;
            float resSize = 20.0f * scale;
            if (mx >= resultSlotX && mx <= resultSlotX + resSize && my >= resultSlotY && my <= resultSlotY + resSize) {
                ItemStack res = getCraftingResult();
                if (res != null && !res.isEmpty()) {
                    if (hotbarSlot.isEmpty()) {
                        hotbarSlot.setType(res.getType());
                        hotbarSlot.setCount(res.getCount());
                        for (ItemStack s : craftSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        return true;
                    } else if (hotbarSlot.getType() == res.getType() && hotbarSlot.getCount() + res.getCount() <= no.minecraft.player.Inventory.MAX_STACK_SIZE) {
                        hotbarSlot.add(res.getCount());
                        for (ItemStack s : craftSlots) {
                            if (!s.isEmpty()) s.add(-1);
                        }
                        no.minecraft.sound.SoundManager.getInstance().play("click");
                        return true;
                    }
                }
                return false;
            }
        }

        // 2. Standard slot swap
        ItemStack hoveredSlot = getSlotAt(mx, my, player, windowWidth, windowHeight);
        if (hoveredSlot != null && hoveredSlot != hotbarSlot) {
            BlockType tempType = hoveredSlot.getType();
            int tempCount = hoveredSlot.getCount();
            int tempDamage = hoveredSlot.getDamage();

            hoveredSlot.setType(hotbarSlot.getType());
            hoveredSlot.setCount(hotbarSlot.getCount());
            hoveredSlot.setDamage(hotbarSlot.getDamage());

            hotbarSlot.setType(tempType);
            hotbarSlot.setCount(tempCount);
            hotbarSlot.setDamage(tempDamage);

            no.minecraft.sound.SoundManager.getInstance().play("click");
            return true;
        }

        return false;
    }

    public boolean handleSwapKeyPress(double mx, double my, Player player, int windowWidth, int windowHeight) {
        if (!isInventoryOpen() || player == null) return false;
        ItemStack hoveredSlot = getSlotAt(mx, my, player, windowWidth, windowHeight);
        ItemStack offhand = player.getOffhandItem();
        if (hoveredSlot != null && hoveredSlot != offhand) {
            BlockType tempType = hoveredSlot.getType();
            int tempCount = hoveredSlot.getCount();
            int tempDamage = hoveredSlot.getDamage();

            hoveredSlot.setType(offhand.getType());
            hoveredSlot.setCount(offhand.getCount());
            hoveredSlot.setDamage(offhand.getDamage());

            offhand.setType(tempType);
            offhand.setCount(tempCount);
            offhand.setDamage(tempDamage);

            no.minecraft.sound.SoundManager.getInstance().play("click", 0.8f);
            return true;
        } else {
            player.swapHands();
            return true;
        }
    }

    public boolean handleDropKeyPress(double mx, double my, boolean dropAll, Player player, int windowWidth, int windowHeight) {
        if (!inventoryOpen && !craftingTableOpen && (!furnaceOpen || activeFurnace == null) && (!chestOpen || activeChest == null)) return false;

        float scale = getGuiScale(windowWidth, windowHeight);
        float invW = 176.0f * scale;
        float invH = 166.0f * scale;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        // If carrying an item on mouse cursor, drop that
        if (!carriedItem.isEmpty()) {
            return dropFromSlot(carriedItem, dropAll, player);
        }

        if (furnaceOpen && activeFurnace != null) {
            float outX = ix + 116.0f * scale;
            float outY = iy + 31.0f * scale;
            float outSize = 24.0f * scale;
            if (mx >= outX && mx <= outX + outSize && my >= outY && my <= outY + outSize) {
                ItemStack out = activeFurnace.getOutput();
                if (!out.isEmpty()) {
                    boolean dropped = dropFromSlot(out, dropAll, player);
                    if (dropped) {
                        no.minecraft.advancement.AdvancementManager.getInstance().unlock(no.minecraft.advancement.AdvancementManager.Advancement.HOT_TOPIC);
                    }
                    return dropped;
                }
            }
            float inX = ix + 56.0f * scale;
            float inY = iy + 17.0f * scale;
            if (mx >= inX && mx <= inX + 18.0f * scale && my >= inY && my <= inY + 18.0f * scale) {
                return dropFromSlot(activeFurnace.getInput(), dropAll, player);
            }
            float fuelX = ix + 56.0f * scale;
            float fuelY = iy + 53.0f * scale;
            if (mx >= fuelX && mx <= fuelX + 18.0f * scale && my >= fuelY && my <= fuelY + 18.0f * scale) {
                return dropFromSlot(activeFurnace.getFuel(), dropAll, player);
            }
        } else if (chestOpen && activeChest != null) {
            float chestGridX = ix + 8.0f * scale;
            float chestGridY = iy + 18.0f * scale;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 9; c++) {
                    int slotIdx = r * 9 + c;
                    float sx = chestGridX + c * 18.0f * scale;
                    float sy = chestGridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        return dropFromSlot(activeChest.getSlot(slotIdx), dropAll, player);
                    }
                }
            }
        } else if (craftingTableOpen) {
            // 1. 3x3 Bench Slots
            float gridX = ix + 30.0f * scale;
            float gridY = iy + 17.0f * scale;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int slotIdx = r * 3 + c;
                    float sx = gridX + c * 18.0f * scale;
                    float sy = gridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        return dropFromSlot(benchSlots[slotIdx], dropAll, player);
                    }
                }
            }

            // 2. Result Slot
            float resX = ix + 124.0f * scale;
            float resY = iy + 31.0f * scale;
            float resSize = 24.0f * scale;
            if (mx >= resX && mx <= resX + resSize && my >= resY && my <= resY + resSize) {
                return dropFromResultSlot(true, dropAll, player);
            }
        } else {
            // 1. 2x2 Crafting Slots
            float craftGridX = ix + 98.0f * scale;
            float craftGridY = iy + 18.0f * scale;
            for (int r = 0; r < 2; r++) {
                for (int c = 0; c < 2; c++) {
                    int slotIdx = r * 2 + c;
                    float sx = craftGridX + c * 18.0f * scale;
                    float sy = craftGridY + r * 18.0f * scale;
                    if (mx >= sx && mx <= sx + 18.0f * scale && my >= sy && my <= sy + 18.0f * scale) {
                        return dropFromSlot(craftSlots[slotIdx], dropAll, player);
                    }
                }
            }

            // 2. Result Slot
            float resultSlotX = ix + 152.0f * scale;
            float resultSlotY = iy + 26.0f * scale;
            float resSize = 20.0f * scale;
            if (mx >= resultSlotX && mx <= resultSlotX + resSize && my >= resultSlotY && my <= resultSlotY + resSize) {
                return dropFromResultSlot(false, dropAll, player);
            }
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
                    return dropFromSlot(player.getInventory().getSlot(slotIndex), dropAll, player);
                }
            }
        }

        // Hotbar Grid in Inventory (1 row x 9 columns)
        float hotbarInvY = iy + 142.0f * scale;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * scale;
            if (mx >= sx && mx <= sx + 18.0f * scale && my >= hotbarInvY && my <= hotbarInvY + 18.0f * scale) {
                return dropFromSlot(player.getInventory().getSlot(col), dropAll, player);
            }
        }

        return false;
    }

    private boolean dropFromSlot(ItemStack slot, boolean dropAll, Player player) {
        if (slot == null || slot.isEmpty()) return false;
        int count = dropAll ? slot.getCount() : 1;
        BlockType type = slot.getType();
        slot.add(-count);
        Vector3f eye = player.getEyePosition();
        Vector3f fwd = player.getCamera().getForward();
        player.getWorld().spawnItemDrop(eye.x, eye.y - 0.2f, eye.z, fwd.x * 4.5f, fwd.y * 4.5f + 1.5f, fwd.z * 4.5f, type, count);
        no.minecraft.sound.SoundManager.getInstance().play("pop", 0.8f);
        return true;
    }

    private boolean dropFromResultSlot(boolean isBench, boolean dropAll, Player player) {
        ItemStack res = isBench ? get3x3CraftingResult() : getCraftingResult();
        if (res == null || res.isEmpty()) return false;
        int count = res.getCount();
        BlockType type = res.getType();
        ItemStack[] sourceSlots = isBench ? benchSlots : craftSlots;
        for (ItemStack s : sourceSlots) {
            if (!s.isEmpty()) s.add(-1);
        }
        Vector3f eye = player.getEyePosition();
        Vector3f fwd = player.getCamera().getForward();
        player.getWorld().spawnItemDrop(eye.x, eye.y - 0.2f, eye.z, fwd.x * 4.5f, fwd.y * 4.5f + 1.5f, fwd.z * 4.5f, type, count);
        no.minecraft.sound.SoundManager.getInstance().play("pop", 0.8f);
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
        glDisable(GL_CULL_FACE);
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

        // 2. Minecraft Crosshair in center (only when inventory is closed and not in front third person)
        if (!isInventoryOpen() && player.getCamera().getPerspective() != no.minecraft.player.Perspective.THIRD_PERSON_FRONT) {
            float cx = windowWidth / 2.0f;
            float cy = windowHeight / 2.0f;
            if (showDebugInfo) {
                drawDebugCrosshair(geom, overlayGeom, cx, cy, player.getCamera());
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

        // 3f. Scrolling Combat Text (damage dealt to mobs in hearts)
        renderCombatTexts(overlayGeom, windowWidth, windowHeight, player);


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
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    /**
     * Runs the three HUD shader passes (untextured, textured, overlay) over
     * geometry emitted by a container screen (inventory / crafting table /
     * furnace). The screens only build vertex lists; the GL work lives here
     * so the dark container backdrop covers the hotbar etc. exactly like the
     * legacy single-render HUD.
     */
    public void drawScreenGeometry(int windowWidth, int windowHeight, TextureAtlas atlas,
                                   List<Float> geom, List<Float> tex, List<Float> overlayGeom) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Matrix4f ortho = new Matrix4f().ortho(0, windowWidth, windowHeight, 0, -1, 1);
        hudShader.bind();
        hudShader.setUniform("uOrtho", ortho);

        hudShader.setUniform("uUseTexture", 0);
        drawVertices(geom);

        hudShader.setUniform("uUseTexture", 1);
        atlas.bind();
        drawVertices(tex);
        atlas.unbind();

        hudShader.setUniform("uUseTexture", 0);
        drawVertices(overlayGeom);

        hudShader.unbind();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glDisable(GL_BLEND);
    }

    private void renderMinecraftHUD(List<Float> geom, List<Float> tex, List<Float> overlayGeom, int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        float pScale = getGuiScale(windowWidth, windowHeight); // Dynamic GUI scale as in Minecraft 1.16.1
        float hotbarW = 182.0f * pScale;
        float hotbarH = 22.0f * pScale;
        float hx = (windowWidth - hotbarW) / 2.0f;
        float hy = windowHeight - hotbarH - 4.0f * pScale;

        // --- A. Health Bar (10 Hearts on left) ---
        if (player.getGameMode() != GameMode.CREATIVE) {
            float heartY = hy - 18.0f * pScale;
            int hp = player.getHealth();

            for (int i = 0; i < 10; i++) {
                float heartX = hx + i * (8.0f * pScale);
                int state = getHeartState(hp, i);
                drawPixelHeart(geom, heartX, heartY, pScale, state);
            }

            // --- A2. Armor Bar (10 Armor icons directly above health bar when totalArmor > 0) ---
            int totalArmor = player.getTotalArmor();
            if (totalArmor > 0) {
                float armorY = heartY - 10.0f * pScale;
                for (int i = 0; i < 10; i++) {
                    float armorX = hx + i * (8.0f * pScale);
                    int state = getArmorState(totalArmor, i);
                    drawPixelArmor(geom, armorX, armorY, pScale, state);
                }
            }

            // --- B. Hunger Bar (10 Drumsticks on right, empties from left to right) ---
            float hungerY = hy - 18.0f * pScale;
            int hungerVal = player.getHunger();
            for (int i = 0; i < 10; i++) {
                float drumX = hx + hotbarW - (10 - i) * (8.0f * pScale) - 1.0f * pScale;
                int state = getDrumstickState(hungerVal, i);
                // Low hunger shake
                float shakeY = (hungerVal <= 6 && ((int)(System.currentTimeMillis() / 90) + i) % 3 == 0)
                        ? (1.5f * pScale) : 0.0f;
                drawPixelDrumstick(geom, drumX, hungerY + shakeY, pScale, state);
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
                int tileId = block.getItemTexture();
                float[] uv = TextureAtlas.getUVs(tileId);
                addRect(tex, slotX, slotY, slotInnerSize, slotInnerSize, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);

                // Draw stack count number directly in the bottom-right corner of the slot on TOP of texture
                if (count > 1 && !block.isDamageable()) {
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

        // --- Offhand Slot (to the left of hotbar) ---
        ItemStack offhand = player.getOffhandItem();
        if (offhand != null && !offhand.isEmpty()) {
            float offhandX = hx - 29.0f * pScale;
            float offhandY = hy;
            drawPixelOffhandSlot(geom, offhandX, offhandY, pScale);

            BlockType offBlock = offhand.getType();
            int offCount = offhand.getCount();
            if (offBlock != null && offBlock != BlockType.AIR && offCount > 0) {
                float offItemX = offhandX + 6.0f * pScale;
                float offItemY = offhandY + 3.0f * pScale;
                int tileId = offBlock.getItemTexture();
                float[] uv = TextureAtlas.getUVs(tileId);
                addRect(tex, offItemX, offItemY, slotInnerSize, slotInnerSize, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);

                if (offCount > 1 && !offBlock.isDamageable()) {
                    float numRight = offhandX + 22.0f * pScale;
                    float numBottom = hy + 18.5f * pScale;
                    drawMinecraftNumber(overlayGeom, offCount, numRight, numBottom, pScale * 0.95f);
                }

                if (offBlock.isDamageable() && offhand.getDamage() > 0) {
                    float barW = 12.0f * pScale;
                    float barH = 1.2f * pScale;
                    float bx = offItemX + 2.0f * pScale;
                    float by = offItemY + 13.0f * pScale;
                    float ratio = offhand.getDurabilityRatio();
                    addRect(overlayGeom, bx, by, barW, barH, 0, 0, 0, 0, 0, 0, 0, 1.0f);
                    float r = ratio < 0.5f ? 1.0f : (1.0f - ratio) * 2.0f;
                    float g = ratio > 0.5f ? 1.0f : ratio * 2.0f;
                    addRect(overlayGeom, bx, by, barW * ratio, barH, 0, 0, 0, 0, r, g, 0.0f, 1.0f);
                }
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

    private void drawPixelOffhandSlot(List<Float> g, float x, float y, float p) {
        float w = 29.0f * p;
        float h = 24.0f * p;
        // Outer dark border
        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.12f, 0.12f, 0.12f, 1.0f);
        // Inner base fill
        addRect(g, x + p, y + p, w - 2 * p, h - 2 * p, 0, 0, 0, 0, 0.54f, 0.54f, 0.54f, 1.0f);
        // Slot background
        float sx = x + 4.0f * p;
        addRect(g, sx + p, y + 2.0f * p + p, 18.0f * p, 18.0f * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        // Inset bevel
        addRect(g, sx + p, y + 3.0f * p, 18.0f * p, p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
        addRect(g, sx + p, y + 3.0f * p, p, 18.0f * p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
        addRect(g, sx + p, y + 20.0f * p, 18.0f * p, p, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
        addRect(g, sx + 18.0f * p, y + 3.0f * p, p, 18.0f * p, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
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

    public static int getHeartState(int hp, int index) {
        int heartVal = (index + 1) * 2;
        if (hp >= heartVal) {
            return 2; // Full
        } else if (hp == heartVal - 1) {
            return 1; // Half
        } else {
            return 0; // Empty
        }
    }

    public static int getDrumstickState(int hungerVal, int index) {
        // Hunger empties from left to right (index 0 is leftmost, empties first at threshold 20)
        int threshold = (10 - index) * 2;
        if (hungerVal >= threshold) {
            return 2; // Full
        } else if (hungerVal == threshold - 1) {
            return 1; // Half
        } else {
            return 0; // Empty
        }
    }

    public static int getArmorState(int totalArmor, int index) {
        int threshold = (index + 1) * 2;
        if (totalArmor >= threshold) {
            return 2; // Full
        } else if (totalArmor == threshold - 1) {
            return 1; // Half
        } else {
            return 0; // Empty
        }
    }

    private void drawPixelArmor(List<Float> g, float x, float y, float p, int state) {
        // 9x9 Pixel Armor Chestplate icon matching Minecraft Java Edition GUI icons
        int[][] pat = {
                {0,1,1,0,0,0,1,1,0},
                {1,2,2,1,0,1,2,2,1},
                {1,3,2,2,1,2,2,4,1},
                {1,3,2,2,2,2,4,4,1},
                {0,1,2,2,2,2,4,1,0},
                {0,1,2,2,2,2,4,1,0},
                {0,0,1,2,2,4,1,0,0},
                {0,0,1,2,2,4,1,0,0},
                {0,0,0,1,1,1,0,0,0}
        };

        for (int py = 0; py < pat.length; py++) {
            for (int px = 0; px < pat[py].length; px++) {
                int c = pat[py][px];
                if (c == 0) continue;

                float rx = x + px * p;
                float ry = y + py * p;

                if (c == 1) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 1.0f); // Dark outline
                } else {
                    if (state == 0 || (state == 1 && px >= 5)) {
                        // Empty: dark translucent background
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 0.4f);
                    } else if (c == 3) {
                        // Highlight
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
                    } else if (c == 4) {
                        // Shading
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.60f, 0.60f, 0.65f, 1.0f);
                    } else {
                        // Metal base
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.88f, 0.88f, 0.92f, 1.0f);
                    }
                }
            }
        }
    }

    private void drawPixelHeart(List<Float> g, float x, float y, float p, int state) {
        drawPixelHeart(g, x, y, p, state, 1.0f);
    }

    private void drawPixelHeart(List<Float> g, float x, float y, float p, int state, float a) {
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
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, a); // Black border
                } else {
                    if (state == 0 || (state == 1 && px >= 5)) {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.22f, 0.12f, 0.12f, a); // Empty
                    } else if (c == 3) {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, a); // White sheen
                    } else if (c == 4) {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.65f, 0.05f, 0.05f, a); // Dark shadow
                    } else {
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.95f, 0.12f, 0.12f, a); // Red
                    }
                }
            }
        }
    }

    private void drawPixelDrumstick(List<Float> g, float x, float y, float p, int state) {
        // 9x9 Pixel Drumstick matching Minecraft
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
                } else if (c == 5) {
                    addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.95f, 0.90f, 0.82f, 1.0f); // Bone white
                } else {
                    // Meat (c == 2, 3, 4)
                    if (state == 0) {
                        // Empty drumstick: dark background
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.18f, 0.12f, 0.08f, 0.35f);
                    } else if (state == 1 && px < 4) {
                        // Half drumstick: left half is empty
                        addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.18f, 0.12f, 0.08f, 0.35f);
                    } else {
                        // Full or right half
                        if (c == 2) {
                            addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.58f, 0.35f, 0.18f, 1.0f); // Meat brown
                        } else if (c == 3) {
                            addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.88f, 0.35f, 0.35f, 1.0f); // Meat sheen
                        } else if (c == 4) {
                            addRect(g, rx, ry, p, p, 0, 0, 0, 0, 0.38f, 0.22f, 0.10f, 1.0f); // Dark shadow
                        }
                    }
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

    private void drawThickLine(List<Float> g, float x0, float y0, float x1, float y1, float thickness, float r, float gr, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.8f) {
            addRect(g, x0 - thickness * 0.5f, y0 - thickness * 0.5f, thickness, thickness, 0, 0, 0, 0, r, gr, b, a);
            return;
        }
        float nx = -dy / len * (thickness * 0.5f);
        float ny = dx / len * (thickness * 0.5f);

        addVertex(g, x0 - nx, y0 - ny, 0, 0, r, gr, b, a);
        addVertex(g, x0 + nx, y0 + ny, 0, 0, r, gr, b, a);
        addVertex(g, x1 + nx, y1 + ny, 0, 0, r, gr, b, a);

        addVertex(g, x0 - nx, y0 - ny, 0, 0, r, gr, b, a);
        addVertex(g, x1 + nx, y1 + ny, 0, 0, r, gr, b, a);
        addVertex(g, x1 - nx, y1 - ny, 0, 0, r, gr, b, a);
    }

    private static class AxisData {
        final float endX, endY, dz;
        final float r, g, b;
        final char label;

        AxisData(float endX, float endY, float dz, float r, float g, float b, char label) {
            this.endX = endX;
            this.endY = endY;
            this.dz = dz;
            this.r = r;
            this.g = g;
            this.b = b;
            this.label = label;
        }
    }

    private void drawDebugCrosshair(List<Float> geom, List<Float> overlayGeom, float cx, float cy, no.minecraft.player.Camera camera) {
        if (camera == null) {
            drawMinecraftCrosshair(geom, cx, cy);
            return;
        }

        org.joml.Vector3f right = camera.getRight();
        org.joml.Vector3f up = camera.getUp();
        org.joml.Vector3f fwd = camera.getForward();

        // Length of crosshair arms in screen pixels
        float armLength = 16.0f;

        // 3D Oblique Perspective projection:
        // World vectors for +X, +Y, +Z axes
        org.joml.Vector3f[] axes = {
                new org.joml.Vector3f(1.0f, 0.0f, 0.0f),  // X (Red)
                new org.joml.Vector3f(0.0f, 1.0f, 0.0f),  // Y (Green)
                new org.joml.Vector3f(0.0f, 0.0f, 1.0f)   // Z (Blue)
        };
        float[][] colors = {
                {0.95f, 0.18f, 0.18f}, // Red
                {0.18f, 0.95f, 0.18f}, // Green
                {0.25f, 0.55f, 1.00f}  // Blue
        };
        char[] labels = {'X', 'Y', 'Z'};

        List<AxisData> axisList = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            org.joml.Vector3f v = axes[i];
            float dx = right.dot(v);
            float dy = -up.dot(v);   // Screen Y is down
            float dz = fwd.dot(v);   // Forward into screen

            // Oblique perspective: axes pointing into screen have depth offset
            float ex = cx + (dx - dz * 0.32f) * armLength;
            float ey = cy + (dy + dz * 0.32f) * armLength;

            axisList.add(new AxisData(ex, ey, dz, colors[i][0], colors[i][1], colors[i][2], labels[i]));
        }

        // Draw axes from back to front (dz > 0 is deeper in screen, dz < 0 is closer)
        axisList.sort((a, b) -> Float.compare(b.dz, a.dz));

        for (AxisData a : axisList) {
            // Dark border around line
            drawThickLine(geom, cx, cy, a.endX, a.endY, 4.0f, 0.0f, 0.0f, 0.0f, 0.75f);
            // Colored axis line
            drawThickLine(geom, cx, cy, a.endX, a.endY, 2.0f, a.r, a.g, a.b, 1.0f);

            // Tip indicator
            addRect(geom, a.endX - 1.5f, a.endY - 1.5f, 3.0f, 3.0f, 0, 0, 0, 0, a.r, a.g, a.b, 1.0f);

            // Label at tip (X, Y, Z)
            float lx = a.endX + (a.endX >= cx ? 3.0f : -7.0f);
            float ly = a.endY + (a.endY >= cy ? 2.0f : -6.0f);
            drawHudChar(overlayGeom, a.label, lx, ly, 0.9f, a.r, a.g, a.b);
        }

        // Center white crosshair dot
        addRect(geom, cx - 2.0f, cy - 2.0f, 4.0f, 4.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.8f);
        addRect(geom, cx - 1.0f, cy - 1.0f, 2.0f, 2.0f, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    void drawMinecraftNumber(List<Float> g, int number, float rightX, float bottomY, float s) {
        if (number <= 1) return;
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

    // addRect/addVertex are shared via static imports from UiBatch

    private void drawVertices(List<Float> vertices) {
        UiBatch.drawVertices(vaoId, vboId, vertices);
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

        float p = getGuiScale(windowWidth, 720);
        float barW = 182.0f * p;
        float barH = 5.0f * p;
        float bx = (windowWidth - barW) / 2.0f;
        float by = 12.0f * p;

        // Boss Title text: "Ender Dragon"
        String title = I18n.get("boss.ender_dragon");
        float scale = p * 0.70f;
        float textW = title.length() * (6.0f * scale);
        float tx = (windowWidth - textW) / 2.0f;
        drawHudText(overlayGeom, title, tx, by - 6.0f * (p / 2.4f), scale, 0.95f, 0.4f, 0.95f);

        // Background dark bar
        addRect(geom, bx - p * 0.8f, by - p * 0.8f, barW + p * 1.6f, barH + p * 1.6f, 0, 0, 0, 0, 0.08f, 0.08f, 0.08f, 0.85f);
        addRect(geom, bx, by, barW, barH, 0, 0, 0, 0, 0.22f, 0.08f, 0.25f, 1.0f);

        // Purple Boss Health Fill
        float hpRatio = Math.clamp((float) dragon.getHealth() / dragon.getType().getMaxHealth(), 0.0f, 1.0f);
        float fillW = barW * hpRatio;
        if (fillW > 0) {
            addRect(geom, bx, by, fillW, barH, 0, 0, 0, 0, 0.82f, 0.18f, 0.88f, 1.0f);
            addRect(geom, bx, by, fillW, 1.2f * p, 0, 0, 0, 0, 0.95f, 0.45f, 1.0f, 1.0f);
        }
    }

    private void renderVictoryOverlay(List<Float> geom, List<Float> overlayGeom, int windowWidth, int windowHeight) {
        // Dark translucent overlay
        addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.75f);

        float p = getGuiScale(windowWidth, windowHeight);
        // Game Over / Victory banners
        String title = I18n.get("victory.title");
        String titleSub = I18n.get("victory.subtitle");
        float scale = p * 1.4f;
        float textW = title.length() * (6.0f * scale);
        float tx = (windowWidth - textW) / 2.0f;
        float ty = windowHeight * 0.30f;

        drawHudText(overlayGeom, title, tx, ty, scale, 1.0f, 0.85f, 0.15f);

        float scale2 = p * 0.85f;
        float textW2 = titleSub.length() * (6.0f * scale2);
        drawHudText(overlayGeom, titleSub, (windowWidth - textW2) / 2.0f, ty + 16.0f * p, scale2, 0.85f, 0.45f, 0.95f);

        String desc1 = I18n.get("victory.dragon_defeated");
        float scale3 = p * 0.70f;
        drawHudText(overlayGeom, desc1, (windowWidth - desc1.length() * (6.0f * scale3)) / 2.0f, ty + 34.0f * p, scale3, 0.9f, 0.9f, 0.9f);

        String hint = I18n.get("victory.hint");
        drawHudText(overlayGeom, hint, (windowWidth - hint.length() * (6.0f * scale3)) / 2.0f, ty + 50.0f * p, scale3, 0.7f, 0.7f, 0.7f);
    }

    void drawHudText(List<Float> g, String text, float startX, float startY, float s, float r, float gr, float b) {
        drawHudText(g, text, startX, startY, s, r, gr, b, 1.0f);
    }

    void drawHudText(List<Float> g, String text, float startX, float startY, float s, float r, float gr, float b, float a) {
        for (int i = 0; i < text.length(); i++) {
            float px = startX + i * (6.0f * s);
            char c = Character.toUpperCase(text.charAt(i));
            drawHudChar(g, c, px + s * 0.5f, startY + s * 0.5f, s, 0.12f, 0.12f, 0.12f, a * 0.85f);
            drawHudChar(g, c, px, startY, s, r, gr, b, a);
        }
    }

    private void drawHudChar(List<Float> g, char ch, float x, float y, float s, float r, float gr, float b) {
        drawHudChar(g, ch, x, y, s, r, gr, b, 1.0f);
    }

    private void drawHudChar(List<Float> g, char ch, float x, float y, float s, float r, float gr, float b, float a) {
        UiBatch.drawLetterBlock(g, ch, x, y, s, r, gr, b, a);
    }

    private void renderChat(List<Float> geom, List<Float> overlayGeom, int windowWidth, int windowHeight, no.minecraft.chat.ChatManager chat) {
        float p = getGuiScale(windowWidth, windowHeight);
        float scale = p * 0.55f;
        float chatX = 4.0f * p;
        float chatBottom = windowHeight - (chat.isOpen() ? 16.0f * p : 34.0f * p);
        float lineHeight = 10.0f * scale;

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
            float boxH = 12.0f * scale;
            float boxY = windowHeight - boxH - 2.0f * p;
            float boxW = windowWidth - chatX * 2.0f;

            // Dark input background with white/gray border
            addRect(geom, chatX - 2.0f, boxY, boxW, boxH, 0, 0, 0, 0, 0.05f, 0.05f, 0.05f, 0.85f);
            addRect(geom, chatX - 2.0f, boxY, boxW, 1.0f, 0, 0, 0, 0, 0.6f, 0.6f, 0.6f, 1.0f);
            addRect(geom, chatX - 2.0f, boxY + boxH, boxW, 1.0f, 0, 0, 0, 0, 0.6f, 0.6f, 0.6f, 1.0f);

            // Prompt cursor text
            boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;
            String prompt = "> " + chat.getInputText() + (blink ? "_" : "");
            drawHudText(overlayGeom, prompt, chatX + 2.0f, boxY + 2.0f * scale, scale, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderDebugMenu(List<Float> geom, List<Float> overlayGeom, int windowWidth, int windowHeight,
                                Player player, no.minecraft.world.World world) {
        if (world == null || player == null) return;

        float p = getGuiScale(windowWidth, windowHeight);
        float scale = p * 0.9f;
        float lineHeight = 10.0f * scale;
        float startX = 4.0f * p;
        float startY = 4.0f * p;

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
                facing = I18n.get("debug.facing.east");
                toward = I18n.get("debug.towards.positive_x");
            } else {
                facing = I18n.get("debug.facing.west");
                toward = I18n.get("debug.towards.negative_x");
            }
        } else {
            if (fwd.z > 0) {
                facing = I18n.get("debug.facing.south");
                toward = I18n.get("debug.towards.positive_z");
            } else {
                facing = I18n.get("debug.facing.north");
                toward = I18n.get("debug.towards.negative_z");
            }
        }

        float yaw = player.getCamera().getYaw();
        float pitch = player.getCamera().getPitch();

        int skyLight = (int) (world.getSunLightLevel() * 15.0f);
        boolean openToSky = !world.isDarkAt(bx, by, bz);
        int light = openToSky ? skyLight : Math.max(0, skyLight - 8);

        int day = (int) (world.getWorldTime() / no.minecraft.world.World.DAY_LENGTH_SECONDS) + 1;
        String timeStr = world.isNight() ? I18n.get("debug.time.night") : I18n.get("debug.time.day");

        List<String> leftLines = new ArrayList<>();
        leftLines.add(I18n.format("debug.header", lastFps));
        leftLines.add(I18n.format("debug.xyz", pos.x, pos.y, pos.z));
        leftLines.add(I18n.format("debug.block", bx, by, bz));
        leftLines.add(I18n.format("debug.chunk", cx, cy, cz, inCx, inCy, inCz));
        leftLines.add(I18n.format("debug.facing", facing, toward, yaw, pitch));
        leftLines.add(I18n.format("debug.camera", player.getCamera().getPerspective().name()));
        leftLines.add(I18n.format("debug.dimension", world.getCurrentDimension().name().toLowerCase()));
        leftLines.add(I18n.format("debug.biome", world.getBiomeName(bx, by, bz)));
        leftLines.add(I18n.format("debug.light", light, skyLight, 0));
        leftLines.add(I18n.format("debug.day", day, timeStr, world.getSunLightLevel()));
        leftLines.add(I18n.format("debug.chunks",
                world.getRenderedChunkCount(), world.getLoadedChunkCount(), world.getMobs().size(), world.getDroppedItems().size()));

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

        rightLines.add(I18n.format("debug.java", javaVer, arch));
        rightLines.add(I18n.format("debug.mem", memPct, usedMem, totalMem));
        rightLines.add(I18n.format("debug.allocated", totalMem, maxMem));
        rightLines.add(I18n.format("debug.display", windowWidth, windowHeight));
        String flyStr = player.isFlying() ? " " + I18n.get("debug.flying") : "";
        rightLines.add(I18n.format("debug.gamemode", player.getGameMode().getDisplayName(), flyStr));

        if (lastTargetedHit != null) {
            rightLines.add(""); // spacer
            rightLines.add(I18n.format("debug.targeted_block",
                    lastTargetedHit.hitX, lastTargetedHit.hitY, lastTargetedHit.hitZ));
            BlockType tb = world.getBlock(lastTargetedHit.hitX, lastTargetedHit.hitY, lastTargetedHit.hitZ);
            rightLines.add(I18n.get("debug.block_name") + " " + tb.name());
            rightLines.add(I18n.get("debug.solid") + " " + tb.isSolid());
        }

        for (int i = 0; i < rightLines.size(); i++) {
            String line = rightLines.get(i);
            if (line.isEmpty()) continue;
            float y = startY + i * lineHeight;
            float textW = line.length() * (6.0f * scale);
            float rx = windowWidth - 4.0f * p - textW;
            addRect(geom, rx - 2.0f, y - 1.0f, textW + 4.0f, lineHeight - 1.0f, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.55f);
            drawHudText(overlayGeom, line, rx, y, scale, 0.90f, 0.90f, 0.90f);
        }
    }

    private void renderCombatTexts(List<Float> overlayGeom, int windowWidth, int windowHeight, Player player) {
        List<CombatTextManager.CombatText> list = CombatTextManager.getInstance().getTexts();
        if (list.isEmpty()) return;

        float fov = no.minecraft.settings.GameSettings.getInstance().getFov();
        Matrix4f proj = new Matrix4f().perspective(
                (float) Math.toRadians(fov),
                (float) windowWidth / (float) Math.max(1, windowHeight),
                0.05f,
                300.0f
        );
        Matrix4f vp = new Matrix4f(proj).mul(player.getCamera().getViewMatrix());
        Vector4f clip = new Vector4f();

        for (CombatTextManager.CombatText ct : list) {
            float t = ct.age / ct.maxLifetime;
            float floatUp = (float) Math.sin(Math.min(1.0f, ct.age * 2.2f) * (Math.PI * 0.5)) * 0.75f + ct.age * 0.35f;

            clip.set(ct.worldPos.x + ct.driftX * t, ct.worldPos.y + floatUp, ct.worldPos.z + ct.driftZ * t, 1.0f);
            vp.transform(clip);

            if (clip.w <= 0.05f) continue; // Behind camera

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) continue;

            float sx = (ndcX + 1.0f) * 0.5f * windowWidth;
            float sy = (1.0f - ndcY) * 0.5f * windowHeight;

            float alpha = t < 0.65f ? 1.0f : Math.max(0.0f, 1.0f - (t - 0.65f) / 0.35f);
            float scale = (t < 0.12f ? (1.8f - (t / 0.12f) * 0.4f) : 1.4f) * 2.0f;
            if (ct.isCrit) {
                scale *= 1.22f;
            }

            int fullHearts = (int) Math.floor(ct.hearts);
            boolean hasHalf = (ct.hearts - fullHearts) >= 0.25f;
            if (fullHearts == 0 && !hasHalf) {
                hasHalf = true;
            }
            int totalHearts = fullHearts + (hasHalf ? 1 : 0);
            totalHearts = Math.min(10, totalHearts);
            fullHearts = Math.min(fullHearts, totalHearts);

            float heartW = 9.0f * scale;
            float spacing = 2.0f * scale;
            float totalW = totalHearts * heartW + (totalHearts - 1) * spacing;

            float startX = sx - totalW * 0.5f;
            float startY = sy - 4.0f * scale;

            // Semi-transparent background pill for contrast against any scenery
            if (ct.isCrit) {
                // Golden outer accent border for critical hit
                addRect(overlayGeom, startX - 4.5f * scale, startY - 3.5f * scale, totalW + 9.0f * scale, 15.0f * scale, 0, 0, 0, 0, 1.0f, 0.78f, 0.15f, 0.70f * alpha);
                addRect(overlayGeom, startX - 3.0f * scale, startY - 2.0f * scale, totalW + 6.0f * scale, 12.0f * scale, 0, 0, 0, 0, 0.12f, 0.04f, 0.0f, 0.55f * alpha);
            } else {
                addRect(overlayGeom, startX - 3.0f * scale, startY - 2.0f * scale, totalW + 6.0f * scale, 12.0f * scale, 0, 0, 0, 0, 0.0f, 0.0f, 0.0f, 0.40f * alpha);
            }

            // Red pixel hearts only (full and half hearts)
            for (int i = 0; i < totalHearts; i++) {
                int heartState = (i < fullHearts) ? 2 : 1;
                drawPixelHeart(overlayGeom, startX + i * (heartW + spacing), startY, scale, heartState, alpha);
            }
        }
    }

    // Pixel font lives in UiBatch

    public void cleanup() {
        hudShader.cleanup();
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}
