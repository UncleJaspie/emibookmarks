package com.unclejaspie.emibookmarks.client;

import com.google.gson.JsonParser;
import com.unclejaspie.emibookmarks.bookmark.*;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Captures the chosen EMI route without querying for an arbitrary producer. */
public final class BookmarkCapture {
    private static final class Budget {
        private long chars;
        void add(String json) {
            chars += json.length();
            if (chars > 1_048_576) throw new IllegalArgumentException("Recipe snapshot is too large to save safely");
        }
    }
    private static final Map<String, EmiIngredient> DECODED = new ConcurrentHashMap<>();
    public static void clearCache() { DECODED.clear(); BookmarkChains.clearCache(); BookmarkRecipePreview.clearCache(); BookmarkRecipeDefaults.clearCache(); }
    public static String calculationIssue(BookmarkCollection.Recipe record) {
        if (!record.isRecipe()) return null;
        var recipe = resolve(record);
        if (recipe == null) return "Saved recipe route is unavailable; snapshot retained";
        return null;
    }

    public static BookmarkCollection.Recipe item(EmiIngredient hovered, boolean retainCount) {
        var snapshot = hovered.copy();
        if (!retainCount) snapshot.setAmount(1);
        var record = new BookmarkCollection.Recipe(UUID.randomUUID(), false, null,
                List.of(slot(snapshot, BookmarkSlot.Role.ITEM)), 1);
        record.counted(retainCount);
        return record;
    }
    public static BookmarkCollection.Recipe recipe(EmiRecipe recipe, EmiIngredient hovered) {
        var outputs = recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
        int index = outputIndex(recipe, hovered);
        if (index < 0 && outputs.size() == 1) index = 0;
        if (index < 0) throw new IllegalArgumentException("Choose the intended output of this recipe");
        return recipe(recipe, index, hovered);
    }
    /** Identity first; equality is accepted only when it identifies exactly one output instance. */
    public static int outputIndex(EmiRecipe recipe, EmiIngredient hovered) {
        var outputs = recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
        int identity = -1;
        for (int i = 0; i < outputs.size(); i++) if (outputs.get(i) == hovered) {
            if (identity >= 0) return -1;
            identity = i;
        }
        if (identity >= 0) return identity;
        if (hovered == null || hovered.isEmpty()) return -1;
        int found = -1;
        for (int i = 0; i < outputs.size(); i++) {
            var output = outputs.get(i);
            boolean matches = output.getAmount() == hovered.getAmount() && Float.compare(output.getChance(), hovered.getChance()) == 0 &&
                    output.getEmiStacks().stream().anyMatch(o -> hovered.getEmiStacks().stream().anyMatch(h ->
                            o.isEqual(h) && Objects.equals(o.getNbt(), h.getNbt())));
            if (matches) { if (found >= 0) return -1; found = i; }
        }
        return found;
    }
    public static BookmarkCollection.Recipe recipe(EmiRecipe recipe, int primary, EmiIngredient hovered) {
        List<BookmarkSlot> slots = new ArrayList<>();
        var budget = new Budget();
        var outputs = recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
        if ((long) outputs.size() + recipe.getInputs().size() + recipe.getCatalysts().size() > 512)
            throw new IllegalArgumentException("Recipe has too many slots to save");
        if (primary < 0 || primary >= outputs.size()) throw new IllegalArgumentException("Invalid output choice");
        for (var output : outputs) slots.add(slot(output, BookmarkSlot.Role.OUTPUT, budget));
        var primarySlot = slots.get(primary);
        if (hovered != null && !hovered.isEmpty()) {
            for (int a = 0; a < outputs.get(primary).getEmiStacks().size(); a++) {
                var option = outputs.get(primary).getEmiStacks().get(a);
                if (hovered.getEmiStacks().stream().anyMatch(h -> option.isEqual(h) && Objects.equals(option.getNbt(), h.getNbt()))) {
                    for (int n = 0; n < a; n++) primarySlot.cycle(1);
                    break;
                }
            }
        }
        for (var input : recipe.getInputs()) if (!input.isEmpty()) slots.add(slot(input, BookmarkSlot.Role.INPUT, budget));
        for (var catalyst : recipe.getCatalysts()) if (!catalyst.isEmpty()) slots.add(slot(catalyst, BookmarkSlot.Role.CATALYST, budget));
        if (slots.size() > 512) throw new IllegalArgumentException("Recipe has too many slots to save");
        return new BookmarkCollection.Recipe(UUID.randomUUID(), true,
                recipe.getId() == null ? null : recipe.getId().toString(), slots, 1, primarySlot.id);
    }
    /** Forge EMI's native fluid amounts use mB. Custom resources need an explicit unit adapter. */
    public static BookmarkSlot.Unit unit(EmiStack stack) {
        return stack.getClass() == dev.emi.emi.api.stack.FluidEmiStack.class ? BookmarkSlot.Unit.MILLIBUCKET :
                stack.getClass() == dev.emi.emi.api.stack.ItemEmiStack.class ? BookmarkSlot.Unit.ITEM :
                new BookmarkSlot.Unit("emi:" + stack.getClass().getName(), "?", 1, 1);
    }
    private static BookmarkSlot slot(EmiIngredient ingredient, BookmarkSlot.Role role) {
        return slot(ingredient, role, new Budget());
    }
    private static BookmarkSlot slot(EmiIngredient ingredient, BookmarkSlot.Role role, Budget budget) {
        List<BookmarkSlot.Variant> alternatives = new ArrayList<>();
        // Keep every accepted stack, NBT, chance and remainder as opaque EMI serializer data.
        // Selecting the first accepted option is explicit and can be changed with Shift+wheel.
        for (var stack : ingredient.getEmiStacks()) {
            if (stack.isEmpty()) continue;
            if (alternatives.size() >= 4096) throw new IllegalArgumentException("Too many accepted alternatives");
            var copy = stack.copy().setAmount(ingredient.getAmount()).setChance(ingredient.getChance());
            var serialized = EmiIngredientSerializer.getSerialized(copy);
            if (serialized == null || serialized.isJsonNull())
                throw new IllegalArgumentException("EMI cannot serialize " + stack.getName().getString());
            String json = serialized.toString(); budget.add(json);
            alternatives.add(new BookmarkSlot.Variant(json, ingredient.getAmount(),
                    ingredient.getChance(), stack.getName().getString(),
                    unit(stack)));
        }
        if (alternatives.isEmpty()) throw new IllegalArgumentException("No serializable resource in slot");
        return new BookmarkSlot(UUID.randomUUID(), role, alternatives, 0);
    }
    public static EmiIngredient ingredient(BookmarkSlot slot, long batches) {
        var variant = slot.variant();
        var base = DECODED.computeIfAbsent(variant.serialized(), json -> {
            try {
                var parsed = EmiIngredientSerializer.getDeserialized(JsonParser.parseString(json));
                return parsed == null ? EmiStack.EMPTY : parsed;
            } catch (RuntimeException | LinkageError e) { return EmiStack.EMPTY; }
        });
        if (base.isEmpty()) return EmiStack.EMPTY;
        return base.copy().setAmount(slot.amount(batches)).setChance(variant.chance());
    }
    public static EmiRecipe resolve(BookmarkCollection.Recipe record) {
        if (!record.isRecipe() || record.recipeKey() == null) return null;
        try { return EmiApi.getRecipeManager().getRecipe(ResourceLocation.tryParse(record.recipeKey())); }
        catch (RuntimeException | LinkageError e) { return null; }
    }
}

