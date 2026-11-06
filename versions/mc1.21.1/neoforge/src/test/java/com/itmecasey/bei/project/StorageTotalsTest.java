package com.itmecasey.bei.project;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageTotalsTest {
    /** A fake source: an id and what it holds. */
    private record Source(String id, Map<String, Long> items, boolean fails) {
    }

    private static Map<String, Integer> run(List<Source> sources, List<Throwable> failures) {
        return StorageTotals.collect(sources, (source, totals) -> {
            source.items().forEach(totals::add);
            if (source.fails()) {
                throw new IllegalStateException("broken");
            }
        }, failures::add);
    }

    @Test
    void sourcesAreAddedTogether() {
        Map<String, Integer> totals = run(List.of(new Source("a", Map.of("iron", 5L, "gold", 1L), false),
                new Source("b", Map.of("iron", 7L), false)), new ArrayList<>());
        assertEquals(Map.of("iron", 12, "gold", 1), totals);
    }

    @Test
    void aTotalIsCappedAtIntegerMax() {
        Map<String, Integer> totals = run(List.of(new Source("a", Map.of("iron", 4_000_000_000L), false),
                new Source("b", Map.of("iron", 5L), false)), new ArrayList<>());
        assertEquals(Integer.MAX_VALUE, totals.get("iron"));
    }

    @Test
    void zeroAndNegativeCountsAreIgnored() {
        Map<String, Integer> totals = run(List.of(new Source("a", Map.of("iron", 0L, "gold", -3L), false)), new ArrayList<>());
        assertEquals(Map.of(), totals);
    }

    @Test
    void aSourceThatThrowsCountsNothingAndTheOthersStillCount() {
        List<Throwable> failures = new ArrayList<>();
        Map<String, Integer> totals = run(List.of(new Source("bad", Map.of("iron", 100L), true),
                new Source("good", Map.of("iron", 2L), false)), failures);
        assertEquals(Map.of("iron", 2), totals);
        assertEquals(1, failures.size());
    }

    @Test
    void aSourceWithTheSameIdReplacesTheEarlierOneInPlace() {
        StorageTotals.Registry<Source> registry = new StorageTotals.Registry<>(Source::id);
        registry.register(new Source("a", Map.of("iron", 1L), false));
        registry.register(new Source("b", Map.of("iron", 2L), false));
        registry.register(new Source("a", Map.of("gold", 3L), false));
        List<Source> sources = registry.snapshot();
        assertEquals(2, sources.size());
        assertEquals(Map.of("gold", 3L), sources.get(0).items());
        assertEquals("b", sources.get(1).id());
    }
}
