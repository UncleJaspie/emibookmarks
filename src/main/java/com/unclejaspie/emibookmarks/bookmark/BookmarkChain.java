package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.*;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Original fixed-recipe DAG calculation. No Minecraft dependencies or upstream implementation text.
 * Only the supplied bookmark scope supplies producers; unsupported semantics fail as a whole. */
public final class BookmarkChain {
    public enum Kind { BASE_INPUT, RESULT, REMAINDER, INTERMEDIATE, CHANCE }
    public record Resource(JsonElement identity, BookmarkSlot.Unit unit) {}
    public record Material(Resource resource, String label, long amount) {}
    public record Result(String issue, boolean expected, Map<UUID, Long> batches, Map<UUID, Long> amounts,
                         Map<UUID, Kind> kinds, Set<UUID> targets,
                         List<Material> inputs, List<Material> outputs, List<Material> leftovers) {
        public Result {
            batches = Map.copyOf(batches); amounts = Map.copyOf(amounts); kinds = Map.copyOf(kinds);
            targets = Set.copyOf(targets); inputs = List.copyOf(inputs); outputs = List.copyOf(outputs); leftovers = List.copyOf(leftovers);
        }
        public boolean valid() { return issue == null; }
    }
    private static final class Unsupported extends RuntimeException { Unsupported(String message) { super(message); } }
    /** Parse standard pinned EMI resources conservatively; native amount, chance and remainder are separate semantics. */
    public static Resource resource(BookmarkSlot.Variant variant) {
        JsonElement json;
        try { json = JsonParser.parseString(variant.serialized()); }
        catch (RuntimeException e) { throw new Unsupported("Unreadable resource: " + variant.label()); }
        if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String text = json.getAsString(); int prefix = text.indexOf(':'); int nbt = text.indexOf('{');
            if (prefix < 0) throw new Unsupported("Unknown resource: " + variant.label());
            var object = new JsonObject(); object.addProperty("type", text.substring(0, prefix));
            object.addProperty("id", text.substring(prefix + 1, nbt < 0 ? text.length() : nbt));
            if (nbt >= 0) object.addProperty("nbt", text.substring(nbt));
            json = object;
        }
        if (!json.isJsonObject()) throw new Unsupported("Unknown resource: " + variant.label());
        var object = json.getAsJsonObject().deepCopy();
        String type = object.has("type") ? object.get("type").getAsString() : "";
        if (!object.has("id") || !(type.equals("item") && variant.unit().equals(BookmarkSlot.Unit.ITEM) ||
                type.equals("fluid") && variant.unit().equals(BookmarkSlot.Unit.MILLIBUCKET)))
            throw new Unsupported("Unsupported resource/unit: " + variant.label());
        for (String key : object.keySet()) if (!Set.of("type", "id", "nbt", "amount", "chance", "remainder").contains(key))
            throw new Unsupported("Unrecognized recipe resource metadata: " + key);
        object.remove("amount"); object.remove("chance"); object.remove("remainder");
        return new Resource(object, variant.unit());
    }
    public static Result calculate(List<BookmarkCollection.Recipe> records) {
        return calculate(records, null);
    }
    /** Transactional stock allocation: unsupported scopes leave the supplied budget untouched. */
    public static Result calculate(List<BookmarkCollection.Recipe> records, Map<Resource, Long> stock) {
        var budget = stock == null ? null : new HashMap<>(stock);
        try {
            var result = solve(records, budget);
            if (stock != null) { stock.clear(); stock.putAll(budget); }
            return result;
        }
        catch (Unsupported e) { return failed(e.getMessage()); }
        catch (ArithmeticException e) { return failed("Chain quantities exceed the supported integer range"); }
        catch (RuntimeException e) { return failed("Recipe snapshot cannot be calculated safely"); }
    }
    public static Result failed(String issue) { return new Result(issue, false, Map.of(), Map.of(), Map.of(), Set.of(), List.of(), List.of(), List.of()); }
    private static void add(Map<Resource, Long> amounts, Resource key, long value) { amounts.merge(key, value, Math::addExact); }
    private static long total(Map<Resource, Long> consumed, Map<Resource, Long> tools, Resource key) {
        return Math.addExact(consumed.getOrDefault(key, 0L), tools.getOrDefault(key, 0L));
    }
    private static long ceil(long amount, BigDecimal yield) { return BigDecimal.valueOf(amount).divide(yield, 0, RoundingMode.CEILING).longValueExact(); }
    private record Return(Resource key, String label, long amount) {}
    private static Return remainder(BookmarkSlot.Variant v) {
        var json = JsonParser.parseString(v.serialized());
        if (!json.isJsonObject() || !json.getAsJsonObject().has("remainder")) return null;
        var value = json.getAsJsonObject().get("remainder");
        if (value.isJsonNull() || value.equals(new JsonPrimitive("empty"))) return null;
        var unit = value.isJsonObject() && value.getAsJsonObject().has("type") && value.getAsJsonObject().get("type").getAsString().equals("fluid") || value.isJsonPrimitive() && value.getAsString().startsWith("fluid:") ? BookmarkSlot.Unit.MILLIBUCKET : BookmarkSlot.Unit.ITEM;
        long amount = value.isJsonObject() && value.getAsJsonObject().has("amount") ? value.getAsJsonObject().get("amount").getAsLong() : 1;
        float chance = value.isJsonObject() && value.getAsJsonObject().has("chance") ? value.getAsJsonObject().get("chance").getAsFloat() : 1;
        if (amount < 1 || chance != 1 || value.isJsonObject() && value.getAsJsonObject().has("remainder")) throw new Unsupported("Ambiguous nested/chance remainder: " + v.label());
        var key = resource(new BookmarkSlot.Variant(value.toString(), amount, 1, v.label(), unit));
        return new Return(key, key.identity().getAsJsonObject().get("id").getAsString(), amount);
    }
    private static Result solve(List<BookmarkCollection.Recipe> records, Map<Resource, Long> stock) {
        Map<UUID, Resource> keys = new HashMap<>(); Map<Resource, String> labels = new LinkedHashMap<>();
        Map<Resource, BookmarkCollection.Recipe> producer = new HashMap<>();
        Map<UUID, Map<Resource, BigDecimal>> yields = new LinkedHashMap<>();
        Map<UUID, BookmarkCollection.Recipe> recipes = new LinkedHashMap<>();
        Set<UUID> uncertain = new HashSet<>(), expectedSlots = new HashSet<>(), reusable = new HashSet<>();
        Map<UUID, Return> returns = new HashMap<>();
        Set<Resource> required = new HashSet<>();
        for (var r : records) for (var slot : r.icons())
            if (slot.role != BookmarkSlot.Role.OUTPUT || slot.id.equals(r.primaryOutput())) required.add(resource(slot.variant()));
        for (var r : records) {
            for (var slot : r.icons()) {
                var key = resource(slot.variant()); keys.put(slot.id, key); labels.putIfAbsent(key, slot.variant().label());
                var returned = remainder(slot.variant());
                if (returned != null) {
                    if (slot.role != BookmarkSlot.Role.INPUT) throw new Unsupported("Remainder must belong to an input: " + slot.variant().label());
                    if (returned.key().identity().getAsJsonObject().get("id").equals(key.identity().getAsJsonObject().get("id")) && returned.key().unit().equals(key.unit()) && returned.amount() == slot.variant().amount()) reusable.add(slot.id);
                    else {
                        if (required.contains(returned.key())) throw new Unsupported("Returned container reuse needs an explicit starting supply: " + returned.label());
                        returns.put(slot.id, returned); labels.putIfAbsent(returned.key(), returned.label());
                    }
                }
                float chance = slot.variant().chance();
                if (Float.compare(chance, 1) == 0 || slot.role == BookmarkSlot.Role.CATALYST && chance == 0) continue;
                if (slot.role == BookmarkSlot.Role.OUTPUT && chance > 0 && chance < 1 && required.contains(key)) expectedSlots.add(slot.id);
                else if (slot.role == BookmarkSlot.Role.OUTPUT && !slot.id.equals(r.primaryOutput()) && chance >= 0 && chance < 1) uncertain.add(slot.id);
                else throw new Unsupported("Chance-dependent quantities are unsupported: " + slot.variant().label());
            }
            if (!r.isRecipe()) continue;
            recipes.put(r.id(), r); var output = new LinkedHashMap<Resource, BigDecimal>();
            for (var slot : r.icons()) if (slot.role == BookmarkSlot.Role.OUTPUT) {
                if (uncertain.contains(slot.id)) continue;
                var key = keys.get(slot.id); var previous = producer.putIfAbsent(key, r);
                if (previous != null && previous != r) throw new Unsupported("Multiple bookmarked producers for " + labels.get(key) + "; separate the routes into groups");
                output.merge(key, BigDecimal.valueOf(slot.variant().amount()).multiply(new BigDecimal(Float.toString(slot.variant().chance()))), BigDecimal::add);
            }
            yields.put(r.id(), output);
            for (var slot : r.icons()) if (expectedSlots.contains(slot.id) && r.icons().stream().filter(s -> s.role == BookmarkSlot.Role.OUTPUT && keys.get(s.id).equals(keys.get(slot.id))).count() > 1)
                throw new Unsupported("Repeated chance output needs pooled display rounding: " + slot.variant().label());
        }
        var chanceResources = uncertain.stream().map(keys::get).collect(java.util.stream.Collectors.toSet());
        if (stock != null && (!uncertain.isEmpty() || !expectedSlots.isEmpty() || !returns.isEmpty()))
            throw new Unsupported("Output-aware stock tracking requires deterministic outputs without returned containers");
        for (var r : records) for (var slot : r.icons())
            if ((slot.role == BookmarkSlot.Role.INPUT || slot.role == BookmarkSlot.Role.CATALYST || slot.role == BookmarkSlot.Role.ITEM) &&
                    chanceResources.contains(keys.get(slot.id)) && !producer.containsKey(keys.get(slot.id)))
                throw new Unsupported("Chance output cannot supply a guaranteed requirement: " + slot.variant().label());
        Map<UUID, Set<UUID>> dependencies = new LinkedHashMap<>(); Map<UUID, Integer> consumers = new HashMap<>();
        Set<Resource> linked = new HashSet<>();
        recipes.keySet().forEach(id -> { dependencies.put(id, new LinkedHashSet<>()); consumers.put(id, 0); });
        for (var r : recipes.values()) for (var slot : r.icons())
            if (slot.role == BookmarkSlot.Role.INPUT || slot.role == BookmarkSlot.Role.CATALYST) {
                var key = keys.get(slot.id); var p = producer.get(key);
                if (p != null) { linked.add(key); if (dependencies.get(r.id()).add(p.id())) consumers.merge(p.id(), 1, Integer::sum); }
            }
        var ready = new ArrayDeque<UUID>();
        consumers.forEach((id, count) -> { if (count == 0) ready.add(id); });
        var order = new ArrayList<UUID>();
        while (!ready.isEmpty()) {
            var id = ready.remove(); order.add(id);
            for (var dependency : dependencies.get(id)) if (consumers.merge(dependency, -1, Integer::sum) == 0) ready.add(dependency);
        }
        if (order.size() != recipes.size()) throw new Unsupported("Cyclic bookmarked recipes need an explicit external starting input");
        Map<Resource, Long> demand = new LinkedHashMap<>(), tools = new LinkedHashMap<>(), goals = new LinkedHashMap<>(), produced = new LinkedHashMap<>();
        Set<UUID> targets = new HashSet<>();
        Map<UUID, Long> minimumCrafts = new HashMap<>();
        // A terminal primary output requests its saved craft count. An intermediate count above one
        // requests that many minus one extra crafts, independently of downstream demand.
        for (var r : records) {
            if (!r.isRecipe()) { var slot = r.icons().get(0); add(goals, keys.get(slot.id), slot.amount(r.batches())); targets.add(r.id()); continue; }
            var primary = r.icons().stream().filter(s -> s.id.equals(r.primaryOutput())).findFirst().orElseThrow();
            long requests = linked.contains(keys.get(primary.id)) ? r.batches() - 1 : r.batches();
            if (stock == null && !linked.contains(keys.get(primary.id))) minimumCrafts.put(r.id(), r.batches());
            if (requests > 0) {
                long yield = primary.variant().amount();
                if (stock != null || linked.contains(keys.get(primary.id))) {
                    yield = 0;
                    for (var slot : r.icons()) if (slot.role == BookmarkSlot.Role.OUTPUT && keys.get(slot.id).equals(keys.get(primary.id))) yield = Math.addExact(yield, slot.variant().amount());
                }
                add(goals, keys.get(primary.id), Math.multiplyExact(requests, yield)); targets.add(r.id());
            }
        }
        demand.putAll(goals);
        Map<UUID, Long> batches = new LinkedHashMap<>(), amounts = new HashMap<>();
        Map<Resource, Long> credit = new HashMap<>();
        for (var id : order) {
            long crafts = minimumCrafts.getOrDefault(id, 0L);
            for (var output : yields.get(id).entrySet()) {
                var key = output.getKey();
                long needed = total(demand, tools, key);
                if (stock != null) credit.put(key, take(stock, key, needed));
                crafts = Math.max(crafts, ceil(needed - credit.getOrDefault(key, 0L), output.getValue()));
            }
            batches.put(id, crafts);
            for (var slot : recipes.get(id).icons()) {
                long amount = crafts == 0 ? 0 : (slot.role == BookmarkSlot.Role.CATALYST || reusable.contains(slot.id)) ? slot.variant().amount() : Math.multiplyExact(crafts, slot.variant().amount());
                if (expectedSlots.contains(slot.id)) amount = BigDecimal.valueOf(amount).multiply(new BigDecimal(Float.toString(slot.variant().chance()))).setScale(0, RoundingMode.FLOOR).longValueExact();
                amounts.put(slot.id, amount); var key = keys.get(slot.id);
                var returned = returns.get(slot.id);
                if (returned != null) add(produced, returned.key(), Math.multiplyExact(crafts, returned.amount()));
                if (slot.role == BookmarkSlot.Role.OUTPUT && !uncertain.contains(slot.id)) add(produced, key, amount);
                else if (slot.role == BookmarkSlot.Role.INPUT && !reusable.contains(slot.id)) add(demand, key, amount);
                else if (reusable.contains(slot.id)) tools.merge(key, amount, Math::max);
                else if (slot.role == BookmarkSlot.Role.CATALYST) tools.merge(key, amount, Math::max);
            }
        }
        for (var r : records) if (!r.isRecipe()) { batches.put(r.id(), r.batches()); amounts.put(r.icons().get(0).id, r.icons().get(0).amount(r.batches())); }
        if (stock != null) {
            for (var key : labels.keySet()) if (!credit.containsKey(key)) credit.put(key, take(stock, key, total(demand, tools, key)));
            var consumedCredit = new HashMap<>(credit);
            // Demand is pooled first; allocate each physical stock item once, in saved record order.
            for (var r : records) for (var slot : r.icons()) {
                var key = keys.get(slot.id);
                if (slot.role == BookmarkSlot.Role.INPUT && !reusable.contains(slot.id) || slot.role == BookmarkSlot.Role.ITEM) {
                    long amount = amounts.get(slot.id), used = Math.min(amount, consumedCredit.getOrDefault(key, 0L));
                    amounts.put(slot.id, amount - used); consumedCredit.put(key, consumedCredit.getOrDefault(key, 0L) - used);
                }
            }
            for (var r : records) for (var slot : r.icons()) if (slot.role == BookmarkSlot.Role.CATALYST || reusable.contains(slot.id)) {
                long amount = amounts.get(slot.id);
                amounts.put(slot.id, Math.max(0, amount - consumedCredit.getOrDefault(keys.get(slot.id), 0L)));
            }
        }
        Map<Resource, Long> base = new LinkedHashMap<>(), left = new LinkedHashMap<>();
        for (var key : labels.keySet()) {
            long needed = Math.max(0, total(demand, tools, key) - credit.getOrDefault(key, 0L)), made = produced.getOrDefault(key, 0L);
            if (needed > made) base.put(key, needed - made);
            if (made > needed) left.put(key, made - needed);
        }
        Map<UUID, Kind> kinds = new HashMap<>();
        for (var r : records) for (var slot : r.icons()) {
            var key = keys.get(slot.id);
            Kind kind = uncertain.contains(slot.id) ? Kind.CHANCE : slot.role == BookmarkSlot.Role.INPUT || slot.role == BookmarkSlot.Role.CATALYST ?
                    (producer.containsKey(key) ? Kind.INTERMEDIATE : Kind.BASE_INPUT) :
                    goals.containsKey(key) ? Kind.RESULT : left.containsKey(key) ? Kind.REMAINDER : Kind.INTERMEDIATE;
            kinds.put(slot.id, kind);
        }
        return new Result(null, !expectedSlots.isEmpty(), batches, amounts, kinds, targets, materials(base, labels), materials(goals, labels), materials(left, labels));
    }
    private static List<Material> materials(Map<Resource, Long> amounts, Map<Resource, String> labels) {
        return amounts.entrySet().stream().filter(e -> e.getValue() > 0).map(e -> new Material(e.getKey(), labels.get(e.getKey()), e.getValue())).toList();
    }
    private static long take(Map<Resource, Long> stock, Resource key, long needed) {
        long owned = stock.getOrDefault(key, 0L), used = Math.min(owned, needed);
        stock.put(key, owned - used); return used;
    }
}
