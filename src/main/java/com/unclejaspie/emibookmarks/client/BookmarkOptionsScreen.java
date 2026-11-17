package com.unclejaspie.emibookmarks.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class BookmarkOptionsScreen extends Screen {
    private final Screen parent;
    public BookmarkOptionsScreen(Screen parent) { super(Component.translatable("emibookmarks.options.title")); this.parent = parent; }
    private Component valueText() {
        return Component.translatable("emibookmarks.options.combine",
                Component.translatable(BookmarkClientConfig.COMBINE_INPUTS.get() ? "options.on" : "options.off"));
    }
    @Override protected void init() {
        int buttonWidth = Math.min(230, (width - 30) / 2), left = (width - buttonWidth * 2 - 10) / 2;
        int right = left + buttonWidth + 10, top = Math.max(38, (height - 150) / 2);
        addRenderableWidget(Button.builder(valueText(), button -> {
            BookmarkClientConfig.setCombine(!BookmarkClientConfig.COMBINE_INPUTS.get()); button.setMessage(valueText());
        }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("emibookmarks.options.combine.help")))
                .bounds(left, top, buttonWidth, 20).build());
        toggle("Machine category badge", BookmarkClientConfig.MACHINE_BADGE, left, top + 24, buttonWidth);
        toggle("Output/input underlines", BookmarkClientConfig.ROLE_ACCENTS, left, top + 48, buttonWidth);
        toggle("Leading batch multiplier", BookmarkClientConfig.BATCH_LABEL, left, top + 72, buttonWidth);
        toggle("Check for updates", BookmarkClientConfig.CHECK_UPDATES, left, top + 96, buttonWidth);
        addRenderableWidget(Button.builder(Component.literal("Background / opacity..."), b -> minecraft.setScreen(new BookmarkAppearanceScreen(this)))
                .bounds(right, top, buttonWidth, 20).build());
        var export = addRenderableWidget(Button.builder(Component.literal("Export current EMI tree..."), b -> BookmarkTreeSharing.exportCurrent())
                .bounds(right, top + 24, buttonWidth, 20).build()); export.active = dev.emi.emi.bom.BoM.tree != null;
        addRenderableWidget(Button.builder(Component.literal("Import shared tree file..."), b -> minecraft.setScreen(new BookmarkTreeImportScreen(this)))
                .bounds(right, top + 48, buttonWidth, 20).build());
        toggle("Tab material highlights", BookmarkClientConfig.SHIFT_MATERIAL_HIGHLIGHTS, right, top + 72, buttonWidth);
        toggle("Output-aware inventory", BookmarkClientConfig.OUTPUT_AWARE_STOCK, right, top + 96, buttonWidth);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds((width - 160) / 2, height - 35, 160, 20).build());
    }
    private void toggle(String name, net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, int x, int y, int buttonWidth) {
        var builder = Button.builder(Component.literal(name + ": " + (value.get() ? "ON" : "OFF")), b -> {
            value.set(!value.get());
            b.setMessage(Component.literal(name + ": " + (value.get() ? "ON" : "OFF")));
        });
        if (value == BookmarkClientConfig.OUTPUT_AWARE_STOCK) builder.tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Hold Shift to show remaining crafts and inputs using carried outputs. Release to restore your saved plan.")));
        if (value == BookmarkClientConfig.SHIFT_MATERIAL_HIGHLIGHTS) builder.tooltip(net.minecraft.client.gui.components.Tooltip.create(
                BookmarkHelp.materialLegend()));
        if (value == BookmarkClientConfig.CHECK_UPDATES) builder.tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.literal("Quietly check GitHub Releases in the background. Newer compatible versions appear above the inventory's bottom-left controls. No automatic downloads.")));
        addRenderableWidget(builder.bounds(x, y, buttonWidth, 20).build());
    }
    @Override public void render(GuiGraphics g, int x, int y, float delta) {
        renderBackground(g); super.render(g, x, y, delta);
        g.drawCenteredString(font, title, width / 2, 25, 0xFFFFFF);

    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}

