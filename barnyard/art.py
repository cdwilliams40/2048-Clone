"""Procedurally drawn artwork.

Everything the game shows is generated at run time, so the project stays a
single dependency (pygame) with no asset folder to ship. Sprites are drawn at
4x and smooth-scaled down, which gives clean edges without needing AA helpers.
"""

from __future__ import annotations

import math

import pygame

from . import config
from .board import Power

SS = 4  # supersample factor


def _shade(color, amount: float):
    """Lighten (amount > 0) or darken (amount < 0) a colour."""
    if amount >= 0:
        return tuple(min(255, int(c + (255 - c) * amount)) for c in color[:3])
    return tuple(max(0, int(c * (1 + amount))) for c in color[:3])


shade = _shade  # public alias for the game layer


def _ellipse(surf, color, cx, cy, w, h):
    pygame.draw.ellipse(surf, color, pygame.Rect(cx - w / 2, cy - h / 2, w, h))


def _poly(surf, color, points):
    pygame.draw.polygon(surf, color, points)


# --------------------------------------------------------------------- animals
def _cow(s, S, body, accent):
    _ellipse(s, _shade(body, -0.35), 0.20 * S, 0.34 * S, 0.20 * S, 0.24 * S)
    _ellipse(s, _shade(body, -0.35), 0.80 * S, 0.34 * S, 0.20 * S, 0.24 * S)
    _ellipse(s, (238, 226, 196), 0.30 * S, 0.19 * S, 0.13 * S, 0.16 * S)
    _ellipse(s, (238, 226, 196), 0.70 * S, 0.19 * S, 0.13 * S, 0.16 * S)
    _ellipse(s, body, 0.5 * S, 0.50 * S, 0.72 * S, 0.66 * S)
    _ellipse(s, (58, 50, 48), 0.32 * S, 0.36 * S, 0.26 * S, 0.24 * S)
    _ellipse(s, accent, 0.5 * S, 0.68 * S, 0.42 * S, 0.30 * S)
    _ellipse(s, _shade(accent, -0.3), 0.42 * S, 0.68 * S, 0.07 * S, 0.09 * S)
    _ellipse(s, _shade(accent, -0.3), 0.58 * S, 0.68 * S, 0.07 * S, 0.09 * S)
    _ellipse(s, (32, 28, 26), 0.34 * S, 0.44 * S, 0.09 * S, 0.10 * S)
    _ellipse(s, (32, 28, 26), 0.66 * S, 0.44 * S, 0.09 * S, 0.10 * S)


def _pig(s, S, body, accent):
    _poly(s, _shade(body, -0.2), [(0.24 * S, 0.28 * S), (0.20 * S, 0.06 * S),
                                  (0.44 * S, 0.20 * S)])
    _poly(s, _shade(body, -0.2), [(0.76 * S, 0.28 * S), (0.80 * S, 0.06 * S),
                                  (0.56 * S, 0.20 * S)])
    _ellipse(s, body, 0.5 * S, 0.52 * S, 0.76 * S, 0.70 * S)
    _ellipse(s, accent, 0.5 * S, 0.66 * S, 0.36 * S, 0.28 * S)
    _ellipse(s, _shade(accent, -0.4), 0.43 * S, 0.66 * S, 0.07 * S, 0.10 * S)
    _ellipse(s, _shade(accent, -0.4), 0.57 * S, 0.66 * S, 0.07 * S, 0.10 * S)
    _ellipse(s, (44, 32, 34), 0.35 * S, 0.42 * S, 0.09 * S, 0.10 * S)
    _ellipse(s, (44, 32, 34), 0.65 * S, 0.42 * S, 0.09 * S, 0.10 * S)


def _chicken(s, S, body, accent):
    for i, x in enumerate((0.38, 0.50, 0.62)):
        h = 0.20 if i == 1 else 0.15
        _ellipse(s, accent, x * S, (0.22 - h * 0.35) * S, 0.15 * S, h * S)
    _ellipse(s, body, 0.5 * S, 0.52 * S, 0.68 * S, 0.64 * S)
    _poly(s, (240, 158, 42), [(0.50 * S, 0.56 * S), (0.86 * S, 0.62 * S),
                              (0.50 * S, 0.72 * S)])
    _ellipse(s, _shade(accent, -0.1), 0.54 * S, 0.78 * S, 0.12 * S, 0.16 * S)
    _ellipse(s, (44, 34, 30), 0.40 * S, 0.46 * S, 0.11 * S, 0.12 * S)
    _ellipse(s, (255, 255, 255), 0.42 * S, 0.44 * S, 0.04 * S, 0.04 * S)


