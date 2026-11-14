package com.unclejaspie.emibookmarks.client.emi;

import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarType;

/** Custom identity at the page-object layer. The enum boundary always sees the existing EMPTY. */
public final class BookmarkPage extends SidebarPages.SidebarPage {
    public static final String ID = "grouped-bookmarks";
    public BookmarkPage() { super(SidebarType.EMPTY); }
    public static boolean isOurs(SidebarPages.SidebarPage page) {
        return page instanceof BookmarkPage && page.type == SidebarType.EMPTY;
    }
}

