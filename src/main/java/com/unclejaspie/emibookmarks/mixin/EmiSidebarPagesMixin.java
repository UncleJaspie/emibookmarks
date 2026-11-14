package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.BookmarkPages;
import dev.emi.emi.config.SidebarPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SidebarPages.class, remap = false)
public abstract class EmiSidebarPagesMixin {
    @Inject(method = "unique", at = @At("HEAD"), cancellable = true)
    private void bookmarks$unique(CallbackInfo ci) {
        var pages = (SidebarPages) (Object) this;
        if (BookmarkPages.contains(pages)) { BookmarkPages.unique(pages); ci.cancel(); }
    }
}

