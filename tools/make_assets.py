"""Generate the Android launcher icon and splash screen.

The game draws its artwork at run time, but buildozer needs real files on disk,
so this script renders them from the same code and writes them to ``assets/``.
The results are committed; re-run it only when the artwork changes:

    python tools/make_assets.py
"""

from __future__ import annotations

import os
import sys
from pathlib import Path

os.environ.setdefault("SDL_VIDEODRIVER", "dummy")
os.environ.setdefault("SDL_AUDIODRIVER", "dummy")

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pygame  # noqa: E402

from barnyard import art, config  # noqa: E402
from barnyard.board import Power  # noqa: E402

ASSETS = Path(__file__).resolve().parent.parent / "assets"


def make_icon(size: int = 512) -> pygame.Surface:
    surf = pygame.Surface((size, size))
    surf.fill(config.BARN_RED)
    for y in range(size):
        shade = art.shade(config.BARN_RED, 0.16 * (1 - y / size))
        surf.fill(shade, pygame.Rect(0, y, size, 1))
    hill = round(size * 0.74)
    pygame.draw.ellipse(surf, config.FIELD,
                        pygame.Rect(-size * 0.25, hill, size * 1.5, size * 0.8))
    tile = art.build_tile_sprites(round(size * 0.66))[(0, Power.NONE)]
    surf.blit(tile, tile.get_rect(center=(size // 2, round(size * 0.47))))
    return surf


def make_presplash(size: int = 1024) -> pygame.Surface:
    surf = art.build_background(size, size, round(size * 0.06))
    barn = art.build_barn(round(size * 0.34), round(size * 0.27))
    surf.blit(barn, barn.get_rect(center=(size // 2, round(size * 0.40))))

    tile_size = round(size * 0.115)
    sprites = art.build_tile_sprites(tile_size)
    step = round(tile_size * 1.08)
    total = len(config.ANIMALS) * step
    for i in range(len(config.ANIMALS)):
        sprite = sprites[(i, Power.NONE)]
        x = size // 2 - total // 2 + i * step + step // 2
        surf.blit(sprite, sprite.get_rect(center=(x, round(size * 0.60))))

    font = pygame.font.SysFont("georgia,dejavuserif,serif",
                               round(size * 0.085), bold=True)
    label = font.render("BARNYARD BLITZ", True, config.CREAM)
    plate = label.get_rect(center=(size // 2, round(size * 0.75)))
    pad = plate.inflate(round(size * 0.06), round(size * 0.035))
    pygame.draw.rect(surf, config.BARN_RED, pad,
                     border_radius=round(pad.h * 0.28))
    surf.blit(label, plate)
    return surf


def main() -> int:
    pygame.init()
    pygame.display.set_mode((16, 16))
    ASSETS.mkdir(exist_ok=True)
    pygame.image.save(make_icon(), str(ASSETS / "icon.png"))
    pygame.image.save(make_presplash(), str(ASSETS / "presplash.png"))
    print(f"wrote {ASSETS / 'icon.png'} and {ASSETS / 'presplash.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
