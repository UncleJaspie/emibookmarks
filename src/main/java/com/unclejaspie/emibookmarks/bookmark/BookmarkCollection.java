package com.unclejaspie.emibookmarks.bookmark;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Independent bookmark records; resource equality never establishes group membership. */
public final class BookmarkCollection {
    public enum Mode { TODO, COMPACT }
    public static final class Recipe {
        private final UUID id;
        private final boolean recipe;
        private final String recipeKey;
        private final List<BookmarkSlot> icons;
        private long batches;
        private boolean counted = true;
        private boolean collapsed;
        public boolean collapsed() { return collapsed; }
        public void collapsed(boolean value) {
            if (value && !recipe) throw new IllegalArgumentException("Only recipe records can fold");
            collapsed = value;
        }
        public boolean counted() { return counted; }
        public void counted(boolean value) { counted = value; }
        private final UUID primaryOutput;
        public UUID primaryOutput() { return primaryOutput; }
        public Recipe(UUID id, boolean recipe, String recipeKey, List<BookmarkSlot> icons, long batches) {
            this(id, recipe, recipeKey, icons, batches, recipe && !icons.isEmpty() ? icons.get(0).id : null);
        }
        public Recipe(UUID id, boolean recipe, String recipeKey, List<BookmarkSlot> icons, long batches, UUID primaryOutput) {
            if (id == null || icons.isEmpty() || batches < 1) throw new IllegalArgumentException("Invalid bookmark");
            this.id = id; this.recipe = recipe; this.recipeKey = recipeKey; this.icons = List.copyOf(icons);
            if (recipe ? icons.get(0).role != BookmarkSlot.Role.OUTPUT :
                    (icons.size() != 1 || icons.get(0).role != BookmarkSlot.Role.ITEM || recipeKey != null))
                throw new IllegalArgumentException("Invalid bookmark roles");
            if (batches > maxBatches()) throw new IllegalArgumentException("Bookmark amount overflow");
            if (recipe ? icons.stream().noneMatch(s -> s.id.equals(primaryOutput) && s.role == BookmarkSlot.Role.OUTPUT) : primaryOutput != null)
                throw new IllegalArgumentException("Invalid primary output");
            this.primaryOutput = primaryOutput;
            this.batches = batches;
        }
        public UUID id() { return id; }
        public boolean isRecipe() { return recipe; }
        public String recipeKey() { return recipeKey; }
        public List<BookmarkSlot> icons() { return icons; }
        public long batches() { return batches; }
        private long maxBatches() {
            long largest = icons.stream().filter(s -> s.role != BookmarkSlot.Role.CATALYST)
                    .flatMap(s -> s.alternatives.stream()).mapToLong(BookmarkSlot.Variant::amount).max().orElse(1);
            return Long.MAX_VALUE / largest;
        }
        public void adjustBatches(int direction, long step) {
            if (step < 1) throw new IllegalArgumentException("Invalid quantity step");
            if (!counted) {
                counted = true;
                batches = direction > 0 ? Math.min(step, maxBatches()) : 1;
                return;
            }
            counted = true;
            long max = maxBatches();
            if (direction > 0) batches = batches > max - Math.min(step, max) ? max : batches + Math.min(step, max);
            else if (direction < 0) batches = batches <= step ? 1 : batches - step;
        }
        /** Small opaque snapshots for headless layout tests. Production captures use the EMI adapter. */
        public static Recipe of(String... icons) {
            var slots = new ArrayList<BookmarkSlot>();
            for (int i = 0; i < icons.length; i++) slots.add(new BookmarkSlot(UUID.randomUUID(),
                    i == 0 ? BookmarkSlot.Role.OUTPUT : BookmarkSlot.Role.INPUT,
                    List.of(new BookmarkSlot.Variant(icons[i], 1, 1, icons[i])), 0));
            return new Recipe(UUID.randomUUID(), true, null, slots, 1);
        }
    }
    public static final class Group {
        public final UUID id;
        public final List<UUID> members;
        public Mode mode = Mode.COMPACT;
        public boolean chain;
        public boolean collapsed;
        public UUID treeGoal;
        public Group(UUID id, List<UUID> members) { this.id = id; this.members = new ArrayList<>(members); }
    }
    public final UUID id;
    public final String name;
    public final List<Recipe> recipes = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    public Mode mode = Mode.COMPACT;
    public boolean chain;
    public boolean collapsed;

    public BookmarkCollection(String name) { this(UUID.randomUUID(), name); }
    public BookmarkCollection(UUID id, String name) { this.id = id; this.name = name; }
    public void remove(UUID id) { recipes.removeIf(r -> r.id().equals(id)); normalize(); }
    /** Removes the hovered ownership scope, including all ungrouped records for a null group. */
    public void removeScope(UUID groupId) {
        var ids = recipes.stream().filter(r -> {
            var group = groupOf(r.id());
            return groupId == null ? group == null : group != null && group.id.equals(groupId);
        }).map(Recipe::id).toList();
        recipes.removeIf(r -> ids.contains(r.id())); normalize();
    }
    public Group groupOf(UUID recipe) {
        return groups.stream().filter(g -> g.members.contains(recipe)).findFirst().orElse(null);
    }
    public Group groupById(UUID id) {
        return groups.stream().filter(g -> g.id.equals(id)).findFirst().orElse(null);
    }
    public void group(int first, int last, Group reuse) {
        List<UUID> members = recipes.subList(Math.min(first, last), Math.max(first, last) + 1)
                .stream().map(Recipe::id).toList();
        for (Group g : groups) g.members.removeAll(members);
        groups.removeIf(g -> g.members.isEmpty());
        Group selected = reuse == null ? new Group(UUID.randomUUID(), members) : reuse;
        selected.members.clear(); selected.members.addAll(members);
        if (!groups.contains(selected)) groups.add(selected);
        normalize();
    }
    public void exclude(int first, int last) {
        List<UUID> removed = recipes.subList(Math.min(first, last), Math.max(first, last) + 1)
                .stream().map(Recipe::id).toList();
        for (Group g : groups) g.members.removeAll(removed);
        normalize();
    }
    public void move(Group group, int before) {
        if (group == null || before < 0 || before >= recipes.size() || group.members.contains(recipes.get(before).id())) return;
        UUID target = recipes.get(before).id();
        List<Recipe> moving = recipes.stream().filter(r -> group.members.contains(r.id())).toList();
        recipes.removeAll(moving);
        int index = 0;
        while (!recipes.get(index).id().equals(target)) index++;
        recipes.addAll(index, moving);
        normalize();
    }
    private void normalize() {
        List<Group> normalized = new ArrayList<>();
        for (Group old : groups) {
            Group run = null;
            boolean usedOriginal = false;
            for (Recipe recipe : recipes) {
                if (!old.members.contains(recipe.id())) { run = null; continue; }
                if (run == null) {
                    run = new Group(usedOriginal ? UUID.randomUUID() : old.id, List.of());
                    run.mode = old.mode; run.chain = old.chain; run.collapsed = old.collapsed;
                    run.treeGoal = old.treeGoal;
                    normalized.add(run); usedOriginal = true;
                }
                run.members.add(recipe.id());
            }
        }
        groups.clear(); groups.addAll(normalized);
        for (var group : groups) if (group.treeGoal != null && !group.members.contains(group.treeGoal)) group.treeGoal = null;
    }
}

