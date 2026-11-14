package com.unclejaspie.emibookmarks.mixin;

import dev.emi.emi.screen.BoMScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Group;

@Mixin(value = BoMScreen.class, remap = false)
public abstract class EmiTreeBackgroundMixin {
    @Group(name = "bookmarks$treeBackground", min = 1, max = 1)
    @Redirect(method = {"render", "m_88315_"}, require = 0, at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/BoMScreen;renderDirtBackground(Lnet/minecraft/client/gui/GuiGraphics;)V", remap = false))
    private void bookmarks$worldBackground(BoMScreen screen, GuiGraphics graphics) {
        if (Minecraft.getInstance().level != null && com.unclejaspie.emibookmarks.client.BookmarkClientConfig.TREE_WORLD.get())
            com.unclejaspie.emibookmarks.client.BookmarkTreeBackground.render(graphics, screen);
        else screen.renderDirtBackground(graphics);
    }
    @Group(name = "bookmarks$treeBackground", min = 1, max = 1)
    @Redirect(method = {"render", "m_88315_"}, require = 0, at = @At(value = "INVOKE", target = "Ldev/emi/emi/screen/BoMScreen;m_280039_(Lnet/minecraft/client/gui/GuiGraphics;)V", remap = false))
    private void bookmarks$forgeWorldBackground(BoMScreen screen, GuiGraphics graphics) { bookmarks$worldBackground(screen, graphics); }
}
