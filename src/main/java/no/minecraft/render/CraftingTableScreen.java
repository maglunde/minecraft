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
 * The crafting table screen (right-click on a crafting table). It is drawn
 * on top of the game HUD while the crafting table is open; the screen itself
 * only emits geometry and forwards clicks to the HUD's consolidated click
 * handler.
 */
public class CraftingTableScreen extends AbstractContainerScreen {

    public CraftingTableScreen(HUD hud, Supplier<Player> player, IntSupplier width, IntSupplier height) {
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

                ItemStack stack = hud.benchSlots[slotIdx];
                if (hud.mouseX >= sx && hud.mouseX <= sx + 18.0f * p && hud.mouseY >= sy && hud.mouseY <= sy + 18.0f * p) {
                    if (!stack.isEmpty()) hoveredStack = stack;
                }
                renderSlotItem(tex, overlayGeom, stack, sx, sy, p);
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
        ItemStack craftResult = hud.get3x3CraftingResult();
        if (craftResult != null && !craftResult.isEmpty()) {
            if (hud.mouseX >= resX && hud.mouseX <= resX + 24.0f * p && hud.mouseY >= resY && hud.mouseY <= resY + 24.0f * p) {
                hoveredStack = craftResult;
            }
            BlockType outBlock = craftResult.getType();
            int tileId = outBlock.getItemTexture();
            float[] uv = TextureAtlas.getUVs(tileId);
            addRect(tex, resX + 4.0f * p, resY + 4.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
            if (craftResult.getCount() > 0) {
                hud.drawMinecraftNumber(overlayGeom, craftResult.getCount(), resX + 22.0f * p, resY + 22.0f * p, p * 0.95f);
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
                if (hud.mouseX >= sx && hud.mouseX <= sx + 18.0f * p && hud.mouseY >= sy && hud.mouseY <= sy + 18.0f * p) {
                    if (!stack.isEmpty()) hoveredStack = stack;
                }
                renderSlotItem(tex, overlayGeom, stack, sx, sy, p);
            }
        }

        // 9. Hotbar Grid in Inventory (1 row x 9 columns)
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

        // 10. Pop-out Recipe Book panel if toggled
        if (hud.recipeBookOpen) {
            float popW = 126.0f * p;
            float popX = ix - popW - 6.0f;
            ItemStack rbHover = renderRecipeBookPanel(geom, tex, overlayGeom, popX, iy, popW, invH, p, player);
            if (rbHover != null) {
                hoveredStack = rbHover;
            }
        }

        // 11. Carried item on mouse cursor
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

        // 12. Item tooltip popup on hover
        if (hud.carriedItem.isEmpty() && hoveredStack != null && !hoveredStack.isEmpty()) {
            renderItemTooltip(overlayGeom, hoveredStack, hud.mouseX, hud.mouseY, windowWidth, windowHeight);
        }
    }
}
