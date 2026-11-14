package com.unclejaspie.emibookmarks.client;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit output-instance choice when the visible recipe did not establish intent. */
public final class BookmarkOutputScreen extends Screen {
    private final Screen parent;
    private final EmiRecipe recipe;
    private final boolean counted;
    private int page;
    private boolean failed;
    public BookmarkOutputScreen(Screen parent, EmiRecipe recipe) {
        this(parent, recipe, Screen.hasControlDown());
    }
    public BookmarkOutputScreen(Screen parent, EmiRecipe recipe, boolean counted) {
        super(Component.literal("Choose the intended output")); this.parent = parent; this.recipe = recipe; this.counted = counted;
    }
    @Override protected void init() {
        try { failed = false; populate(); }
        catch (RuntimeException | LinkageError error) {
            failed = true; clearWidgets();
            com.mojang.logging.LogUtils.getLogger().warn("Recipe output selection unavailable", error);
            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 - 50, height - 45, 100, 20).build());
        }
    }
    private void populate() {
        var outputs = recipe.getOutputs().stream().filter(s -> !s.isEmpty()).toList();
        int size = Math.max(1, (height - 100) / 24);
        int start = page * size;
        for (int i = start; i < Math.min(outputs.size(), start + size); i++) {
            final int index = i;
            var output = outputs.get(i);
            String name = output.getEmiStacks().get(0).getName().getString();
            var resource = output.getEmiStacks().get(0);
            String detail = resource.getTooltipText().stream().map(Component::getString).collect(java.util.stream.Collectors.joining("\n")) +
                    "\n" + com.unclejaspie.emibookmarks.bookmark.BookmarkQuantities.exact(output.getAmount(), BookmarkCapture.unit(output)) + " | Output instance " + (i + 1) + " | NBT: " + resource.getNbt();
            addRenderableWidget(Button.builder(Component.literal((i + 1) + ". " + name + " | " + com.unclejaspie.emibookmarks.bookmark.BookmarkQuantities.compact(output.getAmount(), BookmarkCapture.unit(output)) + " | " + output.getChance()), b -> {
                minecraft.setScreen(parent); BookmarkSidebar.captureRecipe(recipe, index, output, counted);
            }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(detail))).bounds(Math.max(5, (width - 300) / 2), 45 + (i - start) * 24, Math.min(300, width - 10), 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page = Math.max(0, page - 1); rebuildWidgets(); })
                .bounds(width / 2 - 90, height - 45, 35, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(width / 2 - 50, height - 45, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page = Math.min((outputs.size() - 1) / size, page + 1); rebuildWidgets(); })
                .bounds(width / 2 + 55, height - 45, 35, 20).build());
    }
    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        renderBackground(g); super.render(g, x, y, delta); g.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        if (failed) g.drawCenteredString(font, "Could not read this recipe's outputs", width / 2, 55, 0xFF8888);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
