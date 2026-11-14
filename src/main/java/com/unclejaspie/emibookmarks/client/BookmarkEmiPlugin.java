package com.unclejaspie.emibookmarks.client;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

/** Registers resource interaction; the pinned page adapter uses EMI's allocated sidebar geometry. */
@EmiEntrypoint
public final class BookmarkEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        com.unclejaspie.emibookmarks.bookmark.BookmarkChains.validator(BookmarkCapture::calculationIssue);
        BookmarkCapture.clearCache();
        registry.addGenericStackProvider(BookmarkSidebar::stackAt);
    }
}

