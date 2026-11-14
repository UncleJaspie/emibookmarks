package com.unclejaspie.emibookmarks.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

public final class BookmarkAppearanceScreen extends Screen {
    private final Screen parent;
    public BookmarkAppearanceScreen(Screen parent) { super(Component.literal("Bookmark appearance")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(310, width - 20), x = (width - w) / 2, y = Math.max(32, (height - 178) / 2);
        addRenderableWidget(Button.builder(backgroundText(), b -> {
            BookmarkClientConfig.BACKGROUND.set(BookmarkClientConfig.BACKGROUND.get() == BookmarkClientConfig.Background.EMI
                    ? BookmarkClientConfig.Background.CUSTOM : BookmarkClientConfig.Background.EMI); b.setMessage(backgroundText());
        }).bounds(x, y, w, 20).build());
        slider("Custom sidebar opacity", BookmarkClientConfig.SIDEBAR_OPACITY, x, y + 24, w);
        toggle("Tree shows world", BookmarkClientConfig.TREE_WORLD, x, y + 48, w);
        toggle("Tree background blur", BookmarkClientConfig.TREE_BLUR, x, y + 72, w);
        slider("Tree background darkness", BookmarkClientConfig.TREE_OPACITY, x, y + 96, w);
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, y + 130, w, 20).build());
    }
    private Component backgroundText() { return Component.literal("Sidebar background: " + (BookmarkClientConfig.BACKGROUND.get() == BookmarkClientConfig.Background.EMI ? "Follow EMI" : "Custom")); }
    private void toggle(String label, ForgeConfigSpec.BooleanValue value, int x, int y, int w) {
        addRenderableWidget(Button.builder(Component.literal(label + ": " + (value.get() ? "ON" : "OFF")), b -> {
            value.set(!value.get()); b.setMessage(Component.literal(label + ": " + (value.get() ? "ON" : "OFF")));
        }).bounds(x, y, w, 20).build());
    }
    private void slider(String label, ForgeConfigSpec.DoubleValue setting, int x, int y, int w) {
        addRenderableWidget(new AbstractSliderButton(x, y, w, 20, Component.empty(), setting.get()) {
            { updateMessage(); }
            protected void updateMessage() { setMessage(Component.literal(label + ": " + Math.round(value * 100) + "%")); }
            protected void applyValue() { setting.set(value); }
        });
    }
    @Override public void render(GuiGraphics g, int x, int y, float tick) { renderBackground(g); super.render(g, x, y, tick); g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF); }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
