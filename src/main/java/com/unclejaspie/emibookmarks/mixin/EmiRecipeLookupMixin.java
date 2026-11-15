package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.BookmarkRecipeContext;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain the item that opened each recipe viewer; uses/direct recipe views supply no output intent. */
@Mixin(value = EmiApi.class, remap = false)
public abstract class EmiRecipeLookupMixin {
    @Inject(method = "displayRecipes", at = @At("RETURN"))
    private static void bookmarks$lookup(EmiIngredient ingredient, CallbackInfo ci) {
        BookmarkRecipeContext.openedFor(Minecraft.getInstance().screen, ingredient);
    }
    @Inject(method = "displayUses", at = @At("RETURN"))
    private static void bookmarks$uses(EmiIngredient ingredient, CallbackInfo ci) {
        BookmarkRecipeContext.openedFor(Minecraft.getInstance().screen, null);
    }
    @Inject(method = "displayRecipe", at = @At("RETURN"))
    private static void bookmarks$direct(EmiRecipe recipe, CallbackInfo ci) {
        BookmarkRecipeContext.openedFor(Minecraft.getInstance().screen, null);
    }
}
