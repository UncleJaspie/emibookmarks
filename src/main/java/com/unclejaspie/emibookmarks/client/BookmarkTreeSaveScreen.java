package com.unclejaspie.emibookmarks.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BookmarkTreeSaveScreen extends Screen {
    private final Screen parent; private final int count; private final Runnable update, copy;
    public BookmarkTreeSaveScreen(Screen parent, int count, Runnable update, Runnable copy) {
        super(Component.literal("Save edited recipe tree")); this.parent = parent; this.count = count; this.update = update; this.copy = copy;
    }
    @Override protected void init() {
        int x = (width - 240) / 2, y = height / 2;
        addRenderableWidget(Button.builder(Component.literal("Update saved tree"), b -> { update.run(); onClose(); }).bounds(x, y, 240, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save as a new tree"), b -> { copy.run(); onClose(); }).bounds(x, y + 24, 240, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x, y + 48, 240, 20).build());
    }
    @Override public void render(GuiGraphics g, int x, int y, float tick) {
        renderBackground(g); super.render(g, x, y, tick); g.drawCenteredString(font, title, width / 2, height / 2 - 48, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal(count + " recipes; update replaces this tree's previous routes"), width / 2, height / 2 - 26, 0xCCCCCC);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
