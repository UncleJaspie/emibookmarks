package com.unclejaspie.emibookmarks.bookmark;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Scoped, bounded local storage. A caller must not save after an unsuccessful load. */
public final class BookmarkFiles {
    public static Path path(Path configDirectory, String scope) {
        try {
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
            return configDirectory.resolve("emibookmarks").resolve(key + ".json");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static BookmarkCodec.Document load(Path file) throws IOException {
        if (!Files.exists(file)) return BookmarkCodec.empty();
        if (Files.size(file) > 16 * 1024 * 1024) throw new IOException("Bookmark file exceeds 16 MiB");
        return BookmarkCodec.decode(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
    }
    /** One-time migration snapshot, separate from the ordinary rotating save backup. */
    public static Path backupCollections(Path file) throws IOException {
        Path snapshot = file.resolveSibling(file.getFileName() + ".before-single-list.bak");
        if (Files.isRegularFile(file) && !Files.exists(snapshot)) Files.copy(file, snapshot);
        return snapshot;
    }
    public static void save(Path file, BookmarkCodec.Document document) throws IOException {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(BookmarkCodec.encode(document));
        if (json.getBytes(StandardCharsets.UTF_8).length > 16 * 1024 * 1024) throw new IOException("Bookmark file exceeds 16 MiB");
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Path backup = file.resolveSibling(file.getFileName() + ".bak");
        try {
            if (Files.exists(file)) {
                if (Files.size(file) > 16 * 1024 * 1024) throw new IOException("Existing bookmark file exceeds 16 MiB; refusing overwrite");
                var old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (old.get("version").getAsInt() < BookmarkCodec.VERSION) {
                    Path archive = file.resolveSibling(file.getFileName() + ".before-v" + BookmarkCodec.VERSION + ".bak");
                    if (!Files.exists(archive)) Files.copy(file, archive);
                }
            }
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}