def _sheep(s, S, body, accent):
    for cx, cy, rr in ((0.28, 0.36, 0.20), (0.72, 0.36, 0.20), (0.5, 0.26, 0.22),
                       (0.24, 0.62, 0.20), (0.76, 0.62, 0.20), (0.5, 0.60, 0.30)):
        _ellipse(s, body, cx * S, cy * S, rr * 2 * S, rr * 2 * S)
    _ellipse(s, _shade(accent, 0.05), 0.26 * S, 0.52 * S, 0.16 * S, 0.12 * S)
    _ellipse(s, _shade(accent, 0.05), 0.74 * S, 0.52 * S, 0.16 * S, 0.12 * S)
    _ellipse(s, accent, 0.5 * S, 0.58 * S, 0.40 * S, 0.42 * S)
    _ellipse(s, (250, 250, 250), 0.42 * S, 0.54 * S, 0.10 * S, 0.11 * S)
    _ellipse(s, (250, 250, 250), 0.58 * S, 0.54 * S, 0.10 * S, 0.11 * S)
    _ellipse(s, (30, 26, 26), 0.42 * S, 0.55 * S, 0.05 * S, 0.06 * S)
    _ellipse(s, (30, 26, 26), 0.58 * S, 0.55 * S, 0.05 * S, 0.06 * S)


def _duck(s, S, body, accent):
    _ellipse(s, _shade(body, -0.14), 0.5 * S, 0.16 * S, 0.15 * S, 0.17 * S)
    _ellipse(s, body, 0.5 * S, 0.46 * S, 0.70 * S, 0.66 * S)
    _ellipse(s, (208, 108, 26), 0.5 * S, 0.735 * S, 0.48 * S, 0.25 * S)
    _ellipse(s, accent, 0.5 * S, 0.715 * S, 0.46 * S, 0.20 * S)
    _ellipse(s, (255, 196, 104), 0.5 * S, 0.665 * S, 0.40 * S, 0.10 * S)
    _ellipse(s, (176, 88, 20), 0.44 * S, 0.66 * S, 0.04 * S, 0.04 * S)
    _ellipse(s, (176, 88, 20), 0.56 * S, 0.66 * S, 0.04 * S, 0.04 * S)
    _ellipse(s, (40, 34, 28), 0.37 * S, 0.42 * S, 0.10 * S, 0.11 * S)
    _ellipse(s, (40, 34, 28), 0.63 * S, 0.42 * S, 0.10 * S, 0.11 * S)
    _ellipse(s, (255, 255, 255), 0.39 * S, 0.40 * S, 0.04 * S, 0.04 * S)
    _ellipse(s, (255, 255, 255), 0.65 * S, 0.40 * S, 0.04 * S, 0.04 * S)


def _horse(s, S, body, accent):
    _poly(s, _shade(body, -0.2), [(0.28 * S, 0.26 * S), (0.26 * S, 0.05 * S),
                                  (0.44 * S, 0.18 * S)])
    _poly(s, _shade(body, -0.2), [(0.72 * S, 0.26 * S), (0.74 * S, 0.05 * S),
                                  (0.56 * S, 0.18 * S)])
    _ellipse(s, body, 0.5 * S, 0.50 * S, 0.60 * S, 0.78 * S)
    _poly(s, accent, [(0.22 * S, 0.30 * S), (0.42 * S, 0.10 * S),
                      (0.58 * S, 0.10 * S), (0.78 * S, 0.30 * S),
                      (0.62 * S, 0.24 * S), (0.5 * S, 0.30 * S),
                      (0.38 * S, 0.24 * S)])
    _ellipse(s, _shade(body, 0.30), 0.5 * S, 0.74 * S, 0.40 * S, 0.28 * S)
    _ellipse(s, accent, 0.43 * S, 0.74 * S, 0.06 * S, 0.08 * S)
    _ellipse(s, accent, 0.57 * S, 0.74 * S, 0.06 * S, 0.08 * S)
    _ellipse(s, (30, 24, 20), 0.36 * S, 0.46 * S, 0.09 * S, 0.10 * S)
    _ellipse(s, (30, 24, 20), 0.64 * S, 0.46 * S, 0.09 * S, 0.10 * S)


