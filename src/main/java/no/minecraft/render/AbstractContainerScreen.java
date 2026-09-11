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
 * Shared base of the container screens (inventory, crafting table, furnace).
 *
 * These screens own the container GUI geometry emission and input dispatch;
 * the inventory, crafting and furnace state (slots, carried item, drag state,
 * open flags, recipe book state) stays on the {@link HUD} and is read through
 * the hud reference. The geometry methods were moved out of HUD.java
 * unchanged; the GL pass that draws the emitted lists still runs through the
 * HUD shader (see {@link HUD#drawScreenGeometry}).
 */
abstract class AbstractContainerScreen implements GuiScreen {

    protected final HUD hud;
    private final Supplier<Player> player;
    private final IntSupplier width;
    private final IntSupplier height;

    AbstractContainerScreen(HUD hud, Supplier<Player> player, IntSupplier width, IntSupplier height) {
        this.hud = hud;
        this.player = player;
        this.width = width;
        this.height = height;
    }

    /** Emits this container's geometry into the three HUD vertex lists. */
    protected abstract void renderGui(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                      int windowWidth, int windowHeight, Player player, TextureAtlas atlas);

    @Override
    public boolean handleMouseClick(double mx, double my, int button, boolean shiftDown) {
        // All slot/drag/recipe-book button logic lives in the HUD's consolidated click handler.
        return hud.handleMouseClick(mx, my, button, shiftDown, player.get(), width.getAsInt(), height.getAsInt());
    }

    @Override
    public void handleChar(char c) {
        // The HUD method itself checks whether the recipe search field is focused.
        hud.addRecipeSearchChar(c);
    }

    @Override
    public boolean handleKey(int key, int action) {
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            if (key >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && key <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
                return hud.handleInventoryKeyPress(key, hud.getMouseX(), hud.getMouseY(), player.get(), width.getAsInt(), height.getAsInt());
            } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
                return hud.handleSwapKeyPress(hud.getMouseX(), hud.getMouseY(), player.get(), width.getAsInt(), height.getAsInt());
            }
        }
        return false;
    }

    @Override
    public void render(List<Float> geom, List<Float> texGeom, int width, int height) {
        // Geometry is emitted via renderGui into the HUD's three-pass pipeline.
    }

    void renderSlotItem(List<Float> tex, List<Float> overlayGeom, ItemStack stack, float sx, float sy, float p) {
        if (hud.isLeftDragging && hud.draggedSlots.contains(stack) && hud.draggedSlots.size() > 1 && !hud.carriedItem.isEmpty()) {
            int perSlot = hud.carriedItem.getCount() / hud.draggedSlots.size();
            BlockType type = hud.carriedItem.getType();
            int count = (stack.isEmpty() ? 0 : stack.getCount()) + perSlot;
            if (count > 0) {
                int tId = type.getItemTexture();
                float[] uv = TextureAtlas.getUVs(tId);
                addRect(tex, sx + 2.0f * p, sy + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1.0f, 1.0f, 1.0f, 0.85f);
                hud.drawMinecraftNumber(overlayGeom, count, sx + 17.0f * p, sy + 17.0f * p, p * 0.95f);
            }
            addRect(overlayGeom, sx + p, sy + p, 16.0f * p, 16.0f * p, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.25f);
            return;
        }

        if (!stack.isEmpty()) {
            int tId = stack.getType().getItemTexture();
            float[] uv = TextureAtlas.getUVs(tId);
            addRect(tex, sx + 2.0f * p, sy + 2.0f * p, 14.0f * p, 14.0f * p, uv[0], uv[1], uv[2], uv[3], 1.0f, 1.0f, 1.0f, 1.0f);
            if (stack.getCount() > 0) {
                hud.drawMinecraftNumber(overlayGeom, stack.getCount(), sx + 17.0f * p, sy + 17.0f * p, p * 0.95f);
            }
        }
    }
    void renderItemTooltip(List<Float> overlayGeom, ItemStack item, float mx, float my, int windowWidth, int windowHeight) {
        if (item == null || item.isEmpty()) return;

        BlockType bt = item.getType();
        List<String> lines = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();

        // Title: Item name (White)
        lines.add(bt.getName());
        colors.add(new float[]{1.0f, 1.0f, 1.0f});

        // Food info
        if (bt.isFood()) {
            float hearts = bt.getFoodValue() / 2.0f;
            String heartStr = (hearts == (int) hearts) ? String.valueOf((int) hearts) : String.format(java.util.Locale.ROOT, "%.1f", hearts);
            lines.add("+" + I18n.format("tooltip.food", heartStr));
            colors.add(new float[]{0.70f, 0.70f, 0.70f});
        }

        // Attack damage
        int attackDmg = bt.getAttackDamage();
        if (attackDmg > 1) {
            lines.add("+" + I18n.format("tooltip.attack_damage", attackDmg));
            colors.add(new float[]{0.35f, 0.85f, 0.35f});
        }

        // Armor defense
        if (bt.isArmor()) {
            lines.add("+" + I18n.format("tooltip.armor", bt.getArmorDefense()));
            colors.add(new float[]{0.35f, 0.70f, 1.0f});
        }

        // Durability
        if (bt.isDamageable()) {
            int maxDur = bt.getMaxDurability();
            int currentDur = maxDur - item.getDamage();
            lines.add(I18n.format("tooltip.durability", currentDur, maxDur));
            colors.add(new float[]{0.75f, 0.75f, 0.75f});
        }

        float p = hud.getGuiScale(windowWidth, windowHeight);
        float textScale = p * 0.55f;
        float charW = 6.0f * textScale;
        float lineH = 10.0f * textScale;

        float maxW = 0.0f;
        for (String line : lines) {
            float w = line.length() * charW;
            if (w > maxW) maxW = w;
        }

        float padX = 2.5f * p;
        float padY = 2.0f * p;
        float boxW = maxW + padX * 2.0f;
        float boxH = lines.size() * lineH + padY * 2.0f;

        float tx = mx + 5.0f * p;
        float ty = my - 5.0f * p;

        if (tx + boxW > windowWidth - 4.0f) {
            tx = mx - boxW - 3.0f * p;
        }
        if (tx < 4.0f) tx = 4.0f;

        if (ty + boxH > windowHeight - 4.0f) {
            ty = windowHeight - 4.0f - boxH;
        }
        if (ty < 4.0f) ty = 4.0f;

        float border = Math.max(1.0f, p * 0.5f);
        // Outer dark border
        addRect(overlayGeom, tx - border, ty - border, boxW + border * 2.0f, boxH + border * 2.0f, 0, 0, 0, 0, 0.05f, 0.05f, 0.05f, 0.96f);
        // Purple border (Minecraft tooltip)
        addRect(overlayGeom, tx, ty, boxW, boxH, 0, 0, 0, 0, 0.28f, 0.05f, 0.65f, 0.96f);
        // Dark inner background
        addRect(overlayGeom, tx + border, ty + border, boxW - border * 2.0f, boxH - border * 2.0f, 0, 0, 0, 0, 0.08f, 0.04f, 0.12f, 0.94f);

        // Draw lines
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            float[] col = colors.get(i);
            float ly = ty + padY + i * lineH;
            hud.drawHudText(overlayGeom, text, tx + padX, ly, textScale, col[0], col[1], col[2], 1.0f);
        }
    }
    ItemStack renderRecipeBookPanel(List<Float> geom, List<Float> tex, List<Float> overlayGeom,
                                            float popX, float popY, float popW, float invH, float p, Player player) {
        drawMinecraftWindowFrame(geom, popX, popY, popW, invH, p);

        // Search bar at the top
        float searchX = popX + 10.0f * p;
        float searchY = popY + 6.0f * p;
        float searchW = popW - 20.0f * p;
        float searchH = 11.0f * p;

        // Dark inset search bar background
        addRect(geom, searchX, searchY, searchW, searchH, 0, 0, 0, 0, 0.09f, 0.09f, 0.09f, 1.0f);
        if (hud.recipeSearchFocused) {
            drawInsetBorder(geom, searchX, searchY, searchW, searchH, p * 0.6f);
            addRect(geom, searchX, searchY, searchW, p * 0.6f, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.85f);
            addRect(geom, searchX, searchY + searchH - p * 0.6f, searchW, p * 0.6f, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.85f);
            addRect(geom, searchX, searchY, p * 0.6f, searchH, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.85f);
            addRect(geom, searchX + searchW - p * 0.6f, searchY, p * 0.6f, searchH, 0, 0, 0, 0, 1.0f, 1.0f, 1.0f, 0.85f);
        } else {
            drawInsetBorder(geom, searchX, searchY, searchW, searchH, p * 0.6f);
        }

        // Search text / placeholder
        if (hud.recipeSearchText.isEmpty()) {
            if (!hud.recipeSearchFocused) {
                hud.drawHudText(overlayGeom, I18n.get("container.search_hint"), searchX + 3.0f * p, searchY + 2.5f * p, p * 0.42f, 0.45f, 0.45f, 0.45f);
            } else {
                boolean blink = (System.currentTimeMillis() % 1000) < 500;
                if (blink) {
                    hud.drawHudText(overlayGeom, "_", searchX + 3.0f * p, searchY + 2.5f * p, p * 0.42f, 1.0f, 1.0f, 1.0f);
                }
            }
        } else {
            boolean blink = hud.recipeSearchFocused && (System.currentTimeMillis() % 1000) < 500;
            String displayTxt = hud.recipeSearchText + (blink ? "_" : "");
            hud.drawHudText(overlayGeom, displayTxt, searchX + 3.0f * p, searchY + 2.5f * p, p * 0.42f, 1.0f, 1.0f, 1.0f);

            // Clear 'X' button
            float clearX = searchX + searchW - 8.5f * p;
            float clearY = searchY + 2.0f * p;
            boolean hClear = (hud.mouseX >= clearX - p && hud.mouseX <= clearX + 7.0f * p && hud.mouseY >= clearY - p && hud.mouseY <= clearY + 8.0f * p);
            hud.drawHudText(overlayGeom, "X", clearX, clearY, p * 0.45f, hClear ? 1.0f : 0.60f, hClear ? 0.35f : 0.60f, hClear ? 0.35f : 0.60f);
        }

        float slotW = 22.0f * p;
        float slotStep = 26.0f * p;
        float startGridX = popX + 11.0f * p;
        float startGridY = popY + 20.0f * p;

        List<CraftingRecipe> activeRecipes = hud.getFilteredRecipes();
        int totalRows = (activeRecipes.size() + 3) / 4;
        int maxScroll = Math.max(0, totalRows - 5);
        hud.recipeScrollRow = Math.max(0, Math.min(maxScroll, hud.recipeScrollRow));

        if (activeRecipes.isEmpty()) {
            hud.drawHudText(overlayGeom, I18n.get("container.no_results"), popX + 22.0f * p, popY + 55.0f * p, p * 0.48f, 0.50f, 0.50f, 0.50f);
        }

        int startIdx = hud.recipeScrollRow * 4;
        int endIdx = Math.min(activeRecipes.size(), startIdx + 5 * 4);
        ItemStack hovered = null;

        for (int i = startIdx; i < endIdx; i++) {
            int visualRow = (i - startIdx) / 4;
            int col = (i - startIdx) % 4;
            float sx = startGridX + col * slotStep;
            float sy = startGridY + visualRow * slotStep;

            CraftingRecipe r = activeRecipes.get(i);
            boolean canCraft = r.canCraft(player.getInventory());

            if (hud.mouseX >= sx && hud.mouseX <= sx + slotW && hud.mouseY >= sy && hud.mouseY <= sy + slotW) {
                hovered = r.getOutput();
            }

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
            int tId = r.getOutput().getType().getItemTexture();
            float[] uv = TextureAtlas.getUVs(tId);
            float bright = canCraft ? 1.0f : 0.45f;
            float alpha = canCraft ? 1.0f : 0.55f;
            addRect(tex, sx + 3.0f * p, sy + 3.0f * p, 16.0f * p, 16.0f * p, uv[0], uv[1], uv[2], uv[3], bright, bright, bright, alpha);

            // Stack count if > 1
            if (r.getOutput().getCount() > 1) {
                hud.drawMinecraftNumber(overlayGeom, r.getOutput().getCount(), sx + 20.5f * p, sy + 20.5f * p, p * 0.95f);
            }
        }

        // Scrollbar on the right side
        if (maxScroll > 0) {
            float sbX = popX + popW - 10.0f * p;
            float sbY = startGridY;
            float sbW = 5.0f * p;
            float sbH = 5 * slotStep - 4.0f * p;
            // Track
            addRect(geom, sbX, sbY, sbW, sbH, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);
            // Thumb
            float thumbH = Math.max(16.0f * p, sbH * (5.0f / totalRows));
            float thumbY = sbY + (sbH - thumbH) * ((float) hud.recipeScrollRow / maxScroll);
            addRect(geom, sbX, thumbY, sbW, thumbH, 0, 0, 0, 0, 0.65f, 0.65f, 0.65f, 1.0f);
            drawInsetBorder(geom, sbX, thumbY, sbW, thumbH, p * 0.5f);
        }

        return hovered;
    }
    void drawPixelSlot(List<Float> g, float x, float y, float size, float p) {
        // Slot border & inset bevel
        addRect(g, x, y, size, size, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x, y, size, p, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
        addRect(g, x, y, p, size, 0, 0, 0, 0, 0.22f, 0.22f, 0.22f, 1.0f);
        addRect(g, x, y + size - p, size, p, 0, 0, 0, 0, 0.95f, 0.95f, 0.95f, 1.0f);
        addRect(g, x + size - p, y, p, size, 0, 0, 0, 0, 0.95f, 0.95f, 0.95f, 1.0f);
    }
    void drawMinecraftWindowFrame(List<Float> g, float x, float y, float w, float h, float p) {
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
    void drawInsetBorder(List<Float> g, float x, float y, float w, float h, float p) {
        addRect(g, x, y, w, p, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);
        addRect(g, x, y, p, h, 0, 0, 0, 0, 0.15f, 0.15f, 0.15f, 1.0f);
        addRect(g, x, y + h - p, w, p, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
        addRect(g, x + w - p, y, p, h, 0, 0, 0, 0, 0.85f, 0.85f, 0.85f, 1.0f);
    }
    void drawPixelCraftingTitle(List<Float> g, float x, float y, float p) {
        // "Crafting" text label in Minecraft font style
        addRect(g, x, y, 42 * p, 6 * p, 0, 0, 0, 0, 0.25f, 0.25f, 0.25f, 0.8f);
    }
    void drawPixelCraftingArrow(List<Float> g, float x, float y, float p) {
        // ➔ Crafting arrow with 3D bevel
        addRect(g, x, y + 4 * p, 12 * p, 4 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 8 * p, y + 1 * p, 3 * p, 3 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 8 * p, y + 8 * p, 3 * p, 3 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
        addRect(g, x + 11 * p, y + 3 * p, 2 * p, 6 * p, 0, 0, 0, 0, 0.55f, 0.55f, 0.55f, 1.0f);
    }
    void drawPixelRecipeBookButton(List<Float> g, float x, float y, float size, float p) {
        // Minecraft Green Recipe Book Button
        addRect(g, x, y, size, size, 0, 0, 0, 0, 0.12f, 0.12f, 0.12f, 1.0f);
        addRect(g, x + p, y + p, size - 2 * p, size - 2 * p, 0, 0, 0, 0, 0.776f, 0.776f, 0.776f, 1.0f);
        // Green book graphic
        addRect(g, x + 4 * p, y + 4 * p, 12 * p, 11 * p, 0, 0, 0, 0, 0.18f, 0.65f, 0.25f, 1.0f);
        addRect(g, x + 5 * p, y + 14 * p, 10 * p, 2 * p, 0, 0, 0, 0, 0.95f, 0.95f, 0.95f, 1.0f); // pages
    }
}
