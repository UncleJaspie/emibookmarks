package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.bookmark.BookmarkSlot;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.client.Minecraft;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/** Per-icon stock comparison, not a reservation or an inventory-aware crafting plan. */
public final class BookmarkInventoryView {
    public record Amount(long needed, long remaining) {
        public boolean reduced() { return remaining < needed; }
        public boolean covered() { return needed > 0 && remaining == 0; }
    }
    private final Map<EmiStack, Long> stock = new HashMap<>();

    public BookmarkInventoryView() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        // Read the player's slots only: do not mistake an open machine/chest for owned stock.
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var item = inventory.getItem(i);
            if (item.isEmpty()) continue;
            var key = EmiStack.of(item).comparison(EmiPort.compareStrict());
            stock.merge(key, (long) item.getCount(), Long::sum);
        }
    }

    public Amount amount(BookmarkSlot slot, EmiIngredient ingredient, long needed) {
        if (!slot.variant().unit().equals(BookmarkSlot.Unit.ITEM)) return new Amount(needed, needed);
        long remaining = needed;
        var seen = new HashSet<EmiStack>();
        for (var option : ingredient.getEmiStacks()) {
            var key = option.copy().comparison(EmiPort.compareStrict());
            if (!seen.add(key)) continue;
            long owned = stock.getOrDefault(key, 0L);
            remaining -= Math.min(remaining, owned);
            if (remaining == 0) break;
        }
        return new Amount(needed, remaining);
    }
}
