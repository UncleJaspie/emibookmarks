package com.unclejaspie.emibookmarks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.unclejaspie.emibookmarks.Emibookmarks;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Emibookmarks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BookmarkKeys {
    public static final KeyMapping TOGGLE = new KeyMapping("key.emibookmarks.toggle", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.emibookmarks");
    public static final KeyMapping BOOKMARK = new KeyMapping("key.emibookmarks.bookmark", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_A, "key.categories.emibookmarks");
    public static final KeyMapping TREE = new KeyMapping("key.emibookmarks.tree", KeyConflictContext.GUI,
            KeyModifier.SHIFT, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.emibookmarks");
    public static boolean matches(KeyMapping mapping, InputConstants.Key key) {
        // Forge's NONE modifier rejects Shift/Ctrl in GUI context; those are our action modifiers.
        return !key.equals(InputConstants.UNKNOWN) && mapping.getKey().equals(key) &&
                mapping.getKeyConflictContext().isActive() && (mapping.getKeyModifier() == KeyModifier.NONE ||
                mapping.getKeyModifier().isActive(mapping.getKeyConflictContext()));
    }
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(BOOKMARK); event.register(TREE); }
}

