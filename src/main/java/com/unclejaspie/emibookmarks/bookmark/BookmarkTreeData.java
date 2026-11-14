package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Portable single-tree snapshots and transactional replacements; no Minecraft or network code. */
public final class BookmarkTreeData {
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public record Tree(List<BookmarkCollection.Recipe> recipes, BookmarkCollection.Group group) {
        public Tree { recipes = List.copyOf(recipes); }
        public BookmarkCollection.Recipe goal() {
            return recipes.stream().filter(r -> r.id().equals(group.treeGoal)).findFirst().orElseThrow();
        }
    }
    private static BookmarkCollection.Group group(BookmarkCollection.Group old, UUID id, List<UUID> members, UUID goal) {
        var copy = new BookmarkCollection.Group(id, members);
        copy.mode = old.mode; copy.chain = old.chain; copy.collapsed = old.collapsed; copy.treeGoal = goal; return copy;
    }
    public static Tree selected(BookmarkCollection collection, UUID groupId, UUID goalId) {
        var source = groupId == null ? null : collection.groupById(groupId);
        var records = collection.recipes.stream().filter(r -> source == null ? r.id().equals(goalId) : source.members.contains(r.id())).toList();
        if (records.isEmpty()) throw new IllegalArgumentException("Saved recipe tree no longer exists");
        UUID root = source == null ? goalId : source.treeGoal != null ? source.treeGoal : inferGoal(records);
        if (records.stream().noneMatch(r -> r.isRecipe() && r.id().equals(root))) throw new IllegalArgumentException("Choose a recipe bookmark as the tree goal");
        var g = source == null ? new BookmarkCollection.Group(UUID.nameUUIDFromBytes(("tree:" + root).getBytes(StandardCharsets.UTF_8)), List.of(root)) : source;
        return new Tree(records, group(g, g.id, records.stream().map(BookmarkCollection.Recipe::id).toList(), root));
    }
    /** Membership comes only from the parent group; resource links identify its terminal goal. */
    private static UUID inferGoal(List<BookmarkCollection.Recipe> records) {
        var consumed = new HashSet<BookmarkChain.Resource>();
        for (var r : records) for (var s : r.icons()) if (s.role == BookmarkSlot.Role.INPUT)
            consumed.add(BookmarkChain.resource(s.variant()));
        var roots = records.stream().filter(BookmarkCollection.Recipe::isRecipe).filter(r ->
                r.icons().stream().filter(s -> s.id.equals(r.primaryOutput()))
                        .anyMatch(s -> !consumed.contains(BookmarkChain.resource(s.variant())))).toList();
        if (roots.size() != 1) throw new IllegalArgumentException("This group needs one connected recipe goal; split independent goals into separate groups");
        return roots.get(0).id();
    }
    public static JsonObject encode(Tree tree) {
        var used = new HashSet<UUID>(); used.add(tree.group.id);
        for (var r : tree.recipes) { used.add(r.id()); r.icons().forEach(s -> used.add(s.id)); }
        UUID container; int salt = 0;
        do { container = UUID.nameUUIDFromBytes(("share:" + tree.group.id + ":" + salt++).getBytes(StandardCharsets.UTF_8)); } while (used.contains(container));
        var c = new BookmarkCollection(container, "Shared recipe tree");
        c.recipes.addAll(tree.recipes); c.groups.add(tree.group);
        var root = new JsonObject(); root.addProperty("format", "emibookmarks-tree"); root.addProperty("version", 1);
        root.add("document", BookmarkCodec.encode(new BookmarkCodec.Document(List.of(c), c.id))); return root;
    }
    public static String fingerprint(Tree tree) { return encode(tree).toString(); }
    public static Tree decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Shared tree exceeds 16 MiB");
        try { return decode(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()); }
        catch (java.nio.charset.CharacterCodingException invalid) { throw new IllegalArgumentException("Shared tree is not valid UTF-8", invalid); }
    }
    public static Tree decode(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Shared tree exceeds 16 MiB");
        int depth = 0; boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (ch == '\\') escaped = true; else if (ch == '"') quoted = false; }
            else if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') { if (++depth > 64) throw new IllegalArgumentException("Shared tree JSON is nested too deeply"); }
            else if (ch == '}' || ch == ']') depth--;
        }
        var root = JsonParser.parseString(text).getAsJsonObject();
        if (!root.has("format") || !root.has("version") || !root.get("format").getAsString().equals("emibookmarks-tree") || root.get("version").getAsInt() != 1)
            throw new IllegalArgumentException("Not a supported EMI Bookmarks tree file");
        var document = BookmarkCodec.decode(root.getAsJsonObject("document"));
        if (document.collections().size() != 1) throw new IllegalArgumentException("Share files must contain one tree");
        var c = document.collections().get(0);
        if (c.groups.size() != 1 || c.recipes.isEmpty() || c.recipes.size() > BookmarkTreePlanner.MAX_RECIPES)
            throw new IllegalArgumentException("Share files must contain one bounded tree group");
        var g = c.groups.get(0);
        if (g.treeGoal == null || g.members.size() != c.recipes.size()) throw new IllegalArgumentException("Missing tree goal or ungrouped share records");
        long chars = c.recipes.stream().flatMap(r -> r.icons().stream()).flatMap(s -> s.alternatives.stream()).mapToLong(v -> v.serialized().length()).sum();
        if (chars > 2_097_152) throw new IllegalArgumentException("Shared snapshots exceed 2 MiB");
        return remap(new Tree(c.recipes, g));
    }
    /** Imports always allocate identities; sharing a tree with yourself cannot collide with live records. */
    public static Tree remap(Tree tree) {
        var ids = new HashMap<UUID, UUID>(); var records = new ArrayList<BookmarkCollection.Recipe>();
        for (var old : tree.recipes) {
            UUID next = UUID.randomUUID(); ids.put(old.id(), next); var slots = new ArrayList<BookmarkSlot>(); UUID primary = null;
            for (var s : old.icons()) {
                var copy = new BookmarkSlot(UUID.randomUUID(), s.role, s.alternatives, s.selected()); slots.add(copy);
                if (s.id.equals(old.primaryOutput())) primary = copy.id;
            }
            var copy = new BookmarkCollection.Recipe(next, old.isRecipe(), old.recipeKey(), slots, old.batches(), primary);
            copy.counted(old.counted()); copy.collapsed(old.collapsed()); records.add(copy);
        }
        return new Tree(records, group(tree.group, UUID.randomUUID(), tree.group.members.stream().map(ids::get).toList(), ids.get(tree.group.treeGoal)));
    }
    /** Build a candidate collection. Caller validates full-document size before committing it. */
    public static BookmarkCollection stage(BookmarkCollection source, Tree tree, UUID replaceGroup, UUID replaceRecipe) {
        var oldGroup = replaceGroup == null ? null : source.groupById(replaceGroup);
        if (replaceGroup != null && oldGroup == null) throw new IllegalArgumentException("Tree group no longer exists");
        var removed = oldGroup != null ? new HashSet<>(oldGroup.members) : replaceRecipe == null ? Set.<UUID>of() : Set.of(replaceRecipe);
        if (!removed.isEmpty() && source.recipes.stream().noneMatch(r -> removed.contains(r.id()))) throw new IllegalArgumentException("Saved goal no longer exists");
        if (replaceRecipe != null && source.groupOf(replaceRecipe) != null) throw new IllegalArgumentException("Do not replace one member of a manual group");
        var inserted = tree;
        if (!removed.isEmpty()) {
            var records = new ArrayList<BookmarkCollection.Recipe>(); var ids = new HashMap<UUID, UUID>();
            var reused = new HashSet<UUID>();
            for (var fresh : tree.recipes) {
                var old = source.recipes.stream().filter(r -> removed.contains(r.id()) && !reused.contains(r.id()) && BookmarkIdentity.sameCapture(r, fresh)).findFirst().orElse(null);
                if (old == null) { records.add(fresh); ids.put(fresh.id(), fresh.id()); continue; }
                var slots = new ArrayList<BookmarkSlot>(); UUID primary = null;
                for (int i = 0; i < fresh.icons().size(); i++) {
                    var s = fresh.icons().get(i); var copy = new BookmarkSlot(old.icons().get(i).id, s.role, s.alternatives, s.selected()); slots.add(copy);
                    if (s.id.equals(fresh.primaryOutput())) primary = copy.id;
                }
                long batches = fresh.id().equals(tree.group.treeGoal) ? fresh.batches() : old.batches();
                var copy = new BookmarkCollection.Recipe(old.id(), fresh.isRecipe(), fresh.recipeKey(), slots, batches, primary);
                reused.add(old.id());
                copy.counted(old.counted()); copy.collapsed(old.collapsed()); records.add(copy); ids.put(fresh.id(), old.id());
            }
            inserted = new Tree(records, group(tree.group, oldGroup == null ? tree.group.id : oldGroup.id,
                    tree.group.members.stream().map(ids::get).toList(), ids.get(tree.group.treeGoal)));
            if (oldGroup != null) { inserted.group.mode = oldGroup.mode; inserted.group.collapsed = oldGroup.collapsed; inserted.group.chain &= oldGroup.chain; }
        }
        var result = new BookmarkCollection(source.id, source.name); result.mode = source.mode; result.chain = source.chain; result.collapsed = source.collapsed;
        boolean added = false;
        for (var r : source.recipes) {
            if (removed.contains(r.id())) { if (!added) { result.recipes.addAll(inserted.recipes); added = true; } }
            else result.recipes.add(r);
        }
        if (!added) result.recipes.addAll(inserted.recipes);
        for (var g : source.groups) result.groups.add(g == oldGroup ? inserted.group : g);
        if (oldGroup == null) result.groups.add(inserted.group);
        return result;
    }
}
