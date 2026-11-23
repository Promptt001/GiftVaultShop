# Single-player playtest

Use a disposable Minecraft Java 26.2 Fabric world, not your existing stream save. These are manual checks; the build does not run them.

| Try this | Expect this |
| --- | --- |
| `/giftsim gift rose playtest-1 3 Alex`, then repeat it | One credit of 15 points, one gift cue, duplicate rejected |
| `/giftsim gift heart playtest-2 1 Alex`, then `/giftsim gift spark playtest-3 1 Alex` | Speed and +10 points, then Glowing and −3 points; see the actual effects in Minecraft |
| `/giftsim comment Alex Hello!`, `/giftsim follow Jamie`, `/giftsim like 25 Jamie`, `/giftsim share Jamie`, `/giftsim join Jamie` | All five messages in chat; comment and join are quiet. Leave a second between commands when checking each cue. |
| Open `/giftshop`, choose torches, then click the barrier or press Esc | Nothing delivered or charged |
| Review torches and double-click Buy | Eight torches, one debit of five points, and the shop returns to browsing |
| Fill all 36 main inventory slots with items other than torches and try to buy more | Clear refusal; points and inventory unchanged |
| Try an offer that costs more than the current balance | No purchase |
| Save and quit, reopen, then check balance and repeat `playtest-1` | Points and items persist, and the old gift is still rejected |
| Open another local world | A fresh wallet; the same simulated ID can be used in that world |
| Turn off sound and then particles in separate trials, reopening between each | Each switch works without hiding chat or changing gift rewards |
| Open the world to Local Area Network (LAN) | Shop and simulator cannot be used |

If something fails, note the command, expected and actual results, loader and mod versions, and attach the relevant lines from `logs/latest.log` after removing account or private information. A normal save/reopen does not verify abrupt shutdown or crash recovery.
