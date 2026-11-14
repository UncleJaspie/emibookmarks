package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.JsonParser;
import java.util.Objects;

/** Capture equivalence only; never used to infer group ownership. */
public final class BookmarkIdentity {
    public static boolean sameCapture(BookmarkCollection.Recipe a, BookmarkCollection.Recipe b) {
        // Two ID-less producer routes may have identical visible slots. Never delete one on a guess.
        if (a.isRecipe() && (a.recipeKey() == null || b.recipeKey() == null)) return false;
        if (a.isRecipe() != b.isRecipe() || !Objects.equals(a.recipeKey(), b.recipeKey()) ||
                a.icons().size() != b.icons().size()) return false;
        for (int i = 0; i < a.icons().size(); i++) {
            var x = a.icons().get(i); var y = b.icons().get(i);
            if (x.role != y.role || x.selected() != y.selected() || x.alternatives.size() != y.alternatives.size() ||
                    x.id.equals(a.primaryOutput()) != y.id.equals(b.primaryOutput())) return false;
            for (int j = 0; j < x.alternatives.size(); j++) {
                var v = x.alternatives.get(j); var w = y.alternatives.get(j);
                if (v.amount() != w.amount() || Float.compare(v.chance(), w.chance()) != 0 || !v.unit().equals(w.unit())) return false;
                try {
                    if (!JsonParser.parseString(v.serialized()).equals(JsonParser.parseString(w.serialized()))) return false;
                } catch (RuntimeException opaque) { if (!v.serialized().equals(w.serialized())) return false; }
            }
        }
        return true;
    }
    /** Toggle one exact route/primary/variant snapshot; batch and visibility edits do not defeat removal. */
    public static boolean toggle(BookmarkCollection collection, BookmarkCollection.Recipe capture) {
        for (var existing : collection.recipes) if (sameCapture(existing, capture)) {
            collection.remove(existing.id()); return false;
        }
        collection.recipes.add(capture); return true;
    }
}
