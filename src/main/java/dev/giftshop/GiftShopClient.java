package dev.giftshop;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class GiftShopClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(ShopMenu.TYPE, ShopScreen::new);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
            ClientCommands.literal("giftgui").executes(context -> {
                var source = context.getSource();
                var client = source.getClient();
                var server = client.getSingleplayerServer();
                if (server == null || !server.isRunning()) {
                    source.sendError(Component.literal("Gift Vault settings are available only in your private world."));
                    return 0;
                }
                var owner = source.getPlayer().getUUID();
                server.execute(() -> {
                    try {
                        var draft = GiftShop.instance().openEditor(server, owner);
                        client.execute(() -> {
                            if (client.getSingleplayerServer() == server)
                                client.gui.setScreen(new ConfigScreen(server, owner, draft));
                        });
                    } catch (Exception e) {
                        client.execute(() -> {
                            if (client.getSingleplayerServer() == server)
                                source.sendError(Component.literal("Could not open Gift Vault settings: " + e.getMessage()));
                        });
                    }
                });
                return 1;
            })));
        var portrait = new PortraitAlertRenderer();
        ClientPlayNetworking.registerGlobalReceiver(AlertBannerPacket.TYPE, (alert, context) -> context.client().execute(() -> portrait.receive(alert)));
        ClientPlayNetworking.registerGlobalReceiver(LikeScorePacket.TYPE, (score, context) -> context.client().execute(() -> portrait.score(score.total())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(portrait::clear));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.parse("fabric_gift_shop:portrait_alerts"),
            (graphics, ticks) -> portrait.draw(graphics));
    }
}
