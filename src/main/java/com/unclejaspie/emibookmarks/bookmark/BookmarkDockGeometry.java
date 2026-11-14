package com.unclejaspie.emibookmarks.bookmark;

import java.util.List;

/** Largest rectangular bookmark grid inside EMI's row-by-row available main-sidebar region. */
public final class BookmarkDockGeometry {
    public record Fit(int x, int y, int columns, int rows) {}
    public static Fit fit(List<BookmarkLayout.Rect> usableRows) {
        Fit best = null; int area = 0;
        for (int first = 0; first < usableRows.size(); first++) {
            int left = Integer.MIN_VALUE, right = Integer.MAX_VALUE;
            for (int last = first; last < usableRows.size(); last++) {
                var row = usableRows.get(last);
                if (last > first && row.y() != usableRows.get(last - 1).y() + usableRows.get(last - 1).height()) break;
                left = Math.max(left, row.x()); right = Math.min(right, row.x() + row.width());
                int columns = (right - left - BookmarkLayout.GUTTER) / BookmarkLayout.CELL;
                int rows = (row.y() + row.height() - usableRows.get(first).y() - BookmarkLayout.HEADER - BookmarkLayout.FOOTER) / BookmarkLayout.ROW; // One header row and one collection-control row.
                if (columns < 2 || rows < 1) continue;
                if (columns * rows > area) {
                    area = columns * rows;
                    best = new Fit(left, usableRows.get(first).y(), columns, rows);
                }
            }
        }
        return best;
    }
}

