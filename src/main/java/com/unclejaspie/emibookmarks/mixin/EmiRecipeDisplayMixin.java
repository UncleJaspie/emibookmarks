package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.BookmarkRecipeButton;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.RecipeDisplay;
import dev.emi.emi.screen.WidgetGroup;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = RecipeDisplay.class, remap = false)
public abstract class EmiRecipeDisplayMixin {
    @Shadow @Final public EmiRecipe recipe;
    @Shadow private int leftWidth;
    @Inject(method = "<init>(Ldev/emi/emi/api/recipe/EmiRecipe;)V", at = @At("TAIL"))
    private void bookmarks$reserve(EmiRecipe recipe, CallbackInfo ci) { leftWidth += 14; }
    @Inject(method = "getWidgets", at = @At("RETURN"))
    private void bookmarks$button(int x, int y, int width, int height, CallbackInfoReturnable<WidgetGroup> cir) {
        if (recipe != null) {
            var group = cir.getReturnValue();
            for (var slot : com.unclejaspie.emibookmarks.client.emi.BookmarkRecipeContext.slots(group))
                slot.appendTooltip(com.unclejaspie.emibookmarks.client.BookmarkHelp::viewerTooltip);
            group.add(new BookmarkRecipeButton(recipe, -leftWidth - 3, Math.max(0, (group.getHeight() - 13) / 2), group));
        }
    }
}

