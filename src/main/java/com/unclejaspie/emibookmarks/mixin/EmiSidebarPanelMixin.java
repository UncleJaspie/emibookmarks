package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.BookmarkDock;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.EmiScreenManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EmiScreenManager.SidebarPanel.class, remap = false)
public abstract class EmiSidebarPanelMixin {
    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void bookmarks$background(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (BookmarkDock.owns((EmiScreenManager.SidebarPanel) (Object) this) &&
                com.unclejaspie.emibookmarks.client.BookmarkClientConfig.BACKGROUND.get() ==
                        com.unclejaspie.emibookmarks.client.BookmarkClientConfig.Background.CUSTOM) ci.cancel();
    }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bookmarks$render(EmiDrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var panel = (EmiScreenManager.SidebarPanel) (Object) this;
        if (BookmarkDock.owns(panel)) { BookmarkDock.render(panel, context, mouseX, mouseY, delta); ci.cancel(); }
    }
    @Inject(method = "updateWidgetVisibility", at = @At("TAIL"))
    private void bookmarks$controls(CallbackInfo ci) {
        var panel = (EmiScreenManager.SidebarPanel) (Object) this;
        if (BookmarkDock.owns(panel)) { panel.pageLeft.visible = false; panel.pageRight.visible = false; }
    }
}

