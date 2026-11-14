package com.unclejaspie.emibookmarks.client;

import com.unclejaspie.emibookmarks.bookmark.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** User-initiated portable files only. No account, server channel or automatic transmission. */
public final class BookmarkTreeSharing {
    public static void exportCurrent() {
        try {
            var plan = BookmarkTreeActions.currentPlan();
            var tree = new BookmarkTreeData.Tree(plan.recipes(), plan.group());
            Path folder = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("emibookmarks-shares");
            Files.createDirectories(folder);
            String name = "tree-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".emibookmarks.json";
            var path = folder.resolve(name);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(BookmarkTreeData.encode(tree));
            if (json.getBytes(StandardCharsets.UTF_8).length > BookmarkTreeData.MAX_BYTES) throw new IllegalArgumentException("Shared tree exceeds file-size limit");
            Files.writeString(path, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            BookmarkWorkspace.message("Tree exported to emibookmarks-shares/" + name);
        } catch (Exception | LinkageError failure) { BookmarkWorkspace.message("Tree export failed: " + failure.getMessage()); }
    }
    public static BookmarkTreeData.Tree load(Path path) throws java.io.IOException {
        byte[] bytes;
        try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(BookmarkTreeData.MAX_BYTES + 1); }
        if (bytes.length > BookmarkTreeData.MAX_BYTES) throw new IllegalArgumentException("Shared tree exceeds 16 MiB");
        var tree = BookmarkTreeData.decode(bytes);
        if (!BookmarkChain.calculate(tree.recipes()).valid() || tree.recipes().stream().anyMatch(r -> r.isRecipe() && BookmarkCapture.resolve(r) == null)) tree.group().chain = false;
        return tree;
    }
}
