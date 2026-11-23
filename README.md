# Gift Vault Shop

**Give your TikTok LIVE a place in your Minecraft world.** A Rose can fill your points wallet, a Heart can give you Speed and a burst of hearts, and a mischievous Spark can take a few points and make you glow. Spend those points in a chest-style shop when you’re ready. Viewer comments and activity appear in Minecraft chat, so you can keep playing instead of looking at another screen.

> **0.2.0 offline preview:** The shop, configurable gifts, chat, sounds and particles are available through local simulation. Real TikTok LIVE events are **not connected**. This mod does not currently use Livecade. See [Live connection](docs/LIVE-CONNECTION.md) for what a provider needs to support.

## Install and try it in two minutes

1. Use **Minecraft Java Edition 26.2**, **Java 25**, and **Fabric Loader 0.19.5 or newer** for Minecraft 26.2. Put the mod archive and **Fabric API 0.160.0+26.2 or newer for 26.2** in that instance’s `mods` folder. Fabric API is the library the mod uses to work with Minecraft. Do not use the Paper Gift Vault plugin here.
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

4. Choose torches and click the emerald to buy eight for five points, or the barrier to cancel. Hover over the sunflower to see your balance. Try `/giftsim balance` out of the shop.

Chat messages marked `[TikTok · Test]` are simulated. Simulator gifts do change the world’s real in-game wallet. Use a new identifier for each new gift; repeating an identifier does not award or deduct points again. The balance never goes below zero, and no real money is used.

## Make it yours

After opening a world once, edit `config/gift-vault-shop.json` and reopen the world. Choose how much each gift adds or deducts, which Minecraft effect it gives, and whether it gets a chime, hearts, sparkles, celebration, smoke, or no cue. Set individual chat types to show or hide, mute sounds or particles separately, and change what the shop sells. The default setup keeps comments and joins quiet without hiding them.

- [Configuration reference](docs/CONFIGURATION.md) — gift rules, shop offers, sound and particle cues, and chat switches.
- [Playtest checklist](docs/PLAYTEST.md) — take the shop, wallet and cues through a real Minecraft session.
- [Live connection](docs/LIVE-CONNECTION.md) — the Livecade question and what’s missing for real events.
- [Static review](docs/STATIC-REVIEW.md) — what the source review found, what changed, and what still needs testing.

## Build from source

Install Java Development Kit 25. From the repository root, run `./gradlew build` on Linux or macOS, or `gradlew.bat build` on Windows. The wrapper downloads Gradle 9.6.1 on first use, and the build downloads Minecraft and Fabric dependencies. The mod archive is `build/libs/fabric-gift-shop-0.2.0.jar`. Use `./gradlew runClient` for a development client.

The build runs checks for the wallet, duplicate handling, saving and loading wallet data, alert settings and cooldown, and viewer text cleanup. It does not exercise the Minecraft window, play audio, or connect to a real stream. The chest-shop layout was inspired by [LeafClaims](https://github.com/Promptt001/LeafClaims); no LeafClaims code or artwork is included.

The offline preview download includes the repository files, the built mod archive, and `GiftVaultShop.bundle`. A Git bundle carries the commit history without a remote server. To get a working clone with that history, run `git clone GiftVaultShop.bundle gift-vault-shop` from the extracted folder. This project has not been published to GitHub, and no open-source license has been selected.
