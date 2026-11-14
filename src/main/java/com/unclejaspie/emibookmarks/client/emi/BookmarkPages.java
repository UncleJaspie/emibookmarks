package com.unclejaspie.emibookmarks.client.emi;

import dev.emi.emi.config.*;
import dev.emi.emi.screen.ConfigEnumScreen;
import dev.emi.emi.screen.ConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Adds an ID-based choice to EMI's own page picker without extending SidebarType. */
public final class BookmarkPages {
    public static Component name() { return Component.translatable("emibookmarks.sidebar.name"); }
    public static boolean contains(SidebarPages pages) { return pages.pages.stream().anyMatch(BookmarkPage::isOurs); }
    public static boolean containsToken(String text) {
        return Arrays.stream(text.split(",")).anyMatch(s -> s.strip().equalsIgnoreCase(BookmarkPage.ID));
    }
    public static String serialize(SidebarPages pages) {
        return pages.pages.isEmpty() ? "none" : String.join(", ", pages.pages.stream()
                .map(p -> BookmarkPage.isOurs(p) ? BookmarkPage.ID : p.type.getName()).toList());
    }
    public static void parse(SidebarPages pages, String text) {
        var parsed = new ArrayList<SidebarPages.SidebarPage>();
        for (String token : text.split(",")) {
            String name = token.strip().toLowerCase(Locale.ROOT);
            parsed.add(name.equals(BookmarkPage.ID) ? new BookmarkPage() : new SidebarPages.SidebarPage(SidebarType.fromName(name)));
        }
        pages.pages.clear(); pages.pages.addAll(parsed); unique(pages);
    }
    public static void unique(SidebarPages pages) {
        // Page identity differs from the transport enum. Empty and Grouped Bookmarks may coexist.
        Set<Object> seen = new HashSet<>();
        pages.pages.removeIf(p -> p.type == SidebarType.NONE || !seen.add(BookmarkPage.isOurs(p) ? BookmarkPage.ID : p.type));
        var subpanels = pages.settings == null ? null : pages.settings.subpanels();
        if (subpanels != null) subpanels.subpanels.removeIf(p -> p.type == SidebarType.NONE || !seen.add(p.type));
    }
    private record Choice(SidebarType nativeType, boolean bookmarks) {}
    public static void choose(SidebarPages pages, int index) {
        var client = Minecraft.getInstance();
        if (!(client.screen instanceof ConfigScreen parent)) return;
        List<ConfigEnumScreen.Entry<Choice>> options = new ArrayList<>();
        options.add(new ConfigEnumScreen.Entry<>(new Choice(null, true), name(), List.of()));
        for (var type : SidebarType.values()) {
            if (type == SidebarType.CHESS && !pages.canShowChess()) continue;
            options.add(new ConfigEnumScreen.Entry<>(new Choice(type, false), type.getText(), List.of()));
        }
        client.setScreen(new ConfigEnumScreen<>(parent, options, choice -> {
            SidebarPages.SidebarPage page = choice.bookmarks ? new BookmarkPage() : new SidebarPages.SidebarPage(choice.nativeType);
            if (index < 0) pages.pages.add(page); else if (index < pages.pages.size()) pages.pages.set(index, page);
            pages.unique();
        }));
    }
}

