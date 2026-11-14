package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.BookmarkPages;
import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.com.unascribed.qdcss.QDCSS;
import java.lang.reflect.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = EmiConfig.class, remap = false)
public abstract class EmiPageConfigMixin {
    @Inject(method = "assignField", at = @At("HEAD"), cancellable = true)
    private static void bookmarks$read(QDCSS css, String annotation, Field field, CallbackInfo ci) throws IllegalAccessException {
        if (field.getType() != SidebarPages.class) return;
        String text = css.get(annotation).get();
        if (BookmarkPages.containsToken(text)) { BookmarkPages.parse((SidebarPages) field.get(null), text); ci.cancel(); }
    }
    @Inject(method = "writeField", at = @At("HEAD"), cancellable = true)
    private static void bookmarks$write(String key, Field field, CallbackInfoReturnable<String> cir) throws IllegalAccessException {
        if (field.getType() == SidebarPages.class && BookmarkPages.contains((SidebarPages) field.get(null)))
            cir.setReturnValue("\t" + key + ": " + BookmarkPages.serialize((SidebarPages) field.get(null)) + ";\n");
    }
}

