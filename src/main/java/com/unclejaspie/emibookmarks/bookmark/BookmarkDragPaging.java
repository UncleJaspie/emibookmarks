package com.unclejaspie.emibookmarks.bookmark;

/** First page turn requires a real dwell; crossing the panel never immediately jumps a page. */
public final class BookmarkDragPaging {
    private int zone;
    private long next;
    public void reset() { zone = 0; next = 0; }
    public int update(BookmarkLayout layout, double x, double y, long now) {
        int hovered = layout.previous.contains(x, y) ? -1 : layout.next.contains(x, y) ? 1 : 0;
        if (hovered == 0) { reset(); return 0; }
        if (hovered != zone) { zone = hovered; next = now + 450; return 0; }
        if (now < next) return 0;
        next = now + 350; return zone;
    }
}
