package dev.giftshop;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public final class GiftShopClient implements ClientModInitializer {
    @Override public void onInitializeClient() { MenuScreens.register(ShopMenu.TYPE, ShopScreen::new); }
}
