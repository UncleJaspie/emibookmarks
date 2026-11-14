package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.*;
import java.util.*;

/** View-only aggregation. Source slots and recipe/group identities are never modified. */
public final class BookmarkProjection {
    public record Slot(int representative, List<Integer> sources, long amount) {
        public Slot { sources = List.copyOf(sources); }
    }
    private record VariantKey(JsonElement resource, int chance, BookmarkSlot.Unit unit) {}
    private record Key(List<VariantKey> variants, int selected) {}

    public static List<Slot> slots(BookmarkCollection.Recipe recipe, boolean combine) {
        return slots(recipe, combine, null);
    }
    public static List<Slot> slots(BookmarkCollection.Recipe recipe, boolean combine, BookmarkChain.Result calculation) {
        List<Slot> result = new ArrayList<>();
        Map<Key, Integer> positions = new HashMap<>();
        for (int i = 0; i < recipe.icons().size(); i++) {
            var source = recipe.icons().get(i);
            long amount = calculation != null && calculation.valid() ? calculation.amounts().get(source.id) : source.amount(recipe.batches());
            Key key = combine && source.role == BookmarkSlot.Role.INPUT ? key(source) : null;
            Integer existing = key == null ? null : positions.get(key);
            if (existing != null) {
                var previous = result.get(existing);
                try {
                    long sum = Math.addExact(previous.amount, amount);
                    var sources = new ArrayList<>(previous.sources); sources.add(i);
                    result.set(existing, new Slot(previous.representative, sources, sum)); continue;
                } catch (ArithmeticException overflow) {
                    // Keep valid individual amounts separate instead of displaying a wrapped total.
                }
            }
            if (key != null) positions.put(key, result.size());
            result.add(new Slot(i, List.of(i), amount));
        }
        if (recipe.isRecipe()) {
            int primary = -1;
            for (int i = 0; i < result.size(); i++) if (recipe.icons().get(result.get(i).representative()).id.equals(recipe.primaryOutput())) primary = i;
            if (primary > 0) result.add(0, result.remove(primary));
        }
        return List.copyOf(result);
    }
    private static Key key(BookmarkSlot slot) {
        if (slot.alternatives.stream().anyMatch(v -> v.unit().symbol().equals("?"))) return null;
        try {
            var variants = new ArrayList<VariantKey>();
            for (var variant : slot.alternatives)
                variants.add(new VariantKey(resource(variant.serialized()), Float.floatToIntBits(variant.chance()), variant.unit()));
            return new Key(List.copyOf(variants), slot.selected());
        } catch (RuntimeException unreadable) { return null; }
    }
    private static JsonElement resource(String serialized) {
        JsonElement json = JsonParser.parseString(serialized);
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String text = json.getAsString();
            if (text.startsWith("item:") || text.startsWith("fluid:")) {
                int separator = text.indexOf(':'); int nbt = text.indexOf('{', separator + 1);
                var canonical = new JsonObject();
                canonical.addProperty("type", text.substring(0, separator));
                canonical.addProperty("id", text.substring(separator + 1, nbt < 0 ? text.length() : nbt));
                if (nbt >= 0) canonical.addProperty("nbt", text.substring(nbt));
                json = canonical;
            }
        }
        if (json.isJsonObject()) {
            var object = json.getAsJsonObject().deepCopy();
            String type = object.has("type") ? object.get("type").getAsString() : "";
            if (type.equals("item") || type.equals("fluid")) {
                object.remove("amount"); object.remove("chance"); // Chance is compared explicitly above.
            }
            return object;
        }
        return json;
    }
}

