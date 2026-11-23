# Single-player playtest

Use a disposable Minecraft Java 26.2 Fabric world, not your existing stream save. Use a fresh Fabric instance for the default-catalog check, or adopt the new `shop` section as described in [Configuration](CONFIGURATION.md). Opening a different world in the same instance does not replace its configuration. These are manual checks; the build does not run them.

| Try this | Expect this |
| --- | --- |
| `/giftsim gift rose playtest-1 3 Alex`, then repeat it | One credit of 15 points, one gift cue, duplicate rejected |
| `/giftsim gift heart playtest-2 1 Alex`, then `/giftsim gift spark playtest-3 1 Alex` | Speed and +10 points, then Glowing and −3 points; see the actual effects in Minecraft |
| `/giftsim comment Alex Hello!`, `/giftsim follow Jamie`, `/giftsim like 25 Jamie`, `/giftsim share Jamie`, `/giftsim join Jamie` | All five messages in chat; comment, like and join are quiet. Leave a second between commands when checking each cue. |
| Open `/giftshop`; inspect all six offers and the balance | Item names, quantities and prices visible without hovering; offers say Ready or show the shortfall |
| Open `/giftshop`, choose torches, then click Cancel or press Esc | Nothing delivered or charged |
| Review torches and double-click Buy | 16 torches, one debit of five points, and the shop returns to browsing; the villager confirmation sounds different from the gift chime. With a free hotbar slot, check whether the torches appear before or only after closing the shop |
| Fill all 36 main inventory slots with items other than torches and try to buy more | Clear refusal; points and inventory unchanged |
| Try an offer that costs more than the current balance | No purchase |
| Save and quit, reopen, then check balance and repeat `playtest-1` | Points and items persist, and the old gift is still rejected |
| Open another local world | A fresh wallet; the same simulated ID can be used in that world |
| Turn off sound and then particles in separate trials, reopening between each | Each switch works without hiding chat or changing gift rewards |
| Open the world to Local Area Network (LAN) | Shop and simulator cannot be used |

If something fails, note the command, expected and actual results, loader and mod versions, and attach the relevant lines from `logs/latest.log` after removing account or private information. A normal save/reopen does not verify abrupt shutdown or crash recovery.

## TikTools on your stream

Follow [read-only setup](LIVE-CONNECTION.md) first. With a disposable world, verify the status command, literal comments, each activity type, gift recipient and finalized count, and unchanged points. Briefly interrupt the network and resume it: expect a visible gap warning and resumed delivery for new events, not guaranteed backfill. In a separate rewards-mode trial, confirm that a mapped gift affects only your local player, a streak awards exactly its final count, and the saved wallet survives Save and Quit. If TikTools resends an already recorded transaction, expect no second award. If you cannot observe repeat delivery on demand, the local WebSocket build check covers a simulated repeat; do not record that as a live pass. Verify leaving the world and opening it to the Local Area Network both stop live input.
