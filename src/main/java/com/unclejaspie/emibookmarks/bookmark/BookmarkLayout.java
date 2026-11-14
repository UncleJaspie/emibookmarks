package com.unclejaspie.emibookmarks.bookmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** GUI-coordinate geometry shared by rendering and every pointer operation. */
public final class BookmarkLayout {
    public static final int CELL = 18, ROW = 20, GUTTER = 7, HEADER = 18, FOOTER = 18;
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    public record Cell(Rect bounds, UUID recipe, int recipeIndex, int icon, UUID group, List<Integer> sources, long amount) {
        public boolean blank() { return icon < 0; }
        public Rect artwork() { return new Rect(bounds.x() + 1, bounds.y(), 16, 16); }
        public Rect quantity() { return new Rect(artwork().x(), bounds.y() + 8, 16, 8); }
        public Rect batch() { return new Rect(artwork().x(), bounds.y(), 9, 6); }
        public Rect badge() { return new Rect(artwork().x() + 10, bounds.y(), 6, 6); }
        public Rect accent() { return new Rect(artwork().x(), bounds.y() + 17, 16, 1); }
    }
    public record Bracket(Rect stem, Rect top, Rect bottom) {}
    public record Row(Rect gutter, int firstRecipe, int lastRecipe, UUID group) {}
    public final Rect bounds, body, previous, label, next, footer, treeImport, options;
    public final List<Cell> cells;
    public final List<Row> rows;
    public final int page, pages, columns;
    public final java.util.Map<UUID, BookmarkChain.Result> calculations;
    private final BookmarkCollection collection;

