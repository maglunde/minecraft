package no.minecraft.render;

import no.minecraft.player.CraftingRecipe;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static no.minecraft.render.UiBatch.addRect;

/**
 * The player inventory screen (E key). It is drawn on top of the game HUD
 * while the plain inventory is open; the screen itself only emits geometry
 * and forwards clicks to the HUD's consolidated click handler.
 */
public class InventoryScreen extends AbstractContainerScreen {

    public InventoryScreen(HUD hud, Supplier<Player> player, IntSupplier width, IntSupplier height) {
        super(hud, player, width, height);
    }

    @Override
    public void renderGui(List<Float> geom, List<Float> tex, List<Float> overlayGeom, int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        float p = hud.getGuiScale(windowWidth, windowHeight); // Scale
        float invW = 176.0f * p;
        float invH = 166.0f * p;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        ItemStack hoveredStack = null;

        // 1. Dark background overlay
        addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0, 0, 0, 0.65f);

        // 2. Main Window Panel (Gray with 3D Bevel)
        drawMinecraftWindowFrame(geom, ix, iy, invW, invH, p);

        // 3. Armor Slots (4 vertical slots on left)
        float armorX = ix + 8.0f * p;
        for (int i = 0; i < 4; i++) {
            float armorY = iy + (8.0f + i * 18.0f) * p;
            drawPixelSlot(geom, armorX, armorY, 18.0f * p, p);
            ItemStack armorItem = player.getArmorSlot(i);
            if (armorItem == null || armorItem.isEmpty()) {
                drawArmorSilhouette(geom, armorX + 2.0f * p, armorY + 2.0f * p, i, p);
            } else {
                if (hud.mouseX >= armorX && hud.mouseX <= armorX + 18.0f * p
                        && hud.mouseY >= armorY && hud.mouseY <= armorY + 18.0f * p) {
                    hoveredStack = armorItem;
                }
                renderSlotItem(tex, overlayGeom, armorItem, armorX, armorY, p);
            }
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
        ItemStack offhand = player.getOffhandItem();
        if (offhand == null || offhand.isEmpty()) {
            drawShieldSilhouette(geom, shieldX + 3.0f * p, shieldY + 3.0f * p, p);
        } else {
            if (hud.mouseX >= shieldX && hud.mouseX <= shieldX + 18.0f * p
                    && hud.mouseY >= shieldY && hud.mouseY <= shieldY + 18.0f * p) {
                hoveredStack = offhand;
            }
            renderSlotItem(tex, overlayGeom, offhand, shieldX, shieldY, p);
        }

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

                ItemStack stack = hud.craftSlots[slotIdx];
                if (hud.mouseX >= sx && hud.mouseX <= sx + 18.0f * p && hud.mouseY >= sy && hud.mouseY <= sy + 18.0f * p) {
                    if (!stack.isEmpty()) hoveredStack = stack;
                }
                renderSlotItem(tex, overlayGeom, stack, sx, sy, p);
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
        ItemStack craftResult = hud.getCraftingResult();
        if (craftResult != null && !craftResult.isEmpty()) {
            if (hud.mouseX >= resultSlotX && hud.mouseX <= resultSlotX + 20.0f * p && hud.mouseY >= resultSlotY && hud.mouseY <= resultSlotY + 20.0f * p) {
                hoveredStack = craftResult;
            }
            BlockType outBlock = craftResult.getType();
            int tileId = outBlock.getItemTexture();
            float[] uv = TextureAtlas.getUVs(tileId);
            addRect(tex, resultSlotX + 3.0f * p, resultSlotY + 3.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
            if (craftResult.getCount() > 0) {
                hud.drawMinecraftNumber(overlayGeom, craftResult.getCount(), resultSlotX + 19.0f * p, resultSlotY + 19.0f * p, p * 0.95f);
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
                if (hud.mouseX >= sx && hud.mouseX <= sx + 18.0f * p && hud.mouseY >= sy && hud.mouseY <= sy + 18.0f * p) {
                    if (!stack.isEmpty()) hoveredStack = stack;
                }
                renderSlotItem(tex, overlayGeom, stack, sx, sy, p);
            }
        }

        // 12. Hotbar Grid in Inventory (1 row x 9 columns)
        float hotbarInvY = iy + 142.0f * p;
        for (int col = 0; col < 9; col++) {
            float sx = mainInvX + col * 18.0f * p;
            drawPixelSlot(geom, sx, hotbarInvY, 18.0f * p, p);

            ItemStack stack = player.getInventory().getSlot(col);
            if (hud.mouseX >= sx && hud.mouseX <= sx + 18.0f * p && hud.mouseY >= hotbarInvY && hud.mouseY <= hotbarInvY + 18.0f * p) {
                if (!stack.isEmpty()) hoveredStack = stack;
            }
            renderSlotItem(tex, overlayGeom, stack, sx, hotbarInvY, p);
        }

        // 13. Pop-out Recipe Book panel if toggled
        if (hud.recipeBookOpen) {
            float popW = 126.0f * p;
            float popX = ix - popW - 6.0f;
            ItemStack rbHover = renderRecipeBookPanel(geom, tex, overlayGeom, popX, iy, popW, invH, p, player);
            if (rbHover != null) {
                hoveredStack = rbHover;
            }
        }

        // 14. Carried item on mouse cursor
        if (!hud.carriedItem.isEmpty()) {
            int tId = hud.carriedItem.getType().getItemTexture();
            float[] uv = TextureAtlas.getUVs(tId);
            float cx = hud.mouseX - 8.0f * p;
            float cy = hud.mouseY - 8.0f * p;
            addRect(tex, cx, cy, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1.0f, 1.0f, 1.0f, 1.0f);
            int displayCount = hud.getCarriedDisplayCount();
            if (displayCount > 0) {
                hud.drawMinecraftNumber(overlayGeom, displayCount, cx + 16.0f * p, cy + 16.0f * p, p * 0.95f);
            }
        }

        // 15. Item tooltip popup on hover
        if (hud.carriedItem.isEmpty() && hoveredStack != null && !hoveredStack.isEmpty()) {
            renderItemTooltip(overlayGeom, hoveredStack, hud.mouseX, hud.mouseY, windowWidth, windowHeight);
        }
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
}
