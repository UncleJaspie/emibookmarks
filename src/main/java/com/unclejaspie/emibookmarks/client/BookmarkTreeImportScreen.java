package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.bookmark.BookmarkTreeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;

public final class BookmarkTreeImportScreen extends Screen {
    private final Screen parent; private EditBox path; private Button confirm;
    private BookmarkTreeData.Tree staged; private String status = "Paste the path to a shared .emibookmarks.json file";
    public BookmarkTreeImportScreen(Screen parent) { super(Component.literal("Import shared recipe tree")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(360, width - 20), x = (width - w) / 2, y = height / 2 - 34;
        path = new EditBox(font, x, y, w, 20, Component.literal("Tree file path")); path.setMaxLength(4096);
        path.setResponder(value -> { staged = null; if (confirm != null) confirm.active = false; });
        addRenderableWidget(path);
        addRenderableWidget(Button.builder(Component.literal("Read file / preview"), b -> load()).bounds(x, y + 26, w, 20).build());
        confirm = addRenderableWidget(Button.builder(Component.literal("Import as a new tree"), b -> {
            if (staged != null) { BookmarkTreeActions.installShared(staged); onClose(); }
        }).bounds(x, y + 78, w, 20).build()); confirm.active = false;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x, y + 102, w, 20).build());
        setInitialFocus(path);
    }
    private void load() {
        try {
            String value = path.getValue().strip(); if (value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length() - 1);
            var file = Path.of(value); if (!file.isAbsolute()) file = minecraft.gameDirectory.toPath().resolve(file);
            staged = BookmarkTreeSharing.load(file);
            var goal = staged.goal(); var primary = goal.icons().stream().filter(s -> s.id.equals(goal.primaryOutput())).findFirst().orElseThrow();
            long missing = staged.recipes().stream().filter(r -> r.isRecipe() && BookmarkCapture.resolve(r) == null).count();
            status = primary.variant().label() + ": " + staged.recipes().size() + " recipes" + (missing > 0 ? "; " + missing + " unavailable routes retained" : ""); confirm.active = true;
        } catch (Exception | LinkageError failure) { staged = null; confirm.active = false; status = "Cannot import: " + failure.getMessage(); }
    }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        renderBackground(g); super.render(g, x, y, tick); g.drawCenteredString(font, title, width / 2, height / 2 - 66, 0xFFFFFF);
        g.drawCenteredString(font, font.plainSubstrByWidth(status, Math.max(0, width - 20)), width / 2, height / 2 + 22, 0xCCCCCC);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
