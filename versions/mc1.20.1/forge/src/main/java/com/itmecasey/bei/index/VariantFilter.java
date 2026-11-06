package com.itmecasey.bei.index;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/** Picks the variants out of the creative tabs' stacks, free of game types so a unit test can run it. */
public final class VariantFilter {
    private VariantFilter() {
    }

    /**
     * The stacks that are variants (not the plain default item), each kind once, in the order first seen.
     *
     * @param stacks    every stack the creative tabs show, possibly the same one several times
     * @param isVariant whether a stack differs from its item's default stack
     * @param keyOf     what makes two stacks the same variant (the item plus its data)
     */
    public static <S> List<S> select(Collection<S> stacks, Predicate<S> isVariant, Function<S, String> keyOf) {
        Set<String> seen = new HashSet<>();
        List<S> out = new ArrayList<>();
        for (S stack : stacks) {
            if (isVariant.test(stack) && seen.add(keyOf.apply(stack))) {
                out.add(stack);
            }
        }
        return out;
    }
}
