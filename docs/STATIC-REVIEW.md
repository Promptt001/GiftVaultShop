# Static review — Gift Vault Shop 0.2.0

This review followed local commands through configuration, gift scoring, chat presentation, shop confirmation, inventory delivery, and wallet serialization. It did not start a new Minecraft session or send real events. The separate [playtest](PLAYTEST.md) covers the parts that require the game.

| Priority | Finding | Disposition |
| --- | --- | --- |
| Blocks live use | `GiftShop` only accepts locally simulated commands. There is no TikTok or Livecade input. A provider may resend gift updates or report a growing combo total; treating every update as final would over-credit gifts. | Documented the [live connector requirements](LIVE-CONNECTION.md). No listener or provider added. |
| Before live use | `Wallet` retains each processed gift identifier and copies that set on credit. As the world’s history grows, so do processing cost and save size. Simply removing old IDs would let a late retry award points again. | Keep stable replay behavior for the preview. Choose retention only after verifying the provider's replay contract. |
| Before live use | Every enabled activity writes a chat line. Busy joins or likes can bury actual messages. A shared cue cooldown can also let a like win over a gift if it arrives first. | Kept the existing per-type chat switches and added independent sound and particle controls and a cue cooldown. A busy-stream display policy and priority between cues are future work. |
| Fixed | Viewer text allowed Unicode line and paragraph separators, which can interfere with a one-line chat display. | Strip the extra separators from viewer text. Text is displayed literally, not interpreted as commands or clickable markup. |
| Verify in game | `ShopMenu` consumes confirmation once and checks funds and complete inventory fit before mutation. Wallet and inventory share the player save path, but the standalone save test only encodes and decodes a wallet. | Verify clicks, full inventory, save/reopen, world isolation and LAN refusal. Do not infer crash durability from the shared save path or the codec test. |
| Verify in game | Cues are sent only to the owner. Actual audibility and visibility depend on Minecraft volume, particles, and the scene in front of the player. | Test sound and particles independently and try the cooldown in a burst. |
| Preview limitation | Simulator commands can change wallet data, and the current simulator and any future live feed are not isolated by code yet. | Use a disposable world for testing. Require separate test and live identifiers before enabling a real feed. |

The wallet checks cover both balance bounds, duplicate event IDs, invalid spend and gift inputs, and retention of identifiers through saving and loading. The new standalone checks cover partial and legacy alert settings, invalid cue names, the cooldown boundary and reset, and text cleanup. They are not a substitute for the in-game checks above.
