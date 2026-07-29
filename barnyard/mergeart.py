"""Artwork for the merge items.

Thirty-six items would be thirty-six drawing routines done naively. Instead a
tier picks a *container* - a bare thing, a bundle, a basket, a crate, a cart, a
building - and the chain picks a *motif* stamped onto it. Six containers times
six motifs covers the lot, and it means a player can read an item's tier from
its silhouette even in a chain they have never seen.
"""

from __future__ import annotations

import math

import pygame

from . import art, config
from .art import _shade as shade
from .art import ellipse, poly
from .merge.items import CHAINS, MAX_TIER

WOOD = (166, 118, 74)
WOOD_DARK = (124, 86, 54)
WOOD_LIGHT = (198, 150, 100)


# ---------------------------------------------------------------------- motifs
def _motif_egg(s, S, cx, cy, r, base, accent):
    ellipse(s, shade(base, -0.18), cx, cy + r * 0.10, r * 1.7, r * 2.1)
    ellipse(s, base, cx, cy, r * 1.7, r * 2.1)
    ellipse(s, shade(base, 0.5), cx - r * 0.35, cy - r * 0.45, r * 0.55,
            r * 0.7)


def _motif_corn(s, S, cx, cy, r, base, accent):
    poly(s, accent, [(cx - r * 0.95, cy + r * 0.4), (cx - r * 0.3, cy - r * 1.2),
                     (cx - r * 0.1, cy + r * 0.9)])
    poly(s, accent, [(cx + r * 0.95, cy + r * 0.4), (cx + r * 0.3, cy - r * 1.2),
                     (cx + r * 0.1, cy + r * 0.9)])
    ellipse(s, base, cx, cy, r * 1.15, r * 2.1)
    for i in range(3):
        ellipse(s, shade(base, -0.22), cx, cy - r * 0.6 + i * r * 0.6,
                r * 0.95, r * 0.22)


def _motif_bottle(s, S, cx, cy, r, base, accent):
    pygame.draw.rect(s, base, pygame.Rect(cx - r * 0.62, cy - r * 0.5,
                                          r * 1.24, r * 1.6),
                     border_radius=int(r * 0.3))
    pygame.draw.rect(s, base, pygame.Rect(cx - r * 0.3, cy - r * 1.25,
                                          r * 0.6, r * 0.95))
    pygame.draw.rect(s, accent, pygame.Rect(cx - r * 0.38, cy - r * 1.45,
                                            r * 0.76, r * 0.38),
                     border_radius=int(r * 0.14))
    ellipse(s, shade(base, -0.25), cx, cy + r * 0.55, r * 1.15, r * 0.4)


def _motif_wool(s, S, cx, cy, r, base, accent):
    for dx, dy, rr in ((-0.55, 0.05, 0.62), (0.55, 0.05, 0.62),
                       (0.0, -0.45, 0.7), (0.0, 0.35, 0.75)):
        ellipse(s, base, cx + dx * r, cy + dy * r, rr * r * 2, rr * r * 1.9)
    ellipse(s, shade(accent, 0.25), cx - r * 0.2, cy - r * 0.1, r * 0.5,
            r * 0.5)


def _motif_tool(s, S, cx, cy, r, base, accent):
    pygame.draw.rect(s, accent, pygame.Rect(cx - r * 0.22, cy - r * 0.2,
                                            r * 0.44, r * 1.5),
                     border_radius=int(r * 0.18))
    pygame.draw.rect(s, base, pygame.Rect(cx - r * 1.0, cy - r * 1.25,
                                          r * 2.0, r * 0.85),
                     border_radius=int(r * 0.24))
    ellipse(s, shade(base, 0.4), cx - r * 0.45, cy - r * 1.05, r * 0.6,
            r * 0.28)


def _motif_berry(s, S, cx, cy, r, base, accent):
    for dx, dy in ((-0.45, 0.3), (0.45, 0.3), (0.0, -0.15)):
        ellipse(s, base, cx + dx * r, cy + dy * r, r * 1.15, r * 1.15)
        ellipse(s, shade(base, 0.35), cx + dx * r - r * 0.2,
                cy + dy * r - r * 0.22, r * 0.32, r * 0.32)
    poly(s, accent, [(cx, cy - r * 0.75), (cx + r * 0.85, cy - r * 1.3),
                     (cx + r * 0.2, cy - r * 1.35)])


