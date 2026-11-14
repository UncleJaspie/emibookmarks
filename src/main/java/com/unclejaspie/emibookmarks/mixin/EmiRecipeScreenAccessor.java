package com.unclejaspie.emibookmarks.mixin;

import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.WidgetGroup;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = RecipeScreen.class, remap = false)
public interface EmiRecipeScreenAccessor {
    @Accessor("currentPage") List<WidgetGroup> bookmarks$currentPage();
}

