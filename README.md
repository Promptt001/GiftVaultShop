# Gift Vault Shop

**Give your TikTok LIVE a place in your Minecraft world.** A Rose can fill your points wallet, a Heart can give you Speed and a burst of hearts, and a mischievous Spark can take a few points and make you glow. Spend those points in a shop with prices shown on every item when you’re ready. Viewer comments and activity appear in Minecraft chat, so you can keep playing instead of looking at another screen.

> **0.6.0 in-game settings preview:** Connect your live chat and activity through TikTools. The connection is off by default; try [read-only setup](docs/LIVE-CONNECTION.md) before enabling gift rewards. Live delivery has not been verified on a real broadcast.

## Install and try it in two minutes

1. Use **Minecraft Java Edition 26.2**, **Java 25**, and **Fabric Loader 0.19.5 or newer** for Minecraft 26.2. Remove the older Gift Vault Shop archive if you have one. Put `mods/fabric-gift-shop-0.6.0.jar` from the download and **Fabric API 0.160.0+26.2 or newer for 26.2** in that instance’s `mods` folder. Fabric API is the library the mod uses to work with Minecraft. Do not use the Paper Gift Vault plugin here.
2. Start Fabric and open a disposable single-player world. Don’t open it to your local network for this demo.
3. Enter these commands, allowing about a second between them if you want to see and hear each alert:

   ```text
   /giftsim comment Alex That cave looks promising!
   /giftsim follow Jamie
   /giftsim like 25 Jamie
   /giftsim gift rose demo-1 3 Alex
   /giftsim gift heart demo-2 1 Jamie
   /giftsim gift spark demo-3 1 Alex
   /giftshop
   ```

4. Choose torches, then confirm to buy 16 for five points, or cancel to go back. The shop shows your balance, prices and how many more points you need without hovering. Try `/giftsim balance` out of the shop.

Chat messages marked `[TikTok · Test]` are simulated. Simulator gifts do change the world’s real in-game wallet. Use a new identifier for each new gift; repeating an identifier does not award or deduct points again. The balance never goes below zero, and no real money is used.

## Place alerts inside your TikTok crop

Gifts, follows, shares and grouped likes appear near the top centre, inside a full-height 9:16 crop of your game window. Move it under `/giftgui` → **Portrait banners** → **Height**, then choose **Save & apply**. If you edit the file by hand, set `portraitAlerts.verticalPercent` and reopen the world. The default is `15`; increase it to move the banner down. Comments and joins stay in chat unless you enable their banners. Your capture setup can stay the same. See the [position and event settings](docs/CONFIGURATION.md#portrait-alerts-for-tiktok).

## Edit settings without leaving Minecraft

In your private single-player world, enter `/giftgui`. The item icons lead to gift rules, shop offers, chat messages, sound and particles, and portrait banners. Use the page arrows to see more entries or fields. Choose **Save & apply** to write your changes and use them immediately. Closing with unsaved edits asks whether to discard them. You can still [edit the file by hand](docs/CONFIGURATION.md) if you prefer.

## Make it yours

After opening a world once, edit `config/gift-vault-shop.json` and reopen the world. Choose how much each gift adds or deducts, which Minecraft effect it gives, and whether it gets a chime, hearts, sparkles, celebration, smoke, or no cue. Set individual chat types to show or hide, mute sounds or particles separately, and change what the shop sells. The default setup keeps comments, joins and likes quiet without hiding them. A gift sounds different from a successful purchase.

- [Configuration reference](docs/CONFIGURATION.md) — gift rules, shop offers, sound and particle cues, and chat switches.
- [Playtest checklist](docs/PLAYTEST.md) — take the shop, wallet and cues through a real Minecraft session.
- [Live connection](docs/LIVE-CONNECTION.md) — TikTools setup, read-only trial, gift mapping and reconnect limits.
- [Static development review](docs/STATIC-REVIEW-0.4.0.md) — what the shop review found and what to prioritize in your own test.

## Build from source

Install Java Development Kit 25. From the repository root, run `./gradlew build` on Linux or macOS, or `gradlew.bat build` on Windows. The wrapper downloads Gradle 9.6.1 on first use, and the build downloads Minecraft and Fabric dependencies. The mod archive is `build/libs/fabric-gift-shop-0.6.0.jar`. Use `./gradlew runClient` for a development client.

The build runs checks for the wallet, duplicate handling, saving and loading wallet data, alert settings and cooldown, viewer text cleanup, settings saves, and a local WebSocket test for the TikTools connector. It does not exercise the Minecraft window, play audio, or connect to a real stream. The chest-shop and settings layouts were inspired by [LeafClaims](https://github.com/Promptt001/LeafClaims); no LeafClaims code or artwork is included.

The preview download includes the repository files, the built mod archive, and `GiftVaultShop.bundle`. A Git bundle carries the commit history without a remote server. To get a working clone with that history, run `git clone GiftVaultShop.bundle gift-vault-shop` from the extracted folder. This project has not been published to GitHub, and no open-source license has been selected.
