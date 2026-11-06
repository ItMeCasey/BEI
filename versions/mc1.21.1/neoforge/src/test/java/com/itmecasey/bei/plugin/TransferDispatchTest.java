package com.itmecasey.bei.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.itmecasey.bei.api.TransferState;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransferDispatchTest {
    private static Class<?> classOf(Class<?> type) {
        return type;
    }

    @Test
    void noHandlerMatchesAnUnrelatedMenu() {
        assertNull(TransferDispatch.find(List.<Class<?>>of(String.class), 42, TransferDispatchTest::classOf));
    }

    @Test
    void aHandlerAlsoHandlesSubclasses() {
        assertEquals(List.class, TransferDispatch.find(List.<Class<?>>of(List.class), new ArrayList<String>(), TransferDispatchTest::classOf));
    }

    @Test
    void theMostSpecificClassWinsWhateverTheOrder() {
        List<Class<?>> general = List.of(List.class, ArrayList.class);
        List<Class<?>> reversed = List.of(ArrayList.class, List.class);
        assertEquals(ArrayList.class, TransferDispatch.find(general, new ArrayList<String>(), TransferDispatchTest::classOf));
        assertEquals(ArrayList.class, TransferDispatch.find(reversed, new ArrayList<String>(), TransferDispatchTest::classOf));
    }

    @Test
    void siblingsKeepRegistrationOrder() {
        List<Class<?>> handlers = List.of(LinkedList.class, ArrayList.class);
        assertEquals(LinkedList.class, TransferDispatch.find(handlers, new LinkedList<String>(), TransferDispatchTest::classOf));
        List<Class<?>> twice = List.of(ArrayList.class, ArrayList.class);
        assertEquals(ArrayList.class, TransferDispatch.find(twice, new ArrayList<String>(), TransferDispatchTest::classOf));
    }

    @Test
    void anEnabledHandlerShowsItsButton() {
        assertEquals(TransferDispatch.Mode.HANDLER, TransferDispatch.resolve(TransferState.Kind.ENABLED, false));
        assertEquals(TransferDispatch.Mode.HANDLER, TransferDispatch.resolve(TransferState.Kind.ENABLED, true));
    }

    @Test
    void aDisabledHandlerShowsADimmedButtonEvenIfBuiltInFillWouldWork() {
        assertEquals(TransferDispatch.Mode.DISABLED, TransferDispatch.resolve(TransferState.Kind.DISABLED, true));
    }

    @Test
    void aHiddenHandlerOrNoHandlerFallsBackToBuiltInFill() {
        assertEquals(TransferDispatch.Mode.BUILT_IN, TransferDispatch.resolve(TransferState.Kind.HIDDEN, true));
        assertEquals(TransferDispatch.Mode.BUILT_IN, TransferDispatch.resolve(null, true));
        assertEquals(TransferDispatch.Mode.HIDDEN, TransferDispatch.resolve(TransferState.Kind.HIDDEN, false));
        assertEquals(TransferDispatch.Mode.HIDDEN, TransferDispatch.resolve(null, false));
    }
}
