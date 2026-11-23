package dev.giftshop;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.function.Function;

/** A private snapshot with no side effects until its candidate has been validated. */
final class ConfigDraft {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final byte[] original;
    private final JsonObject baseline;
    final JsonObject root;

    private ConfigDraft(byte[] original) {
        this.original = original;
        baseline = JsonParser.parseString(new String(original, StandardCharsets.UTF_8)).getAsJsonObject();
        root = baseline.deepCopy();
    }

    static ConfigDraft open(Path path) throws IOException { return new ConfigDraft(Files.readAllBytes(path)); }
    boolean dirty() { return !root.equals(baseline); }

    static JsonObject section(JsonObject parent, String key) {
        var existing = parent.getAsJsonObject(key);
        if (existing != null) return existing;
        var created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    <T> T save(Path path, JsonObject candidate, Function<JsonObject, T> validate) throws IOException {
        var result = validate.apply(candidate);
        requireCurrent(path);
        if (candidate.equals(baseline)) return result;
        var temporary = Files.createTempFile(path.toAbsolutePath().getParent(), ".gift-vault-shop-", ".json");
        try {
            Files.writeString(temporary, JSON.toJson(candidate) + "\n", StandardCharsets.UTF_8);
            requireCurrent(path);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return result;
    }

    private void requireCurrent(Path path) throws IOException {
        if (!Arrays.equals(original, Files.readAllBytes(path)))
            throw new IOException("File changed outside Minecraft. Reopen /giftgui before editing again.");
    }

    static void number(JsonObject row, String key, String text) {
        try { row.addProperty(key, new java.math.BigInteger(text)); }
        catch (NumberFormatException e) { row.addProperty(key, text); } // Keep incomplete edits visible; validation rejects them.
    }
}
