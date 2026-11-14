package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.*;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager.SidebarPanel;
import dev.emi.emi.screen.widget.SidebarButtonWidget;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = SidebarButtonWidget.class, remap = false)
public abstract class EmiSidebarButtonMixin {
    @Shadow @Final private SidebarPanel panel;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void bookmarks$tooltip(int x, int y, int width, int height, SidebarPanel panel, CallbackInfo ci) {
        var access = (EmiSizedButtonAccessor) this;
        var original = access.bookmarks$text();
        access.bookmarks$text(() -> BookmarkDock.owns(panel) ? List.of(BookmarkPages.name(),
                Component.translatable("emibookmarks.sidebar.description"),
                Component.literal("Click: switch sidebar page | Shift-click: previous")) : original.get());
    }
    @Inject(method = "getU", at = @At("HEAD"), cancellable = true)
    private void bookmarks$icon(int x, int y, CallbackInfoReturnable<Integer> cir) {
        if (BookmarkDock.owns(panel)) cir.setReturnValue(SidebarType.FAVORITES.u);
    }
}

