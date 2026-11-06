package com.itmecasey.bei.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SearchListenersTest {
    @Test
    void listenersHearTheNewText() {
        SearchListeners listeners = new SearchListeners();
        List<String> heard = new ArrayList<>();
        listeners.add(heard::add);
        listeners.fire("iron");
        listeners.fire("gold");
        assertEquals(List.of("iron", "gold"), heard);
    }

    @Test
    void aRemovedListenerIsNotCalled() {
        SearchListeners listeners = new SearchListeners();
        List<String> heard = new ArrayList<>();
        Consumer<String> listener = heard::add;
        listeners.add(listener);
        listeners.remove(listener);
        listeners.fire("iron");
        assertEquals(List.of(), heard);
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheOthers() {
        SearchListeners listeners = new SearchListeners();
        List<String> heard = new ArrayList<>();
        listeners.add(text -> {
            throw new IllegalStateException("broken");
        });
        listeners.add(heard::add);
        listeners.fire("iron");
        assertEquals(List.of("iron"), heard);
    }
}
