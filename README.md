# Barnyard Blitz

A farm-animal match-3 game in the spirit of Bejeweled Blitz, built with Python
and pygame. Swap two neighbouring critters to line up three or more, chain the
cascades, and squeeze as many points as you can out of a sixty-second round.

Everything you see is drawn at run time — the animals, the barn, the sky and
the sound effects are all generated in code, so there are no assets to ship.

It runs on the desktop and on Android from the same `main.py`; the layout
rearranges itself between landscape and portrait.

## Running it

```bash
pip install -r requirements.txt
python main.py
```

Python 3.10 or newer. The window is resizable — the board, the fonts and the
panels all rescale with it.

## How to play

Tap a critter and then tap a neighbour, or swipe one straight into its
neighbour. A swap only sticks if it lines up three or more of the same animal.

| You match | You get | It does |
| --- | --- | --- |
| 3 in a row | — | clears the three |
| 4 in a row | **Golden Egg** | blasts the surrounding 3×3 |
| an L or T shape | **Hay Bale** | clears the whole row *and* column |
| 5 in a row | **Prize Rooster** | swap it onto any animal to clear every one of that species |

Specials caught in someone else's blast set each other off, so a well-placed
Golden Egg next to a Hay Bale can take out a quarter of the barn.

Every clear in an uninterrupted cascade raises the chain multiplier, up to
×10 — cascades are where the big scores live. When the clock runs out, any
specials still sitting on the board go off in one last hurrah for bonus points.

Run out of legal moves and the barnyard reshuffles itself. Sit still for a few
seconds and the game will glow a hint at you.

### Modes

- **Blitz** — 60 seconds on the clock.
- **Relaxed** — no clock, play as long as you like.

### Controls

Pause, Restart, Mute and Menu are on-screen buttons, so a phone needs nothing
else. On the desktop there are keys too:

| Key | |
| --- | --- |
| `1` / `2` | start Blitz / Relaxed from the menu |
| `P` or `Space` | pause |
| `R` | restart the round |
| `M` | mute |
| `Esc` (or Android back) | back to the menu, or quit |

High scores are kept per mode in `~/.local/share/barnyard-blitz/highscores.json`
(or under `$XDG_DATA_HOME` if you set one). On Android they go to the app's
private storage, so the game asks for no permissions at all.

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
buildozer android debug         # writes bin/barnyardblitz-1.0.0-*-debug.apk
buildozer android debug deploy run    # install and launch on a connected device
```

Buildozer downloads the Android SDK and NDK on first use, so the machine needs
network access to `dl.google.com` and a few GB of free disk.

> **Not verified end to end.** The porting work below was tested at phone
> resolutions, but no APK has been produced yet — this repo's build
> environment blocks `dl.google.com`, so the SDK and NDK cannot be fetched
> here. The CI workflow is the intended way to get a real APK and its first
> run is what will confirm the toolchain pins.

### What the Android port changes

- **Layout is computed, not hard coded.** `barnyard/layout.py` derives every
  rectangle from the current surface size and picks a landscape arrangement
  (board plus side panel) or a portrait one (stats strip on top, board in
  thumb reach, button bar along the bottom).
- **Touch input.** Tap-then-tap still works, and swiping a critter towards a
  neighbour now swaps it — the gesture people expect on a phone.
- **On-screen buttons** for pause, restart, mute and menu, since there is no
  keyboard; the Android back button maps to "return to the menu".
- **Lifecycle handling.** Backgrounding the app pauses the round and flushes
  the high score file.
- **Sprites re-render** at the device's tile size, with the supersample factor
  scaled down for large tiles so start-up stays quick.

## Layout

```
main.py                    entry point, desktop and Android alike
buildozer.spec             Android packaging config
barnyard/
  config.py                tuning constants and the palette
  board.py                 the rules: matches, specials, gravity, shuffles
  layout.py                resolution-independent geometry
  game.py                  state machine, input, scoring, drawing
  art.py                   procedurally drawn animals, tiles and scenery
  audio.py                 synthesised sound effects (no audio files, no numpy)
  effects.py               particles, score popups, screen shake
  scores.py                high score persistence
  platform.py              Android detection and per-platform paths
assets/                    generated launcher icon and splash screen
tools/make_assets.py       regenerates those two PNGs
tests/test_board.py        rules tests
.github/workflows/android.yml   builds the APK
```

`board.py` has no pygame dependency, so the rules can be exercised headlessly:

```bash
python tests/test_board.py     # or: pytest tests
```