    public BookmarkLayout(BookmarkCollection collection, int x, int y, int columns, int visibleRows, int requestedPage) {
        this(collection, x, y, columns, visibleRows, requestedPage, false);
    }
    public BookmarkLayout(BookmarkCollection collection, int x, int y, int columns, int visibleRows, int requestedPage, boolean combine) {
        if (columns < 2 || visibleRows < 1) throw new IllegalArgumentException("Empty grid");
        this.columns = columns;
        this.collection = collection;
        calculations = BookmarkChains.active(collection);
        int width = GUTTER + columns * CELL;
        bounds = new Rect(x, y, width, HEADER + visibleRows * ROW + FOOTER);
        body = new Rect(x, y + HEADER, width, visibleRows * ROW);
        previous = new Rect(x, y, 16, HEADER);
        next = new Rect(x + width - 16, y, 16, HEADER);
        label = new Rect(x + 16, y, width - 32, HEADER);
        footer = new Rect(x, y + HEADER + visibleRows * ROW, width - 32, FOOTER);
        treeImport = new Rect(x + width - 32, footer.y(), 16, FOOTER);
        options = new Rect(x + width - 16, footer.y(), 16, FOOTER);
        List<LogicalCell> packed = new ArrayList<>();
        UUID previousGroup = null;
        for (int i = 0; i < collection.recipes.size(); i++) {
            var recipe = collection.recipes.get(i);
            var group = collection.groupOf(recipe.id());
            UUID groupId = group == null ? null : group.id;
            boolean boundary = !Objects.equals(previousGroup, groupId);
            var mode = group == null ? collection.mode : group.mode;
            boolean collapsed = group == null ? collection.collapsed : group.collapsed;
            boolean chain = group == null ? collection.chain : group.chain;
            boolean scopeRepresentatives = collapsed || (chain && mode == BookmarkCollection.Mode.COMPACT);
            boolean representatives = scopeRepresentatives || recipe.collapsed();
            if (boundary || (!scopeRepresentatives && mode == BookmarkCollection.Mode.TODO)) pad(packed, columns);
            var projected = BookmarkProjection.slots(recipe, combine, calculations.get(recipe.id()));
            int count = representatives ? 1 : projected.size();
            for (int icon = 0; icon < count; icon++) {
                var slot = projected.get(icon);
                packed.add(new LogicalCell(recipe.id(), i, slot.representative(), groupId, slot.sources(), slot.amount()));
            }
            previousGroup = groupId;
        }
        pad(packed, columns);
        int totalRows = packed.size() / columns;
        pages = Math.max(1, (totalRows + visibleRows - 1) / visibleRows);
        page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<Cell> cells = new ArrayList<>(); List<Row> rows = new ArrayList<>();
        for (int row = 0; row < visibleRows; row++) {
            int offset = (page * visibleRows + row) * columns;
            if (offset >= packed.size()) break;
            int first = Integer.MAX_VALUE, last = -1;
            UUID rowGroup = packed.get(offset).group;
            for (int col = 0; col < columns; col++) {
                var cell = packed.get(offset + col);
                first = Math.min(first, cell.index); last = Math.max(last, cell.index);
                cells.add(new Cell(new Rect(x + GUTTER + col * CELL, y + HEADER + row * ROW, CELL, ROW),
                        cell.recipe, cell.index, cell.icon, cell.group, cell.sources, cell.amount));
            }
            rows.add(new Row(new Rect(x, y + HEADER + row * ROW, GUTTER, ROW), first, last, rowGroup));
        }
        this.cells = List.copyOf(cells); this.rows = List.copyOf(rows);
    }
    private record LogicalCell(UUID recipe, int index, int icon, UUID group, List<Integer> sources, long amount) {}
    private static void pad(List<LogicalCell> cells, int columns) {
        if (cells.isEmpty()) return;
        var owner = cells.get(cells.size() - 1);
        while (cells.size() % columns != 0) cells.add(new LogicalCell(owner.recipe, owner.index, -1, owner.group, List.of(), 0));
    }
    public Bracket bracket(int index) {
        var row = rows.get(index);
        if (row.group == null) return null;
        boolean first = index == 0 || !row.group.equals(rows.get(index - 1).group);
        boolean last = index + 1 == rows.size() || !row.group.equals(rows.get(index + 1).group);
        var r = row.gutter;
        int top = r.y + (first ? 2 : 0), bottom = r.y + r.height - (last ? 4 : 0);
        return new Bracket(new Rect(r.x + 2, top, 1, bottom - top),
                first ? new Rect(r.x + 2, top, 5, 1) : null, last ? new Rect(r.x + 2, bottom - 1, 5, 1) : null);
    }
    public Row gutter(double x, double y) { return rows.stream().filter(r -> r.gutter.contains(x, y)).findFirst().orElse(null); }
    public Cell cell(double x, double y) { return cells.stream().filter(c -> c.bounds.contains(x, y)).findFirst().orElse(null); }
    public Row row(double x, double y) { return body.contains(x, y) ? rows.stream().filter(r -> y >= r.gutter.y && y < r.gutter.y + ROW).findFirst().orElse(null) : null; }
    public record Drop(UUID target, boolean after, Rect marker) {}
    /** Whole recipe/group boundaries; TODO uses vertical position, compact uses the full raster span. */
    public Drop drop(double x, double y, java.util.Set<UUID> moving, boolean wholeGroup, Drop previousDrop) {
        if (!body.contains(x, y)) return null;
        Cell hit = cell(Math.max(x, body.x + GUTTER), y);
        if (hit != null && moving.contains(hit.recipe)) return null;
        if (hit == null) {
            var last = cells.stream().filter(c -> !c.blank() && !moving.contains(c.recipe)).reduce((a, b) -> b).orElse(null);
            return last == null ? null : new Drop(last.recipe, true, marker(last.recipe, true, wholeGroup));
        }
        final var anchor = hit;
        var unit = cells.stream().filter(c -> wholeGroup && anchor.group != null ? anchor.group.equals(c.group) : anchor.recipe.equals(c.recipe)).toList();
        var first = unit.get(0); var last = unit.get(unit.size() - 1);
        var group = collection.groupById(hit.group);
        boolean vertical = wholeGroup || (group == null ? collection.mode : group.mode) == BookmarkCollection.Mode.TODO;
        double position, midpoint, deadZone;
        if (vertical) {
            position = y; midpoint = (first.bounds.y + last.bounds.y + ROW) / 2.0; deadZone = 2;
        } else {
            var populated = unit.stream().filter(c -> !c.blank()).toList();
            first = populated.get(0); last = populated.get(populated.size() - 1);
            position = Math.floor((y - body.y) / ROW) * columns + Math.max(0, (x - body.x - GUTTER) / CELL);
            midpoint = (((first.bounds.y - body.y) / ROW * columns + (first.bounds.x - body.x - GUTTER) / CELL) +
                    ((last.bounds.y - body.y) / ROW * columns + (last.bounds.x - body.x - GUTTER) / CELL) + 1) / 2.0;
            deadZone = 0.12;
        }
        boolean after = position >= midpoint;
        var previousGroup = previousDrop == null ? null : collection.groupOf(previousDrop.target);
        boolean sameUnit = previousDrop != null && (previousDrop.target.equals(hit.recipe) ||
                wholeGroup && hit.group != null && previousGroup != null && hit.group.equals(previousGroup.id));
        if (sameUnit && Math.abs(position - midpoint) < deadZone) after = previousDrop.after;
        return new Drop(hit.recipe, after, marker(hit.recipe, after, wholeGroup));
    }
    public Rect marker(UUID target, boolean after, boolean wholeGroup) {
        var targetGroup = collection.groupOf(target);
        var unit = cells.stream().filter(c -> wholeGroup && targetGroup != null ? targetGroup.id.equals(c.group) : target.equals(c.recipe)).toList();
        if (unit.isEmpty()) return null;
        var first = unit.get(0); var last = unit.get(unit.size() - 1);
        boolean vertical = wholeGroup || (targetGroup == null ? collection.mode : targetGroup.mode) == BookmarkCollection.Mode.TODO;
        if (vertical) return new Rect(body.x + GUTTER, after ? last.bounds.y + ROW - 1 : first.bounds.y, columns * CELL, 1);
        var filled = unit.stream().filter(c -> !c.blank()).toList();
        var cell = after ? filled.get(filled.size() - 1) : filled.get(0);
        return new Rect(after ? cell.bounds.x + CELL - 1 : cell.bounds.x, cell.bounds.y, 1, ROW);
    }
}

