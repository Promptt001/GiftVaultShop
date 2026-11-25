package dev.giftshop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record TikToolsEvent(String kind, String text, Gift gift, int likes, long totalLikes, String room) {
    private TikToolsEvent(String kind, String text, Gift gift) { this(kind, text, gift, 0, -1, ""); }
    static final String LIVE_PREFIX = "tiktools:";

    record Gift(String id, String recipient, String transaction, int count, boolean finalized, String viewer) {
        String refusal(TikToolsConfig config, boolean paused) {
            if (config.mode() != TikToolsConfig.Mode.REWARDS) return "read-only (rewards disabled)";
            if (paused) return "rewards paused after a gift backlog overflow";
            if (!finalized) return "gift not finalized";
            if (id.isEmpty()) return "missing numeric gift ID";
            if (recipient.isEmpty()) return "missing recipient ID";
            if (!config.recipient().equals(recipient)) return "gift went to a different recipient";
            if (!config.giftRules().containsKey(id)) return "unmapped gift ID " + id;
            if (count < 1 || count > 999) return "invalid final count";
            if (transaction.isEmpty()) return "missing usable transaction ID";
            return "";
        }
        Reward reward(TikToolsConfig config) {
            if (!refusal(config, false).isEmpty()) return null;
            return new Reward(config.giftRules().get(id), LIVE_PREFIX + digest(recipient + ":" + transaction), count, viewer);
        }
    }
    record Reward(String rule, String key, int count, String viewer) {}

    TikToolsEvent inRoom(String roomId) { return new TikToolsEvent(kind, text, gift, likes, totalLikes, roomId); }

    static int liveGiftPoints(int base) { return base <= 0 ? base : (int) Math.min(Wallet.LIMIT, base * 10L); }

    static TikToolsEvent parse(String json) {
        try {
            var root = JsonParser.parseString(json).getAsJsonObject();
            var event = text(root, "event");
            var data = object(root, "data");
            if (event.equals("roomInfo")) {
                var room = decimalId(root, "roomId");
                if (room.isEmpty()) room = decimalId(data, "roomId");
                return room.isEmpty() ? null : new TikToolsEvent("roomInfo", "", null).inRoom(room);
            }
            if (data == null) return null;
            var user = object(data, "user");
            String handle = ViewerText.clean(text(user, "uniqueId"), 48).replaceFirst("^@", "");
            String screen = ViewerText.clean(text(user, "nickname"), 48);
            String viewer = handle.isBlank() ? (screen.isBlank() ? "Viewer" : screen) :
                screen.isBlank() || screen.equalsIgnoreCase(handle) || screen.equalsIgnoreCase("@" + handle)
                    ? "@" + handle : screen + " / @" + handle;
            return switch (event) {
                case "chat" -> activity("comment", viewer + ": " + ViewerText.clean(text(data, "comment"), 220));
                case "follow" -> activity("follow", viewer + " followed");
                case "share" -> activity("share", viewer + " shared the LIVE");
                case "member" -> activity("join", viewer + " joined the LIVE");
                case "like" -> {
                    int likes = count(data, "likeCount", 1_000_000);
                    long total = nonnegativeLong(data, "totalLikes");
                    yield likes == 0 && total < 0 ? null : new TikToolsEvent("like",
                        likes == 0 ? "" : viewer + " sent " + likes + (likes == 1 ? " like" : " likes"), null, likes, total, "");
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

    private static JsonObject object(JsonObject parent, String key) {
        var element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static long nonnegativeLong(JsonObject parent, String key) {
        var element = parent.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return -1;
        String value = element.getAsString();
        if (!value.matches("0|[1-9][0-9]{0,18}")) return -1;
        try { return Long.parseLong(value); } catch (NumberFormatException invalid) { return -1; }
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
