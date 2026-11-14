package com.unclejaspie.emibookmarks.bookmark;

import java.util.UUID;

/** Per-recipe presentation only: never changes membership, group flags or requested/calculated quantities. */
public final class BookmarkFolding {
    public static boolean available(BookmarkCollection c, BookmarkCollection.Recipe recipe, UUID slotId) {
        if (!recipe.isRecipe() || recipe.icons().stream().noneMatch(s -> s.id.equals(slotId) && s.role == BookmarkSlot.Role.OUTPUT)) return false;
        var group = c.groupOf(recipe.id());
        boolean collapsed = group == null ? c.collapsed : group.collapsed;
        boolean chain = group == null ? c.chain : group.chain;
        var mode = group == null ? c.mode : group.mode;
        // A group already hiding every recipe's inputs has no visible per-recipe expansion destination.
        return !collapsed && !(chain && mode == BookmarkCollection.Mode.COMPACT);
    }
    public static boolean toggle(BookmarkCollection c, BookmarkCollection.Recipe recipe, UUID slotId) {
        if (!available(c, recipe, slotId)) return false;
        recipe.collapsed(!recipe.collapsed()); return true;
    }
}
