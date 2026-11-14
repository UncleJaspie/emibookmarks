package com.unclejaspie.emibookmarks.bookmark;

import java.util.UUID;

/** Resolves click versus whole-recipe drag; no Minecraft or ingredient-equality dependencies. */
public final class BookmarkGestures {
    private int button, first, last, anchorLast, targetFirst;
    private UUID originGroup;
    private boolean active, dragging, move, alt;
    private long started;
    public boolean active() { return active; }
    public int first() { return Math.min(first, targetFirst); }
    public int last() { return Math.max(anchorLast, last); }
    public boolean dragging() { return dragging; }

    public boolean press(BookmarkCollection collection, BookmarkLayout layout, double x, double y,
                         int button, boolean shift, boolean alt, long now) {
        var row = layout.gutter(x, y);
        if (row == null || (button != 0 && button != 1)) return false;
        active = true; dragging = false; this.button = button;
        first = row.firstRecipe(); targetFirst = first;
        last = row.lastRecipe(); anchorLast = last; originGroup = row.group();
        move = shift && button == 0 && originGroup != null;
        this.alt = alt; started = now;
        return true;
    }
    public void update(BookmarkLayout layout, double x, double y, long now) {
        if (!active) return;
        var row = layout.row(x, y);
        if (row == null) return;
        if (row.firstRecipe() != first || now - started >= 250) dragging = true;
        last = row.lastRecipe();
        targetFirst = row.firstRecipe();
    }
    public boolean release(BookmarkCollection collection, BookmarkLayout layout, double x, double y,
                           int releasedButton, long now) {
        if (!active || releasedButton != button) return false;
        if (!layout.body.contains(x, y)) { cancel(); return true; }
        update(layout, x, y, now);
        var group = collection.groupById(originGroup);
        if (dragging) {
            int low = first(), high = last();
            // A collapsed group represents all its hidden recipes.
            for (int i = low; i <= high; i++) {
                var memberGroup = collection.groupOf(collection.recipes.get(i).id());
                if (memberGroup != null && memberGroup.collapsed) {
                    for (int j = 0; j < collection.recipes.size(); j++) {
                        if (memberGroup.members.contains(collection.recipes.get(j).id())) {
                            low = Math.min(low, j); high = Math.max(high, j);
                        }
                    }
                }
            }
            if (move) collection.move(group, targetFirst);
            else if (button == 1) collection.exclude(low, high);
            else collection.group(low, high, group);
        } else if (layout.gutter(x, y) != null && group != null) {
            if (button == 1) group.chain = !group.chain;
            else if (alt) group.collapsed = !group.collapsed;
            else group.mode = group.mode == BookmarkCollection.Mode.TODO ? BookmarkCollection.Mode.COMPACT : BookmarkCollection.Mode.TODO;
        }
        cancel();
        return true;
    }
    public void cancel() { active = false; dragging = false; }
}