def _rooster(s, S, *_):
    for x in (0.40, 0.50, 0.60):
        _ellipse(s, (208, 58, 50), x * S, 0.16 * S, 0.16 * S, 0.22 * S)
    _ellipse(s, (250, 246, 238), 0.5 * S, 0.52 * S, 0.66 * S, 0.62 * S)
    _poly(s, (244, 166, 40), [(0.50 * S, 0.54 * S), (0.88 * S, 0.61 * S),
                              (0.50 * S, 0.72 * S)])
    _ellipse(s, (208, 58, 50), 0.55 * S, 0.80 * S, 0.14 * S, 0.18 * S)
    _ellipse(s, (44, 34, 30), 0.40 * S, 0.46 * S, 0.12 * S, 0.13 * S)
    _ellipse(s, (255, 255, 255), 0.42 * S, 0.44 * S, 0.045 * S, 0.045 * S)


DRAWERS = (_cow, _pig, _chicken, _sheep, _duck, _horse)


# ----------------------------------------------------------------------- tiles
def _pad(surf, S, color, radius_frac=0.22):
    r = int(S * radius_frac)
    inset = int(S * 0.05)
    rect = pygame.Rect(inset, inset, S - inset * 2, S - inset * 2)
    pygame.draw.rect(surf, _shade(color, -0.35), rect.move(0, S * 0.03),
                     border_radius=r)
    pygame.draw.rect(surf, color, rect, border_radius=r)
    gloss = pygame.Rect(rect.x + S * 0.07, rect.y + S * 0.06,
                        rect.w - S * 0.14, rect.h * 0.42)
    pygame.draw.rect(surf, _shade(color, 0.22), gloss,
                     border_radius=int(S * 0.16))
    pygame.draw.rect(surf, _shade(color, 0.45), rect, width=max(2, S // 40),
                     border_radius=r)


def _rainbow_pad(surf, S):
    r = int(S * 0.22)
    inset = int(S * 0.05)
    rect = pygame.Rect(inset, inset, S - inset * 2, S - inset * 2)
    band = pygame.Surface((rect.w, rect.h), pygame.SRCALPHA)
    hues = [(226, 106, 158), (238, 173, 52), (250, 226, 90), (94, 178, 146),
            (91, 127, 181), (128, 100, 200)]
    step = rect.h / len(hues)
    for i, color in enumerate(hues):
        pygame.draw.rect(band, color, pygame.Rect(0, i * step, rect.w,
                                                  step + 2))
    mask = pygame.Surface((rect.w, rect.h), pygame.SRCALPHA)
    pygame.draw.rect(mask, (255, 255, 255), mask.get_rect(), border_radius=r)
    band.blit(mask, (0, 0), special_flags=pygame.BLEND_RGBA_MIN)
    surf.blit(band, rect.topleft)
    pygame.draw.rect(surf, (255, 255, 255), rect, width=max(2, S // 34),
                     border_radius=r)


def _egg_overlay(surf, S):
    _ellipse(surf, (250, 226, 150), 0.5 * S, 0.5 * S, 0.5 * S, 0.62 * S)
    _ellipse(surf, (244, 200, 74), 0.5 * S, 0.52 * S, 0.44 * S, 0.56 * S)
    _ellipse(surf, (255, 246, 214), 0.42 * S, 0.38 * S, 0.14 * S, 0.18 * S)
    for angle in range(0, 360, 45):
        rad = math.radians(angle)
        x = 0.5 * S + math.cos(rad) * 0.40 * S
        y = 0.5 * S + math.sin(rad) * 0.40 * S
        _ellipse(surf, (255, 240, 180), x, y, 0.07 * S, 0.07 * S)


def _hay_overlay(surf, S):
    band = int(S * 0.13)
    for rect in (pygame.Rect(S * 0.05, S * 0.5 - band / 2, S * 0.9, band),
                 pygame.Rect(S * 0.5 - band / 2, S * 0.05, band, S * 0.9)):
        pygame.draw.rect(surf, (232, 198, 108), rect,
                         border_radius=int(band / 2))
        pygame.draw.rect(surf, (196, 154, 66), rect, width=max(2, S // 60),
                         border_radius=int(band / 2))
    _ellipse(surf, (255, 246, 214), 0.5 * S, 0.5 * S, 0.18 * S, 0.18 * S)


def build_tile_sprites(size: int) -> dict:
    """Return ``{(kind, power): Surface}`` for every animal and special."""
    S = size * SS
    sprites: dict = {}
    for kind, (_name, pad, body, accent) in enumerate(config.ANIMALS):
        for power in (Power.NONE, Power.EGG, Power.HAY):
            surf = pygame.Surface((S, S), pygame.SRCALPHA)
            _pad(surf, S, pad)
            DRAWERS[kind](surf, S, body, accent)
            if power is Power.EGG:
                _egg_overlay(surf, S)
            elif power is Power.HAY:
                _hay_overlay(surf, S)
            sprites[(kind, power)] = pygame.transform.smoothscale(
                surf, (size, size))
    surf = pygame.Surface((S, S), pygame.SRCALPHA)
    _rainbow_pad(surf, S)
    _rooster(surf, S)
    rooster = pygame.transform.smoothscale(surf, (size, size))
    for kind in range(len(config.ANIMALS)):
        sprites[(kind, Power.ROOSTER)] = rooster
    return sprites


# ------------------------------------------------------------------ background
def _hill(surf, color, base_y, height, phase, width):
    points = [(0, surf.get_height()), (0, base_y)]
    for x in range(0, width + 8, 8):
        y = base_y - math.sin(x / width * math.pi * 1.5 + phase) * height
        points.append((x, y))
    points.append((width, surf.get_height()))
    pygame.draw.polygon(surf, color, points)


def build_background(width: int, height: int) -> pygame.Surface:
    surf = pygame.Surface((width, height))
    for y in range(height):
        t = y / height
        surf.fill(
            tuple(int(a + (b - a) * t)
                  for a, b in zip(config.SKY_TOP, config.SKY_BOTTOM)),
            pygame.Rect(0, y, width, 1),
        )
    # Kept high enough that no cloud pokes through the sliver of sky between
    # the header plank and the board.
    for cx, cy, scale in ((width * 0.18, 58, 1.0), (width * 0.62, 44, 0.7),
                          (width * 0.88, 60, 0.85)):
        for dx, dy, rr in ((-26, 6, 20), (0, -6, 27), (26, 4, 22), (8, 10, 20)):
            _ellipse(surf, (252, 252, 255), cx + dx * scale, cy + dy * scale,
                     rr * 2 * scale, rr * 1.7 * scale)
    _hill(surf, _shade(config.FIELD, 0.18), height * 0.52, 34, 0.4, width)
    _hill(surf, config.FIELD, height * 0.66, 26, 2.2, width)
    _hill(surf, config.FIELD_DARK, height * 0.86, 18, 4.0, width)
    return surf


def build_barn(width: int, height: int) -> pygame.Surface:
    """A small decorative barn for the menu screen."""
    S = 4
    surf = pygame.Surface((width * S, height * S), pygame.SRCALPHA)
    w, h = width * S, height * S
    body = pygame.Rect(w * 0.12, h * 0.38, w * 0.76, h * 0.56)
    _poly(surf, config.BARN_RED_DARK,
          [(w * 0.06, h * 0.40), (w * 0.5, h * 0.06), (w * 0.94, h * 0.40)])
    pygame.draw.rect(surf, config.BARN_RED, body)
    door = pygame.Rect(w * 0.36, h * 0.56, w * 0.28, h * 0.38)
    pygame.draw.rect(surf, config.WOOD_DARK, door)
    pygame.draw.line(surf, config.CREAM, door.topleft, door.bottomright, S * 2)
    pygame.draw.line(surf, config.CREAM, door.topright, door.bottomleft, S * 2)
    pygame.draw.rect(surf, config.CREAM, door, width=S * 2)
    for x in (0.20, 0.72):
        win = pygame.Rect(w * x, h * 0.48, w * 0.09, h * 0.12)
        pygame.draw.rect(surf, config.CREAM, win)
        pygame.draw.rect(surf, config.WOOD_DARK, win, width=S)
    return pygame.transform.smoothscale(surf, (width, height))
