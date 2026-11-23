# Configuration

This file covers local game rules. For the separate `gift-vault-tiktools.json` and how to map actual TikTools gift IDs to those rules, see [Live connection](LIVE-CONNECTION.md).

Open a single-player world once to generate `config/gift-vault-shop.json` in your Fabric instance. Back up the file and save, edit the file, then leave and reopen the world. Settings apply to the whole instance; balances and processed gift identifiers are stored per world. See the [shipped defaults](../src/main/resources/gift-vault-shop-default.json) for a complete valid JavaScript Object Notation (JSON) example.

## Gifts and shop

A gift rule can award points, deduct points, apply a Minecraft status effect, or combine all three. This example deducts three points per gift, attempts ten seconds of Glowing I once per event, and uses a soft descending cue with smoke:

```json
"spark": { "points": -3, "effect": "minecraft:glowing", "seconds": 10, "level": 1, "cue": "drain" }
```

- `points` is an integer from −1,000,000,000 to +1,000,000,000. It defaults to zero and is multiplied by the bundle count. The wallet stays between zero and 1,000,000,000. Points are not TikTok Coins, Diamonds, or money.
- Omit `effect` for points only. `seconds` is 1–300 (default 15); `level` is 1–5 (default 1). Minecraft decides whether an existing status effect is replaced. Some Minecraft effects can harm your character, so try new rules in a disposable save first.
- A `cue` overrides the general gift cue. Rule names can contain lowercase letters, digits, underscores, hyphens, or dots.
- Each shop entry has an `item` identifier, positive `price` in points, and optional `quantity` (default one, at most one stack). The declaration order is the shop display order. The shop shows six offers per page, with the balance at the top. Review shows the cost and resulting balance before you confirm.

### Default catalog

| Offer | Price |
| --- | ---: |
| 16 torches | 5 points |
| 4 cooked beef (steak) | 10 points |
| 16 oak logs | 15 points |
| 4 iron ingots | 25 points |
| 1 shield | 30 points |
| 1 golden apple | 75 points |

A fresh configuration gets these offers. Existing files are never rewritten. To keep your gift and alert settings but adopt the new offers, replace only the `shop` section using `examples/gift-vault-shop.json` from the download, then reopen the world. This does not reset points. Set your existing `alerts.events.like` to `off` if you also want the new quiet-like default.

## Chat, sounds and particles

Set individual `chat` switches to `false` to hide `comment`, `follow`, `like`, `share`, `join`, or `gift`. All six are shown by default. Chat, presentation cues, and the wallet are independent: hiding a gift message does not mute its cue or disable its reward.

| Cue | What it plays |
| --- | --- |
| `off` | Nothing |
| `ping` | A short chime |
| `hearts` | A note and heart particles |
| `sparkle` | An amethyst chime and bright particles |
| `celebrate` | A level-up sound and green particles |
| `drain` | A low note and smoke |

In `alerts`, set `sounds` or `particles` to `false` independently. `events` overrides a cue by event type. The default shared cooldown of `20` game ticks (roughly one second at normal speed) can be set from `5` to `200`. Events within that interval still appear in chat and gifts still apply; only the cue is skipped. Cues are not queued. Successful purchases play a short villager confirmation, controlled by the same `sounds` switch but separate from the gift cooldown. A cancellation or refusal does not play it. Existing files without an `alerts` section use preset event cues; add `cue` to older gift entries if you want the per-gift overrides from the new default file.

If the shop reports invalid configuration, check `logs/latest.log`. You can move the bad file aside and reopen the world to regenerate defaults without resetting your wallet.

## Portrait alerts for TikTok

The banner sits inside a centred, full-height 9:16 crop. It is drawn by Minecraft; you do not need a separate stream overlay. To move it clear of TikTok's controls, back up `config/gift-vault-shop.json`, then add or edit this **top-level** section and reopen the world. Leave the existing `shop`, `gifts` and `alerts` sections in place.

```json
"portraitAlerts": {
  "enabled": true,
  "verticalPercent": 15,
  "events": {
    "gift": true,
    "follow": true,
    "share": true,
    "like": true,
    "comment": false,
    "join": false
  }
}
```

`verticalPercent` is a whole number from `0` (top) to `100` (bottom). The default is `15`. Increase it to move the banner down; try `25` or `35` if the top of your stream is busy. Keep the game interface visible. The banner is hidden while the game is paused, and a very small game view can leave too little room to draw it.

If the section is missing, the defaults above apply without the mod rewriting your file. You can set individual event types or turn all banners off with `enabled: false`. These switches do not change chat, sounds, particles or rewards. Gift banners take priority over activity, and likes are grouped without displaying individual names. The display queue is capped and drops old banners during bursts; this has no effect on rewards. A rewarded gift shows the actual points change, while a read-only gift shows no points line. Simulated banners carry a **Test** label.