MOTIFS = {
    "egg": _motif_egg,
    "corn": _motif_corn,
    "bottle": _motif_bottle,
    "wool": _motif_wool,
    "tool": _motif_tool,
    "berry": _motif_berry,
}


def _stamp(surf, S, chain, cx, cy, r):
    base, accent = chain.palette[0], chain.palette[-1]
    MOTIFS[chain.motif](surf, S, cx, cy, r, base, accent)


# ------------------------------------------------------------------ containers
def _shadow(surf, S):
    ellipse(surf, (0, 0, 0, 55), 0.5 * S, 0.90 * S, 0.62 * S, 0.13 * S)


def _tier0(surf, S, chain):
    _stamp(surf, S, chain, 0.5 * S, 0.52 * S, 0.21 * S)


def _tier1(surf, S, chain):
    """A bundle: three of the thing, tied together."""
    _stamp(surf, S, chain, 0.32 * S, 0.62 * S, 0.145 * S)
    _stamp(surf, S, chain, 0.68 * S, 0.62 * S, 0.145 * S)
    _stamp(surf, S, chain, 0.50 * S, 0.36 * S, 0.155 * S)
    pygame.draw.rect(surf, (196, 92, 84),
                     pygame.Rect(0.20 * S, 0.66 * S, 0.60 * S, 0.075 * S),
                     border_radius=int(0.04 * S))


def _tier2(surf, S, chain):
    """A woven basket with the goods peeking over the rim."""
    _stamp(surf, S, chain, 0.36 * S, 0.44 * S, 0.125 * S)
    _stamp(surf, S, chain, 0.64 * S, 0.44 * S, 0.125 * S)
    body = pygame.Rect(0.20 * S, 0.50 * S, 0.60 * S, 0.34 * S)
    pygame.draw.rect(surf, WOOD, body, border_bottom_left_radius=int(0.2 * S),
                     border_bottom_right_radius=int(0.2 * S))
    for i in range(1, 4):
        y = body.y + body.h * i / 4
        pygame.draw.line(surf, shade(WOOD, -0.22), (body.x + 2, y),
                         (body.right - 2, y), max(2, int(S * 0.018)))
    pygame.draw.rect(surf, WOOD_LIGHT,
                     pygame.Rect(0.16 * S, 0.47 * S, 0.68 * S, 0.09 * S),
                     border_radius=int(0.045 * S))


def _tier3(surf, S, chain):
    """A slatted crate."""
    _stamp(surf, S, chain, 0.35 * S, 0.34 * S, 0.115 * S)
    _stamp(surf, S, chain, 0.65 * S, 0.34 * S, 0.115 * S)
    body = pygame.Rect(0.16 * S, 0.42 * S, 0.68 * S, 0.42 * S)
    pygame.draw.rect(surf, WOOD, body, border_radius=int(0.045 * S))
    pygame.draw.rect(surf, shade(WOOD, -0.28), body,
                     width=max(2, int(S * 0.028)),
                     border_radius=int(0.045 * S))
    for i in (1, 2):
        y = body.y + body.h * i / 3
        pygame.draw.line(surf, shade(WOOD, -0.2), (body.x, y),
                         (body.right, y), max(2, int(S * 0.022)))
    pygame.draw.line(surf, WOOD_LIGHT, body.bottomleft, body.topright,
                     max(2, int(S * 0.022)))


