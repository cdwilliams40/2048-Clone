"""The merge yard: the game's home screen.

Tap a generator to spend energy on a new item, drag matching items together to
climb the chain, and hand the results to the customers waiting along the top.
"""

from __future__ import annotations

import math

import pygame

from .. import art, config, mergeart
from ..app import APP_PAUSING, Scene
from ..merge.board import STORAGE_SLOTS
from ..merge.items import CHAINS, MAX_TIER

CELL_LIGHT = (206, 224, 186)
CELL_DARK = (194, 214, 172)
CELL_EDGE = (168, 192, 148)
DRAG_THRESHOLD = 0.25  # of a cell, before a press counts as a drag


class FarmScene(Scene):
    layout_kind = "farm"

    def __init__(self, app):
        super().__init__(app)
        self.selected: tuple[int, int] | None = None
        self.drag_cell: tuple[int, int] | None = None
        self.drag_pos: tuple[int, int] | None = None
        self.drag_origin: tuple[int, int] | None = None
        self.dragging = False
        self.pop: dict[tuple[int, int], float] = {}
        self.storage_open = False
        self.detail: int | None = None
        self.elapsed = 0.0

    # ------------------------------------------------------------------ enter
    def on_enter(self, **kwargs) -> None:
        self.selected = None
        self.detail = None
        self.storage_open = False
        self._clear_drag()
        session = self.app.session
        session.orders.refill(session.economy.level, session.rng)
        chapter = session.story.current
        if chapter is not None and chapter.key not in session.story.seen_intro:
            self.app.go("story", auto_scene="intro")

    def _clear_drag(self) -> None:
        self.drag_cell = None
        self.drag_pos = None
        self.drag_origin = None
        self.dragging = False

    # ------------------------------------------------------------------ input
    def handle(self, event) -> None:
        if event.type == pygame.KEYDOWN:
            self._on_key(event)
        elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
            self._on_press(event.pos)
        elif event.type == pygame.MOUSEMOTION and event.buttons[0]:
            self._on_motion(event.pos)
        elif event.type == pygame.MOUSEBUTTONUP and event.button == 1:
            self._on_release(event.pos)
        elif APP_PAUSING is not None and event.type == APP_PAUSING:
            self._clear_drag()

    def _on_key(self, event) -> None:
        back = getattr(pygame, "K_AC_BACK", None)
        if event.key == pygame.K_ESCAPE or (back and event.key == back):
            if self.storage_open or self.detail is not None:
                self.storage_open = False
                self.detail = None
            elif self.selected is not None:
                self.selected = None
            else:
                self.app.running = False
        elif event.key == pygame.K_s:
            self.app.go("story")
        elif event.key == pygame.K_b:
            self.app.go("blitz")

    def _on_press(self, pos) -> None:
        L = self.layout
        if self.detail is not None:
            self._press_detail(pos)
            return
        if self.storage_open:
            self._press_storage(pos)
            return
        for key in ("story", "blitz", "storage", "menu"):
            if self.ui.hit(key, pos):
                self._press_button(key)
                return
        if self.selected is not None:
            if self.ui.hit("sell", pos):
                self.app.session.sell(self.selected)
                self.app.audio.play("coin")
                self.selected = None
                return
            if self.ui.hit("store", pos):
                self.app.session.store(self.selected)
                self.selected = None
                return
        for i in range(len(self.app.session.orders.active)):
            if self.ui.hit(f"order{i}", pos):
                self.detail = i
                return
        cell = L.cell_at(pos)
        if cell is None:
            self.selected = None
            return
        if self.app.session.board.at(cell) is None:
            self.selected = None
            return
        self.drag_cell = cell
        self.drag_origin = pos
        self.drag_pos = pos
        self.dragging = False

    def _press_button(self, key: str) -> None:
        if key == "story":
            self.app.go("story")
        elif key == "blitz":
            self.app.go("blitz")
        elif key == "storage":
            self.storage_open = True
        elif key == "menu":
            self.selected = None
            self.app.running = False

    def _press_storage(self, pos) -> None:
        if self.ui.hit("storage_close", pos):
            self.storage_open = False
            return
        for i in range(len(self.app.session.board.storage)):
            if self.ui.hit(f"slot{i}", pos):
                self.app.session.unstore(i)
                return

    def _press_detail(self, pos) -> None:
        session = self.app.session
        if self.ui.hit("detail_close", pos):
            self.detail = None
        elif self.ui.hit("detail_deliver", pos):
            index = self.detail
            if session.deliver(index) is not None:
                self.app.audio.play("rooster")
                self.detail = None
        elif self.ui.hit("detail_skip", pos):
            session.skip_order(self.detail)
            self.detail = None

    def _on_motion(self, pos) -> None:
        if self.drag_cell is None:
            return
        self.drag_pos = pos
        if not self.dragging:
            moved = max(abs(pos[0] - self.drag_origin[0]),
                        abs(pos[1] - self.drag_origin[1]))
            if moved > self.layout.cell * DRAG_THRESHOLD:
                self.dragging = True
                self.selected = None

    def _on_release(self, pos) -> None:
        if self.drag_cell is None:
            return
        source = self.drag_cell
        was_dragging = self.dragging
        self._clear_drag()
        if not was_dragging:
            self._tap(source)
            return
        target = self.layout.cell_at(pos)
        if target is None or target == source:
            return
        result = self.app.session.drop(source, target)
        if result.kind == "merge":
            self.pop[target] = 0.0
            self.app.audio.match(min(7, result.item.tier + 1))
            rect = self.layout.cell_rect(target)
            self.app.effects.burst(*rect.center,
                                   CHAINS[result.item.chain].palette[0], 10)
            self.app.effects.popup(rect.centerx, rect.top,
                                   result.item.name, config.CREAM,
                                   self.layout.fs(18))
            if result.item.tier == MAX_TIER:
                self.app.effects.kick(8)
        elif result.kind in ("move", "swap"):
            self.app.audio.play("swap")

    def _tap(self, cell) -> None:
        session = self.app.session
        item = session.board.at(cell)
        if item is None:
            return
        if item.is_generator:
            before = {c for c, _i in session.board.items()}
            if session.tap(cell):
                self.app.audio.play("select")
                landed = [c for c, _i in session.board.items()
                          if c not in before]
                for spot in landed:
                    self.pop[spot] = 0.0
            else:
                self.app.audio.play("invalid")
            return
        self.selected = None if self.selected == cell else cell

    # ----------------------------------------------------------------- update
    def update(self, dt: float) -> None:
        self.elapsed += dt
        for cell in list(self.pop):
            self.pop[cell] += dt
            if self.pop[cell] > 0.34:
                del self.pop[cell]

    # ------------------------------------------------------------------- draw
    def draw(self) -> None:
        self._draw_topbar()
        self._draw_orders()
        self._draw_board()
        self._draw_bar()
        self.app.effects.draw(self.ui.canvas, self.ui.font)
        if self.storage_open:
            self._draw_storage()
        elif self.detail is not None:
            self._draw_detail()

    # ----------------------------------------------------------------- topbar
    def _draw_topbar(self) -> None:
        L = self.layout
        eco = self.app.session.economy
        bar = L.topbar
        self.ui.plank(bar, config.BARN_RED)
        pad = round(bar.w * 0.02)

        # Level badge with its XP ring drawn as a bar underneath.
        badge_w = round(bar.w * 0.26)
        self.ui.text(f"Lv {eco.level}", L.fs(22), config.CREAM,
                     topleft=(bar.x + pad, bar.y + bar.h * 0.16), bold=True)
        meter = pygame.Rect(bar.x + pad, bar.y + bar.h * 0.62,
                            badge_w, max(5, round(bar.h * 0.14)))
        self.ui.meter(meter, eco.xp_fraction, config.GOLD, (120, 46, 42))

        coin_x = bar.x + bar.w * 0.48
        self._pill(coin_x, bar.centery, config.GOLD, f"{eco.coins:,}")

        energy_x = bar.x + bar.w * 0.76
        label = f"{eco.energy}/{eco.energy_cap}"
        self._pill(energy_x, bar.centery, (126, 200, 240), label)
        if not eco.energy_full:
            secs = int(eco.seconds_to_next_energy)
            self.ui.text(f"+1 in {secs}s", L.fs(13), config.CREAM,
                         right=(bar.right - pad, bar.bottom - bar.h * 0.18),
                         shadow=False)

    def _pill(self, x, y, color, text) -> None:
        L = self.layout
        radius = max(8, round(L.topbar.h * 0.22))
        pygame.draw.circle(self.ui.canvas, color, (int(x), int(y)), radius)
        pygame.draw.circle(self.ui.canvas, art.shade(color, -0.3),
                           (int(x), int(y)), radius, width=max(2, radius // 6))
        self.ui.text(text, L.fs(21), config.CREAM,
                     topleft=(x + radius * 1.35, y - L.fs(21) * 0.62),
                     bold=True)

    # ----------------------------------------------------------------- orders
    def _draw_orders(self) -> None:
        L = self.layout
        session = self.app.session
        for i, card in enumerate(L.order_cards):
            if i >= len(session.orders.active):
                continue
            order = session.orders.active[i]
            ready = order.filled_by(session.board)
            face = (226, 244, 220) if ready else config.CREAM
            self.ui.hitboxes[f"order{i}"] = card
            self.ui.plank(card, face)

            portrait = self.app.portrait_small[order.portrait]
            size = min(card.h - L.fs(22), round(card.w * 0.42))
            portrait = pygame.transform.smoothscale(portrait, (size, size))
            self.ui.canvas.blit(portrait,
                                (card.x + L.fs(6), card.y + L.fs(6)))
            self.ui.text(order.customer, L.fs(14), config.INK,
                         topleft=(card.x + L.fs(6) + size + L.fs(4),
                                  card.y + L.fs(8)), bold=True, shadow=False)

            icon = max(14, min(round(card.h * 0.34), round(L.cell * 0.62)))
            x = card.x + L.fs(6) + size + L.fs(4)
            y = card.y + L.fs(26)
            for request in order.requests[:3]:
                sprite = pygame.transform.smoothscale(
                    self.app.items[(request.chain, request.tier)],
                    (icon, icon))
                self.ui.canvas.blit(sprite, (x, y))
                if request.quantity > 1:
                    self.ui.text(f"x{request.quantity}", L.fs(12), config.INK,
                                 topleft=(x + icon * 0.72, y + icon * 0.6),
                                 bold=True, shadow=False)
                x += icon * 0.92
            self.ui.text(f"{order.coins:,}", L.fs(15), config.BARN_RED,
                         topleft=(card.x + L.fs(8), card.bottom - L.fs(20)),
                         bold=True, shadow=False)
            if ready:
                self.ui.text("READY", L.fs(14), (58, 140, 66),
                             right=(card.right - L.fs(8),
                                    card.bottom - L.fs(12)), bold=True,
                             shadow=False)

    # ------------------------------------------------------------------ board
    def _draw_board(self) -> None:
        L = self.layout
        session = self.app.session
        frame = L.board.inflate(L.gap, L.gap)
        self.ui.plank(frame, config.WOOD, radius=max(8, L.gap))

        for r in range(L.rows):
            for c in range(L.cols):
                rect = L.cell_rect((r, c))
                color = CELL_LIGHT if (r + c) % 2 == 0 else CELL_DARK
                pygame.draw.rect(self.ui.canvas, color, rect)
                pygame.draw.rect(self.ui.canvas, CELL_EDGE, rect, width=1)

        for cell, item in session.board.items():
            if self.dragging and cell == self.drag_cell:
                continue
            self._draw_item(cell, item)

        if self.selected is not None:
            rect = L.cell_rect(self.selected)
            pulse = (math.sin(self.elapsed * 9) + 1) / 2
            pygame.draw.rect(self.ui.canvas, (255, 255, 255),
                             rect.inflate(-3, -3),
                             width=max(2, round(L.cell * 0.05 + pulse * 3)),
                             border_radius=round(L.cell * 0.18))
            self._draw_actions()

        if self.dragging and self.drag_cell is not None:
            item = session.board.at(self.drag_cell)
            if item is not None:
                target = L.cell_at(self.drag_pos)
                if target is not None and target != self.drag_cell:
                    other = session.board.at(target)
                    if other is not None and item.matches(other):
                        rect = L.cell_rect(target)
                        glow = pygame.Surface(rect.size, pygame.SRCALPHA)
                        pygame.draw.rect(glow, (255, 246, 170, 150),
                                         glow.get_rect(),
                                         border_radius=round(L.cell * 0.18))
                        self.ui.canvas.blit(glow, rect.topleft)
                sprite = mergeart.sprite_for(self.app.items, item)
                big = round(sprite.get_width() * 1.14)
                sprite = pygame.transform.smoothscale(sprite, (big, big))
                self.ui.canvas.blit(sprite,
                                    sprite.get_rect(center=self.drag_pos))

    def _draw_item(self, cell, item) -> None:
        L = self.layout
        rect = L.cell_rect(cell)
        sprite = mergeart.sprite_for(self.app.items, item)
        scale = 1.0
        if cell in self.pop:
            t = self.pop[cell] / 0.34
            scale = 1.0 + 0.34 * math.sin(t * math.pi)
        if item.is_generator:
            ready = self.app.session.economy.can_spend(
                CHAINS[item.chain].generator.energy)
            if ready:
                scale *= 1.0 + 0.025 * math.sin(self.elapsed * 4
                                                + cell[0] + cell[1])
            else:
                sprite = sprite.copy()
                sprite.set_alpha(140)
        if scale != 1.0:
            size = max(4, round(sprite.get_width() * scale))
            sprite = pygame.transform.smoothscale(sprite, (size, size))
        self.ui.canvas.blit(sprite, sprite.get_rect(center=rect.center))

    def _draw_actions(self) -> None:
        """Sell / store buttons for the selected item, above the button bar."""
        L = self.layout
        item = self.app.session.board.at(self.selected)
        if item is None or item.is_generator:
            return
        width = round(L.board.w * 0.46)
        height = max(36, round(L.cell * 0.62))
        gap = L.gap
        total = width * 2 + gap
        x = L.board.centerx - total // 2
        y = L.board.bottom + L.gap
        if y + height > L.h - L.margin:
            y = L.board.bottom - height - L.gap
        self.ui.button("sell", pygame.Rect(x, y, width, height),
                       f"Sell  {item.value:,}", config.GOLD, size=18,
                       text_color=config.INK)
        self.ui.button("store", pygame.Rect(x + width + gap, y, width, height),
                       "Store", config.WOOD_DARK, size=18,
                       enabled=len(self.app.session.board.storage)
                       < STORAGE_SLOTS)

    # --------------------------------------------------------------- brm bar
    def _draw_bar(self) -> None:
        L = self.layout
        session = self.app.session
        labels = {
            "story": "Story",
            "blitz": "Blitz",
            "storage": f"Store {len(session.board.storage)}/{STORAGE_SLOTS}",
            "menu": "Quit",
        }
        colors = {"story": config.BARN_RED, "blitz": config.WOOD_DARK,
                  "storage": config.WOOD_DARK, "menu": config.WOOD_DARK}
        for key, rect in L.buttons.items():
            self.ui.button(key, rect, labels[key], colors[key], size=17)
        task = session.story.next_task()
        if task is not None and session.economy.can_afford(task.cost):
            rect = L.buttons["story"]
            pygame.draw.circle(self.ui.canvas, (86, 190, 96),
                               (rect.right - L.fs(8), rect.y + L.fs(8)),
                               max(5, L.fs(7)))

    # ---------------------------------------------------------------- storage
    def _draw_storage(self) -> None:
        L = self.layout
        self.ui.veil(170)
        card = L.centre_card(0.86, 0.5)
        inner = self.ui.panel(card)
        self.ui.text("Storage", L.fs(28), config.BARN_RED,
                     midtop=(inner.centerx, inner.y), bold=True)
        storage = self.app.session.board.storage
        cols = 4
        size = min(round(inner.w / cols) - L.gap,
                   round(inner.h * 0.34))
        top = inner.y + L.fs(40)
        for i in range(STORAGE_SLOTS):
            row, col = divmod(i, cols)
            rect = pygame.Rect(inner.x + col * (size + L.gap),
                               top + row * (size + L.gap), size, size)
            pygame.draw.rect(self.ui.canvas, (214, 206, 186), rect,
                             border_radius=round(size * 0.16))
            if i < len(storage):
                self.ui.hitboxes[f"slot{i}"] = rect
                sprite = pygame.transform.smoothscale(
                    mergeart.sprite_for(self.app.items, storage[i]),
                    (size, size))
                self.ui.canvas.blit(sprite, rect.topleft)
        self.ui.text("Tap an item to send it back to the yard", L.fs(15),
                     config.INK_SOFT, midtop=(inner.centerx,
                                              inner.bottom - L.fs(52)),
                     shadow=False)
        self.ui.button("storage_close",
                       pygame.Rect(0, 0, round(inner.w * 0.4),
                                   max(38, L.fs(44))).move(
                           inner.centerx - round(inner.w * 0.2),
                           inner.bottom - max(38, L.fs(44))),
                       "Close", config.WOOD_DARK, size=20)

    # ----------------------------------------------------------------- detail
    def _draw_detail(self) -> None:
        L = self.layout
        session = self.app.session
        if self.detail >= len(session.orders.active):
            self.detail = None
            return
        order = session.orders.active[self.detail]
        self.ui.veil(175)
        card = L.centre_card(0.88, 0.58)
        inner = self.ui.panel(card)

        portrait = self.app.portrait_big[order.portrait]
        self.ui.canvas.blit(portrait, portrait.get_rect(
            midtop=(inner.centerx, inner.y)))
        y = inner.y + portrait.get_height() + L.fs(6)
        self.ui.text(order.customer, L.fs(26), config.BARN_RED,
                     midtop=(inner.centerx, y), bold=True)
        y += L.fs(32)
        used = self.ui.wrapped(f'"{order.line}"', L.fs(17), config.INK_SOFT,
                               pygame.Rect(inner.x, y, inner.w, inner.h))
        y += used + L.fs(10)

        icon = max(28, round(L.cell * 0.78))
        span = len(order.requests) * (icon + L.fs(10))
        x = inner.centerx - span // 2
        for request in order.requests:
            sprite = pygame.transform.smoothscale(
                self.app.items[(request.chain, request.tier)], (icon, icon))
            self.ui.canvas.blit(sprite, (x, y))
            have = (session.board.count(request.chain, request.tier)
                    + sum(1 for s in session.board.storage
                          if s.chain == request.chain
                          and s.tier == request.tier))
            colour = (58, 140, 66) if have >= request.quantity \
                else config.BARN_RED
            self.ui.text(f"{min(have, request.quantity)}/{request.quantity}",
                         L.fs(15), colour,
                         center=(x + icon // 2, y + icon + L.fs(10)),
                         bold=True, shadow=False)
            x += icon + L.fs(10)
        y += icon + L.fs(30)
        self.ui.text(f"Reward  {order.coins:,} coins   {order.xp} xp",
                     L.fs(18), config.INK, midtop=(inner.centerx, y),
                     bold=True)

        bw = round(inner.w * 0.30)
        bh = max(38, L.fs(46))
        by = inner.bottom - bh
        self.ui.button("detail_deliver",
                       pygame.Rect(inner.centerx - bw - L.gap, by, bw, bh),
                       "Deliver", (86, 160, 82), size=19,
                       enabled=order.filled_by(session.board))
        self.ui.button("detail_close",
                       pygame.Rect(inner.centerx, by, bw, bh),
                       "Close", config.WOOD_DARK, size=19)
        self.ui.button("detail_skip",
                       pygame.Rect(inner.right - bw // 2, by, bw // 2, bh),
                       "Skip", config.WOOD, size=16)
