package com.itmecasey.bei.project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Adds up what storage sources report, free of game types so a unit test can run it. */
public final class StorageTotals<K> {
    private final Map<K, Integer> totals = new HashMap<>();

    /** Adds {@code count} of {@code key}; the total is capped at {@link Integer#MAX_VALUE}, zero and negative counts are ignored. */
    public void add(K key, long count) {
        if (count > 0) {
            totals.merge(key, (int) Math.min(count, Integer.MAX_VALUE), (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
        }
    }

    /** The totals so far. */
    public Map<K, Integer> result() {
        return totals;
    }

    /**
     * The combined totals of every source. A source that throws counts nothing (its partial counts are dropped) and
     * is reported to {@code onFailure}; the others still count.
     */
    public static <K, S> Map<K, Integer> collect(List<S> sources, BiConsumer<S, StorageTotals<K>> counter,
                                                 Consumer<Throwable> onFailure) {
        StorageTotals<K> all = new StorageTotals<>();
        for (S source : sources) {
            StorageTotals<K> mine = new StorageTotals<>();
            try {
                counter.accept(source, mine);
            } catch (RuntimeException | LinkageError e) {
                onFailure.accept(e);
                continue;
            }
            mine.totals.forEach(all::add);
        }
        return all.totals;
    }

    /** A list of sources in which a source with an id already there replaces it (keeping its place). */
    public static final class Registry<S> {
        private final List<S> sources = new ArrayList<>();
        private final Function<S, String> idOf;

        public Registry(Function<S, String> idOf) {
            this.idOf = idOf;
        }

        /** Adds a source, or replaces the one with the same id. */
        public synchronized void register(S source) {
            String id = idOf.apply(source);
            for (int i = 0; i < sources.size(); i++) {
                if (idOf.apply(sources.get(i)).equals(id)) {
                    sources.set(i, source);
                    return;
                }
            }
            sources.add(source);
        }

        /** A copy of the sources, in registration order. */
        public synchronized List<S> snapshot() {
            return List.copyOf(sources);
        }
    }
}