def _tier4(surf, S, chain):
    """The crate, now on wheels."""
    _stamp(surf, S, chain, 0.38 * S, 0.28 * S, 0.105 * S)
    _stamp(surf, S, chain, 0.63 * S, 0.28 * S, 0.105 * S)
    body = pygame.Rect(0.14 * S, 0.36 * S, 0.72 * S, 0.32 * S)
    pygame.draw.rect(surf, WOOD, body, border_radius=int(0.04 * S))
    pygame.draw.rect(surf, shade(WOOD, -0.28), body,
                     width=max(2, int(S * 0.026)),
                     border_radius=int(0.04 * S))
    pygame.draw.line(surf, WOOD_DARK, (0.84 * S, 0.52 * S), (0.96 * S, 0.40 * S),
                     max(3, int(S * 0.035)))
    for x in (0.30, 0.70):
        pygame.draw.circle(surf, (62, 52, 46), (int(x * S), int(0.75 * S)),
                           int(0.115 * S))
        pygame.draw.circle(surf, (150, 138, 128), (int(x * S), int(0.75 * S)),
                           int(0.05 * S))


def _tier5(surf, S, chain):
    """The chain's building: a little barn with the motif on the sign."""
    base = shade(chain.palette[-1], -0.05)
    poly(surf, shade(base, -0.25), [(0.08 * S, 0.42 * S), (0.5 * S, 0.14 * S),
                                    (0.92 * S, 0.42 * S)])
    body = pygame.Rect(0.16 * S, 0.42 * S, 0.68 * S, 0.44 * S)
    pygame.draw.rect(surf, base, body)
    door = pygame.Rect(0.40 * S, 0.58 * S, 0.20 * S, 0.28 * S)
    pygame.draw.rect(surf, WOOD_DARK, door)
    pygame.draw.rect(surf, config.CREAM, door, width=max(2, int(S * 0.018)))
    sign = pygame.Rect(0.22 * S, 0.46 * S, 0.56 * S, 0.11 * S)
    pygame.draw.rect(surf, config.CREAM, sign, border_radius=int(0.03 * S))
    _stamp(surf, S, chain, 0.5 * S, 0.515 * S, 0.042 * S)
    for x in (0.24, 0.68):
        win = pygame.Rect(x * S, 0.62 * S, 0.09 * S, 0.10 * S)
        pygame.draw.rect(surf, config.CREAM, win)


CONTAINERS = (_tier0, _tier1, _tier2, _tier3, _tier4, _tier5)


def _generator(surf, S, chain):
    """A signpost hut, deliberately unlike any tier so it reads as a source."""
    pygame.draw.rect(surf, WOOD_DARK,
                     pygame.Rect(0.44 * S, 0.52 * S, 0.12 * S, 0.36 * S),
                     border_radius=int(0.03 * S))
    board = pygame.Rect(0.12 * S, 0.20 * S, 0.76 * S, 0.40 * S)
    pygame.draw.rect(surf, shade(chain.palette[-1], -0.1), board,
                     border_radius=int(0.09 * S))
    pygame.draw.rect(surf, config.CREAM, board.inflate(-S * 0.09, -S * 0.09),
                     border_radius=int(0.06 * S))
    _stamp(surf, S, chain, 0.5 * S, 0.40 * S, 0.115 * S)
    for angle in (200, 250, 290, 340):
        rad = math.radians(angle)
        ellipse(surf, (255, 240, 176),
                0.5 * S + math.cos(rad) * 0.46 * S,
                0.40 * S + math.sin(rad) * 0.34 * S, 0.05 * S, 0.05 * S)


# ------------------------------------------------------------------- interface
def build_item_sprites(size: int) -> dict:
    """``{(chain_key, tier): Surface}`` plus ``{(chain_key, "gen"): Surface}``."""
    ss = art._supersample(size)
    S = size * ss
    sprites: dict = {}
    for key, chain in CHAINS.items():
        for tier in range(MAX_TIER + 1):
            surf = pygame.Surface((S, S), pygame.SRCALPHA)
            _shadow(surf, S)
            CONTAINERS[tier](surf, S, chain)
            sprites[(key, tier)] = pygame.transform.smoothscale(
                surf, (size, size))
        surf = pygame.Surface((S, S), pygame.SRCALPHA)
        _shadow(surf, S)
        _generator(surf, S, chain)
        sprites[(key, "gen")] = pygame.transform.smoothscale(surf, (size, size))
    return sprites


def sprite_for(sprites: dict, item) -> pygame.Surface:
    return sprites[(item.chain, "gen" if item.is_generator else item.tier)]
