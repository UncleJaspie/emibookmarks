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
    public static final ForgeConfigSpec.BooleanValue COMBINE_INPUTS, MACHINE_BADGE, ROLE_ACCENTS, BATCH_LABEL, TREE_WORLD, TREE_BLUR, SHIFT_MATERIAL_HIGHLIGHTS;
    public static final ForgeConfigSpec.EnumValue<Background> BACKGROUND;
    public static final ForgeConfigSpec.BooleanValue OUTPUT_AWARE_STOCK;
    public static final ForgeConfigSpec.BooleanValue CHECK_UPDATES;
    public static final ForgeConfigSpec.DoubleValue SIDEBAR_OPACITY, TREE_OPACITY;

    static {
        var builder = new ForgeConfigSpec.Builder();
        COMBINE_INPUTS = builder.comment("Combine identical consumed input slots within each recipe for display. Original saved slots remain unchanged.")
                .define("combineIdenticalIngredients", true);
        MACHINE_BADGE = builder.define("machineBadge", true);
        ROLE_ACCENTS = builder.define("roleUnderlines", true);
        SHIFT_MATERIAL_HIGHLIGHTS = builder.comment("Show two-color chain material inspection while holding Tab. Legacy key name retained to preserve existing preferences. Independent of Shift inventory tracking.")
                .define("shiftMaterialHighlights", true);
        OUTPUT_AWARE_STOCK = builder.comment("While holding Shift, allocate carried output items and recalculate remaining recipe work. Unsupported scopes keep original quantities.")
                .define("outputAwareInventoryTracking", true);
        BATCH_LABEL = builder.define("leadingBatchMultiplier", true);
        CHECK_UPDATES = builder.comment("Check public GitHub releases in the background when an inventory opens. A quiet notice appears only for newer compatible versions; no automatic downloads.")
                .define("checkForUpdates",true);
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

