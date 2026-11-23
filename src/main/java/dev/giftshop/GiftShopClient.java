package dev.giftshop;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class GiftShopClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MenuScreens.register(ShopMenu.TYPE, ShopScreen::new);
        var portrait = new PortraitAlertRenderer();
        ClientPlayNetworking.registerGlobalReceiver(AlertBannerPacket.TYPE, (alert, context) -> context.client().execute(() -> portrait.receive(alert)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(portrait::clear));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.parse("fabric_gift_shop:portrait_alerts"),
            (graphics, ticks) -> portrait.draw(graphics));
    }
}
