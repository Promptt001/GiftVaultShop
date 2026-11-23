package dev.giftshop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record TikToolsEvent(String kind, String text, Gift gift, int likes) {
    private TikToolsEvent(String kind, String text, Gift gift) { this(kind, text, gift, 0); }
    static final String LIVE_PREFIX = "tiktools:";

    record Gift(String id, String recipient, String transaction, int count, boolean finalized, String viewer) {
        Reward reward(TikToolsConfig config) {
            if (config.mode() != TikToolsConfig.Mode.REWARDS || !finalized || count < 1 || count > 999
                || transaction.isEmpty() || !config.recipient().equals(recipient)) return null;
            var rule = config.giftRules().get(id);
            return rule == null ? null : new Reward(rule, LIVE_PREFIX + digest(recipient + ":" + transaction), count, viewer);
        }
    }
    record Reward(String rule, String key, int count, String viewer) {}

    static TikToolsEvent parse(String json) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            var event = text(root, "event");
            var data = root.getAsJsonObject("data");
            if (event.equals("roomInfo")) return decimalId(root, "roomId").isEmpty() && decimalId(data, "roomId").isEmpty()
                ? null : new TikToolsEvent("roomInfo", "", null);
            if (data == null) return null;
            var user = data.getAsJsonObject("user");
            var viewer = ViewerText.clean(text(user, "uniqueId"), 48);
            if (viewer.isBlank()) viewer = ViewerText.clean(text(user, "nickname"), 48);
            if (viewer.isBlank()) viewer = "Viewer";
            return switch (event) {
                case "chat" -> activity("comment", viewer + ": " + ViewerText.clean(text(data, "comment"), 220));
                case "follow" -> activity("follow", viewer + " followed");
                case "share" -> activity("share", viewer + " shared the LIVE");
                case "member" -> activity("join", viewer + " joined the LIVE");
                case "like" -> {
                    int likes = count(data, "likeCount", 1_000_000);
                    yield likes == 0 ? null : new TikToolsEvent("like", viewer + " sent " + likes + " likes", null, likes);
                }
                case "gift" -> {
                    JsonElement end = data.get("repeatEnd");
                    boolean finalized = end != null && end.isJsonPrimitive() && end.getAsJsonPrimitive().isBoolean() && end.getAsBoolean();
                    var gift = new Gift(decimalId(data, "giftId"), decimalId(data, "receiverUserId"), opaqueId(data, "transactionId"),
                        count(data, "repeatCount", 999), finalized, viewer);
                    var name = ViewerText.clean(text(data, "giftName"), 48);
                    if (name.isBlank()) name = "a gift";
                    yield new TikToolsEvent("gift", viewer + " sent " + (gift.count() == 0 ? "" : gift.count() + " × ") + name, gift);
                }
                default -> null;
            };
        } catch (RuntimeException ignored) { return null; }
    }

    private static TikToolsEvent activity(String kind, String text) { return new TikToolsEvent(kind, text, null); }
    static String text(JsonObject o, String key) {
        var e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : "";
    }
    private static String opaqueId(JsonObject o, String key) {
        var e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive()) return "";
        String s = e.getAsString();
        if (e.getAsJsonPrimitive().isNumber()) return s.matches("[1-9][0-9]{0,39}") ? s : "";
        return e.getAsJsonPrimitive().isString() && s.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") && !s.equals("0") ? s : "";
    }
    private static String decimalId(JsonObject o, String key) {
        var s = opaqueId(o, key);
        return s.matches("[1-9][0-9]{0,39}") ? s : "";
    }
    private static int count(JsonObject o, String key, int max) {
        var e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return 0;
        String s = e.getAsString();
        if (!s.matches("[1-9][0-9]{0,6}")) return 0;
        int n = Integer.parseInt(s);
        return n <= max ? n : 0;
    }
    private static String digest(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
