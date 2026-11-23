package dev.giftshop;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.Consumer;

final class ConfigScreen extends Screen {
    private static final int PANEL = 0xff14232b, CARD = 0xff223640, GOLD = 0xffffd578, TEXT = 0xfff2f7f5,
        MUTED = 0xffbdcbd0, GREEN = 0xff9ff0c8, RED = 0xffffaaa0;
    private static final List<String> CUES = List.of("off", "ping", "hearts", "sparkle", "celebrate", "drain");
    private enum View {
        HOME("Settings", Items.CHEST), GIFTS("Gift rules", Items.AMETHYST_SHARD), SHOP("Shop offers", Items.EMERALD),
        CHAT("Chat messages", Items.WRITABLE_BOOK), ALERTS("Sound & particles", Items.NOTE_BLOCK),
        PORTRAIT("Portrait banners", Items.PAINTING), DETAIL("Edit entry", Items.NAME_TAG),
        CREATE("Add entry", Items.PAPER), DISCARD("Discard changes?", Items.BARRIER);
        final String label;
        final Item icon;
        View(String label, Item icon) { this.label = label; this.icon = icon; }
    }
    private record Label(String text, int x, int y, int color) {}
    private record Icon(ItemStack stack, int x, int y) {}
    private final List<Label> labels = new ArrayList<>();
    private final List<Icon> icons = new ArrayList<>();
    private final IntegratedServer server;
    private final UUID owner;
    private final ConfigDraft draft;
    private View view = View.HOME, category = View.GIFTS, returnView;
    private int page, listPage, left, top, w, h, rows;
    private boolean saving, error;
    private String entry, newKey = "", status = "";

