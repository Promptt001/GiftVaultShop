# Configuration

Open a single-player world once to generate `config/gift-vault-shop.json` in your Fabric instance. Back up the file and save, edit the file, then leave and reopen the world. Settings apply to the whole instance; balances and processed gift identifiers are stored per world. See the [shipped defaults](../src/main/resources/gift-vault-shop-default.json) for a complete valid JavaScript Object Notation (JSON) example.

## Gifts and shop

A gift rule can award points, deduct points, apply a Minecraft status effect, or combine all three. This example deducts three points per gift, attempts ten seconds of Glowing I once per event, and uses a soft descending cue with smoke:

```json
"spark": { "points": -3, "effect": "minecraft:glowing", "seconds": 10, "level": 1, "cue": "drain" }
```

- `points` is an integer from −1,000,000,000 to +1,000,000,000. It defaults to zero and is multiplied by the bundle count. The wallet stays between zero and 1,000,000,000. Points are not TikTok Coins, Diamonds, or money.
- Omit `effect` for points only. `seconds` is 1–300 (default 15); `level` is 1–5 (default 1). Minecraft decides whether an existing status effect is replaced. Some Minecraft effects can harm your character, so try new rules in a disposable save first.
- A `cue` overrides the general gift cue. Rule names can contain lowercase letters, digits, underscores, hyphens, or dots.
- Each shop entry has an `item` identifier, positive `price` in points, and optional `quantity` (default one, at most one stack). The declaration order is the shop display order. The sunflower shows your balance. The emerald confirms and the barrier cancels.

## Chat, sounds and particles

Set individual `chat` switches to `false` to hide `comment`, `follow`, `like`, `share`, `join`, or `gift`. All six are shown by default. Chat, presentation cues, and the wallet are independent: hiding a gift message does not mute its cue or disable its reward.

| Cue | What it plays |
| --- | --- |
| `off` | Nothing |
| `ping` | A short chime |
| `hearts` | A note and heart particles |
| `sparkle` | A note and bright particles |
| `celebrate` | A level-up sound and green particles |
| `drain` | A low note and smoke |

In `alerts`, set `sounds` or `particles` to `false` independently. `events` overrides a cue by event type. The default shared cooldown of `20` game ticks (roughly one second at normal speed) can be set from `5` to `200`. Events within that interval still appear in chat and gifts still apply; only the cue is skipped. Cues are not queued. Existing files without an `alerts` section use preset event cues; add `cue` to older gift entries if you want the per-gift overrides from the new default file.

If the shop reports invalid configuration, check `logs/latest.log`. You can move the bad file aside and reopen the world to regenerate defaults without resetting your wallet.
