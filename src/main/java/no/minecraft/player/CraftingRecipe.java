package no.minecraft.player;

import no.minecraft.world.BlockType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CraftingRecipe {
    private final String name;
    private final Map<BlockType, Integer> inputs;
    private final ItemStack output;

    public CraftingRecipe(String name, Map<BlockType, Integer> inputs, ItemStack output) {
        this.name = name;
        this.inputs = inputs;
        this.output = output;
    }

    public String getName() {
        return name;
    }

    public Map<BlockType, Integer> getInputs() {
        return Collections.unmodifiableMap(inputs);
    }

    public ItemStack getOutput() {
        return output;
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

    public static List<CraftingRecipe> getDefaultRecipes() {
        List<CraftingRecipe> list = new ArrayList<>();

        // 1. Planker: 1 Eiketre -> 4 Treplanker
        list.add(new CraftingRecipe("Treplanker", Map.of(BlockType.WOOD, 1), new ItemStack(BlockType.PLANKS, 4)));

        // 2. Arbeidsbenk: 4 Treplanker -> 1 Arbeidsbenk
        list.add(new CraftingRecipe("Arbeidsbenk", Map.of(BlockType.PLANKS, 4), new ItemStack(BlockType.CRAFTING_TABLE, 1)));

        // 3. Pinner: 2 Treplanker -> 4 Pinner
        list.add(new CraftingRecipe("Pinne", Map.of(BlockType.PLANKS, 2), new ItemStack(BlockType.STICK, 4)));

        // 4. Trehakke: 3 Treplanker + 2 Pinner -> 1 Trehakke
        list.add(new CraftingRecipe("Trehakke", Map.of(BlockType.PLANKS, 3, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_PICKAXE, 1)));

        // 5. Treøks: 3 Treplanker + 2 Pinner -> 1 Treøks
        list.add(new CraftingRecipe("Treøks", Map.of(BlockType.PLANKS, 3, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_AXE, 1)));

        // 6. Trespade: 1 Treplanke + 2 Pinner -> 1 Trespade
        list.add(new CraftingRecipe("Trespade", Map.of(BlockType.PLANKS, 1, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_SHOVEL, 1)));

        // 7. Tresverd: 2 Treplanker + 1 Pinne -> 1 Tresverd
        list.add(new CraftingRecipe("Tresverd", Map.of(BlockType.PLANKS, 2, BlockType.STICK, 1), new ItemStack(BlockType.WOODEN_SWORD, 1)));

        // 8. Tregrev: 2 Treplanker + 2 Pinner -> 1 Tregrev
        list.add(new CraftingRecipe("Tregrev", Map.of(BlockType.PLANKS, 2, BlockType.STICK, 2), new ItemStack(BlockType.WOODEN_HOE, 1)));

        // 9. Trebåt: 5 Treplanker -> 1 Trebåt
        list.add(new CraftingRecipe("Trebåt", Map.of(BlockType.PLANKS, 5), new ItemStack(BlockType.BOAT, 1)));

        // 10. Kiste: 8 Treplanker -> 1 Kiste
        list.add(new CraftingRecipe("Kiste", Map.of(BlockType.PLANKS, 8), new ItemStack(BlockType.CHEST, 1)));

        // 11. Tredør: 6 Treplanker -> 3 Tredører
        list.add(new CraftingRecipe("Tredør", Map.of(BlockType.PLANKS, 6), new ItemStack(BlockType.WOODEN_DOOR, 3)));

        // 12. Fallelem: 6 Treplanker -> 2 Fallemmer
        list.add(new CraftingRecipe("Fallelem", Map.of(BlockType.PLANKS, 6), new ItemStack(BlockType.TRAPDOOR, 2)));

        // 13. Stige: 7 Pinner -> 3 Stiger
        list.add(new CraftingRecipe("Stige", Map.of(BlockType.STICK, 7), new ItemStack(BlockType.LADDER, 3)));

        // 14. Tregjerde: 4 Treplanker + 2 Pinner -> 3 Tregjerder
        list.add(new CraftingRecipe("Tregjerde", Map.of(BlockType.PLANKS, 4, BlockType.STICK, 2), new ItemStack(BlockType.FENCE, 3)));

        // 15. Gjerdeport: 2 Treplanker + 4 Pinner -> 1 Gjerdeport
        list.add(new CraftingRecipe("Gjerdeport", Map.of(BlockType.PLANKS, 2, BlockType.STICK, 4), new ItemStack(BlockType.FENCE_GATE, 1)));

        // 16. Trehelle: 3 Treplanker -> 6 Treheller
        list.add(new CraftingRecipe("Trehelle", Map.of(BlockType.PLANKS, 3), new ItemStack(BlockType.WOODEN_SLAB, 6)));

        // 17. Tretrapp: 6 Treplanker -> 4 Tretrapper
        list.add(new CraftingRecipe("Tretrapp", Map.of(BlockType.PLANKS, 6), new ItemStack(BlockType.WOODEN_STAIRS, 4)));

        // 18. Trebolle: 3 Treplanker -> 4 Treboller
        list.add(new CraftingRecipe("Trebolle", Map.of(BlockType.PLANKS, 3), new ItemStack(BlockType.BOWL, 4)));

        // 19. Trykkplate: 2 Treplanker -> 1 Trykkplate
        list.add(new CraftingRecipe("Tre trykkplate", Map.of(BlockType.PLANKS, 2), new ItemStack(BlockType.WOODEN_PRESSURE_PLATE, 1)));

        // 20. Treknapp: 1 Treplanke -> 1 Treknapp
        list.add(new CraftingRecipe("Treknapp", Map.of(BlockType.PLANKS, 1), new ItemStack(BlockType.WOODEN_BUTTON, 1)));

        // 21. Steinhakke: 3 Brostein + 2 Pinner -> 1 Steinhakke
        list.add(new CraftingRecipe("Steinhakke", Map.of(BlockType.COBBLESTONE, 3, BlockType.STICK, 2), new ItemStack(BlockType.STONE_PICKAXE, 1)));

        // 22. Steinøks: 3 Brostein + 2 Pinner -> 1 Steinøks
        list.add(new CraftingRecipe("Steinøks", Map.of(BlockType.COBBLESTONE, 3, BlockType.STICK, 2), new ItemStack(BlockType.STONE_AXE, 1)));

        // 23. Steinspade: 1 Brostein + 2 Pinner -> 1 Steinspade
        list.add(new CraftingRecipe("Steinspade", Map.of(BlockType.COBBLESTONE, 1, BlockType.STICK, 2), new ItemStack(BlockType.STONE_SHOVEL, 1)));

        // 24. Steinsverd: 2 Brostein + 1 Pinne -> 1 Steinsverd
        list.add(new CraftingRecipe("Steinsverd", Map.of(BlockType.COBBLESTONE, 2, BlockType.STICK, 1), new ItemStack(BlockType.STONE_SWORD, 1)));

        // 25. Ovn: 8 Brostein -> 1 Ovn
        list.add(new CraftingRecipe("Ovn", Map.of(BlockType.COBBLESTONE, 8), new ItemStack(BlockType.FURNACE, 1)));

        // 26. Flammepulver: 1 Flammestav -> 2 Flammepulver
        list.add(new CraftingRecipe("Flammepulver", Map.of(BlockType.BLAZE_ROD, 1), new ItemStack(BlockType.BLAZE_POWDER, 2)));

        // 27. Enderøye: 1 Enderperle + 1 Flammepulver -> 1 Enderøye
        list.add(new CraftingRecipe("Enderøye", Map.of(BlockType.ENDER_PEARL, 1, BlockType.BLAZE_POWDER, 1), new ItemStack(BlockType.EYE_OF_ENDER, 1)));

        // 28. Bue: 3 Pinner + 3 Tråd -> 1 Bue
        list.add(new CraftingRecipe("Bue", Map.of(BlockType.STICK, 3, BlockType.STRING, 3), new ItemStack(BlockType.BOW, 1)));

        // 29. Pil: 1 Brostein + 1 Pinne + 1 Tråd -> 4 Piler
        list.add(new CraftingRecipe("Pil", Map.of(BlockType.COBBLESTONE, 1, BlockType.STICK, 1, BlockType.STRING, 1), new ItemStack(BlockType.ARROW, 4)));

        // 30. Obsidian: 4 Stein + 4 Brostein -> 2 Obsidian
        list.add(new CraftingRecipe("Obsidian", Map.of(BlockType.STONE, 4, BlockType.COBBLESTONE, 4), new ItemStack(BlockType.OBSIDIAN, 2)));

        // 31. Ildstål: 1 Jernbarre + 1 Krutt -> 1 Ildstål
        list.add(new CraftingRecipe("Ildstål", Map.of(BlockType.IRON_INGOT, 1, BlockType.GUNPOWDER, 1), new ItemStack(BlockType.FLINT_AND_STEEL, 1)));

        // 32. Fakkel: 1 Kull + 1 Pinne -> 4 Fakler
        list.add(new CraftingRecipe("Fakkel", Map.of(BlockType.COAL, 1, BlockType.STICK, 1), new ItemStack(BlockType.TORCH, 4)));

        // 33. Jernhakke: 3 Jernbarre + 2 Pinner -> 1 Jernhakke
        list.add(new CraftingRecipe("Jernhakke", Map.of(BlockType.IRON_INGOT, 3, BlockType.STICK, 2), new ItemStack(BlockType.IRON_PICKAXE, 1)));

        // 34. Jernsverd: 2 Jernbarre + 1 Pinne -> 1 Jernsverd
        list.add(new CraftingRecipe("Jernsverd", Map.of(BlockType.IRON_INGOT, 2, BlockType.STICK, 1), new ItemStack(BlockType.IRON_SWORD, 1)));

        // 35. Jernøks: 3 Jernbarre + 2 Pinner -> 1 Jernøks
        list.add(new CraftingRecipe("Jernøks", Map.of(BlockType.IRON_INGOT, 3, BlockType.STICK, 2), new ItemStack(BlockType.IRON_AXE, 1)));

        // 36. Jernspade: 1 Jernbarre + 2 Pinner -> 1 Jernspade
        list.add(new CraftingRecipe("Jernspade", Map.of(BlockType.IRON_INGOT, 1, BlockType.STICK, 2), new ItemStack(BlockType.IRON_SHOVEL, 1)));

        // 37. Diamanthakke: 3 Diamanter + 2 Pinner -> 1 Diamanthakke
        list.add(new CraftingRecipe("Diamanthakke", Map.of(BlockType.DIAMOND, 3, BlockType.STICK, 2), new ItemStack(BlockType.DIAMOND_PICKAXE, 1)));

        // 38. Diamantsverd: 2 Diamanter + 1 Pinne -> 1 Diamantsverd
        list.add(new CraftingRecipe("Diamantsverd", Map.of(BlockType.DIAMOND, 2, BlockType.STICK, 1), new ItemStack(BlockType.DIAMOND_SWORD, 1)));

        // 39. Diamantøks: 3 Diamanter + 2 Pinner -> 1 Diamantøks
        list.add(new CraftingRecipe("Diamantøks", Map.of(BlockType.DIAMOND, 3, BlockType.STICK, 2), new ItemStack(BlockType.DIAMOND_AXE, 1)));

        // 40. Diamantspade: 1 Diamant + 2 Pinner -> 1 Diamantspade
        list.add(new CraftingRecipe("Diamantspade", Map.of(BlockType.DIAMOND, 1, BlockType.STICK, 2), new ItemStack(BlockType.DIAMOND_SHOVEL, 1)));

        // 41. Smelting: Jernsmelting (1 Jernmalm + 1 Kull -> 1 Jernbarre)
        list.add(new CraftingRecipe("Smelt Jernbarre", Map.of(BlockType.IRON_ORE, 1, BlockType.COAL, 1), new ItemStack(BlockType.IRON_INGOT, 1)));

        // 42. Smelting: Gullsmelting (1 Gullmalm + 1 Kull -> 1 Gullbarre)
        list.add(new CraftingRecipe("Smelt Gullbarre", Map.of(BlockType.GOLD_ORE, 1, BlockType.COAL, 1), new ItemStack(BlockType.GOLD_INGOT, 1)));

        // 43. Steking: Biff (1 Rått Storfekjøtt + 1 Kull -> 1 Stekt Biff)
        list.add(new CraftingRecipe("Stek Biff", Map.of(BlockType.BEEF, 1, BlockType.COAL, 1), new ItemStack(BlockType.COOKED_BEEF, 1)));

        // 44. Steking: Svinekjøtt (1 Rått Svinekjøtt + 1 Kull -> 1 Stekt Svinekjøtt)
        list.add(new CraftingRecipe("Stek Svinekjøtt", Map.of(BlockType.PORKCHOP, 1, BlockType.COAL, 1), new ItemStack(BlockType.COOKED_PORKCHOP, 1)));

        // 45. Steking: Kylling (1 Rå Kylling + 1 Kull -> 1 Stekt Kylling)
        list.add(new CraftingRecipe("Stek Kylling", Map.of(BlockType.CHICKEN_MEAT, 1, BlockType.COAL, 1), new ItemStack(BlockType.COOKED_CHICKEN, 1)));

        return list;
    }
}
