package com.unclejaspie.emibookmarks.client.emi;

import com.unclejaspie.emibookmarks.bookmark.*;
import com.unclejaspie.emibookmarks.client.BookmarkSidebar;
import dev.emi.emi.config.*;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

public final class BookmarkDock {
    private static final SidebarSide[] SIDES = {SidebarSide.LEFT, SidebarSide.RIGHT, SidebarSide.TOP, SidebarSide.BOTTOM};
    private static final Map<SidebarSide, Integer> RETURN_PAGE = new EnumMap<>(SidebarSide.class);
    public static boolean owns(EmiScreenManager.SidebarPanel panel) {
        return panel.sidebarPage >= 0 && panel.sidebarPage < panel.pages.pages.size() &&
                BookmarkPage.isOurs(panel.pages.pages.get(panel.sidebarPage));
    }
    public static BookmarkLayout layout(EmiScreenManager.SidebarPanel panel, int page) {
        if (!panel.isVisible() || !owns(panel) || panel.space == null) return null;
        var space = panel.space;
        List<BookmarkLayout.Rect> rows = new ArrayList<>();
        // A header-less EMI panel needs one row for its native page selector.
        for (int row = panel.header ? 0 : 1; row < space.th; row++)
            rows.add(new BookmarkLayout.Rect(space.getX(0, row), space.getY(0, row), space.getWidth(row) * 18, 18));
        var fit = BookmarkDockGeometry.fit(rows);
        return fit == null ? null : new BookmarkLayout(BookmarkSidebar.displayCollection(),
                fit.x(), fit.y(), fit.columns(), fit.rows(), page, com.unclejaspie.emibookmarks.client.BookmarkClientConfig.COMBINE_INPUTS.get());
    }
    public static BookmarkLayout layout(Screen screen, int page) {
        if (EmiScreenBase.of(screen).isEmpty() || EmiScreenManager.isDisabled()) return null;
        for (var side : SIDES) {
            var panel = EmiScreenManager.getPanelFor(side);
            if (panel != null && owns(panel)) {
                var layout = layout(panel, page);
                if (layout != null) return layout;
            }
        }
        return null;
    }
    public static BookmarkLayout layoutAt(Screen screen, int page, double x, double y) {
        if (EmiScreenBase.of(screen).isEmpty() || EmiScreenManager.isDisabled()) return null;
        for (var side : SIDES) {
            var panel = EmiScreenManager.getPanelFor(side);
            if (panel == null || !owns(panel)) continue;
            var layout = layout(panel, page);
            if (layout != null && layout.bounds.contains(x, y)) return layout;
        }
        return null;
    }
    public static void show() {
        EmiScreenManager.SidebarPanel selected = null;
        int index = -1;
        for (var side : SIDES) {
            var panel = EmiScreenManager.getPanelFor(side);
            if (panel == null) continue;
            for (int i = 0; i < panel.pages.pages.size(); i++) if (BookmarkPage.isOurs(panel.pages.pages.get(i))) {
                selected = panel; index = i; break;
            }
            if (selected != null) break;
        }
        if (selected == null) {
            selected = EmiScreenManager.getPanelFor(SidebarSide.LEFT);
            if (selected == null) return;
            index = selected.pages.pages.size();
            selected.pages.pages.add(new BookmarkPage());
            selected.pages.unique();
            EmiConfig.writeConfig();
        }
        if (!owns(selected)) RETURN_PAGE.put(selected.side, selected.sidebarPage);
        selected.setSidebarPage(index); EmiScreenManager.forceRecalculate();
    }
    public static void toggle() {
        for (var side : SIDES) {
            var panel = EmiScreenManager.getPanelFor(side);
            if (panel != null && owns(panel)) {
                int target = RETURN_PAGE.getOrDefault(side, 0);
                if (target >= panel.pages.pages.size() || BookmarkPage.isOurs(panel.pages.pages.get(target))) {
                    target = -1;
                    for (int i = 0; i < panel.pages.pages.size(); i++) if (!BookmarkPage.isOurs(panel.pages.pages.get(i))) { target = i; break; }
                }
                if (target >= 0) panel.setSidebarPage(target);
                return;
            }
        }
        show();
    }
    public static void render(EmiScreenManager.SidebarPanel panel, EmiDrawContext context, int mouseX, int mouseY, float delta) {
        if (!panel.isVisible() || panel.space == null) return;
        var raw = context.raw(); var font = Minecraft.getInstance().font;
        // Keep EMI's native type-cycle button; content pagination belongs to the bookmark model.
        panel.cycle.visible = true;
        if (!panel.header) { panel.cycle.setX(panel.space.tx); panel.cycle.setY(panel.space.ty); }
        var layout = layout(panel, BookmarkSidebar.page());
        if (layout != null) BookmarkSidebar.renderDocked(layout, raw, Minecraft.getInstance().screen, mouseX, mouseY, delta);
        raw.pose().pushPose(); raw.pose().translate(0, 0, 400);
        panel.cycle.render(raw, mouseX, mouseY, delta);
        int x = panel.cycle.getX() + 19, y = panel.cycle.getY() + 5;
        raw.drawString(font, font.plainSubstrByWidth(nameText(), Math.max(0, panel.space.tx + panel.space.tw * 18 - x)), x, y, 0xDDDDDD);
        if (layout == null) raw.drawString(font, font.plainSubstrByWidth("Increase sidebar size", panel.space.tw * 18),
                panel.space.tx, panel.space.ty + 20, 0xCCCCCC);
        raw.pose().popPose();
        // Native subpanels remain independent and keep their own renderers.
        for (var subpanel : panel.getSpaces()) if (subpanel != panel.space) subpanel.render(context, mouseX, mouseY, delta, 0);
    }
    private static String nameText() { return BookmarkPages.name().getString(); }
}

