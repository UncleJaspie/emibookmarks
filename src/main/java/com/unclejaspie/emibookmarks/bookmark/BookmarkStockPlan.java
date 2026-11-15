package com.unclejaspie.emibookmarks.bookmark;

import java.util.*;

/** Temporary inventory-aware projection. Allocation follows saved order across independent scopes. */
public final class BookmarkStockPlan {
    private record State(BookmarkCollection.Recipe record, UUID group, boolean chain, long batches, List<Integer> variants) {}
    private record Key(List<State> records, Map<UUID, BookmarkChain.Result> baseline, Map<BookmarkChain.Resource, Long> stock) {}
    private static final Map<Key, Plan> CACHE = new LinkedHashMap<>(4, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Plan> entry) { return size() > 4; }
    };
    public record Plan(Map<UUID, Long> amounts, Map<UUID, Long> batches, Map<UUID, String> issues) {
        public Plan { amounts = Map.copyOf(amounts); batches = Map.copyOf(batches); issues = Map.copyOf(issues); }
    }
    public static synchronized Plan calculate(BookmarkCollection collection, Map<UUID, BookmarkChain.Result> baseline,
                                 Map<BookmarkChain.Resource, Long> inventory) {
        var states = collection.recipes.stream().map(r -> {
            var group = collection.groupOf(r.id());
            return new State(r, group == null ? null : group.id, group == null ? collection.chain : group.chain,
                    r.batches(), r.icons().stream().map(BookmarkSlot::selected).toList());
        }).toList();
        var key = new Key(states, Map.copyOf(baseline), Map.copyOf(inventory));
        return CACHE.computeIfAbsent(key, ignored -> solve(collection, baseline, inventory));
    }
    private static Plan solve(BookmarkCollection collection, Map<UUID, BookmarkChain.Result> baseline,
                              Map<BookmarkChain.Resource, Long> inventory) {
        var stock = new HashMap<>(inventory);
        var visited = new HashSet<UUID>();
        Map<UUID, Long> amounts = new HashMap<>(), batches = new HashMap<>();
        Map<UUID, String> issues = new HashMap<>();
        for (var record : collection.recipes) {
            if (!visited.add(record.id())) continue;
            var group = collection.groupOf(record.id());
            boolean chain = group == null ? collection.chain : group.chain;
            var records = chain ? collection.recipes.stream().filter(r -> {
                var owner = collection.groupOf(r.id());
                return group == null ? owner == null : owner != null && owner.id.equals(group.id);
            }).toList() : List.of(record);
            records.forEach(r -> visited.add(r.id()));
            var original = baseline.get(record.id());
            var result = original != null && !original.valid() ? original : BookmarkChain.calculate(records, stock);
            if (!result.valid()) records.forEach(r -> issues.put(r.id(), result.issue()));
            else { amounts.putAll(result.amounts()); batches.putAll(result.batches()); }
        }
        return new Plan(amounts, batches, issues);
    }
}
