package com.unclejaspie.emibookmarks.bookmark;

import java.util.List;
import java.util.UUID;

/** Immutable accepted resources plus an explicit selected alternative, independent of EMI runtime classes. */
public final class BookmarkSlot {
    public enum Role { ITEM, OUTPUT, INPUT, CATALYST }
    public record Unit(String identity, String symbol, long numerator, long denominator) {
        public static final Unit UNKNOWN = new Unit("unknown", "?", 1, 1);
        public static final Unit ITEM = new Unit("item", "", 1, 1);
        public static final Unit MILLIBUCKET = new Unit("forge:fluid", "mB", 1, 1);
        public Unit {
            if (identity == null || identity.isBlank() || symbol == null || numerator < 1 || denominator < 1)
                throw new IllegalArgumentException("Invalid quantity unit");
        }
    }
    public record Variant(String serialized, long amount, float chance, String label, Unit unit) {
        public Variant(String serialized, long amount, float chance, String label) {
            this(serialized, amount, chance, label, legacyUnit(serialized));
        }
        private static Unit legacyUnit(String serialized) {
            try {
                var json = com.google.gson.JsonParser.parseString(serialized);
                String type = json.isJsonObject() && json.getAsJsonObject().has("type") ?
                        json.getAsJsonObject().get("type").getAsString() : json.isJsonPrimitive() ? json.getAsString().split(":", 2)[0] : "";
                return type.equals("item") ? Unit.ITEM : type.equals("fluid") ? Unit.MILLIBUCKET : Unit.UNKNOWN;
            } catch (RuntimeException e) { return Unit.UNKNOWN; }
        }
        public Variant {
            if (unit == null || serialized == null || serialized.isBlank() || label == null || amount < 1 ||
                    !Float.isFinite(chance) || chance < 0) throw new IllegalArgumentException("Invalid resource snapshot");
        }
    }
    public final UUID id;
    public final Role role;
    public final List<Variant> alternatives;
    private int selected;

    public BookmarkSlot(UUID id, Role role, List<Variant> alternatives, int selected) {
        if (id == null || role == null || alternatives.isEmpty() || selected < 0 || selected >= alternatives.size())
            throw new IllegalArgumentException("Invalid slot");
        this.id = id; this.role = role; this.alternatives = List.copyOf(alternatives); this.selected = selected;
    }
    public int selected() { return selected; }
    public Variant variant() { return alternatives.get(selected); }
    public void cycle(int direction) { selected = Math.floorMod(selected + Integer.signum(direction), alternatives.size()); }
    public long amount(long batches) {
        long base = variant().amount();
        if (role == Role.CATALYST) return base;
        if (batches < 1 || batches > Long.MAX_VALUE / base) throw new ArithmeticException("Bookmark amount overflow");
        return base * batches;
    }
}

