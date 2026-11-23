package dev.giftshop;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class ConfigChecks {
    static void run() throws Exception {
        Path scratch = Path.of(System.getProperty("giftshop.testScratch"));
        Files.createDirectories(scratch);
        Path dir = Files.createTempDirectory(scratch, "config-editor-");
        Path path = dir.resolve("gift-vault-shop.json");
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap(); // Registries only: no client, server or world is started.
            // Load only the stack limit used by this test; no world or resource packs are opened.
            var torch = net.minecraft.world.item.Items.TORCH.builtInRegistryHolder();
            if (!torch.areComponentsBound()) torch.bindComponents(net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build());
            var baseline = """
                {"gifts":{"rose":{"points":5,"custom":"keep"}},
                 "shop":{"torches":{"item":"minecraft:torch","price":5,"quantity":16}},
                 "other":{"data":[1,2]}}
                """;
            Files.writeString(path, baseline);
            var draft = ConfigDraft.open(path);
            check(!draft.dirty(), "opening a draft has no changes");
            var rose = draft.root.getAsJsonObject("gifts").getAsJsonObject("rose");
            ConfigDraft.number(rose, "points", "7");
            var banner = ConfigDraft.section(draft.root, "portraitAlerts");
            ConfigDraft.number(banner, "verticalPercent", "101");
            check(draft.dirty() && Files.readString(path).equals(baseline), "draft must not affect the file");
            try {
                draft.save(path, draft.root, Settings::parse);
                throw new AssertionError("expected invalid height to fail");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("verticalPercent"), "reject the invalid height");
            }
            check(Files.readString(path).equals(baseline), "invalid save preserves bytes");
            ConfigDraft.number(banner, "verticalPercent", "35");
            var applied = draft.save(path, draft.root, Settings::parse);
            var saved = ConfigDraft.open(path);
            check(applied.gifts().get("rose").points() == 7 && applied.portrait().verticalPercent() == 35,
                "validated settings match successful save");
            var original = JsonParser.parseString(baseline).getAsJsonObject();
            check(saved.root.getAsJsonObject("shop").equals(original.getAsJsonObject("shop"))
                && saved.root.get("other").equals(original.get("other"))
                && saved.root.getAsJsonObject("gifts").getAsJsonObject("rose").get("custom").getAsString().equals("keep"),
                "unknown, unedited and nested settings survive round trip");
            var external = Files.readString(path) + "\n";
            Files.writeString(path, external);
            try {
                saved.save(path, saved.root, Settings::parse);
                throw new AssertionError("expected stale draft to fail");
            } catch (IOException expected) {
                check(expected.getMessage().contains("changed outside"), "stale edit explains what to do");
            }
            check(Files.readString(path).equals(external), "stale save preserves external edit");
            var offer = ConfigDraft.open(path);
            var entry = offer.root.getAsJsonObject("shop").getAsJsonObject("torches");
            ConfigDraft.number(entry, "quantity", "65");
            try {
                offer.save(path, offer.root, Settings::parse);
                throw new AssertionError("expected stack quantity rejection");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains("quantity"), "uses actual item stack limit");
            }
            check(Files.readString(path).equals(external), "invalid offer preserves file");
            try (var children = Files.list(dir)) {
                check(children.toList().equals(java.util.List.of(path)), "save left a temporary file");
            }
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir);
        }
        System.out.println("PASS: settings draft, persisted edits, unknown field preservation, validation and stale-write refusal");
    }

    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
