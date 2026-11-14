package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.bookmark.BookmarkCollection;
import com.unclejaspie.emibookmarks.bookmark.BookmarkSlot;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.registry.EmiRecipeFiller;

/** Delegate grid transfers to the active EMI handler; never emulate container slot clicks. */
public final class BookmarkRecipeFill {
    public static boolean available(BookmarkCollection.Recipe record, BookmarkSlot slot, EmiIngredient ingredient) {
        var screen = EmiApi.getHandledScreen();
        if (screen == null) return false;
        try {
            var recipe = BookmarkRecipePreview.recipe(record, slot, ingredient);
            return recipe != null && EmiRecipeFiller.getFirstValidHandler(recipe, screen) != null;
        } catch (RuntimeException | LinkageError unavailable) { return false; }
    }

    public static boolean fill(BookmarkCollection.Recipe record, BookmarkSlot slot) {
        var screen = EmiApi.getHandledScreen();
        if (screen == null) return false;
        try {
            var ingredient = BookmarkCapture.ingredient(slot, record.batches());
            var recipe = BookmarkRecipePreview.recipeForFill(record, slot, ingredient);
            // Native EMI Shift-click: all available batches, fill only, leave output in its slot.
            boolean filled = recipe != null && EmiRecipeFiller.performFill(recipe, screen,
                    EmiCraftContext.Type.CRAFTABLE, EmiCraftContext.Destination.NONE, Integer.MAX_VALUE);
            if (filled) BookmarkRecipePreview.clearCache();
            return filled;
        } catch (RuntimeException | LinkageError unavailable) {
            com.mojang.logging.LogUtils.getLogger().warn("Could not fill bookmarked recipe through EMI", unavailable);
            return false;
        }
    }
}
