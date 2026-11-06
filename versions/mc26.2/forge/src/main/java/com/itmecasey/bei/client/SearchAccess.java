package com.itmecasey.bei.client;

import com.itmecasey.bei.api.ISearchAccess;
import java.util.function.Consumer;

/** The search box as plugins see it; see {@link ISearchAccess}. */
public final class SearchAccess implements ISearchAccess {
    @Override
    public String getText() {
        return BrowserPanel.searchText();
    }

    @Override
    public void setText(String text) {
        BrowserPanel.setSearchText(text);
    }

    @Override
    public void addListener(Consumer<String> listener) {
        BrowserPanel.searchListeners().add(listener);
    }

    @Override
    public void removeListener(Consumer<String> listener) {
        BrowserPanel.searchListeners().remove(listener);
    }
}
