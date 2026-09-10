package no.minecraft.render;

import no.minecraft.i18n.I18n;
import no.minecraft.player.ItemStack;
import no.minecraft.player.Player;
import no.minecraft.world.ChestData;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static no.minecraft.render.UiBatch.addRect;

/**
 * The chest screen (right-click on a chest). Renders 27 chest slots (3 rows x 9 cols)
 * above the player's 27 inventory + 9 hotbar slots.
 */
public class ChestScreen extends AbstractContainerScreen {

    public ChestScreen(HUD hud, Supplier<Player> player, IntSupplier width, IntSupplier height) {
        super(hud, player, width, height);
    }

    @Override
    public void renderGui(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                          int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        ChestData chest = hud.getActiveChest();
        if (chest == null) return;

        float p = hud.getGuiScale(windowWidth, windowHeight);
        float invW = 176.0f * p;
        float invH = 166.0f * p;
        float ix = (windowWidth - invW) / 2.0f;
        float iy = (windowHeight - invH) / 2.0f;

        ItemStack hoveredStack = null;

        // 1. Dark background overlay
        addRect(geom, 0, 0, windowWidth, windowHeight, 0, 0, 0, 0, 0, 0, 0, 0.65f);

        // 2. Main Window Panel
        drawMinecraftWindowFrame(geom, ix, iy, invW, invH, p);

        // 3. Titles (container.chest / container.inventory)
        hud.drawHudText(overlayGeom, I18n.get("container.chest"), ix + 8.0f * p, iy + 6.0f * p, p * 0.48f, 0.25f, 0.25f, 0.25f);
        hud.drawHudText(overlayGeom, I18n.get("container.inventory"), ix + 8.0f * p, iy + 73.0f * p, p * 0.48f, 0.25f, 0.25f, 0.25f);

        // 4. Chest Slots Grid (3 rows x 9 columns = 27 slots)
        float chestGridX = ix + 8.0f * p;
        float chestGridY = iy + 18.0f * p;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                float sx = chestGridX + col * 18.0f * p;
                float sy = chestGridY + row * 18.0f * p;
                drawPixelSlot(geom, sx, sy, 18.0f * p, p);

                ItemStack stack = chest.getSlot(slotIndex);
                if (stack != null && hud.mouseX >= sx && hud.mouseX <= sx + 18.0f * p && hud.mouseY >= sy && hud.mouseY <= sy + 18.0f * p) {
                    if (!stack.isEmpty()) hoveredStack = stack;
                }
                if (stack != null) {
                    renderSlotItem(tex, overlayGeom, stack, sx, sy, p);
                }
            }
        }

        // 5. Main Player Inventory Grid (3 rows x 9 columns)
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

        // 6. Hotbar Grid (1 row x 9 columns)
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

        // 7. Carried item on cursor
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

        // 8. Hover tooltip popup
        if (hud.carriedItem.isEmpty() && hoveredStack != null && !hoveredStack.isEmpty()) {
            renderItemTooltip(overlayGeom, hoveredStack, hud.mouseX, hud.mouseY, windowWidth, windowHeight);
        }
    }
}