    ConfigScreen(IntegratedServer server, UUID owner, ConfigDraft draft) {
        super(Component.literal("Gift Vault Settings"));
        this.server = server;
        this.owner = owner;
        this.draft = draft;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        w = Math.min(326, width - 12);
        h = Math.min(224, height - 12);
        left = (width - w) / 2;
        top = (height - h) / 2;
        rows = Math.max(1, (h - 94) / 21);
        labels.clear();
        icons.clear();
        if (w < 214 || h < 148) {
            label("Lower Minecraft's GUI scale to edit settings.", 6, 32, GOLD);
            button("Close", 6, h - 24, Math.min(80, w - 12), this::onClose);
            return;
        }
        if (saving) return;
        if (view == View.DISCARD) {
            label("Your unsaved edits will be lost.", 12, 43, TEXT);
            button("Keep editing", 10, h - 30, half(), () -> show(returnView, page));
            button("Discard", w - 10 - half(), h - 30, half(), super::onClose);
            return;
        }
        switch (view) {
            case HOME -> home();
            case GIFTS, SHOP -> entries();
            case CHAT, ALERTS, PORTRAIT -> options();
            case DETAIL -> detail();
            case CREATE -> create();
            default -> throw new IllegalStateException();
        }
        button(view == View.HOME ? "Close" : "Back", 10, h - 27, 60, view == View.HOME ? this::onClose : this::back);
        var save = button("Save & apply", w - 106, h - 27, 96, this::save);
        save.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
            "Write this draft to disk and apply it to the current world.")));
    }

    private void home() {
        var pages = List.of(View.GIFTS, View.SHOP, View.CHAT, View.ALERTS, View.PORTRAIT);
        for (int i = 0; i < pages.size(); i++) {
            var target = pages.get(i);
            int x = i % 2 == 0 ? 10 : w - 10 - half(), y = 31 + i / 2 * 22;
            button(target.label, x, y, half(), () -> show(target, 0));
            if (w >= 306) icon(target.icon, x + 4, y + 1);
        }
    }

    private JsonObject entriesObject() { return draft.root.getAsJsonObject(category == View.GIFTS ? "gifts" : "shop"); }

    private void entries() {
        category = view;
        var keys = new ArrayList<>(entriesObject().keySet());
        int count = Math.max(1, (keys.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, count - 1);
        for (int i = 0; i < rows && page * rows + i < keys.size(); i++) {
            var key = keys.get(page * rows + i);
            var row = entriesObject().getAsJsonObject(key);
            var summary = category == View.GIFTS ? value(row, "points", "0") + " pts" : value(row, "price", "?") + " pts";
            int y = 29 + i * 21;
            button(clip(key + "   ·   " + summary, w - 72), 10, y, w - 20, () -> {
                entry = key;
                listPage = page;
                show(View.DETAIL, 0);
            });
            icon(view.icon, 16, y + 1);
        }
        pager(count);
        button("+ Add", w - 70, h - 63, 60, () -> { listPage = page; newKey = ""; show(View.CREATE, 0); });
    }

    private void create() {
        label("Unique key: lower-case letters, digits, _ . -", 10, 34, MUTED);
        field("Key", 54, newKey, 64, text -> newKey = text);
        button("Add to draft", 10, 80, 112, () -> {
            if (!newKey.matches("[a-z0-9_.-]{1,64}") || entriesObject().has(newKey)) {
                message("Use a unique key with 1–64 allowed characters.", true);
                return;
            }
            var row = new JsonObject();
            if (category == View.GIFTS) row.addProperty("points", 0);
            else { row.addProperty("item", "minecraft:torch"); row.addProperty("quantity", 1); row.addProperty("price", 1); }
            entriesObject().add(newKey, row);
            entry = newKey;
            show(View.DETAIL, 0);
        });
    }

    private void detail() {
        var row = entriesObject().getAsJsonObject(entry);
        int total = category == View.GIFTS ? 6 : 4;
        int count = (total + rows - 1) / rows;
        page = Math.clamp(page, 0, count - 1);
        for (int i = page * rows; i < Math.min(total, (page + 1) * rows); i++) {
            int y = 29 + (i % rows) * 21;
            if (i == total - 1) {
                button("Remove from draft", 10, y, w - 20, () -> { entriesObject().remove(entry); show(category, listPage); });
            } else if (category == View.GIFTS) switch (i) {
                case 0 -> number("Points", y, row, "points", "0");
                case 1 -> field("Effect ID", y, value(row, "effect", ""), 255, text -> {
                    if (text.isBlank()) row.remove("effect"); else row.addProperty("effect", text.trim());
                });
                case 2 -> number("Seconds", y, row, "seconds", "15");
                case 3 -> number("Level (1–5)", y, row, "level", "1");
                case 4 -> cycle("Gift cue", y, row, "cue", "inherit", true);
                default -> throw new IllegalStateException();
            } else switch (i) {
                case 0 -> field("Item ID", y, value(row, "item", ""), 255, text -> row.addProperty("item", text.trim()));
                case 1 -> number("Quantity", y, row, "quantity", "1");
                case 2 -> number("Price", y, row, "price", "1");
                default -> throw new IllegalStateException();
            }
        }
        pager(count);
    }

    private void options() {
        int total = Settings.KINDS.size() + (view == View.ALERTS ? 3 : view == View.PORTRAIT ? 2 : 0);
        int count = (total + rows - 1) / rows;
        page = Math.clamp(page, 0, count - 1);
        for (int i = page * rows; i < Math.min(total, (page + 1) * rows); i++) {
            int y = 29 + (i % rows) * 21;
            if (view == View.CHAT) {
                String kind = Settings.KINDS.get(i);
                toggle(name(kind), y, kind, true, "chat");
            } else if (view == View.ALERTS) {
                switch (i) {
                    case 0 -> toggle("Sounds", y, "sounds", true, "alerts");
                    case 1 -> toggle("Particles", y, "particles", true, "alerts");
                    case 2 -> number("Cooldown (ticks)", y, "cooldownTicks", "20", "alerts");
                    default -> {
                        String kind = Settings.KINDS.get(i - 3);
                        String fallback = Alerts.Options.parse(null).events().get(kind).name().toLowerCase(Locale.ROOT);
                        cycle(name(kind), y, kind, fallback, "alerts", "events");
                    }
                }
            } else {
                switch (i) {
                    case 0 -> toggle("Banners", y, "enabled", true, "portraitAlerts");
                    case 1 -> number("Height (0–100%)", y, "verticalPercent", "15", "portraitAlerts");
                    default -> {
                        String kind = Settings.KINDS.get(i - 2);
                        toggle(name(kind), y, kind, PortraitAlerts.Options.parse(null).shows(kind), "portraitAlerts", "events");
                    }
                }
            }
        }
        pager(count);
    }

    private void pager(int count) {
        if (count <= 1) return;
        button("<", 10, h - 63, 25, () -> show(view, page - 1)).active = page > 0;
        label((page + 1) + " / " + count, 41, h - 57, MUTED);
        button(">", 85, h - 63, 25, () -> show(view, page + 1)).active = page + 1 < count;
    }

    private JsonObject find(String[] path, boolean create) {
        JsonObject node = draft.root;
        for (String key : path) {
            node = create ? ConfigDraft.section(node, key) : node.getAsJsonObject(key);
            if (node == null) break;
        }
        return node;
    }

    private void toggle(String label, int y, String key, boolean fallback, String... path) {
        boolean enabled = Settings.flag(find(path, false), key, fallback);
        control(label, enabled ? "On" : "Off", y, () -> {
            find(path, true).addProperty(key, !enabled);
            refresh();
        });
    }

    private void cycle(String label, int y, String key, String fallback, String... path) {
        String current = value(find(path, false), key, fallback);
        control(label, name(current), y, () -> {
            find(path, true).addProperty(key, CUES.get((CUES.indexOf(current) + 1) % CUES.size()));
            refresh();
        });
    }

    private void cycle(String label, int y, JsonObject row, String key, String fallback, boolean allowInherit) {
        String current = value(row, key, fallback);
        control(label, name(current), y, () -> {
            int next = CUES.indexOf(current) + 1;
            if (next == CUES.size() && allowInherit) row.remove(key);
            else row.addProperty(key, CUES.get(next % CUES.size()));
            refresh();
        });
    }

    private void control(String label, String text, int y, Runnable action) {
        label(label, 12, y + 6, TEXT);
        button(text, half() + 12, y, w - half() - 22, action);
    }
    private void number(String label, int y, JsonObject row, String key, String fallback) {
        field(label, y, value(row, key, fallback), 16, value -> ConfigDraft.number(row, key, value));
    }
    private void number(String label, int y, String key, String fallback, String... path) {
        field(label, y, value(find(path, false), key, fallback), 16, value -> ConfigDraft.number(find(path, true), key, value));
    }
    private void field(String label, int y, String value, int limit, Consumer<String> change) {
        label(label, 12, y + 6, TEXT);
        int x = half() + 12;
        var box = new EditBox(font, left + x, top + y, w - x - 10, 18, Component.literal(label));
        box.setMaxLength(Math.max(limit, value.length()));
        box.setValue(value);
        box.setResponder(change);
        addRenderableWidget(box);
    }

    private void back() {
        if (view == View.DETAIL || view == View.CREATE) show(category, listPage);
        else show(View.HOME, 0);
    }
    private void show(View target, int page) { view = target; this.page = page; refresh(); }
    private void refresh() { clearFocus(); clearWidgets(); init(); }
    @Override public void onClose() {
        if (saving) return;
        if (view == View.DISCARD) show(returnView, page);
        else if (draft.dirty() && w >= 214 && h >= 148) { returnView = view; show(View.DISCARD, page); }
        else super.onClose();
    }

    private void save() {
        if (saving) return;
        var client = Minecraft.getInstance();
        if (!server.isRunning() || client.getSingleplayerServer() != server) { message("The private world is no longer open.", true); return; }
        var candidate = draft.root.deepCopy();
        saving = true;
        message("Saving settings…", false);
        refresh();
        server.execute(() -> {
            try {
                GiftShop.instance().saveEditor(server, owner, draft, candidate);
                client.execute(() -> {
                    if (client.getSingleplayerServer() != server || client.gui.screen() != this) return;
                    if (client.player != null) client.player.sendSystemMessage(Component.literal("[Gift Vault] Settings saved and applied."));
                    super.onClose();
                });
            } catch (Exception e) {
                client.execute(() -> {
                    saving = false;
                    message(e.getMessage() == null ? "Could not save settings." : e.getMessage(), true);
                    if (client.gui.screen() == this) {
                        if (client.player != null) client.player.sendSystemMessage(Component.literal("[Gift Vault] " + status));
                        refresh();
                    }
                });
            }
        });
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(left, top, left + w, top + h, PANEL);
        g.fill(left, top, left + w, top + 2, GOLD);
        g.fill(left + 8, top + 24, left + w - 8, top + h - 7, CARD);
        g.item(new ItemStack(view.icon), left + 8, top + 5);
        String heading = view == View.DETAIL ? entry : view.label;
        g.text(font, clip("Gift Vault / " + heading, w - 39), left + 31, top + 9, GOLD);
        for (var l : labels) g.text(font, clip(l.text, w - l.x - 8), left + l.x, top + l.y, l.color);
        super.extractRenderState(g, mx, my, delta);
        for (var icon : icons) g.item(icon.stack, left + icon.x, top + icon.y);
        String footer = status.isEmpty() ? (draft.dirty() ? "Unsaved edits" : "Settings shared by your worlds") : status;
        g.text(font, clip(footer, w - 20), left + 10, top + h - 40, error ? RED : draft.dirty() ? GOLD : GREEN);
    }

    private Button button(String text, int x, int y, int size, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(clip(text, Math.max(0, size - 8))), b -> action.run())
            .bounds(left + x, top + y, Math.max(1, size), 18).build());
    }
    private void label(String text, int x, int y, int color) { labels.add(new Label(text, x, y, color)); }
    private void icon(Item item, int x, int y) { icons.add(new Icon(new ItemStack(item), x, y)); }
    private void message(String text, boolean error) { this.status = text; this.error = error; }
    private int half() { return (w - 26) / 2; }
    private String clip(String text, int size) {
        return font.width(text) <= size ? text : font.plainSubstrByWidth(text, Math.max(0, size - 7)) + "…";
    }
    private static String value(JsonObject row, String key, String fallback) {
        return row != null && row.has(key) ? row.get(key).getAsString() : fallback;
    }
    private static String name(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
}
