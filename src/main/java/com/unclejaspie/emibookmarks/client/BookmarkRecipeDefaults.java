package com.unclejaspie.emibookmarks.client;

import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.bom.BoM;

/** Use native EMI defaults and player hearts without reordering recipe providers. */
public final class BookmarkRecipeDefaults {
    public static void clearCache() {}
    public static boolean machine(EmiRecipe recipe) {
        // GT remains optional: recognize the pinned native machine adapter, including subclasses.
        for (Class<?> type = recipe.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals("com.gregtechceu.gtceu.integration.emi.recipe.GTEmiRecipe")) return true;
        return false;
    }
    public static EmiRecipe manual(EmiIngredient ingredient) { return BoM.addedRecipes.get(ingredient); }

    public static EmiRecipe preferred(EmiIngredient ingredient) {
        return preferred(ingredient, 0);
    }
    private static EmiRecipe preferred(EmiIngredient ingredient, int depth) {
        if (ingredient == null || ingredient.isEmpty()) return null;
        if (depth > 32) throw new IllegalArgumentException("Looping EMI ingredient resolution");
        var recipe = BoM.getRecipe(ingredient);
        return recipe instanceof EmiResolutionRecipe resolution ? preferred(resolution.stack, depth + 1) : recipe;
    }
}
