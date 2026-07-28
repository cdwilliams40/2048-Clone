# Barnyard Blitz

A farm-animal match-3 game in the spirit of Bejeweled Blitz, built with Python
and pygame. Swap two neighbouring critters to line up three or more, chain the
cascades, and squeeze as many points as you can out of a sixty-second round.

Everything you see is drawn at run time — the animals, the barn, the sky and
the sound effects are all generated in code, so there are no assets to ship.

## Running it

```bash
pip install -r requirements.txt
python main.py
```

Python 3.10 or newer.

## How to play

Click a critter and then click a neighbour, or drag one onto a neighbour. A
swap only sticks if it lines up three or more of the same animal.

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

### Keys

| Key | |
| --- | --- |
| `1` / `2` | start Blitz / Relaxed from the menu |
| `P` or `Space` | pause |
| `R` | restart the round |
| `M` | mute |
| `Esc` | back to the menu, or quit |

High scores are kept per mode in `~/.local/share/barnyard-blitz/highscores.json`
(or under `$XDG_DATA_HOME` if you set one).

## Layout

```
main.py              entry point
barnyard/
  config.py          tuning constants and the palette
  board.py           the rules: matches, specials, gravity, shuffles
  game.py            state machine, input, scoring, drawing
  art.py             procedurally drawn animals, tiles and scenery
  audio.py           synthesised sound effects (no audio files, no numpy)
  effects.py         particles, score popups, screen shake
  scores.py          high score persistence
tests/test_board.py  rules tests
```

`board.py` has no pygame dependency, so the rules can be exercised headlessly:

```bash
python tests/test_board.py     # or: pytest tests
```
