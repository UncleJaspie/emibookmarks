package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.*;
import java.util.*;

/** Versioned snapshots retain opaque serializer data even when a recipe/mod is unavailable. */
public final class BookmarkCodec {
    public static final int VERSION = 5;
    // A merged legacy document may contain 64 old lists of up to 10,000 records each. File IO remains capped at 16 MiB.
    private static final int MAX_MERGED_RECORDS = 640_000;
    public record Document(List<BookmarkCollection> collections, UUID active) {
        public Document {
            collections = List.copyOf(collections);
            if (collections.isEmpty() || collections.stream().noneMatch(c -> c.id.equals(active)))
                throw new IllegalArgumentException("Missing active collection");
        }
    }
    public static Document empty() {
        var c = new BookmarkCollection("Bookmarks");
        return new Document(List.of(c), c.id);
    }
    /** Flatten legacy saved collections without equating resources or rewriting record/group identities. */
    public static Document singleList(Document document) {
        if (document.collections().size() == 1) return document;
        var active = document.collections().stream().filter(c -> c.id.equals(document.active())).findFirst().orElseThrow();
        var merged = new BookmarkCollection(active.id, "Bookmarks");
        merged.mode = active.mode; merged.chain = active.chain; merged.collapsed = active.collapsed;
        var ordered = new ArrayList<BookmarkCollection>(); ordered.add(active);
        document.collections().stream().filter(c -> c != active).forEach(ordered::add);
        var used = new HashSet<UUID>();
        for (var c : ordered) {
            used.add(c.id);
            for (var recipe : c.recipes) { used.add(recipe.id()); recipe.icons().forEach(slot -> used.add(slot.id)); }
            c.groups.forEach(g -> used.add(g.id));
        }
        for (var source : ordered) {
            merged.recipes.addAll(source.recipes);
            for (var old : source.groups) {
                var copy = new BookmarkCollection.Group(old.id, old.members);
                copy.mode = old.mode; copy.chain = old.chain; copy.collapsed = old.collapsed;
                copy.treeGoal = old.treeGoal;
                merged.groups.add(copy);
            }
            // Distinct former default scopes must not silently gain another scope's modes or chain membership.
            boolean wrapDefault = source != active && (source.chain || source.collapsed || source.mode != merged.mode ||
                    source.chain != merged.chain || source.collapsed != merged.collapsed);
            if (!wrapDefault) continue;
            BookmarkCollection.Group run = null;
            for (var recipe : source.recipes) {
                if (source.groupOf(recipe.id()) != null) { run = null; continue; }
                if (run == null) {
                    UUID id; do { id = UUID.randomUUID(); } while (!used.add(id));
                    run = new BookmarkCollection.Group(id, List.of());
                    run.mode = source.mode; run.chain = source.chain; run.collapsed = source.collapsed;
                    merged.groups.add(run);
                }
                run.members.add(recipe.id());
            }
        }
        return new Document(List.of(merged), merged.id);
    }
    public static JsonObject encode(Document document) {
        var root = new JsonObject(); root.addProperty("version", VERSION);
        root.addProperty("active", document.active.toString());
        var collections = new JsonArray();
        for (var c : document.collections) {
            var json = new JsonObject(); json.addProperty("id", c.id.toString()); json.addProperty("name", c.name);
            flags(json, c.mode, c.chain, c.collapsed);
            var recipes = new JsonArray();
            for (var r : c.recipes) {
                var recipe = new JsonObject(); recipe.addProperty("id", r.id().toString());
                recipe.addProperty("recipe", r.isRecipe()); recipe.addProperty("recipeKey", r.recipeKey());
                recipe.addProperty("primaryOutput", r.primaryOutput() == null ? null : r.primaryOutput().toString());
                recipe.addProperty("batches", r.batches()); var slots = new JsonArray();
                recipe.addProperty("counted", r.counted());
                recipe.addProperty("collapsed", r.collapsed());
                for (var s : r.icons()) {
                    var slot = new JsonObject(); slot.addProperty("id", s.id.toString());
                    slot.addProperty("role", s.role.name()); slot.addProperty("selected", s.selected());
                    var variants = new JsonArray();
                    for (var v : s.alternatives) {
                        var variant = new JsonObject(); variant.addProperty("serialized", v.serialized());
                        variant.addProperty("amount", v.amount()); variant.addProperty("chance", v.chance());
                        variant.addProperty("label", v.label());
                        var unit = new JsonObject(); unit.addProperty("identity", v.unit().identity()); unit.addProperty("symbol", v.unit().symbol());
                        unit.addProperty("numerator", v.unit().numerator()); unit.addProperty("denominator", v.unit().denominator());
                        variant.add("unit", unit); variants.add(variant);
                    }
                    slot.add("alternatives", variants); slots.add(slot);
                }
                recipe.add("slots", slots); recipes.add(recipe);
            }
            json.add("recipes", recipes); var groups = new JsonArray();
            for (var g : c.groups) {
                var group = new JsonObject(); group.addProperty("id", g.id.toString());
                flags(group, g.mode, g.chain, g.collapsed);
                group.addProperty("treeGoal", g.treeGoal == null ? null : g.treeGoal.toString());
                var members = new JsonArray(); g.members.forEach(id -> members.add(id.toString()));
                group.add("members", members); groups.add(group);
            }
            json.add("groups", groups); collections.add(json);
        }
        root.add("collections", collections); return root;
    }
    private static void flags(JsonObject json, BookmarkCollection.Mode mode, boolean chain, boolean collapsed) {
        json.addProperty("mode", mode.name()); json.addProperty("chain", chain); json.addProperty("collapsed", collapsed);
    }
    private static JsonArray array(JsonObject object, String key, int max) {
        var array = object.getAsJsonArray(key);
        if (array == null || array.size() > max) throw new IllegalArgumentException("Invalid " + key);
        return array;
    }
    private static UUID id(JsonObject object, String key, Set<UUID> allIds) {
        UUID id = UUID.fromString(object.get(key).getAsString());
        if (!allIds.add(id)) throw new IllegalArgumentException("Duplicate identity");
        return id;
    }
    public static Document decode(JsonObject root) {
        int version = root.get("version").getAsInt();
        if (version < 1 || version > VERSION) throw new IllegalArgumentException("Unsupported bookmark version");
        List<BookmarkCollection> collections = new ArrayList<>(); Set<UUID> allIds = new HashSet<>();
        for (var element : array(root, "collections", 64)) {
            var json = element.getAsJsonObject();
            var c = new BookmarkCollection(id(json, "id", allIds), json.get("name").getAsString());
            c.mode = BookmarkCollection.Mode.valueOf(json.get("mode").getAsString());
            c.chain = json.get("chain").getAsBoolean(); c.collapsed = json.get("collapsed").getAsBoolean();
            for (var rElement : array(json, "recipes", MAX_MERGED_RECORDS)) {
                var r = rElement.getAsJsonObject(); List<BookmarkSlot> slots = new ArrayList<>();
                UUID recipeId = id(r, "id", allIds);
                for (var sElement : array(r, "slots", 512)) {
                    var s = sElement.getAsJsonObject(); List<BookmarkSlot.Variant> variants = new ArrayList<>();
                    for (var vElement : array(s, "alternatives", 4096)) {
                        var v = vElement.getAsJsonObject();
                        var legacy = new BookmarkSlot.Variant(v.get("serialized").getAsString(), v.get("amount").getAsLong(),
                                v.get("chance").getAsFloat(), v.get("label").getAsString());
                        if (version == 1) variants.add(legacy);
                        else {
                            var u = v.getAsJsonObject("unit");
                            variants.add(new BookmarkSlot.Variant(legacy.serialized(), legacy.amount(), legacy.chance(), legacy.label(),
                                    new BookmarkSlot.Unit(u.get("identity").getAsString(), u.get("symbol").getAsString(),
                                            u.get("numerator").getAsLong(), u.get("denominator").getAsLong())));
                        }
                    }
                    slots.add(new BookmarkSlot(id(s, "id", allIds), BookmarkSlot.Role.valueOf(s.get("role").getAsString()),
                            variants, s.get("selected").getAsInt()));
                }
                String key = r.has("recipeKey") && !r.get("recipeKey").isJsonNull() ? r.get("recipeKey").getAsString() : null;
                boolean isRecipe = r.get("recipe").getAsBoolean();
                // Gson's file writer omits null object members. Plain item bookmarks therefore
                // legitimately have no primaryOutput field; recipe validation still requires one.
                var primaryElement = r.get("primaryOutput");
                UUID primary = version == 1 ? (isRecipe ? slots.get(0).id : null) :
                        primaryElement == null || primaryElement.isJsonNull() ? null : UUID.fromString(primaryElement.getAsString());
                var record = new BookmarkCollection.Recipe(recipeId, isRecipe, key, slots, r.get("batches").getAsLong(), primary);
                record.counted(version < 3 || r.get("counted").getAsBoolean());
                if (version >= 4) record.collapsed(r.get("collapsed").getAsBoolean());
                c.recipes.add(record);
            }
            Set<UUID> assigned = new HashSet<>();
            for (var gElement : array(json, "groups", MAX_MERGED_RECORDS)) {
                var g = gElement.getAsJsonObject(); List<UUID> members = new ArrayList<>();
                int previous = -1;
                for (var m : array(g, "members", MAX_MERGED_RECORDS)) {
                    UUID member = UUID.fromString(m.getAsString()); int index = -1;
                    for (int i = 0; i < c.recipes.size(); i++) if (c.recipes.get(i).id().equals(member)) { index = i; break; }
                    if (index < 0 || (!members.isEmpty() && index != previous + 1) || !assigned.add(member))
                        throw new IllegalArgumentException("Invalid group ownership");
                    members.add(member); previous = index;
                }
                if (members.isEmpty()) throw new IllegalArgumentException("Empty group");
                var group = new BookmarkCollection.Group(id(g, "id", allIds), members);
                group.mode = BookmarkCollection.Mode.valueOf(g.get("mode").getAsString());
                group.chain = g.get("chain").getAsBoolean(); group.collapsed = g.get("collapsed").getAsBoolean();
                if (version >= 5 && g.has("treeGoal") && !g.get("treeGoal").isJsonNull()) {
                    group.treeGoal = UUID.fromString(g.get("treeGoal").getAsString());
                    if (!members.contains(group.treeGoal) || c.recipes.stream().noneMatch(r -> r.id().equals(group.treeGoal) && r.isRecipe()))
                        throw new IllegalArgumentException("Invalid tree goal");
                }
                c.groups.add(group);
            }
            collections.add(c);
        }
        return new Document(collections, UUID.fromString(root.get("active").getAsString()));
    }
}

