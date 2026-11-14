package com.unclejaspie.emibookmarks.bookmark;

import java.util.*;

/** Bounded calculation cache keyed by exact immutable snapshots of every mutable calculation input. */
public final class BookmarkChains {
    private record State(BookmarkCollection.Recipe record, long batches, List<Integer> selections) {}
    private record Key(List<State> states, String issue) {}
    private static java.util.function.Function<BookmarkCollection.Recipe, String> validator = r -> null;
    public static synchronized void validator(java.util.function.Function<BookmarkCollection.Recipe, String> value) { validator = value; CACHE.clear(); }
    public static synchronized void clearCache() { CACHE.clear(); }
    private static final Map<Key, BookmarkChain.Result> CACHE = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, BookmarkChain.Result> entry) { return size() > 32; }
    };
    public static synchronized BookmarkChain.Result scope(BookmarkCollection c, UUID groupId) {
        var records = c.recipes.stream().filter(r -> {
            var group = c.groupOf(r.id());
            return groupId == null ? group == null : group != null && group.id.equals(groupId);
        }).toList();
        String issue = records.stream().map(validator).filter(Objects::nonNull).findFirst().orElse(null);
        var key = new Key(records.stream().map(r -> new State(r, r.batches(), r.icons().stream().map(BookmarkSlot::selected).toList())).toList(), issue);
        return CACHE.computeIfAbsent(key, ignored -> issue == null ? BookmarkChain.calculate(records) : BookmarkChain.failed(issue));
    }
    public static Map<UUID, BookmarkChain.Result> active(BookmarkCollection c) {
        var results = new HashMap<UUID, BookmarkChain.Result>();
        if (c.chain) {
            var result = scope(c, null);
            c.recipes.stream().filter(r -> c.groupOf(r.id()) == null).forEach(r -> results.put(r.id(), result));
        }
        for (var group : c.groups) if (group.chain) {
            var result = scope(c, group.id); group.members.forEach(id -> results.put(id, result));
        }
        return Map.copyOf(results);
    }
}
