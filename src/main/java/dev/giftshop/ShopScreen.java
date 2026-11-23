package dev.giftshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ShopScreen extends AbstractContainerScreen<ShopMenu> {
    private static final int PANEL = 0xff14232b, CARD = 0xff223640, EDGE = 0xff3c5660;
    private static final int WHITE = 0xfff2f7f5, MUTED = 0xffbdcbd0, GOLD = 0xffffd578, GREEN = 0xff9ff0c8;

    ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShopMenu.W, ShopMenu.H);
    }

    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
        super.extractBackground(g, x, y, delta);
        g.fill(leftPos, topPos, leftPos + ShopMenu.W, topPos + ShopMenu.H, PANEL);
        g.fill(leftPos, topPos, leftPos + ShopMenu.W, topPos + 2, GOLD);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        extractBackground(g, mouseX, mouseY, delta);
        g.text(font, getTitle(), leftPos + 11, topPos + 12, WHITE);
        String balance = "BALANCE  " + menu.balance() + " pts";
        g.text(font, balance, leftPos + ShopMenu.W - 11 - font.width(balance), topPos + 12, GOLD);
        g.fill(leftPos + 11, topPos + 27, leftPos + ShopMenu.W - 11, topPos + 28, EDGE);
        int x = mouseX - leftPos, y = mouseY - topPos;
        int selected = menu.selected();
        if (selected >= 0) {
            review(g, selected, x, y);
        } else {
            for (int i = 0; i < ShopMenu.PER_PAGE; i++) offer(g, i, x, y);
            if (menu.hasPrevious()) button(g, "< Previous", 11, 180, 77, x, y, false);
            if (menu.hasNext()) button(g, "Next >", 206, 180, 77, x, y, false);
            g.centeredText(font, "Select an offer to review", leftPos + ShopMenu.W / 2, topPos + 185, MUTED);
        }
    }

    private void offer(GuiGraphicsExtractor g, int i, int x, int y) {
        var stack = menu.getSlot(i).getItem();
        if (stack.isEmpty() || menu.price(i) == 0) return;
        int cx = ShopMenu.cardX(i), cy = ShopMenu.cardY(i);
        int price = menu.price(i), missing = price - menu.balance();
        boolean affordable = missing <= 0;
        box(g, cx, cy, ShopMenu.CARD_W, ShopMenu.CARD_H, affordable ? GREEN : EDGE,
            affordable && over(x, y, cx, cy, ShopMenu.CARD_W, ShopMenu.CARD_H) ? 0xff2d4850 : CARD);
        g.item(stack, leftPos + cx + 6, topPos + cy + 7);
        g.text(font, "x" + stack.getCount(), leftPos + cx + 28, topPos + cy + 11, WHITE);
        var label = stack.getHoverName().getString();
        if (font.width(label) > 76) label = font.plainSubstrByWidth(label, 69) + "…";
        g.text(font, label, leftPos + cx + 6, topPos + cy + 27, WHITE);
        g.text(font, price + " pts", leftPos + cx + 6, topPos + cy + 40, GOLD);
        g.text(font, affordable ? "Ready" : "Need +" + missing, leftPos + cx + 6, topPos + cy + 53, affordable ? GREEN : MUTED);
    }

    private void review(GuiGraphicsExtractor g, int selected, int x, int y) {
        var stack = menu.getSlot(selected).getItem();
        int price = menu.price(selected), balance = menu.balance();
        g.centeredText(font, "REVIEW PURCHASE", leftPos + ShopMenu.W / 2, topPos + 44, GOLD);
        box(g, 27, 64, 240, 96, EDGE, CARD);
        g.item(stack, leftPos + 43, topPos + 79);
        g.text(font, font.plainSubstrByWidth(stack.getCount() + " x " + stack.getHoverName().getString(), 180), leftPos + 70, topPos + 83, WHITE);
        g.text(font, "Cost: " + price + " pts", leftPos + 43, topPos + 111, GOLD);
        g.text(font, balance >= price ? "Balance after purchase: " + (balance - price) + " pts" : "Need " + (price - balance) + " more points",
            leftPos + 43, topPos + 133, balance >= price ? WHITE : MUTED);
        button(g, "Cancel", 27, 174, 111, x, y, false);
        if (balance >= price && !stack.isEmpty()) button(g, "Buy for " + price + " pts", 156, 174, 111, x, y, true);
    }

    private void button(GuiGraphicsExtractor g, String text, int x, int y, int w, int mx, int my, boolean positive) {
        box(g, x, y, w, 21, positive ? GREEN : EDGE, over(mx, my, x, y, w, 21) ? 0xff35515b : CARD);
        g.centeredText(font, text, leftPos + x + w / 2, topPos + y + 7, positive ? GREEN : WHITE);
    }
    private void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int border, int fill) {
        x += leftPos; y += topPos;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }
    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return true;
        double x = event.x() - leftPos, y = event.y() - topPos;
        int selected = menu.selected();
        if (selected >= 0) {
            if (over(x, y, 27, 174, 111, 21)) send(ShopMenu.BACK);
            else if (over(x, y, 156, 174, 111, 21) && menu.balance() >= menu.price(selected)) send(ShopMenu.BUY);
        } else if (menu.hasPrevious() && over(x, y, 11, 180, 77, 21)) send(ShopMenu.PREVIOUS);
        else if (menu.hasNext() && over(x, y, 206, 180, 77, 21)) send(ShopMenu.NEXT);
        else for (int i = 0; i < ShopMenu.PER_PAGE; i++) {
            if (menu.price(i) > 0 && menu.balance() >= menu.price(i) && over(x, y, ShopMenu.cardX(i), ShopMenu.cardY(i), ShopMenu.CARD_W, ShopMenu.CARD_H)) {
                send(i);
                break;
            }
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return true; }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }
    private void send(int button) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
}
