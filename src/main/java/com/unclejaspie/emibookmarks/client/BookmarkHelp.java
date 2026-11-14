package com.unclejaspie.emibookmarks.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import java.util.List;

/** Only advertised inside the chosen recipe viewer; the item index cannot capture routes. */
public final class BookmarkHelp {
    public static String key() { return BookmarkKeys.BOOKMARK.getTranslatedKeyMessage().getString(); }
    public static Component hint() {
        return Component.empty().append(Component.literal("Hold ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("ALT").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" for hotkeys").withStyle(ChatFormatting.DARK_GRAY));
    }
    public static Component title() { return Component.literal("Bookmark hotkeys").withStyle(ChatFormatting.GRAY); }
    public static Component action(String key, String action) {
        return Component.empty().append(Component.literal(key).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("  " + action).withStyle(ChatFormatting.GRAY));
    }
    public static List<Component> viewerLines() {
        if (!Screen.hasAltDown()) return List.of(hint());
        return List.of(title(), action(key(), "Bookmark item"), action("Ctrl + " + key(), "Bookmark item with count"),
                action("Shift + " + key(), "Toggle recipe bookmark"), action("Ctrl + Shift + " + key(), "Toggle recipe with quantities"));
    }
    public static List<Component> captureLines() {
        if (!Screen.hasAltDown()) return List.of(hint());
        return List.of(title(), action("LMB", "Toggle recipe bookmark"), action("Ctrl + LMB", "Toggle recipe with quantities"),
                action("Shift + " + key(), "Toggle recipe bookmark"), action("Ctrl + Shift + " + key(), "Toggle recipe with quantities"));
    }
    /** EMI's slot hook accepts one component; delegate ordinary Minecraft text rows without a custom font. */
    public static ClientTooltipComponent viewerTooltip() {
        var lines = viewerLines().stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
        return new ClientTooltipComponent() {
            public int getHeight() { return lines.stream().mapToInt(ClientTooltipComponent::getHeight).sum(); }
            public int getWidth(Font font) { return lines.stream().mapToInt(line -> line.getWidth(font)).max().orElse(0); }
            public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
                for (var line : lines) { line.renderText(font, x, y, matrix, buffer); y += line.getHeight(); }
            }
        };
    }
}
