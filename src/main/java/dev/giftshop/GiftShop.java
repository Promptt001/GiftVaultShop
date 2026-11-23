package dev.giftshop;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import org.slf4j.LoggerFactory;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class GiftShop implements ModInitializer {
    private static final AttachmentType<Wallet> WALLET = AttachmentRegistry.<Wallet>builder()
        .persistent(Wallet.CODEC).copyOnDeath().buildAndRegister(Identifier.parse("fabric_gift_shop:wallet"));
    private Settings settings;
    private final Alerts.Cooldown alertCooldown = new Alerts.Cooldown();

    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(literal("giftshop").requires(GiftShop::allowed).executes(c -> {
                var p = c.getSource().getPlayerOrException();
                if (!ready(p)) return 0;
                p.openMenu(new SimpleMenuProvider((id, inventory, player) -> new ShopMenu(id, p, settings.offers()),
                    Component.literal("Gift Vault Shop")));
                return 1;
            }));
            var sim = literal("giftsim").requires(GiftShop::allowed);
            sim.then(literal("balance").executes(c -> {
                say(c.getSource().getPlayerOrException(), "Balance: " + wallet(c.getSource().getPlayerOrException()).points() + " points");
                return 1;
            }));
            sim.then(literal("gift").then(argument("rule", StringArgumentType.string()).suggests((c, b) -> {
                if (settings != null) settings.gifts().keySet().stream().sorted().filter(s -> s.startsWith(b.getRemaining())).forEach(b::suggest);
                return b.buildFuture();
            }).then(argument("event_id", StringArgumentType.string()).then(argument("count", IntegerArgumentType.integer(1, 999))
                .then(argument("viewer", StringArgumentType.greedyString()).executes(c -> gift(c.getSource().getPlayerOrException(),
                    StringArgumentType.getString(c, "rule"), StringArgumentType.getString(c, "event_id"),
                    IntegerArgumentType.getInteger(c, "count"), StringArgumentType.getString(c, "viewer"))))))));
            sim.then(literal("comment").then(argument("viewer", StringArgumentType.string()).then(argument("message", StringArgumentType.greedyString()).executes(c ->
                activity(c.getSource().getPlayerOrException(), "comment", ViewerText.clean(StringArgumentType.getString(c, "viewer"), 48) + ": " + ViewerText.clean(StringArgumentType.getString(c, "message"), 220))))));
            sim.then(literal("like").then(argument("count", IntegerArgumentType.integer(1, 1_000_000)).then(argument("viewer", StringArgumentType.greedyString()).executes(c ->
                activity(c.getSource().getPlayerOrException(), "like", ViewerText.clean(StringArgumentType.getString(c, "viewer"), 48) + " sent " + IntegerArgumentType.getInteger(c, "count") + " likes")))));
            for (String kind : new String[]{"follow", "share", "join"}) {
                String action = switch (kind) { case "follow" -> " followed"; case "share" -> " shared the LIVE"; default -> " joined the LIVE"; };
                sim.then(literal(kind).then(argument("viewer", StringArgumentType.greedyString()).executes(c ->
                    activity(c.getSource().getPlayerOrException(), kind, ViewerText.clean(StringArgumentType.getString(c, "viewer"), 48) + action))));
            }
            dispatcher.register(sim);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try { settings = Settings.load(); }
            catch (Exception e) {
                settings = null;
                LoggerFactory.getLogger("Gift Vault Shop").error("Invalid gift-vault-shop.json; shop and simulator disabled until corrected", e);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> { settings = null; alertCooldown.reset(); });
    }

    private static boolean allowed(CommandSourceStack source) { return source.getEntity() instanceof ServerPlayer p && allowed(p); }
    static boolean allowed(ServerPlayer p) {
        var server = p.level().getServer();
        var profile = server.getSingleplayerProfile();
        return !server.isDedicatedServer() && !server.isPublished() && profile != null && profile.id().equals(p.getUUID());
    }
    static Wallet wallet(ServerPlayer p) { return p.getAttachedOrElse(WALLET, Wallet.EMPTY); }
    static void wallet(ServerPlayer p, Wallet state) { p.setAttached(WALLET, state); }
    private boolean ready(ServerPlayer p) {
        if (settings != null) return true;
        say(p, "Invalid config/gift-vault-shop.json. Check the game log, fix it and reopen the world.");
        return false;
    }
    static void say(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal("[Gift Vault] " + text)); }
    private int activity(ServerPlayer p, String kind, String text) {
        if (!ready(p)) return 0;
        present(p, kind, text, null);
        return 1;
    }
    private void present(ServerPlayer p, String kind, String text, Alerts.Cue override) {
        if (settings.chat().contains(kind)) {
            var color = switch (kind) {
                case "gift" -> net.minecraft.ChatFormatting.GOLD;
                case "follow", "share" -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
                default -> net.minecraft.ChatFormatting.AQUA;
            };
            p.sendSystemMessage(Component.literal("[TikTok · Test] ").withStyle(color)
                .append(Component.literal(text).withStyle(net.minecraft.ChatFormatting.WHITE)));
        }
        var options = settings.alerts();
        var cue = override == null ? options.events().get(kind) : override;
        if (cue != Alerts.Cue.OFF && (options.sounds() || options.particles())
            && (options.sounds() || cue != Alerts.Cue.PING)
            && alertCooldown.allow(p.level().getServer().getTickCount(), options.cooldownTicks())) cue.play(p, options);
    }
    private int gift(ServerPlayer p, String key, String id, int count, String viewer) {
        if (!ready(p)) return 0;
        var rule = settings.gifts().get(key);
        if (rule == null) { say(p, "Unknown gift rule: " + key); return 0; }
        var before = wallet(p);
        Wallet after;
        try { after = before.gift(id, rule.points(), count); }
        catch (IllegalArgumentException e) { say(p, "Invalid gift event identifier (1–128 letters, digits, dots, underscores, colons or hyphens; starts with a letter or digit)."); return 0; }
        if (before == after) { say(p, "Already processed: " + id); return 0; }
        wallet(p, after);
        boolean applied = rule.effect() != null && p.addEffect(new MobEffectInstance(rule.effect(), rule.seconds() * 20, rule.level() - 1));
        int delta = after.points() - before.points();
        present(p, "gift", ViewerText.clean(viewer, 48) + " sent " + count + " × " + key + " — " + (delta > 0 ? "+" : "") + delta + " points"
            + (applied ? " · effect applied" : "") + ". Thanks!", rule.cue());
        return 1;
    }
}
