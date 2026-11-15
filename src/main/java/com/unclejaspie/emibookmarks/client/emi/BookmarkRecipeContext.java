package com.unclejaspie.emibookmarks.client.emi;

import com.unclejaspie.emibookmarks.mixin.EmiRecipeScreenAccessor;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.gui.screens.Screen;

public final class BookmarkRecipeContext {
    private static final java.util.Map<Screen, dev.emi.emi.api.stack.EmiIngredient> LOOKUPS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    /** Keep lookup intent with its own viewer, including history, rather than a stale global item. */
    public static void openedFor(Screen screen, dev.emi.emi.api.stack.EmiIngredient ingredient) {
        if (!(screen instanceof RecipeScreen)) return;
        if (ingredient == null || ingredient.isEmpty()) LOOKUPS.remove(screen);
        else LOOKUPS.put(screen, ingredient.copy());
    }
    public static dev.emi.emi.api.stack.EmiIngredient lookup(Screen screen) {
        return LOOKUPS.getOrDefault(screen, dev.emi.emi.api.stack.EmiStack.EMPTY);
    }
    /** Lookup counts/chance describe the clicked item, not the recipe's yield. Require a unique resource/NBT match. */
    public static int lookupOutput(EmiRecipe recipe, dev.emi.emi.api.stack.EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return -1;
        int found = -1;
        var outputs = recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
        for (int i=0; i<outputs.size(); i++) {
            var output = outputs.get(i);
            boolean matches = output.getEmiStacks().stream().anyMatch(o -> ingredient.getEmiStacks().stream()
                    .anyMatch(q -> o.isEqual(q) && java.util.Objects.equals(o.getNbt(), q.getNbt())));
            if (matches) { if (found >= 0) return -1; found = i; }
        }
        return found;
    }
    public static int lookupOutput(Screen screen, EmiRecipe recipe) { return lookupOutput(recipe, lookup(screen)); }
    private static final java.util.Map<Class<?>, java.util.Optional<java.lang.reflect.Field>> NESTED = new java.util.concurrent.ConcurrentHashMap<>();
    /** LDLib/GT wraps native EMI slots in a public slots list instead of exposing them at the top level. */
    public static java.util.List<dev.emi.emi.api.widget.SlotWidget> slots(dev.emi.emi.screen.WidgetGroup group) {
        var result = new java.util.ArrayList<dev.emi.emi.api.widget.SlotWidget>();
        for (var widget : group.widgets) {
            if (widget instanceof dev.emi.emi.api.widget.SlotWidget slot) { if (!result.contains(slot)) result.add(slot); }
            else {
                var field = NESTED.computeIfAbsent(widget.getClass(), type -> {
                    for (var parent = type; parent != null; parent = parent.getSuperclass())
                        if (parent.getName().equals("com.lowdragmc.lowdraglib.emi.ModularWrapperWidget")) {
                            try { return java.util.Optional.of(parent.getField("slots")); }
                            catch (ReflectiveOperationException | LinkageError unavailable) { return java.util.Optional.empty(); }
                        }
                    return java.util.Optional.empty();
                });
                if (field.isPresent()) try {
                    if (field.get().get(widget) instanceof java.util.List<?> children)
                        for (var child : children) if (child instanceof dev.emi.emi.api.widget.SlotWidget slot && !result.contains(slot)) result.add(slot);
                } catch (ReflectiveOperationException | LinkageError unavailable) { /* Optional integration; ordinary slots still work. */ }
            }
        }
        return result;
    }
    /** Recipe widgets are not ordinary sidebar stack providers; retain their displayed amount. */
    public static dev.emi.emi.api.stack.EmiIngredient ingredientAt(Screen screen, int x, int y) {
        if (screen instanceof RecipeScreen) for (var group : ((EmiRecipeScreenAccessor) screen).bookmarks$currentPage())
            for (var slot : slots(group)) if (slot.getBounds().contains(x - group.x(), y - group.y())) return slot.getStack();
        return dev.emi.emi.api.stack.EmiStack.EMPTY;
    }
    public static int outputIndex(dev.emi.emi.screen.WidgetGroup group, dev.emi.emi.api.widget.SlotWidget slot) {
        var outputs = group.recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
        var candidates = new java.util.ArrayList<Integer>();
        for (int i = 0; i < outputs.size(); i++) if (outputs.get(i) == slot.getStack()) candidates.add(i);
        if (candidates.size() == 1) return candidates.get(0);
        if (slot.getRecipe() != group.recipe) return -1;
        if (candidates.isEmpty()) return com.unclejaspie.emibookmarks.client.BookmarkCapture.outputIndex(group.recipe, slot.getStack());
        int occurrence = 0;
        for (var previous : slots(group)) {
            if (previous == slot) break;
            if (previous.getRecipe() == group.recipe &&
                    previous.getStack() == slot.getStack()) occurrence++;
        }
        return occurrence < candidates.size() ? candidates.get(occurrence) : -1;
    }
    public static int outputIndexAt(Screen screen, int x, int y, EmiRecipe recipe) {
        if (screen instanceof RecipeScreen) for (var group : ((EmiRecipeScreenAccessor) screen).bookmarks$currentPage())
            if (group.recipe == recipe) for (var slot : slots(group))
                if (slot.getBounds().contains(x - group.x(), y - group.y()))
                    return outputIndex(group, slot);
        return -1;
    }
    public static dev.emi.emi.api.stack.EmiIngredient outputAt(Screen screen, int x, int y, EmiRecipe recipe) {
        if (!(screen instanceof RecipeScreen)) return dev.emi.emi.api.stack.EmiStack.EMPTY;
        for (var group : ((EmiRecipeScreenAccessor) screen).bookmarks$currentPage()) if (group.recipe == recipe)
            for (var slot : slots(group)) if (slot.getBounds().contains(x - group.x(), y - group.y()) &&
                    outputIndex(group, slot) >= 0) return slot.getStack();
        return dev.emi.emi.api.stack.EmiStack.EMPTY;
    }
    public static EmiRecipe at(Screen screen, int x, int y) {
        if (!(screen instanceof RecipeScreen)) return null;
        for (var group : ((EmiRecipeScreenAccessor) screen).bookmarks$currentPage()) {
            if (group.recipe == null) continue;
            if (new Bounds(group.x(), group.y(), group.getWidth(), group.getHeight()).contains(x, y)) return group.recipe;
            for (var widget : group.widgets) if (widget.getBounds().contains(x - group.x(), y - group.y())) return group.recipe;
        }
        return null;
    }
}

