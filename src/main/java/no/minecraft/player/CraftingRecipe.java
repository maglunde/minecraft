package no.minecraft.player;

import no.minecraft.i18n.I18n;
import no.minecraft.world.BlockType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CraftingRecipe {
    private final String nameKey;
    private final Map<BlockType, Integer> inputs;
    private final ItemStack output;
    private final boolean gridCraftable;
    private final boolean shapelessMatch;
    private final List<String[]> patterns;
    private final Map<Character, BlockType> patternKey;

    /** Non-grid recipe (furnace smelting etc.): usable by recipe book only. */
    public CraftingRecipe(String nameKey, Map<BlockType, Integer> inputs, ItemStack output) {
        this(nameKey, inputs, output, false, false, List.of(), Map.of());
    }

    private CraftingRecipe(String nameKey, Map<BlockType, Integer> inputs, ItemStack output,
                           boolean gridCraftable, boolean shapelessMatch,
                           List<String[]> patterns, Map<Character, BlockType> patternKey) {
        this.nameKey = nameKey;
        this.inputs = inputs;
        this.output = output;
        this.gridCraftable = gridCraftable;
        this.shapelessMatch = shapelessMatch;
        this.patterns = patterns;
        this.patternKey = patternKey;
    }

    public String getName() {
        return I18n.get(nameKey);
    }

    public String getNameKey() {
        return nameKey;
    }

    public Map<BlockType, Integer> getInputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public ItemStack getOutput() {
        return output;
    }

    public List<String[]> getPatterns() {
        return patterns;
    }

    public Map<Character, BlockType> getPatternKey() {
        return Collections.unmodifiableMap(patternKey);
    }

    public boolean canCraft(Inventory inventory) {
        for (Map.Entry<BlockType, Integer> entry : inputs.entrySet()) {
            if (inventory.getItemCount(entry.getKey()) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public boolean craft(Inventory inventory) {
        if (!canCraft(inventory)) {
            return false;
        }

        // Consume inputs
        for (Map.Entry<BlockType, Integer> entry : inputs.entrySet()) {
            inventory.removeItem(entry.getKey(), entry.getValue());
        }

        // Add output
        inventory.addItem(output.getType(), output.getCount());
        return true;
    }

    public String getRequirementsString() {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<BlockType, Integer> entry : inputs.entrySet()) {
            if (i > 0) sb.append(", ");
            sb.append(entry.getValue()).append("x ").append(entry.getKey().getName());
            i++;
        }
        return sb.toString();
    }

    // --- Grid matching: single source of truth for 2x2 and 3x3 crafting ---

    /**
     * Returns the output of the first recipe matching the given grid, or null.
     * Shaped recipes match their pattern at any offset; cells outside the pattern must be empty.
     * Shapeless grid recipes match on per-slot type counts only.
     */
    public static ItemStack matchGrid(ItemStack[] grid, int width, int height) {
        boolean any = false;
        for (ItemStack s : grid) {
            if (s != null && !s.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) return null;

        for (CraftingRecipe r : DEFAULT_RECIPES) {
            if (!r.gridCraftable) continue;
            ItemStack out = r.matchInGrid(grid, width, height);
            if (out != null) return out;
        }
        return null;
    }

    private ItemStack matchInGrid(ItemStack[] grid, int width, int height) {
        if (shapelessMatch) {
            int nonEmpty = 0;
            Map<BlockType, Integer> counts = new HashMap<>();
            for (ItemStack s : grid) {
                if (s == null || s.isEmpty()) continue;
                nonEmpty++;
                counts.merge(s.getType(), 1, Integer::sum);
            }
            int expected = 0;
            for (int v : inputs.values()) expected += v;
            if (nonEmpty != expected) return null;
            for (Map.Entry<BlockType, Integer> e : inputs.entrySet()) {
                if (!e.getValue().equals(counts.get(e.getKey()))) return null;
            }
            return new ItemStack(output.getType(), output.getCount());
        }

        for (String[] pattern : patterns) {
            int ph = pattern.length;
            int pw = pattern[0].length();
            int cells = 0;
            for (String row : pattern) {
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) != ' ') cells++;
                }
            }
            for (int dy = 0; dy + ph <= height; dy++) {
                for (int dx = 0; dx + pw <= width; dx++) {
                    if (matchesPattern(grid, width, pattern, dx, dy, cells)) {
                        return new ItemStack(output.getType(), output.getCount());
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesPattern(ItemStack[] grid, int width, String[] pattern,
                                   int dx, int dy, int cells) {
        int nonEmpty = 0;
        for (ItemStack s : grid) {
            if (s != null && !s.isEmpty()) nonEmpty++;
        }
        if (nonEmpty != cells) return false;

        for (int r = 0; r < pattern.length; r++) {
            String row = pattern[r];
            for (int c = 0; c < row.length(); c++) {
                ItemStack slot = grid[(dy + r) * width + (dx + c)];
                char ch = row.charAt(c);
                if (ch == ' ') {
                    if (slot != null && !slot.isEmpty()) return false;
                } else {
                    BlockType t = patternKey.get(ch);
                    if (slot == null || slot.isEmpty() || slot.getType() != t) return false;
                }
            }
        }
        return true;
    }

    // --- Default recipe table ---

    private static final List<CraftingRecipe> DEFAULT_RECIPES = buildDefaultRecipes();

    public static List<CraftingRecipe> getDefaultRecipes() {
        return DEFAULT_RECIPES;
    }

    private static CraftingRecipe shaped(String name, Map<BlockType, Integer> inputs, ItemStack output,
                                         Map<Character, BlockType> key, String[]... patterns) {
        return new CraftingRecipe(name, inputs, output, true, false, List.of(patterns), key);
    }

    private static CraftingRecipe gridShapeless(String name, Map<BlockType, Integer> inputs, ItemStack output,
                                                Map<Character, BlockType> key, String[]... patterns) {
        return new CraftingRecipe(name, inputs, output, true, true, List.of(patterns), key);
    }

    private static Map<Character, BlockType> key(Object... pairs) {
        Map<Character, BlockType> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((Character) pairs[i], (BlockType) pairs[i + 1]);
        }
        return m;
    }

    // Pattern material chars:
    // W=WOOD P=PLANKS S=STICK C=COBBLESTONE O=COAL I=IRON_INGOT D=DIAMOND
    // G=STRING R=BLAZE_ROD B=BLAZE_POWDER E=ENDER_PEARL F=GUNPOWDER N=STONE H=FEATHER L=LEATHER

    private static List<CraftingRecipe> buildDefaultRecipes() {
        List<CraftingRecipe> list = new ArrayList<>();

        // 1. Planker: 1 Eiketre -> 4 Treplanker
        list.add(gridShapeless("recipe.planks", Map.of(BlockType.WOOD, 1), new ItemStack(BlockType.PLANKS, 4),
                key('W', BlockType.WOOD), new String[]{" W "}));

        // 2. Arbeidsbenk: 4 Treplanker -> 1 Arbeidsbenk
        list.add(shaped("recipe.crafting_table", Map.of(BlockType.PLANKS, 4), new ItemStack(BlockType.CRAFTING_TABLE, 1),
                key('P', BlockType.PLANKS), new String[]{"PP", "PP"}));

        // 3. Pinner: 2 Treplanker -> 4 Pinner
        list.add(shaped("recipe.stick", Map.of(BlockType.PLANKS, 2), new ItemStack(BlockType.STICK, 4),
                key('P', BlockType.PLANKS), new String[]{"P", "P"}));

        // 4. Trehakke: 3 Treplanker + 2 Pinner -> 1 Trehakke
        list.add(shaped("recipe.wooden_pickaxe", Map.of(BlockType.PLANKS, 3, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_PICKAXE, 1),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK), new String[]{"PPP", " S ", " S "}));

        // 5. Treøks: 3 Treplanker + 2 Pinner -> 1 Treøks
        list.add(shaped("recipe.wooden_axe", Map.of(BlockType.PLANKS, 3, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_AXE, 1),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK),
                new String[]{"PP ", "PS ", " S "}, new String[]{" PP", " SP", " S "}));

        // 6. Trespade: 1 Treplanke + 2 Pinner -> 1 Trespade
        list.add(shaped("recipe.wooden_shovel", Map.of(BlockType.PLANKS, 1, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_SHOVEL, 1),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK),
                new String[]{" P ", " S ", " S "}, new String[]{"P  ", "S  ", "S  "}, new String[]{"  P", "  S", "  S"}));

        // 7. Tresverd: 2 Treplanker + 1 Pinne -> 1 Tresverd
        list.add(shaped("recipe.wooden_sword", Map.of(BlockType.PLANKS, 2, BlockType.STICK, 1), new ItemStack(BlockType.WOODEN_SWORD, 1),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK),
                new String[]{" P ", " P ", " S "}, new String[]{"P  ", "P  ", "S  "}, new String[]{"  P", "  P", "  S"}));

        // 8. Tregrev: 2 Treplanker + 2 Pinner -> 1 Tregrev
        list.add(shaped("recipe.wooden_hoe", Map.of(BlockType.PLANKS, 2, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_HOE, 1),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK),
                new String[]{"PP ", " S ", " S "}, new String[]{" PP", " S ", " S "}));

        // 9. Trebåt: 5 Treplanker -> 1 Trebåt
        list.add(shaped("recipe.boat", Map.of(BlockType.PLANKS, 5), new ItemStack(BlockType.BOAT, 1),
                key('P', BlockType.PLANKS), new String[]{"P P", "PPP"}));

        // 10. Kiste: 8 Treplanker -> 1 Kiste
        list.add(shaped("recipe.chest", Map.of(BlockType.PLANKS, 8), new ItemStack(BlockType.CHEST, 1),
                key('P', BlockType.PLANKS), new String[]{"PPP", "P P", "PPP"}));

        // 11. Tredør: 6 Treplanker -> 3 Tredører
        list.add(shaped("recipe.wooden_door", Map.of(BlockType.PLANKS, 6), new ItemStack(BlockType.WOODEN_DOOR, 3),
                key('P', BlockType.PLANKS),
                new String[]{"PP ", "PP ", "PP "}, new String[]{" PP", " PP", " PP"}));

        // 12. Fallelem: 6 Treplanker -> 2 Fallemmer
        list.add(shaped("recipe.trapdoor", Map.of(BlockType.PLANKS, 6), new ItemStack(BlockType.TRAPDOOR, 2),
                key('P', BlockType.PLANKS),
                new String[]{"PPP", "PPP", "   "}, new String[]{"   ", "PPP", "PPP"}));

        // 13. Stige: 7 Pinner -> 3 Stiger
        list.add(shaped("recipe.ladder", Map.of(BlockType.STICK, 7), new ItemStack(BlockType.LADDER, 3),
                key('S', BlockType.STICK), new String[]{"S S", "SSS", "S S"}));

        // 14. Tregjerde: 4 Treplanker + 2 Pinner -> 3 Tregjerder
        list.add(shaped("recipe.fence", Map.of(BlockType.PLANKS, 4, BlockType.STICK, 2), new ItemStack(BlockType.FENCE, 3),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK), new String[]{"   ", "PSP", "PSP"}));

        // 15. Gjerdeport: 2 Treplanker + 4 Pinner -> 1 Gjerdeport
        list.add(shaped("recipe.fence_gate", Map.of(BlockType.PLANKS, 2, BlockType.STICK, 4), new ItemStack(BlockType.FENCE_GATE, 1),
                key('P', BlockType.PLANKS, 'S', BlockType.STICK), new String[]{"   ", "SPS", "SPS"}));

        // 16. Trehelle: 3 Treplanker -> 6 Treheller
        list.add(shaped("recipe.wooden_slab", Map.of(BlockType.PLANKS, 3), new ItemStack(BlockType.WOODEN_SLAB, 6),
                key('P', BlockType.PLANKS), new String[]{"PPP"}));

        // 17. Tretrapp: 6 Treplanker -> 4 Tretrapper
        list.add(shaped("recipe.wooden_stairs", Map.of(BlockType.PLANKS, 6), new ItemStack(BlockType.WOODEN_STAIRS, 4),
                key('P', BlockType.PLANKS),
                new String[]{"P  ", "PP ", "PPP"}, new String[]{"  P", " PP", "PPP"}));

        // 18. Trebolle: 3 Treplanker -> 4 Treboller
        list.add(shaped("recipe.bowl", Map.of(BlockType.PLANKS, 3), new ItemStack(BlockType.BOWL, 4),
                key('P', BlockType.PLANKS), new String[]{"P P", " P "}));

        // 19. Trykkplate: 2 Treplanker -> 1 Trykkplate
        list.add(shaped("recipe.wooden_pressure_plate", Map.of(BlockType.PLANKS, 2), new ItemStack(BlockType.WOODEN_PRESSURE_PLATE, 1),
                key('P', BlockType.PLANKS), new String[]{"PP"}));

        // 20. Treknapp: 1 Treplanke -> 1 Treknapp
        list.add(gridShapeless("recipe.wooden_button", Map.of(BlockType.PLANKS, 1), new ItemStack(BlockType.WOODEN_BUTTON, 1),
                key('P', BlockType.PLANKS), new String[]{" P "}));

        // 21. Steinhakke: 3 Brostein + 2 Pinner -> 1 Steinhakke
        list.add(shaped("recipe.stone_pickaxe", Map.of(BlockType.COBBLESTONE, 3, BlockType.STICK, 2), new ItemStack(BlockType.STONE_PICKAXE, 1),
                key('C', BlockType.COBBLESTONE, 'S', BlockType.STICK), new String[]{"CCC", " S ", " S "}));

        // 22. Steinøks: 3 Brostein + 2 Pinner -> 1 Steinøks
        list.add(shaped("recipe.stone_axe", Map.of(BlockType.COBBLESTONE, 3, BlockType.STICK, 2), new ItemStack(BlockType.STONE_AXE, 1),
                key('C', BlockType.COBBLESTONE, 'S', BlockType.STICK),
                new String[]{"CC ", "CS ", " S "}, new String[]{" CC", " SC", " S "}));

        // 23. Steinspade: 1 Brostein + 2 Pinner -> 1 Steinspade
        list.add(shaped("recipe.stone_shovel", Map.of(BlockType.COBBLESTONE, 1, BlockType.STICK, 2), new ItemStack(BlockType.STONE_SHOVEL, 1),
                key('C', BlockType.COBBLESTONE, 'S', BlockType.STICK),
                new String[]{" C ", " S ", " S "}, new String[]{"C  ", "S  ", "S  "}, new String[]{"  C", "  S", "  S"}));

        // 24. Steinsverd: 2 Brostein + 1 Pinne -> 1 Steinsverd
        list.add(shaped("recipe.stone_sword", Map.of(BlockType.COBBLESTONE, 2, BlockType.STICK, 1), new ItemStack(BlockType.STONE_SWORD, 1),
                key('C', BlockType.COBBLESTONE, 'S', BlockType.STICK),
                new String[]{" C ", " C ", " S "}, new String[]{"C  ", "C  ", "S  "}, new String[]{"  C", "  C", "  S"}));

        // 25. Ovn: 8 Brostein -> 1 Ovn
        list.add(shaped("recipe.furnace", Map.of(BlockType.COBBLESTONE, 8), new ItemStack(BlockType.FURNACE, 1),
                key('C', BlockType.COBBLESTONE), new String[]{"CCC", "C C", "CCC"}));

        // 26. Flammepulver: 1 Flammestav -> 2 Flammepulver
        list.add(gridShapeless("recipe.blaze_powder", Map.of(BlockType.BLAZE_ROD, 1), new ItemStack(BlockType.BLAZE_POWDER, 2),
                key('R', BlockType.BLAZE_ROD), new String[]{" R "}));

        // 27. Enderøye: 1 Enderperle + 1 Flammepulver -> 1 Enderøye
        list.add(gridShapeless("recipe.eye_of_ender", Map.of(BlockType.ENDER_PEARL, 1, BlockType.BLAZE_POWDER, 1), new ItemStack(BlockType.EYE_OF_ENDER, 1),
                key('E', BlockType.ENDER_PEARL, 'B', BlockType.BLAZE_POWDER), new String[]{"EB"}));

        // 28. Bue: 3 Pinner + 3 Tråd -> 1 Bue
        list.add(shaped("recipe.bow", Map.of(BlockType.STICK, 3, BlockType.STRING, 3), new ItemStack(BlockType.BOW, 1),
                key('S', BlockType.STICK, 'G', BlockType.STRING),
                new String[]{" SG", "S G", " SG"}, new String[]{"GS ", "G S", "G S"}));

        // 29. Pil: 1 Brostein + 1 Pinne + 1 Tråd -> 4 Piler
        list.add(shaped("recipe.arrow", Map.of(BlockType.COBBLESTONE, 1, BlockType.STICK, 1, BlockType.STRING, 1), new ItemStack(BlockType.ARROW, 4),
                key('C', BlockType.COBBLESTONE, 'S', BlockType.STICK, 'G', BlockType.STRING, 'H', BlockType.FEATHER),
                new String[]{" C ", " S ", " G "}, new String[]{"C  ", "S  ", "G  "}, new String[]{"  C", "  S", "  G"},
                new String[]{" C ", " S ", " H "}, new String[]{"C  ", "S  ", "H  "}, new String[]{"  C", "  S", "  H"}));

        // 30. Obsidian: 4 Stein + 4 Brostein -> 2 Obsidian
        list.add(gridShapeless("recipe.obsidian", Map.of(BlockType.STONE, 4, BlockType.COBBLESTONE, 4), new ItemStack(BlockType.OBSIDIAN, 2),
                key('N', BlockType.STONE, 'C', BlockType.COBBLESTONE), new String[]{"NCN", "C C", "NCN"}));

        // 31. Ildstål: 1 Jernbarre + 1 Krutt -> 1 Ildstål
        list.add(gridShapeless("recipe.flint_and_steel", Map.of(BlockType.IRON_INGOT, 1, BlockType.GUNPOWDER, 1), new ItemStack(BlockType.FLINT_AND_STEEL, 1),
                key('I', BlockType.IRON_INGOT, 'F', BlockType.GUNPOWDER), new String[]{"IF"}));

        // 32. Fakkel: 1 Kull + 1 Pinne -> 4 Fakler
        list.add(shaped("recipe.torch", Map.of(BlockType.COAL, 1, BlockType.STICK, 1), new ItemStack(BlockType.TORCH, 4),
                key('O', BlockType.COAL, 'S', BlockType.STICK), new String[]{"O", "S"}));

        // 33. Jernhakke: 3 Jernbarre + 2 Pinner -> 1 Jernhakke
        list.add(shaped("recipe.iron_pickaxe", Map.of(BlockType.IRON_INGOT, 3, BlockType.STICK, 2), new ItemStack(BlockType.IRON_PICKAXE, 1),
                key('I', BlockType.IRON_INGOT, 'S', BlockType.STICK), new String[]{"III", " S ", " S "}));

        // 34. Jernsverd: 2 Jernbarre + 1 Pinne -> 1 Jernsverd
        list.add(shaped("recipe.iron_sword", Map.of(BlockType.IRON_INGOT, 2, BlockType.STICK, 1), new ItemStack(BlockType.IRON_SWORD, 1),
                key('I', BlockType.IRON_INGOT, 'S', BlockType.STICK),
                new String[]{" I ", " I ", " S "}, new String[]{"I  ", "I  ", "S  "}, new String[]{"  I", "  I", "  S"}));

        // 35. Jernøks: 3 Jernbarre + 2 Pinner -> 1 Jernøks
        list.add(shaped("recipe.iron_axe", Map.of(BlockType.IRON_INGOT, 3, BlockType.STICK, 2), new ItemStack(BlockType.IRON_AXE, 1),
                key('I', BlockType.IRON_INGOT, 'S', BlockType.STICK),
                new String[]{"II ", "IS ", " S "}, new String[]{" II", " SI", " S "}));

        // 36. Jernspade: 1 Jernbarre + 2 Pinner -> 1 Jernspade
        list.add(shaped("recipe.iron_shovel", Map.of(BlockType.IRON_INGOT, 1, BlockType.STICK, 2), new ItemStack(BlockType.IRON_SHOVEL, 1),
                key('I', BlockType.IRON_INGOT, 'S', BlockType.STICK),
                new String[]{" I ", " S ", " S "}, new String[]{"I  ", "S  ", "S  "}, new String[]{"  I", "  S", "  S"}));

        // 37. Diamanthakke: 3 Diamanter + 2 Pinner -> 1 Diamanthakke
        list.add(shaped("recipe.diamond_pickaxe", Map.of(BlockType.DIAMOND, 3, BlockType.STICK, 2), new ItemStack(BlockType.DIAMOND_PICKAXE, 1),
                key('D', BlockType.DIAMOND, 'S', BlockType.STICK), new String[]{"DDD", " S ", " S "}));

        // 38. Diamantsverd: 2 Diamanter + 1 Pinne -> 1 Diamantsverd
        list.add(shaped("recipe.diamond_sword", Map.of(BlockType.DIAMOND, 2, BlockType.STICK, 1), new ItemStack(BlockType.DIAMOND_SWORD, 1),
                key('D', BlockType.DIAMOND, 'S', BlockType.STICK),
                new String[]{" D ", " D ", " S "}, new String[]{"D  ", "D  ", "S  "}, new String[]{"  D", "  D", "  S"}));

        // 39. Diamantøks: 3 Diamanter + 2 Pinner -> 1 Diamantøks
        list.add(shaped("recipe.diamond_axe", Map.of(BlockType.DIAMOND, 3, BlockType.STICK, 2), new ItemStack(BlockType.DIAMOND_AXE, 1),
                key('D', BlockType.DIAMOND, 'S', BlockType.STICK),
                new String[]{"DD ", "DS ", " S "}, new String[]{" DD", " SD", " S "}));

        // 40. Diamantspade: 1 Diamant + 2 Pinner -> 1 Diamantspade
        list.add(shaped("recipe.diamond_shovel", Map.of(BlockType.DIAMOND, 1, BlockType.STICK, 2), new ItemStack(BlockType.DIAMOND_SHOVEL, 1),
                key('D', BlockType.DIAMOND, 'S', BlockType.STICK),
                new String[]{" D ", " S ", " S "}, new String[]{"D  ", "S  ", "S  "}, new String[]{"  D", "  S", "  S"}));

        // --- Lærrustning (Leather Armor) ---
        // 41. Lærhjelm: 5 Lær -> 1 Lærhjelm
        list.add(shaped("recipe.leather_helmet", Map.of(BlockType.LEATHER, 5), new ItemStack(BlockType.LEATHER_HELMET, 1),
                key('L', BlockType.LEATHER), new String[]{"LLL", "L L"}));

        // 42. Lærbrystplate: 8 Lær -> 1 Lærbrystplate
        list.add(shaped("recipe.leather_chestplate", Map.of(BlockType.LEATHER, 8), new ItemStack(BlockType.LEATHER_CHESTPLATE, 1),
                key('L', BlockType.LEATHER), new String[]{"L L", "LLL", "LLL"}));

        // 43. Lærbukser: 7 Lær -> 1 Lærbukser
        list.add(shaped("recipe.leather_leggings", Map.of(BlockType.LEATHER, 7), new ItemStack(BlockType.LEATHER_LEGGINGS, 1),
                key('L', BlockType.LEATHER), new String[]{"LLL", "L L", "L L"}));

        // 44. Lærstøvler: 4 Lær -> 1 Lærstøvler
        list.add(shaped("recipe.leather_boots", Map.of(BlockType.LEATHER, 4), new ItemStack(BlockType.LEATHER_BOOTS, 1),
                key('L', BlockType.LEATHER), new String[]{"L L", "L L"}));

        // --- Jernrustning (Iron Armor) ---
        // 45. Jernhjelm: 5 Jernbarrer -> 1 Jernhjelm
        list.add(shaped("recipe.iron_helmet", Map.of(BlockType.IRON_INGOT, 5), new ItemStack(BlockType.IRON_HELMET, 1),
                key('I', BlockType.IRON_INGOT), new String[]{"III", "I I"}));

        // 46. Jernbrystplate: 8 Jernbarrer -> 1 Jernbrystplate
        list.add(shaped("recipe.iron_chestplate", Map.of(BlockType.IRON_INGOT, 8), new ItemStack(BlockType.IRON_CHESTPLATE, 1),
                key('I', BlockType.IRON_INGOT), new String[]{"I I", "III", "III"}));

        // 47. Jernbukser: 7 Jernbarrer -> 1 Jernbukser
        list.add(shaped("recipe.iron_leggings", Map.of(BlockType.IRON_INGOT, 7), new ItemStack(BlockType.IRON_LEGGINGS, 1),
                key('I', BlockType.IRON_INGOT), new String[]{"III", "I I", "I I"}));

        // 48. Jernstøvler: 4 Jernbarrer -> 1 Jernstøvler
        list.add(shaped("recipe.iron_boots", Map.of(BlockType.IRON_INGOT, 4), new ItemStack(BlockType.IRON_BOOTS, 1),
                key('I', BlockType.IRON_INGOT), new String[]{"I I", "I I"}));

        // --- Diamantrustning (Diamond Armor) ---
        // 49. Diamanthjelm: 5 Diamanter -> 1 Diamanthjelm
        list.add(shaped("recipe.diamond_helmet", Map.of(BlockType.DIAMOND, 5), new ItemStack(BlockType.DIAMOND_HELMET, 1),
                key('D', BlockType.DIAMOND), new String[]{"DDD", "D D"}));

        // 50. Diamantbrystplate: 8 Diamanter -> 1 Diamantbrystplate
        list.add(shaped("recipe.diamond_chestplate", Map.of(BlockType.DIAMOND, 8), new ItemStack(BlockType.DIAMOND_CHESTPLATE, 1),
                key('D', BlockType.DIAMOND), new String[]{"D D", "DDD", "DDD"}));

        // 51. Diamantbukser: 7 Diamanter -> 1 Diamantbukser
        list.add(shaped("recipe.diamond_leggings", Map.of(BlockType.DIAMOND, 7), new ItemStack(BlockType.DIAMOND_LEGGINGS, 1),
                key('D', BlockType.DIAMOND), new String[]{"DDD", "D D", "D D"}));

        // 52. Diamantstøvler: 4 Diamanter -> 1 Diamantstøvler
        list.add(shaped("recipe.diamond_boots", Map.of(BlockType.DIAMOND, 4), new ItemStack(BlockType.DIAMOND_BOOTS, 1),
                key('D', BlockType.DIAMOND), new String[]{"D D", "D D"}));

        // 53. Smelting: Jernsmelting (1 Jernmalm + 1 Kull -> 1 Jernbarre)
        list.add(new CraftingRecipe("recipe.iron_ingot", Map.of(BlockType.IRON_ORE, 1, BlockType.COAL, 1), new ItemStack(BlockType.IRON_INGOT, 1)));

        // 54. Smelting: Gullsmelting (1 Gullmalm + 1 Kull -> 1 Gullbarre)
        list.add(new CraftingRecipe("recipe.gold_ingot", Map.of(BlockType.GOLD_ORE, 1, BlockType.COAL, 1), new ItemStack(BlockType.GOLD_INGOT, 1)));

        // 55. Steking: Biff (1 Rått Storfekjøtt + 1 Kull -> 1 Stekt Biff)
        list.add(new CraftingRecipe("recipe.cooked_beef", Map.of(BlockType.BEEF, 1, BlockType.COAL, 1), new ItemStack(BlockType.COOKED_BEEF, 1)));

        // 56. Steking: Svinekjøtt (1 Rått Svinekjøtt + 1 Kull -> 1 Stekt Svinekjøtt)
        list.add(new CraftingRecipe("recipe.cooked_porkchop", Map.of(BlockType.PORKCHOP, 1, BlockType.COAL, 1), new ItemStack(BlockType.COOKED_PORKCHOP, 1)));

        // 57. Steking: Kylling (1 Rå Kylling + 1 Kull -> 1 Stekt Kylling)
        list.add(new CraftingRecipe("recipe.cooked_chicken", Map.of(BlockType.CHICKEN_MEAT, 1, BlockType.COAL, 1), new ItemStack(BlockType.COOKED_CHICKEN, 1)));

        return list;
    }
}
