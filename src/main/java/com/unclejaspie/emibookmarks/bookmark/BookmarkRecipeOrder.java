package com.unclejaspie.emibookmarks.bookmark;

import java.util.List;
import java.util.function.Predicate;

/** Selection order shared by the EMI adapter and headless policy checks. */
public final class BookmarkRecipeOrder {
    public static <T> T choose(T manual, List<T> registryOrder, Predicate<T> eligible, Predicate<T> machine) {
        if (manual != null) return manual;
        T first = null;
        for (var recipe : registryOrder) {
            if (!eligible.test(recipe)) continue;
            if (first == null) first = recipe;
            if (machine.test(recipe)) return recipe;
        }
        return first;
    }
}
