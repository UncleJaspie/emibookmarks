package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.bookmark.BookmarkCollection;
import com.unclejaspie.emibookmarks.bookmark.BookmarkSlot;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.screen.tooltip.RecipeTooltipComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Read-only previews. Inferred routes never become saved recipe records or chain members. */
public final class BookmarkRecipePreview {
    private static String automaticKey;
    private static Screen automaticScreen;
    private static long expires;
    private static EmiRecipe automaticRecipe;
    private static EmiRecipe brokenRender;

    public static void clearCache() {
        automaticKey = null; automaticScreen = null; automaticRecipe = null; brokenRender = null; expires = 0;
    }

    public static EmiRecipe recipe(BookmarkCollection.Recipe record, BookmarkSlot slot, EmiIngredient ingredient) {
        return recipe(record, slot, ingredient, false);
    }
    public static EmiRecipe recipeForFill(BookmarkCollection.Recipe record, BookmarkSlot slot, EmiIngredient ingredient) {
        return recipe(record, slot, ingredient, true);
    }
    private static EmiRecipe recipe(BookmarkCollection.Recipe record, BookmarkSlot slot, EmiIngredient ingredient, boolean fresh) {
        // Never silently replace an unavailable explicitly saved route with another producer.
        if (record.isRecipe() && slot.role == BookmarkSlot.Role.OUTPUT) return BookmarkCapture.resolve(record);
        if (ingredient.isEmpty() || ingredient.getEmiStacks().size() != 1) return null;
        // Consult this on every frame so changing EMI's heart selection takes effect immediately.
        var preferred = BookmarkRecipeDefaults.manual(ingredient.getEmiStacks().get(0));
        if (preferred != null) return preferred instanceof dev.emi.emi.api.recipe.EmiResolutionRecipe ? BookmarkRecipeDefaults.preferred(ingredient) : preferred;
        if (fresh) return BookmarkRecipeDefaults.preferred(ingredient);
        long now = System.nanoTime();
        var screen = Minecraft.getInstance().screen;
        String key = slot.variant().serialized();
        if (!key.equals(automaticKey) || screen != automaticScreen || now >= expires) {
            automaticKey = key; automaticScreen = screen; expires = now + 250_000_000L;
            automaticRecipe = null;
            automaticRecipe = BookmarkRecipeDefaults.preferred(ingredient);
        }
        return automaticRecipe;
    }

    public static void render(GuiGraphics graphics, BookmarkCollection.Recipe record, BookmarkSlot slot,
                              EmiIngredient ingredient, List<Component> lines, int x, int y) {
        EmiRecipe recipe;
        try { recipe = recipe(record, slot, ingredient); }
        catch (RuntimeException | LinkageError unavailable) { recipe = null; }
        var client = Minecraft.getInstance();
        if (recipe != null && recipe != brokenRender && client.screen != null) {
            var components = new ArrayList<ClientTooltipComponent>();
            lines.forEach(line -> components.add(ClientTooltipComponent.create(line.getVisualOrderText())));
            try {
                // Match EMI's recipe favorites, including native widget layout and recipe scaling.
                components.add(new RecipeTooltipComponent(recipe, true));
                var context = EmiDrawContext.wrap(graphics);
                if (x > client.screen.width / 2) EmiRenderHelper.drawLeftTooltip(client.screen, context, components, x, y);
                else EmiRenderHelper.drawTooltip(client.screen, context, components, x, y);
                return;
            } catch (RuntimeException | LinkageError unavailable) {
                // A broken mod recipe must not prevent reading the item or repeatedly throw each frame.
                brokenRender = recipe;
                com.mojang.logging.LogUtils.getLogger().warn("Could not render bookmark recipe preview; keeping item tooltip", unavailable);
            }
        }
        graphics.renderTooltip(client.font, lines, java.util.Optional.empty(), x, y);
    }
}
