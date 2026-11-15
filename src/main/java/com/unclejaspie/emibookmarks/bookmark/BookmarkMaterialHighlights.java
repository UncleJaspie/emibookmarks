package com.unclejaspie.emibookmarks.bookmark;

import java.util.*;

/** Pixel-aligned diagonal bands: x+y is constant from bottom-left to top-right. */
public final class BookmarkMaterialHighlights {
    public static final int INPUT = 0x79AE8B, OUTPUT = 0x7A9EBB;
    public record Span(int x, int y, int width, int alpha) {}
    public static final List<Span> STRIPES;
    static {
        var spans = new ArrayList<Span>();
        for (int y=0; y<16; y++) for (int x=0; x<16;) {
            int width = Math.min(16-x, 3-Math.floorMod(x+y,3));
            spans.add(new Span(x,y,width,alpha(x,y))); x+=width;
        }
        STRIPES=List.copyOf(spans);
    }
    public static int alpha(int x,int y) { return Math.floorMod(x+y,6)<3 ? 0x66 : 0x22; }
    public static int color(BookmarkChain.Kind kind) {
        return switch(kind) {
            case BASE_INPUT -> INPUT;
            case RESULT -> OUTPUT;
            case REMAINDER, CHANCE, INTERMEDIATE -> 0;
        };
    }
    /** Loose item goals are not recipe outputs. Shade them only when they remain external requirements. */
    public static int color(BookmarkSlot slot, BookmarkChain.Result calculation) {
        if (slot.role != BookmarkSlot.Role.ITEM) return color(calculation.kinds().get(slot.id));
        try {
            var key = BookmarkChain.resource(slot.variant());
            return calculation.inputs().stream().anyMatch(m -> m.amount()>0 && m.resource().equals(key)) ? INPUT : 0;
        } catch (RuntimeException unsupported) { return 0; }
    }
}
