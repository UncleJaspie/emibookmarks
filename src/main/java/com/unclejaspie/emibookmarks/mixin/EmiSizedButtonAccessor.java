package com.unclejaspie.emibookmarks.mixin;

import dev.emi.emi.screen.widget.SizedButtonWidget;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SizedButtonWidget.class, remap = false)
public interface EmiSizedButtonAccessor {
    @Accessor("text") Supplier<List<Component>> bookmarks$text();
    @Accessor("text") void bookmarks$text(Supplier<List<Component>> text);
}
