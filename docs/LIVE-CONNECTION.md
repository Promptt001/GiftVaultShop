# Connecting a TikTok LIVE

**This build does not connect to TikTok or Livecade.** There are no account settings, network listeners, or incoming provider callbacks in the mod. `[TikTok · Test]` messages are generated locally. Your streaming software sends video and audio; the event source is a separate connection.

## Can Livecade supply the events?

Not from an interface we could verify in its public documentation. The [Livecade action trigger guide](https://livecade.io/docs/action-triggers/) describes actions inside Livecade games; its [comparison page](https://livecade.io/livecade-vs-tikfinity/) distinguishes those games from integrations that control external games. An overlay or simulator does not establish that Livecade can deliver events to a custom Fabric mod. Private or plan-specific access is unknown.

Before picking a provider, ask whether it supports a documented external event feed that works when the broadcast starts through Restream. Ask which events it forwards: comments, follows, likes, shares, joins, and gifts may have different coverage. For gifts, confirm stable identifiers across retries, gift type, the final count for bundled gifts, and how the feed distinguishes partial updates from a final total. Also confirm how it verifies the broadcaster and whether there is a sandbox that requires no paid gifts.

## What a connector must do before it can change a wallet

1. Verify the event source and broadcaster; only accept events for the configured account and currently open local world. Do not use a video stream key as a credential for this mod.
2. Queue work on the integrated Minecraft server thread and discard stale messages when changing worlds. Translate only known gift types to local rules; viewer text must never select a command, item, price, or player.
3. Process finalized gift bundles once. Use separate identifiers for live and test events. Set a documented replay window and retention plan before replacing the current saved identifier set; do not silently forget IDs that can still be resent.
4. Keep comments as plain text. Add an optional chat filter or summary for high-volume likes and joins before enabling those feeds on a busy LIVE. Keep gift handling separate from display limits.

A connector and a real broadcast test are future work. Don't enter a TikTok password or stream key into a mod configuration or issue report.
