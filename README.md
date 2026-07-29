# Barnyard Blitz

A cosy farm merge game in the mould of Gossip Harbor, with a Bejeweled-style
match-3 round bolted on as its energy minigame. Merge eggs into baskets into
carts into henhouses, fill the neighbours' orders, spend the takings on doing
up Gran's farm, and find out who keeps leaving the gate open.

Everything you see is drawn at run time — the animals, the items, the barn,
the sky and the sound effects are all generated in code, so there are no
assets to ship. pygame is the only dependency.

It runs on the desktop and on Android from the same `main.py`; the layout
rearranges itself between landscape and portrait.

## Running it

```bash
pip install -r requirements.txt
python main.py
```

Python 3.10 or newer. The window is resizable — the board, the fonts and the
panels all rescale with it.

## The loop

**Tap a generator** to spend energy on a new item. **Drag one item onto a
matching one** to merge them into the next tier. Every chain climbs the same
six-step ladder, so an unfamiliar item's tier is readable from its silhouette:

| Tier | 0 | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- | --- |
| Shape | the thing | a bundle | a basket | a crate | a cart | a building |
| Eggs | Egg | Egg Trio | Egg Basket | Egg Crate | Egg Cart | Henhouse |

Six chains unlock as you level up — Eggs, Crops, Dairy, Wool, Tools and
Preserves — each with its own generator.

**Customers queue along the top.** Tap one to see what they want; deliver it
for coins and XP. Skip the ones you can't be bothered with and another
neighbour wanders up. Coins pay for **renovation tasks**, and finishing a
chapter's tasks plays the next scene of the story.

**Energy** is the pacing valve: it trickles back at one point every 25 seconds
up to a cap of 60, or you can go and earn a chunk of it by playing a round of
**Blitz**.

Items you're hoarding can go in **storage** (8 slots), and anything you're
done with can be **sold** for coins.

## The Blitz minigame

The original match-3 round, now worth energy and coins. Tap two neighbours or
swipe one into the other to line up three or more of the same critter.

| You match | You get | It does |
| --- | --- | --- |
| 3 in a row | — | clears the three |
| 4 in a row | **Golden Egg** | blasts the surrounding 3×3 |
| an L or T shape | **Hay Bale** | clears the whole row *and* column |
| 5 in a row | **Prize Rooster** | swap it onto any animal to clear that species |

Specials caught in someone else's blast set each other off. Cascades raise a
chain multiplier up to ×10. When the 60 seconds run out, any specials left on
the board go off in one last hurrah, and the score converts into energy and
coins for the farm. There's an untimed Relaxed mode too, which pays nothing
and is purely for fun.

## Controls

Everything is tappable, so a phone needs nothing else. On the desktop there
are keys too: `S` for the story, `B` for Blitz, `M` to mute, `Esc` (or the
Android back button) to go back a screen. In a Blitz round, `P` pauses and `R`
restarts.

The farm saves itself continuously — every 20 seconds, on every screen change,
and when Android backgrounds the app — to
`~/.local/share/barnyard-blitz/farm.json` (or under `$XDG_DATA_HOME`). On
Android it goes to the app's private storage, so the game asks for no
permissions at all.

## Building the Android APK

Packaging is handled by [buildozer](https://buildozer.readthedocs.io), which
drives python-for-android's SDL2 bootstrap and its `pygame` recipe.
`buildozer.spec` is checked in and configured for a portrait, full-screen,
arm64 + armv7 build.

**In CI (recommended).** Run the *Android APK* workflow from the Actions tab
(`workflow_dispatch`, pick `debug` or `release`). It installs the toolchain,
caches the SDK/NDK between runs and uploads the APK as a build artifact. A
cold run takes roughly 45–60 minutes because it compiles CPython for the
device; cached runs are far quicker.

**Locally**, on Linux with a JDK 17 installed:

```bash
pip install "cython==0.29.36" buildozer
python tools/make_assets.py     # regenerates assets/icon.png + presplash.png
buildozer android debug         # writes bin/barnyardblitz-*-debug.apk
buildozer android debug deploy run    # install and launch on a connected device
```

Buildozer downloads the Android SDK and NDK on first use, so the machine needs
network access to `dl.google.com` and a few GB of free disk.

> **Not verified end to end.** The game has been tested at phone resolutions,
> but no APK has been produced yet — this repo's development environment
> blocks `dl.google.com`, so the SDK and NDK cannot be fetched there. The CI
> workflow is the intended way to get a real APK and its first run is what
> will confirm the toolchain pins.

## Layout

```
main.py                    entry point, desktop and Android alike
buildozer.spec             Android packaging config
barnyard/
  app.py                   display, shared services, scene switching, toasts
  ui.py                    shared widgets: planks, buttons, cards, wrapping
  layout.py                resolution-independent geometry for both screens
  config.py                tuning constants and the palette
  platform.py              Android detection and per-platform paths
  art.py                   animals, portraits, tiles and scenery
  mergeart.py              merge items: six containers x six motifs
  audio.py                 synthesised sound effects (no files, no numpy)
  effects.py               particles, popups, screen shake
  board.py                 match-3 rules
  merge/
    items.py               chain and generator definitions
    board.py               the merge grid, storage and selling
    economy.py             energy, coins, XP and levelling
    orders.py              customer orders
    story.py               chapters, renovation tasks and dialogue
    session.py             the save-able game state and cross-system rules
    save.py                JSON persistence
  scenes/
    farm.py                the merge yard - the home screen
    blitz.py               the match-3 minigame
    story.py               chapters, tasks and dialogue scenes
assets/                    generated launcher icon and splash screen
tools/make_assets.py       regenerates those two PNGs
tests/                     rules tests
.github/workflows/android.yml   builds the APK
```

Both rules layers (`barnyard/board.py` and everything under `barnyard/merge/`)
are free of pygame imports, so they can be exercised headlessly:

```bash
python tests/test_board.py     # match-3 rules
python tests/test_merge.py     # merge, economy, orders, story, saving
# or: pytest tests
```
