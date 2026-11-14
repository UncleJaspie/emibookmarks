package com.unclejaspie.emibookmarks.client;

import com.mojang.logging.LogUtils;
import com.unclejaspie.emibookmarks.bookmark.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** One local world/server scope, with deferred saves and read-only recovery on load failure. */
public final class BookmarkWorkspace {
    private static BookmarkCodec.Document document = BookmarkCodec.empty();
    private static Path file;
    private static boolean writable, dirty, warned;
    private static int ticks;
    private static final Map<Path, BookmarkCodec.Document> PENDING = new LinkedHashMap<>();
    private static int retryTicks;

    public static BookmarkCodec.Document document() { return document; }
    public static BookmarkCollection collection() {
        return document.collections().stream().filter(c -> c.id.equals(document.active())).findFirst().orElseThrow();
    }
    public static boolean writable() { return writable; }
    public static void changed() { if (writable) { dirty = true; ticks = 0; } }
    public static void open() {
        flush();
        var client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        String scope;
        if (server != null) scope = "world:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (client.getCurrentServer() != null) scope = "server:" + client.getCurrentServer().ip.toLowerCase(java.util.Locale.ROOT);
        else { file = null; writable = false; document = BookmarkCodec.empty(); return; }
        file = BookmarkFiles.path(FMLPaths.CONFIGDIR.get(), scope);
        dirty = false; warned = false;
        try {
            var loaded = PENDING.containsKey(file) ? PENDING.get(file) : BookmarkFiles.load(file);
            document = BookmarkCodec.singleList(loaded); writable = true;
            if (document != loaded) { BookmarkFiles.backupCollections(file); changed(); flush(); } // Existing file becomes .bak through the normal safe-save path.
        }
        catch (Exception e) {
            document = BookmarkCodec.empty(); writable = false;
            LogUtils.getLogger().error("Could not load bookmarks; original file will be preserved: {}", file, e);
            message("Bookmark save could not be loaded. Editing is disabled to preserve it; see the log.");
        }
        BookmarkCapture.clearCache();
    }
    public static void close() { flush(); writable = false; file = null; BookmarkTreeActions.clearSession(); BookmarkCapture.clearCache(); }
    public static void tick() {
        if (dirty && ++ticks >= 20) flush();
        if (!PENDING.isEmpty() && ++retryTicks >= 100) {
            retryTicks = 0;
            var iterator = PENDING.entrySet().iterator();
            while (iterator.hasNext()) {
                var pending = iterator.next();
                try { BookmarkFiles.save(pending.getKey(), pending.getValue()); iterator.remove(); }
                catch (Exception ignored) { /* Original failure is logged; retain data for the next retry. */ }
            }
        }
    }
    public static void flush() {
        if (!dirty || !writable || file == null) return;
        try { BookmarkFiles.save(file, document); PENDING.remove(file); dirty = false; warned = false; }
        catch (Exception e) {
            // Keep dirty for retry; never replace the in-memory document on save failure.
            ticks = 0;
            PENDING.put(file, document);
            if (!warned) {
                warned = true; LogUtils.getLogger().error("Could not save bookmarks to {}", file, e);
                message("Could not save bookmarks. They remain in memory; see the log.");
            }
        }
    }
    public static void message(String text) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(text), true);
    }
}

