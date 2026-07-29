# Barnyard Blitz

A cosy farm merge game for Android, in the mould of Gossip Harbor, with a
Bejeweled-style match-3 round as its energy minigame. Merge eggs into baskets
into carts into henhouses, fill the neighbours' orders, spend the takings on
doing up Gran's farm, and find out who keeps leaving the gate open.

Native Kotlin, no third-party runtime dependencies. Every pixel and every
sound is generated in code at run time — there are no image or audio assets in
the APK, just a launcher icon.

## Building

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/
./gradlew installDebug           # onto a connected device
./gradlew test                   # the engine unit tests
```

Needs JDK 17 and the Android SDK (compileSdk 34). Minimum device is Android
8.0 (API 26).

Most checks need neither the SDK nor a device:

```bash
./scripts/offline-checks.sh   # type-check, layout geometry, render every screen
```

It fetches a Kotlin compiler and an Android runtime jar on first run, then
type-checks the whole app, verifies the layout across ten screen sizes, and
writes every screen to `build/preview/`.

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

A ring pulses around the generator on your very first visit, then around your
first mergeable pair; after that a hint only appears if you have gone quiet for
a few seconds.

**Customers queue along the top.** Tap one to see what they want; deliver it
for coins and XP. Skip the ones you can't be bothered with and another
neighbour wanders up. Coins pay for **renovation tasks**, and finishing a
chapter's tasks plays the next scene of the story — four chapters, twelve
tasks, six recurring characters.

**Energy** is the pacing valve: one point every 25 seconds up to a cap of 60,
or go and earn a chunk of it playing **Blitz**.

Items you're hoarding go in **storage** (8 slots); anything you're done with
can be **sold**.

## The Blitz minigame

Tap two neighbours or swipe one into the other to line up three or more of the
same critter.

| You match | You get | It does |
| --- | --- | --- |
| 3 in a row | — | clears the three |
| 4 in a row | **Golden Egg** | blasts the surrounding 3×3 |
| an L or T shape | **Hay Bale** | clears the whole row *and* column |
| 5 in a row | **Prize Rooster** | swap it onto any animal to clear that species |

Specials caught in someone else's blast set each other off. Cascades raise a
chain multiplier up to ×10. When the 60 seconds run out, any specials left on
the board go off in one last hurrah, and the score converts into energy and
coins for the farm. Relaxed mode is untimed and pays nothing.

## Saving

The farm saves itself continuously — every 20 seconds, on every screen change,
and in `onPause` — to `farm.json` in the app's private storage. That location
needs no runtime permission, which is why the app declares **none at all**. A
corrupt or truncated save starts a fresh farm rather than crashing.

## Layout

```
app/src/main/kotlin/com/barnyardblitz/
  MainActivity.kt          the single activity
  GameView.kt              the game surface: frame loop and touch dispatch
  engine/                  pure Kotlin, no Android imports - all unit tested
    Json.kt                minimal JSON reader/writer
    Chains.kt              the six merge chains and their generators
    MergeBoard.kt          the merge grid, storage and selling
    Economy.kt             energy, coins, XP and levelling
    Orders.kt              customer orders
    Story.kt               chapters, renovation tasks and dialogue
    Session.kt             the save-able state and cross-system rules
    Match3.kt              the Blitz rules
    Sfx.kt                 waveform synthesis (raw PCM)
  ui/                      layout, widgets and the three scenes
    Layout.kt              resolution-independent geometry
    Ui.kt                  palette, planks, buttons, cards, text wrapping
    Game.kt                shared state and the scene switch
    FarmScene.kt           the merge yard - the home screen
    BlitzScene.kt          the match-3 minigame
    StoryScene.kt          chapters, tasks and dialogue
    Effects.kt             particles, popups, screen shake
  art/Sprites.kt           every sprite, drawn to bitmaps at run time
  audio/SfxPlayer.kt       AudioTrack playback of the synthesised effects
  data/SaveStore.kt        atomic save file handling
app/src/test/kotlin/       engine unit tests (JUnit, no device)
app/src/androidTest/       instrumented smoke test (needs a device)
scripts/offline-checks.sh  everything checkable without the Android SDK
tools/LayoutCheck.kt       geometry sanity check across screen sizes
tools/preview/            renders the real screens to PNG on a desktop JVM
```

Two design choices worth flagging:

- **The engine has no Android imports.** All the rules — merging, the economy,
  orders, the story, saving, and the match-3 board — are plain Kotlin, so the
  whole rules layer runs and is tested on a bare JVM.
- **The UI is a custom `View` drawing to `android.graphics`,** not a
  declarative toolkit. The game is immediate-mode by nature, which makes a
  canvas a better fit, keeps the APK free of third-party dependencies, and
  makes the drawing code a direct port of the geometry it replaces.

## CI

One workflow, five jobs:

| Job | What it proves | Needs |
| --- | --- | --- |
| **Type-check, layout and screens** | The app compiles against the real Android API, the layout holds at ten sizes, and every screen still renders — with the PNGs uploaded so a push can be eyeballed | nothing but a JDK |
| **Engine unit tests** | The rules are correct | JDK |
| **Android lint** | No lint errors | SDK |
| **Build APK** | It packages, and the artifact is downloadable | SDK |
| **Smoke test (API 26 and 34)** | It launches on a real Android runtime, keeps drawing, takes a touch that spends energy, writes its save, and survives a recreate | emulator |

The first job is the one that catches most regressions, and it runs in a couple
of minutes without the SDK. The emulator job is the only place the lifecycle,
touch and save paths are exercised for real, which is why it runs at both ends
of the supported API range.

## Verification status

- **Engine: tested.** 48 JUnit tests covering merging, generators, storage,
  energy and levelling, order generation and delivery, chapter progression,
  save/load round trips, corrupt-save recovery, the full match-3 rule set, and
  waveform synthesis. Includes a 4000-step grind that drives taps, merges,
  deliveries and tasks while asserting nothing goes negative. `./gradlew test`.
- **App layer: type-checked and rendered, not yet run on a device.** The full
  UI, art and audio code compiles cleanly against the Android 14 API.
  `tools/LayoutCheck.kt` verifies the layout geometry across ten screen sizes
  (portrait, landscape, phone and tablet), including that every board cell
  round-trips through the hit test. `tools/preview/` renders the real screens
  to PNG on a desktop JVM through a Java2D stand-in for `android.graphics`,
  which is how the artwork and layout were iterated on.
- **Not yet verified: the Gradle build itself, resource linking, and runtime
  behaviour on a device.** The environment this was written in blocks
  `dl.google.com`, so the Android SDK and Gradle plugin could not be fetched
  and no APK has been produced. The three SDK-dependent CI jobs — lint, APK and
  the emulator smoke test — have therefore never run, and the instrumented test
  under `app/src/androidTest/` has never been compiled. Their first run is what
  will confirm them.

`tools/LayoutCheck.kt` needs a real Android runtime jar to run off-device
(`org.robolectric:android-all` from Maven Central works). `tools/preview/` has
its own README covering how to render screens and where it differs from a real
device.
