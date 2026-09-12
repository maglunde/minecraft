package no.minecraft.render;

import no.minecraft.i18n.I18n;
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
 * The furnace screen (right-click on a furnace). It is drawn on top of the
 * game HUD while the furnace is open; the screen itself only emits geometry
 * and forwards clicks to the HUD's consolidated click handler.
 */
public class FurnaceScreen extends AbstractContainerScreen {

    public FurnaceScreen(HUD hud, Supplier<Player> player, IntSupplier width, IntSupplier height) {
        super(hud, player, width, height);
    }

    @Override
    public void renderGui(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                  int windowWidth, int windowHeight, Player player, TextureAtlas atlas) {
        if (hud.getActiveFurnace() == null) return;

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

        // 3. Titles (container.furnace / container.inventory)
        hud.drawHudText(overlayGeom, I18n.get("container.furnace"), ix + 64.0f * p, iy + 6.0f * p, p * 0.48f, 0.25f, 0.25f, 0.25f);
        hud.drawHudText(overlayGeom, I18n.get("container.inventory"), ix + 8.0f * p, iy + 73.0f * p, p * 0.48f, 0.25f, 0.25f, 0.25f);

        // 4. Input Slot (Top)
        float inX = ix + 56.0f * p;
        float inY = iy + 17.0f * p;
        drawPixelSlot(geom, inX, inY, 18.0f * p, p);
        ItemStack inStack = hud.getActiveFurnace().getInput();
        if (hud.mouseX >= inX && hud.mouseX <= inX + 18.0f * p && hud.mouseY >= inY && hud.mouseY <= inY + 18.0f * p) {
            if (!inStack.isEmpty()) hoveredStack = inStack;
        }
        renderSlotItem(tex, overlayGeom, inStack, inX, inY, p);

        // 5. Burning Flame Icon (Between Input and Fuel)
        float flameX = ix + 58.0f * p;
        float flameY = iy + 37.0f * p;
        drawFurnaceFlame(geom, flameX, flameY, 14.0f * p, 12.0f * p, hud.getActiveFurnace().getBurnTime(), hud.getActiveFurnace().getMaxBurnTime(), p);

        // 6. Fuel Slot (Bottom)
        float fuelX = ix + 56.0f * p;
        float fuelY = iy + 53.0f * p;
        drawPixelSlot(geom, fuelX, fuelY, 18.0f * p, p);
        ItemStack fuelStack = hud.getActiveFurnace().getFuel();
        if (hud.mouseX >= fuelX && hud.mouseX <= fuelX + 18.0f * p && hud.mouseY >= fuelY && hud.mouseY <= fuelY + 18.0f * p) {
            if (!fuelStack.isEmpty()) hoveredStack = fuelStack;
        }
        renderSlotItem(tex, overlayGeom, fuelStack, fuelX, fuelY, p);

        // 7. Cooking Progress Arrow (pointing to output)
        float arrowX = ix + 79.0f * p;
        float arrowY = iy + 35.0f * p;
        drawFurnaceArrow(geom, arrowX, arrowY, hud.getActiveFurnace().getCookTime(), no.minecraft.world.FurnaceData.COOK_TIME_TOTAL, p);

        // 8. Output Slot (Right, 24x24 slot)
        float outX = ix + 116.0f * p;
        float outY = iy + 31.0f * p;
        drawPixelSlot(geom, outX, outY, 24.0f * p, p);
        ItemStack outStack = hud.getActiveFurnace().getOutput();
        if (!outStack.isEmpty()) {
            if (hud.mouseX >= outX && hud.mouseX <= outX + 24.0f * p && hud.mouseY >= outY && hud.mouseY <= outY + 24.0f * p) {
                hoveredStack = outStack;
            }
            int tId = outStack.getType().getItemTexture();
            float[] uv = TextureAtlas.getUVs(tId);
            addRect(tex, outX + 4.0f * p, outY + 4.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], 1, 1, 1, 1);
            if (outStack.getCount() > 0) {
                hud.drawMinecraftNumber(overlayGeom, outStack.getCount(), outX + 22.0f * p, outY + 22.0f * p, p * 0.95f);
            }
        }

        // 9. Main Inventory Grid (3 rows x 9 columns)
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

        // 10. Hotbar Grid (1 row x 9 columns)
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

        // 11. Carried item on cursor
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

        // 12. Hover tooltip popup
        if (hud.carriedItem.isEmpty() && hoveredStack != null && !hoveredStack.isEmpty()) {
            renderItemTooltip(overlayGeom, hoveredStack, hud.mouseX, hud.mouseY, windowWidth, windowHeight);
        }
    }

    private void drawFurnaceFlame(List<Float> g, float x, float y, float w, float h, float burnTime, float maxBurnTime, float p) {
        // Dark outline of flame
        addRect(g, x, y, w, h, 0, 0, 0, 0, 0.35f, 0.35f, 0.35f, 0.6f);
        if (burnTime > 0.0f && maxBurnTime > 0.0f) {
            float frac = Math.clamp(burnTime / maxBurnTime, 0.0f, 1.0f);
            float activeH = h * frac;
            float activeY = y + (h - activeH);
            // Fiery orange base
            addRect(g, x + p, activeY, w - 2 * p, activeH, 0, 0, 0, 0, 0.95f, 0.45f, 0.08f, 1.0f);
            // Bright yellow inner core
            if (activeH > 2 * p) {
                addRect(g, x + 3 * p, activeY + p, w - 6 * p, activeH - p, 0, 0, 0, 0, 1.0f, 0.90f, 0.20f, 1.0f);
            }
        }
    }

    private void drawFurnaceArrow(List<Float> g, float x, float y, float cookTime, float totalCookTime, float p) {
        float aw = 22 * p;
        float ah = 15 * p;
        // Base arrow shape
        addRect(g, x, y + 4 * p, 14 * p, 7 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 14 * p, y + 1 * p, 4 * p, 13 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 18 * p, y + 4 * p, 4 * p, 7 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);

        // Inset border
        drawInsetBorder(g, x, y, aw, ah, p * 0.5f);

        // Progress fill (left to right)
        if (cookTime > 0.0f) {
            float frac = Math.clamp(cookTime / totalCookTime, 0.0f, 1.0f);
            float progressW = aw * frac;
            addRect(g, x + p, y + 4 * p, Math.min(progressW, 14 * p), 7 * p, 0, 0, 0, 0, 0.90f, 0.90f, 0.90f, 1.0f);
            if (progressW > 14 * p) {
                float headW = Math.min(progressW - 14 * p, 8 * p);
                addRect(g, x + 14 * p, y + 1 * p, Math.min(headW, 4 * p), 13 * p, 0, 0, 0, 0, 0.90f, 0.90f, 0.90f, 1.0f);
                if (headW > 4 * p) {
                    addRect(g, x + 18 * p, y + 4 * p, headW - 4 * p, 7 * p, 0, 0, 0, 0, 0.90f, 0.90f, 0.90f, 1.0f);
                }
            }
        }
    }
}
