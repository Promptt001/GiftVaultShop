package dev.giftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

final class PortraitAlertRenderer {
    private final PortraitAlerts.Queue queue = new PortraitAlerts.Queue();

    private long total = -1;
    private String scoreLine = "";

    void score(long value) {
        total = value;
        scoreLine = value < 0 ? "" : "LIVE Likes: " + String.format(java.util.Locale.ROOT, "%,d", value);
    }

    void receive(AlertBannerPacket alert) {
        if (Settings.KINDS.contains(alert.kind()) && alert.likes() >= 0 && alert.likes() <= 1_000_000)
            queue.offer(alert, Util.getMillis());
    }

    void clear() { queue.clear(); score(-1); }

    void draw(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        if (!client.isLocalServer() || client.player == null || client.isPaused()) return;
        long now = Util.getMillis();
        int crop = Math.min(g.guiWidth(), (int) (g.guiHeight() * 9L / 16));
        int scoreWidth = Math.min(crop - 8, Math.max(64, client.font.width(scoreLine) + 16));
        boolean scoreboard = total >= 0 && scoreWidth >= 40 && g.guiHeight() >= 24;
        if (scoreboard) {
            int x = (g.guiWidth() - scoreWidth) / 2;
            g.fill(x, 5, x + scoreWidth, 23, 0xe6211835);
            g.fill(x, 22, x + scoreWidth, 23, 0xffec91d5);
            line(g, client.font, scoreLine, x + scoreWidth / 2, 10, scoreWidth, 0xffffd3ef);
        }
        var alert = queue.current(now);
        if (alert == null) return;
        var box = PortraitAlerts.Box.layout(g.guiWidth(), g.guiHeight(), alert.verticalPercent(), scoreboard);
        if (box == null) return;
        int x = box.x(), y = box.y(), w = box.width(), center = x + w / 2;
        int accent = switch (alert.kind()) {
            case "gift" -> 0xffffd578;
            case "like" -> 0xffec91d5;
            case "join" -> 0xff9ad6ff;
            case "follow" -> 0xffb7a4ff;
            case "share" -> 0xff9ff0de;
            default -> 0xffd2e2e8;
        };
        String label = switch (alert.kind()) {
            case "gift" -> "Gift"; case "follow" -> "Follow"; case "share" -> "Share";
            case "like" -> "Likes"; case "join" -> "Join"; default -> "Comment";
        };
        g.fill(x, y, x + w, y + box.height(), 0xf2162329);
        int duration = alert.kind().equals("gift") ? 3_000 : 2_000;
        long remaining = Math.min(duration, queue.remaining(now));
        g.fill(x, y, x + (int) (w * remaining / duration), y + 2, accent);
        if (w >= 70 && remaining > duration - 700 && !alert.kind().equals("comment")) {
            int elapsed = duration - (int) remaining;
            for (int i = 0; i < (alert.kind().equals("gift") ? 5 : 3); i++) {
                int edge = 3 + i % 3 * 2;
                int rise = y + 31 - (elapsed / 28 + i * 5) % 23;
                int size = i % 2 + 1;
                g.fill(x + edge, rise, x + edge + size, rise + size, accent);
                g.fill(x + w - edge - size, rise, x + w - edge, rise + size, accent);
            }
        }
        line(g, client.font, (alert.live() ? "" : "Test · ") + label, center, y + 5, w, accent);
        line(g, client.font, alert.text(), center, y + (alert.detail().isEmpty() ? 19 : 15), w, 0xfff4f7f6);
        if (!alert.detail().isEmpty()) line(g, client.font, alert.detail(), center, y + 25, w, accent);
    }

    private static void line(GuiGraphicsExtractor g, Font font, String text, int center, int y, int width, int color) {
        int limit = width - 22; // Allow padding and room for Minecraft's text shadow.
        if (font.width(text) > limit) text = font.plainSubstrByWidth(text, limit - font.width("…")) + "…";
        g.centeredText(font, text, center, y, color);
    }
}
