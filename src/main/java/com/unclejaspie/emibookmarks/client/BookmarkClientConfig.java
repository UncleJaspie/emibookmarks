package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.Emibookmarks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;

/** Client-only preferences, separate from world/server bookmark records. */
@Mod.EventBusSubscriber(modid = Emibookmarks.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BookmarkClientConfig {
    public static final ForgeConfigSpec SPEC;
    public enum Background { EMI, CUSTOM }
    public static final ForgeConfigSpec.BooleanValue COMBINE_INPUTS, MACHINE_BADGE, ROLE_ACCENTS, BATCH_LABEL, AUTO_TREE, TREE_WORLD, TREE_BLUR;
    public static final ForgeConfigSpec.EnumValue<Background> BACKGROUND;
    public static final ForgeConfigSpec.DoubleValue SIDEBAR_OPACITY, TREE_OPACITY;

    static {
        var builder = new ForgeConfigSpec.Builder();
        COMBINE_INPUTS = builder.comment("Combine identical consumed input slots within each recipe for display. Original saved slots remain unchanged.")
                .define("combineIdenticalIngredients", true);
        MACHINE_BADGE = builder.define("machineBadge", true);
        ROLE_ACCENTS = builder.define("roleUnderlines", true);
        BATCH_LABEL = builder.define("leadingBatchMultiplier", true);
        AUTO_TREE = builder.comment("Opt-in registry-wide expansion. Off opens an editable tree from saved routes instead.").define("automaticTreeExpansion", false);
        BACKGROUND = builder.defineEnum("sidebarBackground", Background.EMI);
        SIDEBAR_OPACITY = builder.defineInRange("customSidebarOpacity", 0.88, 0, 1);
        TREE_WORLD = builder.define("treeWorldBackground", true);
        TREE_BLUR = builder.define("treeBackgroundBlur", true);
        TREE_OPACITY = builder.defineInRange("treeBackgroundOpacity", 0.35, 0, 1);
        SPEC = builder.build();
    }
    @SubscribeEvent
    public static void construct(FMLConstructModEvent event) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
    }
    @SubscribeEvent
    public static void reload(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager ->
                net.minecraft.client.Minecraft.getInstance().execute(BookmarkTreeBackground::release));
    }
    // Forge's registered CLIENT config is synchronous/autosaving. A second save creates an
    // unnecessary extra watcher reload between an option change and its persisted value.
    public static void setCombine(boolean value) { COMBINE_INPUTS.set(value); }
}

