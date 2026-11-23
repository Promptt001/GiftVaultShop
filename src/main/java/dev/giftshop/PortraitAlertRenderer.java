package dev.giftshop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

final class PortraitAlertRenderer {
    private final PortraitAlerts.Queue queue = new PortraitAlerts.Queue();

    void receive(AlertBannerPacket alert) {
        if (Settings.KINDS.contains(alert.kind()) && alert.likes() >= 0 && alert.likes() <= 1_000_000)
            queue.offer(alert, Util.getMillis());
    }

    void clear() { queue.clear(); }

    void draw(GuiGraphicsExtractor g) {
        var client = Minecraft.getInstance();
        if (!client.isLocalServer() || client.player == null || client.isPaused()) return;
        var alert = queue.current(Util.getMillis());
        if (alert == null) return;
        var box = PortraitAlerts.Box.layout(g.guiWidth(), g.guiHeight(), alert.verticalPercent());
        if (box == null) return;
        int x = box.x(), y = box.y(), w = box.width(), center = x + w / 2;
        int accent = alert.kind().equals("gift") ? 0xffffd578 : 0xff9ff0de;
        String label = switch (alert.kind()) {
            case "gift" -> "Gift"; case "follow" -> "Follow"; case "share" -> "Share";
            case "like" -> "Likes"; case "join" -> "Join"; default -> "Comment";
        };
        g.fill(x, y, x + w, y + box.height(), 0xf2162329);
        g.fill(x, y, x + w, y + 2, accent);
        line(g, client.font, (alert.live() ? "" : "Test · ") + label, center, y + 5, w, accent);
        line(g, client.font, alert.text(), center, y + (alert.detail().isEmpty() ? 19 : 15), w, 0xfff4f7f6);
        if (!alert.detail().isEmpty()) line(g, client.font, alert.detail(), center, y + 25, w, accent);
    }

    private static void line(GuiGraphicsExtractor g, Font font, String text, int center, int y, int width, int color) {
        int limit = width - 12; // Allow padding and room for Minecraft's text shadow.
        if (font.width(text) > limit) text = font.plainSubstrByWidth(text, limit - font.width("…")) + "…";
        g.centeredText(font, text, center, y, color);
    }
}
