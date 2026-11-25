package dev.giftshop;

import com.mojang.serialization.JsonOps;

public final class ShopChecks {
    public static void main(String[] args) throws Exception {
        var w = Wallet.EMPTY.gift("demo:1", 5, 3);
        check(w.points() == 15, "bundle multiplier");
        check(w.gift("demo:1", -5, 3) == w, "same event cannot apply even with changed rule");
        var drained = w.gift("demo:2", -8, 2);
        check(drained.points() == 0 && drained.gifts().contains("demo:2"), "deduction floors and is recorded");
        check(w.gift("demo:3", Wallet.LIMIT, 999).points() == Wallet.LIMIT, "large credit cannot overflow");
        rejects(() -> w.gift("bad id", 1, 1));
        rejects(() -> w.gift("valid", 1, 0));
        rejects(() -> w.spend(16));
        rejects(() -> w.spend(-1));
        var spent = w.spend(5);
        var json = Wallet.CODEC.encodeStart(JsonOps.INSTANCE, spent).getOrThrow();
        var reloaded = Wallet.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        check(reloaded.points() == 10 && reloaded.equals(spent) && reloaded.gift("demo:1", 5, 3) == reloaded, "save retains points and replay guard");
        var defaults = Alerts.Options.parse(null);
        check(defaults.sounds() && defaults.particles() && defaults.events().get("comment") == Alerts.Cue.OFF,
            "legacy config gets quiet comment defaults");
        var configured = Alerts.Options.parse(com.google.gson.JsonParser.parseString(
            "{\"sounds\":false,\"cooldownTicks\":25,\"events\":{\"follow\":\"off\"}}").getAsJsonObject());
        check(!configured.sounds() && configured.cooldownTicks() == 25 && configured.events().get("follow") == Alerts.Cue.OFF
            && configured.events().get("gift") == Alerts.Cue.SPARKLE, "partial alert settings preserve other defaults");
        rejects(() -> Alerts.Cue.parse("unknown"));
        var gate = new Alerts.Cooldown();
        check(gate.allow(0, 20) && !gate.allow(19, 20) && gate.allow(20, 20), "cue cooldown boundary");
        gate.reset();
        check(gate.allow(0, 20), "new world clears cooldown");
        check(ViewerText.clean("A\n\u2028\u202E§B", 2).equals("AB"), "viewer text strips layout and formatting controls");
        System.out.println("PASS: bundle, replay, deductions, bounds, spending, codec, alert settings, cooldown and chat sanitation");
        try (var reader = new java.io.InputStreamReader(java.util.Objects.requireNonNull(
            ShopChecks.class.getResourceAsStream("/gift-vault-shop-default.json")), java.nio.charset.StandardCharsets.UTF_8)) {
            var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            var actual = root.getAsJsonObject("shop").entrySet().stream().map(e -> {
                var offer = e.getValue().getAsJsonObject();
                return offer.get("item").getAsString() + "/" + offer.get("quantity").getAsInt() + "/" + offer.get("price").getAsInt();
            }).toList();
            check(actual.equals(java.util.List.of("minecraft:torch/16/5", "minecraft:cooked_beef/4/10",
                "minecraft:oak_log/16/15", "minecraft:iron_ingot/4/25", "minecraft:shield/1/30",
                "minecraft:golden_apple/1/75")), "six starter offers and exact costs in display order");
            check(Alerts.Options.parse(root.getAsJsonObject("alerts")).events().get("like") == Alerts.Cue.PING,
                "fresh setups use the quiet, throttled like ping");
        }
        System.out.println("PASS: six starter offers, prices, display order and throttled like cue");
        for (int width : new int[]{101, 320, 853}) for (int height : new int[]{120, 240, 479}) {
            int crop = Math.min(width, (int) (height * 9L / 16));
            for (int percent : new int[]{0, 15, 100}) {
                var box = PortraitAlerts.Box.layout(width, height, percent, true);
                check(box != null && box.y() > 23 && box.y() + box.height() <= height - 6
                    && box.x() >= (width - crop) / 2 && box.x() + box.width() <= (width + crop) / 2,
                    "banner clears the scoreboard and stays in the portrait crop");
            }
        }
        TikToolsChecks.run();
        ConfigChecks.run();
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable r) {
        try { r.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("expected validation failure");
    }
}
