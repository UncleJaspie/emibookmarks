package com.unclejaspie.emibookmarks.client.emi;

import com.unclejaspie.emibookmarks.client.BookmarkSidebar;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Explicit capture of this exact recipe, in its own reserved recipe action column. */
public final class BookmarkRecipeButton extends Widget {
    private final EmiRecipe recipe;
    private final Bounds bounds;
    private dev.emi.emi.screen.WidgetGroup group;
    private int primary = -1;
    private dev.emi.emi.api.stack.EmiIngredient hovered = EmiStack.EMPTY;
    public BookmarkRecipeButton(EmiRecipe recipe, int x, int y, dev.emi.emi.screen.WidgetGroup group) {
        this(recipe, x, y); this.group = group;
    }
    public BookmarkRecipeButton(EmiRecipe recipe, int x, int y) { this.recipe = recipe; this.bounds = new Bounds(x, y, 13, 13); }
    @Override public Bounds getBounds() { return bounds; }
    public void observeHover(int mx, int my) {
        if (group != null) for (var slot : BookmarkRecipeContext.slots(group)) {
            if (slot.getBounds().contains(mx, my)) {
                int index = BookmarkRecipeContext.outputIndex(group, slot);
                if (index >= 0) { primary = index; hovered = slot.getStack(); }
            }
        }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        observeHover(mx, my);
        int x = bounds.x(), y = bounds.y();
        // EMI's blank 12px recipe-button tile supplies its normal/hover bevel.
        g.blit(dev.emi.emi.EmiRenderHelper.BUTTONS, x, y, 13, 13, 72, bounds.contains(mx, my) ? 12 : 0, 12, 12, 256, 256);
        g.fill(x + 4, y + 2, x + 9, y + 10, 0xFFECC65C);
        g.fill(x + 4, y + 2, x + 9, y + 3, 0xFFFFE6A0);
        g.fill(x + 8, y + 3, x + 9, y + 10, 0xFF9D6D24);
        g.fill(x + 5, y + 9, x + 8, y + 11, 0xFF444444);
    }
    @Override public List<ClientTooltipComponent> getTooltip(int x, int y) {
        var lines = com.unclejaspie.emibookmarks.client.BookmarkHelp.captureLines();
        return lines.stream().map(line -> ClientTooltipComponent.create(line.getVisualOrderText())).toList();
    }
    @Override public boolean mouseClicked(int x, int y, int button) {
        return mouseClicked(x, y, button, net.minecraft.client.gui.screens.Screen.hasControlDown());
    }
    private boolean mouseClicked(int x, int y, int button, boolean counted) {
        if (button != 0 || !bounds.contains(x, y)) return false;
        BookmarkSidebar.captureRecipe(recipe, primary, hovered, counted); return true;
    }
}

