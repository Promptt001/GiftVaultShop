# Connect your TikTok LIVE with TikTools

**TikTools is the only live-event provider in Gift Vault Shop.** Keep sending picture and sound from Open Broadcaster Software (OBS) through Restream to TikTok as before. Minecraft makes its own outbound encrypted WebSocket connection, a connection that stays open so events arrive as they happen. It needs no inbound port, relay server, TikTok password or Restream stream key.

TikTools is a third-party service, not TikTok. Check its terms, TikTok's rules for your account and your obligations for handling viewer data. [TikTools Basic](https://tik.tools/pricing) is the intended plan for one broadcaster; the listed eight-hour limit is per connection. Confirm pricing and limits when you subscribe. Paying does not guarantee recovery of missed gifts.

## Optional first test: read-only

1. Copy [the example](../examples/gift-vault-tiktools.json) to `config/gift-vault-tiktools.json` in the Minecraft instance. Set `mode` to `read_only` and `username` to your own TikTok handle. Leave the recipient and mapping alone for this check. A missing file or `off` makes no connection.
2. Set `TIKTOOLS_API_KEY` in the environment of the **Minecraft game process**. Do not put the key in JSON, source control, screenshots or commands typed into chat. On Debian with Bash, close any already-running launcher. From a private terminal, run the following, type the key at the prompt, then start your usual launcher **from that same shell**. Some launchers manage child environments separately; check that the game inherits the variable.

   ```bash
   read -rsp 'TikTools key: ' TIKTOOLS_API_KEY; printf '\n'
   export TIKTOOLS_API_KEY
   # Start your usual Minecraft launcher from this shell.
   ```

   The key is used in the encrypted provider connection as required by TikTools. Clear it from the shell with `unset TIKTOOLS_API_KEY` after launching; this does not remove it from already-started processes. Avoid enabling detailed network logging or sharing full logs without checking them.
3. Open a disposable private single-player world, start your LIVE independently, and run `/giftlive status`. Expect `LIVE room connected` once TikTools reports the room. If the world is paused, resume play to let Minecraft process queued events. Dedicated and published worlds are not supported.
4. Check that comments, follows, likes, shares, joins and finalized gifts appear as `[TikTok · LIVE]` when their chat flags are enabled. Likes are delivered in batches, not one line per click. Read-only uses the configured display, sound and particle settings, but never edits the wallet or applies a gift status effect. `/giftsim` remains separate and **does** edit the local wallet.
5. With a controlled gift test, check `/giftlive status` for the last finalized gift's numeric gift ID, recipient ID, count whether a transaction ID was present, and whether the gift was applied or why it was skipped. Verify the recipient independently as your own numeric TikTok user ID, particularly if streaming with guests. Test a single gift and a streak; check your balance with `/giftsim balance` before and after. This command does not display the key or raw transaction ID.

## Set up wallet rewards

In the same file, set `recipientUserId` to your verified numeric ID **in quotes**, and map each observed TikTools gift ID to an existing rule in `gift-vault-shop.json`. With these fields and a valid username, you can omit `mode` to use rewards, or set it explicitly to `rewards`. An explicit `read_only` or `off` stays in effect after upgrading; remove that line or change its value only if you want rewards. No file means off, and an incomplete configuration does not start a connection. For example:

```json
{
  "username": "your_tiktok_username",
  "recipientUserId": "1234567890123456789",
  "giftRules": {"5655": "rose"}
}
```

Replace the example recipient; the [TikTools event example](https://tik.tools/websocket) shows `5655` for Rose, but verify what your feed sends. Reopen the world after edits. Gift names and viewer text cannot pick commands, recipients, points or items. The mod only processes a gift if it has `repeatEnd: true`, a final integer count from 1 to 999, the configured recipient, a mapped gift ID and a usable `transactionId`. A gift missing any of these never changes points or applies a gift status effect. If your feed does not reliably provide those fields, stay in `read_only`. The mod does not substitute a timestamp, gift name, or message ID for a transaction ID.

Each transaction is recorded with the wallet and ignored if it is sent again under the same ID. A live gift awards ten times the positive points in its local rule, per gift unit. A Rose mapped to a 5-point rule awards 50 points; a streak of three awards 150. Negative and zero rules, effects and simulator values are unchanged. Points never go below zero; no real-money debit occurs. Use the [playtest checklist](PLAYTEST.md) before switching on rewards in a valued world.

## LIVE Likes and points

The portrait scoreboard shows the latest reported room total once TikTools sends it, including in read-only mode. In rewards mode, the first valid like batch for a room establishes a baseline. Later batches can earn one point per new like, up to the smaller of the batch size and the increase in room total. This avoids paying for old likes, a replayed batch or unobserved batches after a gap. Batches without a usable room total or batch size do not earn points. The baseline is saved with your wallet so reopening a world does not reset it. Simulated likes do not change the scoreboard or wallet.

## Disconnects and busy streams

The mod reconnects with delays, stops retrying on explicit authentication rejection, and closes the connection when leaving the world or publishing it to the local network. `/giftlive status` reports connection state, possible gaps and dropped activity messages. A disconnect can lose events even when the reconnect works: TikTools does not document full replay of this event feed. A gift backlog overflow pauses rewards until you reopen the world. Activity may be discarded when its separate queue fills. Keep the game unpaused while streaming and check the status after an interruption. The 0.7.0 update has not been playtested on your broadcast.
