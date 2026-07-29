"""Shared widget drawing for every scene.

A :class:`UI` binds a canvas, the active layout and a font cache together, and
keeps the registry of tappable rectangles that scenes hit-test against. Scenes
draw through it rather than reaching for pygame directly.
"""

from __future__ import annotations

import pygame

from . import art, config, platform


class UI:
    def __init__(self, app):
        self.app = app
        self._fonts: dict[tuple[int, bool], pygame.font.Font] = {}
        self.hitboxes: dict[str, pygame.Rect] = {}
        self.disabled: set[str] = set()

    # ------------------------------------------------------------------ state
    @property
    def canvas(self) -> pygame.Surface:
        return self.app.canvas

    @property
    def layout(self):
        return self.app.layout

    def reset_frame(self) -> None:
        self.hitboxes.clear()
        self.disabled.clear()

    def forget_fonts(self) -> None:
        self._fonts.clear()

    def fs(self, base: int) -> int:
        return self.layout.fs(base)

    # ------------------------------------------------------------------ fonts
    def font(self, size: int, bold: bool = False) -> pygame.font.Font:
        key = (size, bold)
        if key not in self._fonts:
            name = "georgia,timesnewroman,dejavuserif,serif" if bold else \
                "verdana,dejavusans,arial,sans"
            try:
                font = pygame.font.SysFont(name, size, bold=bold)
            except pygame.error:
                font = pygame.font.Font(None, size)
            self._fonts[key] = font
        return self._fonts[key]

    def measure(self, text: str, size: int, bold: bool = False) -> tuple:
        return self.font(size, bold).size(text)

    # --------------------------------------------------------------- hit test
    def hit(self, key: str, pos) -> bool:
        if key in self.disabled:
            return False
        rect = self.hitboxes.get(key)
        return rect is not None and rect.collidepoint(pos)

    def hit_any(self, pos) -> str | None:
        for key, rect in self.hitboxes.items():
            if key not in self.disabled and rect.collidepoint(pos):
                return key
        return None

    # ---------------------------------------------------------------- widgets
    def plank(self, rect, color=config.WOOD, radius=None) -> None:
        """A wooden board with a capped drop shadow and a lit top edge."""
        short = min(rect.w, rect.h)
        if radius is None:
            radius = max(6, min(24, round(short * 0.18)))
        drop = max(2, min(8, round(short * 0.05)))
        pygame.draw.rect(self.canvas, art.shade(color, -0.4),
                         rect.move(0, drop), border_radius=radius)
        pygame.draw.rect(self.canvas, color, rect, border_radius=radius)
        pygame.draw.rect(self.canvas, art.shade(color, 0.22), rect,
                         width=max(1, min(4, round(short * 0.025))),
                         border_radius=radius)

    def text(self, text, size, color, center=None, topleft=None, right=None,
             midtop=None, bold=False, shadow=True):
        font = self.font(size, bold)
        label = font.render(text, True, color)
        rect = label.get_rect()
        if center:
            rect.center = center
        elif topleft:
            rect.topleft = topleft
        elif right:
            rect.midright = right
        elif midtop:
            rect.midtop = midtop
        if shadow:
            dark = font.render(text, True, (44, 34, 28))
            dark.set_alpha(110)
            self.canvas.blit(dark, rect.move(2, 2))
        self.canvas.blit(label, rect)
        return rect

    def wrap_lines(self, text, size, width, bold=False) -> list[str]:
        """Split ``text`` into lines that fit ``width``."""
        font = self.font(size, bold)
        lines, current = [], ""
        for word in text.split():
            probe = f"{current} {word}".strip()
            if font.size(probe)[0] <= width or not current:
                current = probe
            else:
                lines.append(current)
                current = word
        if current:
            lines.append(current)
        return lines

    def wrapped(self, text, size, color, rect, bold=False, shadow=False,
                line_gap: float = 1.25) -> int:
        """Draw ``text`` wrapped inside ``rect``; returns the height used."""
        lines = self.wrap_lines(text, size, rect.w, bold)
        step = round(size * line_gap)
        for i, line in enumerate(lines):
            self.text(line, size, color, topleft=(rect.x, rect.y + i * step),
                      bold=bold, shadow=shadow)
        return len(lines) * step

    def button(self, key, rect, label, color=config.BARN_RED, size: int = 22,
               enabled: bool = True, text_color=config.CREAM) -> pygame.Rect:
        self.hitboxes[key] = rect
        if not enabled:
            self.disabled.add(key)
            color = art.shade(color, -0.35)
            text_color = art.shade(text_color, -0.35)
        else:
            # On a touch screen the pointer parks on the last tap, so a hover
            # highlight would stick to a random button.
            if (not platform.touch_first()
                    and rect.collidepoint(pygame.mouse.get_pos())):
                color = art.shade(color, 0.18)
        self.plank(rect, color)
        self.text(label, self.fs(size), text_color, center=rect.center,
                  bold=True)
        return rect

    def card(self, rect, title, value, value_color=config.INK,
             bar: float | None = None, bar_color=config.GOLD,
             face=config.CREAM) -> None:
        self.plank(rect, face)
        self.text(title, self.fs(14), config.INK_SOFT,
                  center=(rect.centerx, rect.y + rect.h * 0.22), shadow=False)
        self.text(value, self.fs(30), value_color,
                  center=(rect.centerx, rect.y + rect.h * 0.58), bold=True)
        if bar is None:
            return
        self.meter(pygame.Rect(rect.x + rect.w * 0.10,
                               rect.bottom - rect.h * 0.20,
                               rect.w * 0.80, max(4, rect.h * 0.10)),
                   bar, bar_color)

    def meter(self, rect, fraction: float, color=config.GOLD,
              track=(206, 196, 176)) -> None:
        radius = int(max(2, rect.h / 2))
        pygame.draw.rect(self.canvas, track, rect, border_radius=radius)
        width = int(rect.w * max(0.0, min(1.0, fraction)))
        if width > 0:
            pygame.draw.rect(self.canvas, color,
                             pygame.Rect(rect.x, rect.y, width, rect.h),
                             border_radius=radius)

    def veil(self, alpha: int = 175) -> None:
        veil = pygame.Surface(self.canvas.get_size(), pygame.SRCALPHA)
        veil.fill((30, 24, 20, alpha))
        self.canvas.blit(veil, (0, 0))

    def panel(self, rect, face=config.CREAM, frame=config.WOOD) -> pygame.Rect:
        """A framed dialog panel; returns the inner area to draw into."""
        self.plank(rect, frame)
        inner = rect.inflate(-round(rect.w * 0.05), -round(rect.h * 0.05))
        self.plank(inner, face)
        return inner.inflate(-round(rect.w * 0.06), -round(rect.h * 0.06))
