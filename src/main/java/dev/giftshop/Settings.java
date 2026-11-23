package dev.giftshop;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

record Settings(Map<String, Rule> gifts, List<Offer> offers, Set<String> chat, Alerts.Options alerts) {
    static final List<String> KINDS = List.of("comment", "follow", "like", "share", "join", "gift");
    record Rule(int points, Holder<MobEffect> effect, int seconds, int level, Alerts.Cue cue) {}
    record Offer(String key, ItemStack stack, int price) {}

    static Settings load() throws IOException {
        var path = FabricLoader.getInstance().getConfigDir().resolve("gift-vault-shop.json");
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            try (var defaults = Settings.class.getResourceAsStream("/gift-vault-shop-default.json")) {
                Files.copy(Objects.requireNonNull(defaults), path);
            }
        }
        try (var reader = Files.newBufferedReader(path)) {
            return parse(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    static Settings parse(JsonObject root) {
        var gifts = new LinkedHashMap<String, Rule>();
        for (var e : root.getAsJsonObject("gifts").entrySet()) {
            if (!e.getKey().matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid gift key: " + e.getKey());
            var rule = e.getValue().getAsJsonObject();
            int points = integer(rule, "points", -Wallet.LIMIT, Wallet.LIMIT, 0);
            Holder<MobEffect> effect = rule.has("effect") ? BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(rule.get("effect").getAsString()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown effect in " + e.getKey())) : null;
            int seconds = integer(rule, "seconds", 1, 300, 15);
            int level = integer(rule, "level", 1, 5, 1);
            gifts.put(e.getKey(), new Rule(points, effect, seconds, level, rule.has("cue") ? Alerts.Cue.parse(rule.get("cue").getAsString()) : null));
        }
        var offers = new ArrayList<Offer>();
        for (var e : root.getAsJsonObject("shop").entrySet()) {
            var entry = e.getValue().getAsJsonObject();
            var item = BuiltInRegistries.ITEM.get(Identifier.parse(entry.get("item").getAsString()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown item in " + e.getKey())).value();
            if (item == Items.AIR) throw new IllegalArgumentException("Cannot sell air");
            var stack = new ItemStack(item);
            stack.setCount(integer(entry, "quantity", 1, stack.getMaxStackSize(), 1));
            offers.add(new Offer(e.getKey(), stack, integer(entry, "price", 1, Wallet.LIMIT, null)));
        }
        var chat = new HashSet<String>();
        var flags = root.getAsJsonObject("chat");
        for (String kind : KINDS) if (flag(flags, kind, true)) chat.add(kind);
        return new Settings(Map.copyOf(gifts), List.copyOf(offers), Set.copyOf(chat), Alerts.Options.parse(root.getAsJsonObject("alerts")));
    }

    static boolean flag(JsonObject o, String key, boolean fallback) {
        if (o == null || !o.has(key)) return fallback;
        var value = o.get(key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException(key + " must be true or false");
        return value.getAsBoolean();
    }

    static int integer(JsonObject o, String key, int min, int max, Integer fallback) {
        if (!o.has(key) && fallback != null) return fallback;
        try {
            var primitive = o.getAsJsonPrimitive(key);
            if (!primitive.isNumber()) throw new NumberFormatException();
            int value = primitive.getAsBigDecimal().intValueExact();
            if (value >= min && value <= max) return value;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException(key + " must be an integer from " + min + " to " + max);
    }
}
