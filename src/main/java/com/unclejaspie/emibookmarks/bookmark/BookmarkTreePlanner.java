package com.unclejaspie.emibookmarks.bookmark;

import java.util.*;

/** Bounded, staged route import. The adapter owns route selection; this owns safety and grouping. */
public final class BookmarkTreePlanner {
    public static final int MAX_RECIPES = 512, MAX_DEPTH = 32, MAX_VISITS = 4096;
    public interface Source<N> {
        Object key(N node);
        BookmarkCollection.Recipe capture(N node);
        List<N> children(N node);
    }
    public record Plan(List<BookmarkCollection.Recipe> recipes, BookmarkChain.Result calculation, int cycleStops) {
        public Plan { recipes = List.copyOf(recipes); }
        public BookmarkCollection.Group group() {
            var group = new BookmarkCollection.Group(UUID.randomUUID(), recipes.stream().map(BookmarkCollection.Recipe::id).toList());
            group.mode = BookmarkCollection.Mode.TODO; group.chain = calculation.valid();
            group.treeGoal = recipes.get(0).id();
            return group;
        }
    }
    private static final class Cycle extends RuntimeException {}

    public static <N> Plan build(N root, Source<N> source) {
        var walk = new Walk<>(source); walk.visit(root, 0);
        if (walk.recipes.isEmpty()) throw new IllegalArgumentException("The goal recipe loops back into itself; choose another EMI route");
        var records = List.copyOf(walk.recipes.values());
        long chars = 0;
        var ids = new HashSet<UUID>();
        for (var record : records) {
            if (!ids.add(record.id())) throw new IllegalArgumentException("Duplicate imported record identity");
            for (var slot : record.icons()) {
                if (!ids.add(slot.id)) throw new IllegalArgumentException("Duplicate imported slot identity");
                for (var variant : slot.alternatives) chars = Math.addExact(chars, variant.serialized().length());
            }
        }
        if (chars > 2_097_152) throw new IllegalArgumentException("Recipe tree exceeds the 2 MiB snapshot budget");
        return new Plan(records, BookmarkChain.calculate(records), walk.cycleStops);
    }

    private static final class Walk<N> {
        final Source<N> source;
        final LinkedHashMap<Object, BookmarkCollection.Recipe> recipes = new LinkedHashMap<>();
        final Set<Object> active = new HashSet<>();
        int visits, cycleStops;
        long capturedChars;
        Walk(Source<N> source) { this.source = source; }
        void visit(N node, int depth) {
            if (++visits > MAX_VISITS || depth > MAX_DEPTH) throw new IllegalArgumentException("Recipe tree is too deep or large; set simpler EMI defaults");
            Object key = Objects.requireNonNull(source.key(node));
            if (active.contains(key)) throw new Cycle();
            if (recipes.containsKey(key)) return;
            if (recipes.size() >= MAX_RECIPES) throw new IllegalArgumentException("Recipe tree exceeds " + MAX_RECIPES + " recipes");
            var before = new HashSet<>(recipes.keySet());
            var record = source.capture(node);
            if (!record.isRecipe()) throw new IllegalArgumentException("Tree route is not a recipe");
            for (var slot : record.icons()) for (var variant : slot.alternatives) {
                capturedChars = Math.addExact(capturedChars, variant.serialized().length());
                if (capturedChars > 2_097_152) throw new IllegalArgumentException("Recipe tree exceeds the 2 MiB snapshot budget");
            }
            recipes.put(key, record); active.add(key);
            try {
                var children = source.children(node);
                if (children.size() > MAX_VISITS) throw new IllegalArgumentException("Too many tree branches");
                for (var child : children) visit(child, depth + 1);
            } catch (Cycle cycle) {
                // Drop the route that would close the loop, keeping its required output as a raw input.
                recipes.keySet().removeIf(k -> !before.contains(k)); cycleStops++;
            } finally { active.remove(key); }
        }
    }
}
