package com.unclejaspie.emibookmarks.mixin;

import com.unclejaspie.emibookmarks.client.emi.*;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.screen.ConfigScreen.Mutator;
import dev.emi.emi.screen.widget.config.SidebarPagesWidget;
import net.minecraft.client.gui.components.Button;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SidebarPagesWidget.class, remap = false)
public abstract class EmiPagePickerMixin {
    @Shadow private List<Button> buttons;
    @Shadow private Mutator<SidebarPages> mutator;
    @Inject(method = "updateButtons", at = @At("TAIL"))
    private void bookmarks$choices(CallbackInfo ci) {
        var pages = mutator.get();
        for (int i = 0; i < buttons.size(); i++) {
            final int index = i < pages.pages.size() ? i : -1;
            var text = index < 0 ? net.minecraft.network.chat.Component.literal("+") :
                    BookmarkPage.isOurs(pages.pages.get(index)) ? BookmarkPages.name() : pages.pages.get(index).type.getText();
            buttons.set(i, Button.builder(text, button -> BookmarkPages.choose(pages, index))
                    .bounds(0, 0, index < 0 ? 20 : 194, 20).build());
        }
    }
}

