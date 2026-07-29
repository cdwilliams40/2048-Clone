"""Resolution-independent layout.

Every rectangle the game draws is derived from the current surface size, so the
same code lays out an 880x674 desktop window and a 1080x2340 phone screen. The
board keeps a side panel in landscape and switches to a stats strip plus a
bottom button bar in portrait.
"""

from __future__ import annotations

import pygame

from . import config

REF_TILE = 66  # the desktop tile size the font sizes were tuned against


def _clamp(value, low, high):
    return max(low, min(high, value))


class Layout:
    def __init__(self, width: int, height: int, rows: int = config.ROWS,
                 cols: int = config.COLS):
        self.w = width
        self.h = height
        self.rows = rows
        self.cols = cols
        self.portrait = height > width * 1.08

        self.margin = _clamp(round(min(width, height) * 0.025), 8, 34)
        self.gap = max(6, round(self.margin * 0.7))

        if self.portrait:
            self._portrait()
        else:
            self._landscape()

        self.scale = _clamp(self.tile / REF_TILE, 0.55, 3.2)
        self._cards()
        self._buttons()

    # ------------------------------------------------------------------ sizing
    def fs(self, base: int) -> int:
        """Scale a font size that was chosen for the reference tile size."""
        return max(9, round(base * self.scale))

    def _fit_board(self, side: float) -> None:
        pad = max(5, round(side * 0.022))
        self.tile = max(16, (int(side) - 2 * pad) // self.cols)
        self.board_w = self.tile * self.cols
        self.board_h = self.tile * self.rows
        self.frame_pad = pad
        self.frame = pygame.Rect(0, 0, self.board_w + 2 * pad,
                                 self.board_h + 2 * pad)

    @property
    def board(self) -> pygame.Rect:
        return pygame.Rect(self.frame.x + self.frame_pad,
                           self.frame.y + self.frame_pad,
                           self.board_w, self.board_h)

    def cell_rect(self, cell) -> pygame.Rect:
        r, c = cell
        board = self.board
        return pygame.Rect(board.x + c * self.tile, board.y + r * self.tile,
                           self.tile, self.tile)

    def cell_at(self, pos):
        board = self.board
        c = (pos[0] - board.x) // self.tile
        r = (pos[1] - board.y) // self.tile
        if 0 <= r < self.rows and 0 <= c < self.cols:
            return (int(r), int(c))
        return None

    # -------------------------------------------------------------- landscape
    def _landscape(self) -> None:
        m, g = self.margin, self.gap
        header_h = _clamp(round(self.h * 0.115), 44, 104)
        self.header = pygame.Rect(m, m, self.w - 2 * m, header_h)

        top = self.header.bottom + g
        avail_h = self.h - top - m
        panel_w = _clamp(round(self.w * 0.30), 168, 330)
        avail_w = self.w - 2 * m - g - panel_w
        self._fit_board(max(96, min(avail_h, avail_w)))

        group_w = self.frame.w + g + panel_w
        self.frame.topleft = (max(m, (self.w - group_w) // 2),
                              top + max(0, (avail_h - self.frame.h) // 2))
        self.panel = pygame.Rect(self.frame.right + g, self.frame.top,
                                 panel_w, self.frame.h)
        self.stats = None

    # --------------------------------------------------------------- portrait
    def _portrait(self) -> None:
        m, g = self.margin, self.gap
        header_h = _clamp(round(self.h * 0.072), 40, 104)
        stats_h = _clamp(round(self.h * 0.088), 54, 132)
        bar_h = _clamp(round(self.h * 0.078), 46, 116)

        self.header = pygame.Rect(m, m, self.w - 2 * m, header_h)
        self.stats = pygame.Rect(m, self.header.bottom + g, self.w - 2 * m,
                                 stats_h)
        self.bar = pygame.Rect(m, self.h - m - bar_h, self.w - 2 * m, bar_h)

        board_top = self.stats.bottom + g
        avail_h = self.bar.top - g - board_top
        self._fit_board(max(96, min(avail_h, self.w - 2 * m)))

        # A tall phone leaves slack the 8-wide board cannot use. Push most of
        # it above the board so the grid sits within thumb reach, and hand the
        # gap that opens up to the legend.
        slack = max(0, avail_h - self.frame.h)
        above = round(slack * 0.62)
        self.frame.topleft = ((self.w - self.frame.w) // 2, board_top + above)
        inset = round(self.w * 0.08)
        self._portrait_info = (
            pygame.Rect(m + inset, board_top, self.w - 2 * (m + inset),
                        above - g)
            if above > g * 3 else None
        )
        self.panel = None

    # ------------------------------------------------------------------ cards
    def _cards(self) -> None:
        """Three read-outs: time, chain multiplier and best score."""
        if self.portrait:
            inner = self.stats
            pad = max(3, round(self.gap * 0.4))
            each = (inner.w - pad * 2) // 3
            self.cards = [
                pygame.Rect(inner.x + i * (each + pad), inner.y, each, inner.h)
                for i in range(3)
            ]
            self.info = self._portrait_info
            return

        pad = max(6, round(self.panel.w * 0.055))
        inner = self.panel.inflate(-2 * pad, -2 * pad)
        card_h = round(inner.h * 0.155)
        gap = round(inner.h * 0.026)
        self.cards = [
            pygame.Rect(inner.x, inner.y + i * (card_h + gap), inner.w, card_h)
            for i in range(3)
        ]
        buttons_h = round(inner.h * 0.20)
        self.info = pygame.Rect(inner.x, self.cards[-1].bottom + gap, inner.w,
                                max(0, inner.bottom - buttons_h - gap
                                    - self.cards[-1].bottom - gap))
        self._button_area = pygame.Rect(inner.x, inner.bottom - buttons_h,
                                        inner.w, buttons_h)

    # ---------------------------------------------------------------- buttons
    def _buttons(self) -> None:
        """Touch targets for pause / restart / sound / menu."""
        keys = ("pause", "restart", "sound", "menu")
        self.buttons: dict[str, pygame.Rect] = {}
        if self.portrait:
            pad = max(4, round(self.gap * 0.5))
            each = (self.bar.w - pad * 3) // 4
            for i, key in enumerate(keys):
                self.buttons[key] = pygame.Rect(
                    self.bar.x + i * (each + pad), self.bar.y, each,
                    self.bar.h)
            return
        area = self._button_area
        pad = max(4, round(area.h * 0.08))
        cw = (area.w - pad) // 2
        ch = (area.h - pad) // 2
        for i, key in enumerate(keys):
            self.buttons[key] = pygame.Rect(
                area.x + (i % 2) * (cw + pad),
                area.y + (i // 2) * (ch + pad), cw, ch)

    # -------------------------------------------------------- overlay helpers
    def centre_card(self, width_frac: float, height_frac: float) -> pygame.Rect:
        w = round(min(self.w * width_frac, self.w - 2 * self.margin))
        h = round(min(self.h * height_frac, self.h - 2 * self.margin))
        rect = pygame.Rect(0, 0, w, h)
        rect.center = (self.w // 2, self.h // 2)
        return rect


class FarmLayout:
    """Geometry for the merge scene: top bar, orders, board, button bar."""

    REF_CELL = 70

    def __init__(self, width: int, height: int, rows: int, cols: int):
        self.w = width
        self.h = height
        self.rows = rows
        self.cols = cols
        self.portrait = height > width * 1.08
        self.margin = m = _clamp(round(min(width, height) * 0.022), 8, 30)
        self.gap = g = max(6, round(m * 0.7))

        top_h = _clamp(round(height * (0.082 if self.portrait else 0.11)),
                       44, 116)
        self.topbar = pygame.Rect(m, m, width - 2 * m, top_h)

        if self.portrait:
            orders_h = _clamp(round(height * 0.15), 96, 250)
            bar_h = _clamp(round(height * 0.078), 46, 116)
            self.orders = pygame.Rect(m, self.topbar.bottom + g,
                                      width - 2 * m, orders_h)
            self.bar = pygame.Rect(m, height - m - bar_h, width - 2 * m, bar_h)
            area = pygame.Rect(m, self.orders.bottom + g, width - 2 * m,
                               self.bar.top - g - (self.orders.bottom + g))
            self.order_cards = self._row(self.orders, 3)
        else:
            panel_w = _clamp(round(width * 0.32), 200, 380)
            area = pygame.Rect(m, self.topbar.bottom + g,
                               width - 2 * m - g - panel_w,
                               height - m - (self.topbar.bottom + g))
            panel = pygame.Rect(area.right + g, area.y, panel_w, area.h)
            self.orders = pygame.Rect(panel.x, panel.y, panel.w,
                                      round(panel.h * 0.66))
            self.bar = pygame.Rect(panel.x, self.orders.bottom + g, panel.w,
                                   panel.bottom - self.orders.bottom - g)
            self.order_cards = self._column(self.orders, 3)

        self.cell = max(24, min(area.w // cols, area.h // rows))
        self.board = pygame.Rect(0, 0, self.cell * cols, self.cell * rows)
        self.board.center = area.center
        self.area = area
        self.scale = _clamp(self.cell / self.REF_CELL, 0.55, 3.0)
        self._buttons()

    # ------------------------------------------------------------------ helpers
    def fs(self, base: int) -> int:
        return max(9, round(base * self.scale))

    def _row(self, rect, count):
        pad = max(3, round(self.gap * 0.4))
        each = (rect.w - pad * (count - 1)) // count
        return [pygame.Rect(rect.x + i * (each + pad), rect.y, each, rect.h)
                for i in range(count)]

    def _column(self, rect, count):
        pad = max(3, round(self.gap * 0.5))
        each = (rect.h - pad * (count - 1)) // count
        return [pygame.Rect(rect.x, rect.y + i * (each + pad), rect.w, each)
                for i in range(count)]

    def _buttons(self):
        keys = ("story", "blitz", "storage", "menu")
        self.buttons: dict[str, pygame.Rect] = {}
        if self.portrait:
            rects = self._row(self.bar, 4)
        else:
            pad = max(4, round(self.bar.h * 0.05))
            cw = (self.bar.w - pad) // 2
            ch = (self.bar.h - pad) // 2
            rects = [pygame.Rect(self.bar.x + (i % 2) * (cw + pad),
                                 self.bar.y + (i // 2) * (ch + pad), cw, ch)
                     for i in range(4)]
        self.buttons = dict(zip(keys, rects))

    def cell_rect(self, cell) -> pygame.Rect:
        r, c = cell
        return pygame.Rect(self.board.x + c * self.cell,
                           self.board.y + r * self.cell, self.cell, self.cell)

    def cell_at(self, pos):
        c = (pos[0] - self.board.x) // self.cell
        r = (pos[1] - self.board.y) // self.cell
        if 0 <= r < self.rows and 0 <= c < self.cols:
            return (int(r), int(c))
        return None

    def centre_card(self, width_frac: float, height_frac: float) -> pygame.Rect:
        w = round(min(self.w * width_frac, self.w - 2 * self.margin))
        h = round(min(self.h * height_frac, self.h - 2 * self.margin))
        rect = pygame.Rect(0, 0, w, h)
        rect.center = (self.w // 2, self.h // 2)
        return rect
