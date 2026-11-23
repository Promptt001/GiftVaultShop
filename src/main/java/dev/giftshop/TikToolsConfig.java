package dev.giftshop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

record TikToolsConfig(Mode mode, String username, String recipient, Map<String, String> giftRules) {
    enum Mode { OFF, READ_ONLY, REWARDS }
    static final TikToolsConfig OFF = new TikToolsConfig(Mode.OFF, "", "", Map.of());
    static final String KEY_ENV = "TIKTOOLS_API_KEY";

    static TikToolsConfig load(Path path, Set<String> rules) throws IOException {
        if (Files.notExists(path)) return OFF;
        try (var reader = Files.newBufferedReader(path)) { return parse(JsonParser.parseReader(reader).getAsJsonObject(), rules); }
    }
    static TikToolsConfig parse(JsonObject root, Set<String> rules) {
        var mode = switch (TikToolsEvent.text(root, "mode")) {
            case "off" -> Mode.OFF;
            case "read_only" -> Mode.READ_ONLY;
            case "rewards" -> Mode.REWARDS;
            default -> throw new IllegalArgumentException("mode must be off, read_only or rewards");
        };
        if (mode == Mode.OFF) return OFF;
        var username = TikToolsEvent.text(root, "username").replaceFirst("^@", "");
        if (!username.matches("[A-Za-z0-9_.]{1,32}")) throw new IllegalArgumentException("invalid TikTok username");
        var recipient = TikToolsEvent.text(root, "recipientUserId");
        var mapping = new HashMap<String, String>();
        if (mode == Mode.REWARDS) {
            if (!recipient.matches("[1-9][0-9]{0,39}")) throw new IllegalArgumentException("recipientUserId must be the broadcaster's numeric ID as a string");
            var entries = root.getAsJsonObject("giftRules");
            if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("giftRules must map TikTools gift IDs to local rules");
            for (var entry : entries.entrySet()) {
                var rule = TikToolsEvent.text(entries, entry.getKey());
                if (!entry.getKey().matches("[1-9][0-9]{0,39}") || !rules.contains(rule))
                    throw new IllegalArgumentException("giftRules contains an invalid ID or unknown rule");
                mapping.put(entry.getKey(), rule);
            }
        }
        return new TikToolsConfig(mode, username, recipient, Map.copyOf(mapping));
    }
    URI endpoint(String key) {
        if (key == null || key.isBlank() || key.length() > 1024 || key.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Set " + KEY_ENV + " in the Minecraft process environment");
        return URI.create("wss://api.tik.tools?uniqueId=" + username + "&apiKey=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
    }
}
