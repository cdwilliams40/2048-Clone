"""Barnyard Blitz - the pygame application layer.

Holds the state machine that turns player input and :mod:`barnyard.board`
results into animation, scoring and sound. All geometry comes from
:class:`barnyard.layout.Layout`, so the same code drives a resizable desktop
window and a full-screen phone display.
"""

from __future__ import annotations

import math
import random
from enum import Enum, auto

import pygame

from . import art, config, platform
from .audio import Audio
from .board import Board, Power
from .effects import Effects
from .layout import Layout
from .scores import HighScores

MODES = {
    "blitz": ("Blitz", "60 seconds. Match fast, cascade faster."),
    "relaxed": ("Relaxed", "No clock. Play until the barn runs dry."),
}

# Present on pygame 2 only, and only ever delivered on mobile.
APP_PAUSING = getattr(pygame, "APP_WILLENTERBACKGROUND", None)
APP_RESUMING = getattr(pygame, "APP_DIDENTERFOREGROUND", None)
BACK_KEY = getattr(pygame, "K_AC_BACK", None)


class Screen(Enum):
    MENU = auto()
    PLAYING = auto()
    GAME_OVER = auto()


class Phase(Enum):
    IDLE = auto()
    SWAP = auto()
    REVERT = auto()
    CLEAR = auto()
    FALL = auto()
    SHUFFLE = auto()
    FINALE = auto()


def ease_out(t: float) -> float:
    return 1 - (1 - t) ** 3


def ease_in(t: float) -> float:
    return t * t


class Game:
    def __init__(self, surface: pygame.Surface | None = None):
        pygame.display.set_caption("Barnyard Blitz")
        if surface is None:
            surface = pygame.display.set_mode(
                (config.WIDTH, config.HEIGHT), pygame.RESIZABLE)
        self.screen = surface
        self.clock = pygame.time.Clock()
        self.rng = random.Random()

        self.audio = Audio()
        self.scores = HighScores()
        self.effects = Effects(self.rng)

        self._fonts: dict[tuple[int, bool], pygame.font.Font] = {}
        self.state = Screen.MENU
        self.mode = "blitz"
        self.board = Board(rng=self.rng)
        self.hitboxes: dict[str, pygame.Rect] = {}
        self.running = True
        self._reset_round()
        self._apply_layout(self.screen.get_size())

    # ----------------------------------------------------------------- layout
    def _apply_layout(self, size) -> None:
        width, height = max(320, size[0]), max(400, size[1])
        self.L = Layout(width, height, self.board.rows, self.board.cols)
        self.canvas = pygame.Surface((width, height))
        self.sprites = art.build_tile_sprites(max(12, self.L.tile - 6))
        self.background = art.build_background(width, height, self.L.margin)
        self.barn = art.build_barn(round(self.L.tile * 2.9),
                                   round(self.L.tile * 2.3))
        self._fonts.clear()

    def _on_resize(self, size) -> None:
        if size == self.canvas.get_size():
            return
        self._apply_layout(size)

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

    # ------------------------------------------------------------------ round
    def _reset_round(self) -> None:
        self.board = Board(rng=self.rng)
        self.score = 0
        self.cascade = 0
        self.best_cascade = 0
        self.moves_made = 0
        self.time_left = float(config.BLITZ_SECONDS)
        self.elapsed = 0.0
        self.time_over = False
        self.new_record = False
        self.paused = False

        self.phase = Phase.IDLE
        self.phase_t = 0.0
        self.phase_len = 0.0
        self.offsets: dict[tuple[int, int], tuple[float, float]] = {}
        self.dying: list[tuple[tuple[int, int], pygame.Surface]] = []
        self.selected: tuple[int, int] | None = None
        self.pending: tuple[tuple[int, int], tuple[int, int]] | None = None
        self.swap_legal = False
        self.idle_time = 0.0
        self.hint: tuple[tuple[int, int], tuple[int, int]] | None = None
        self.banner = ""
        self.banner_time = 0.0
        self.next_tick = int(config.BLITZ_SECONDS)
        self.drag_from: tuple[int, int] | None = None
        self.drag_origin: tuple[int, int] | None = None
        self.drag_used = False
        self.effects.particles.clear()
        self.effects.popups.clear()

    def start(self, mode: str) -> None:
        self.mode = mode
        self._reset_round()
        self.state = Screen.PLAYING
        self.audio.play("start")

    # ------------------------------------------------------------------- loop
    def run(self) -> None:
        while self.running:
            dt = min(self.clock.tick(config.FPS) / 1000.0, 0.05)
            self.handle_events()
            self.update(dt)
            self.draw()
        self.scores.save()
        pygame.quit()

    def handle_events(self) -> None:
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                self.running = False
            elif event.type == pygame.VIDEORESIZE:
                self._on_resize((event.w, event.h))
            elif event.type == pygame.KEYDOWN:
                self._on_key(event)
            elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
                self._on_press(event.pos)
            elif event.type == pygame.MOUSEMOTION and event.buttons[0]:
                self._on_drag(event.pos)
            elif event.type == pygame.MOUSEBUTTONUP and event.button == 1:
                self._on_release(event.pos)
            elif APP_PAUSING is not None and event.type == APP_PAUSING:
                # Android is backgrounding us: freeze play and flush the save.
                if self.state is Screen.PLAYING:
                    self.paused = True
                self.scores.save()
            elif APP_RESUMING is not None and event.type == APP_RESUMING:
                self._on_resize(self.screen.get_size())

    def _on_key(self, event) -> None:
        if BACK_KEY is not None and event.key == BACK_KEY:
            self._go_back()
            return
        if event.key == pygame.K_m:
            muted = self.audio.toggle_mute()
            self._say("Sound off" if muted else "Sound on")
            return
        if event.key == pygame.K_ESCAPE:
            self._go_back()
            return
        if self.state is Screen.PLAYING:
            if event.key in (pygame.K_p, pygame.K_SPACE):
                self.paused = not self.paused
            elif event.key == pygame.K_r:
                self.start(self.mode)
        elif self.state is Screen.MENU:
            if event.key in (pygame.K_1, pygame.K_RETURN):
                self.start("blitz")
            elif event.key == pygame.K_2:
                self.start("relaxed")
        elif self.state is Screen.GAME_OVER:
            if event.key == pygame.K_RETURN:
                self.start(self.mode)

    def _go_back(self) -> None:
        if self.state is Screen.MENU:
            self.running = False
        else:
            self.state = Screen.MENU

    # ------------------------------------------------------------------ input
    def _hit(self, key: str, pos) -> bool:
        rect = self.hitboxes.get(key)
        return rect is not None and rect.collidepoint(pos)

    def _playable(self) -> bool:
        return (self.state is Screen.PLAYING and not self.paused
                and not self.time_over and self.phase is Phase.IDLE)

    def _on_press(self, pos) -> None:
        if self.state is Screen.MENU:
            if self._hit("blitz", pos):
                self.start("blitz")
            elif self._hit("relaxed", pos):
                self.start("relaxed")
            return
        if self.state is Screen.GAME_OVER:
            if self._hit("again", pos):
                self.start(self.mode)
            elif self._hit("menu", pos):
                self.state = Screen.MENU
            return
        if self._on_control(pos):
            return
        if not self._playable():
            return
        cell = self.L.cell_at(pos)
        if cell is None:
            self.selected = None
            return
        self.drag_from = cell
        self.drag_origin = pos
        self.drag_used = False
        if self.selected is not None and Board.adjacent(self.selected, cell):
            self._attempt_swap(self.selected, cell)
            self.drag_from = None
        elif self.selected == cell:
            self.selected = None
        else:
            self.selected = cell
            self.audio.play("select")

    def _on_control(self, pos) -> bool:
        """The on-screen buttons, which are the only controls a phone has."""
        if self._hit("pause", pos):
            self.paused = not self.paused
            return True
        if self._hit("restart", pos):
            self.start(self.mode)
            return True
        if self._hit("sound", pos):
            muted = self.audio.toggle_mute()
            self._say("Sound off" if muted else "Sound on")
            return True
        if self._hit("menu", pos):
            self.state = Screen.MENU
            return True
        return False

    def _on_drag(self, pos) -> None:
        """Swipe a critter towards its neighbour - the natural phone gesture."""
        if self.drag_from is None or self.drag_used or not self._playable():
            return
        dx = pos[0] - self.drag_origin[0]
        dy = pos[1] - self.drag_origin[1]
        threshold = self.L.tile * 0.42
        if max(abs(dx), abs(dy)) < threshold:
            return
        if abs(dx) > abs(dy):
            step = (0, 1 if dx > 0 else -1)
        else:
            step = (1 if dy > 0 else -1, 0)
        target = (self.drag_from[0] + step[0], self.drag_from[1] + step[1])
        if self.board.in_bounds(target):
            self.drag_used = True
            self._attempt_swap(self.drag_from, target)
        self.drag_from = None

    def _on_release(self, pos) -> None:
        self.drag_from = None
        self.drag_origin = None

    def _attempt_swap(self, a, b) -> None:
        self.swap_legal = self.board.swap_is_legal(a, b)
        self.board.swap(a, b)
        self.pending = (a, b)
        self.selected = None
        self.hint = None
        self.idle_time = 0.0
        ax, ay = self.L.cell_rect(a).topleft
        bx, by = self.L.cell_rect(b).topleft
        self.offsets = {a: (bx - ax, by - ay), b: (ax - bx, ay - by)}
        self._set_phase(Phase.SWAP, config.SWAP_TIME)
        self.audio.play("swap")

    # ------------------------------------------------------------------ phases
    def _set_phase(self, phase: Phase, length: float) -> None:
        self.phase = phase
        self.phase_t = 0.0
        self.phase_len = max(0.0001, length)

    def _advance(self, dt: float) -> None:
        if self.phase is Phase.IDLE:
            self._update_idle(dt)
            return
        self.phase_t += dt
        if self.phase_t < self.phase_len:
            return
        finished, self.phase_t = self.phase, 0.0
        if finished is Phase.SWAP:
            self._after_swap()
        elif finished is Phase.REVERT:
            self.offsets.clear()
            self.pending = None
            self._set_phase(Phase.IDLE, 0.0)
        elif finished is Phase.CLEAR:
            self.dying.clear()
            self._collapse()
        elif finished is Phase.FALL:
            self.offsets.clear()
            self._resolve_or_settle()
        elif finished is Phase.SHUFFLE:
            self.offsets.clear()
            self._set_phase(Phase.IDLE, 0.0)
        elif finished is Phase.FINALE:
            self._finale_step()

    def _update_idle(self, dt: float) -> None:
        if self.time_over:
            self._start_finale()
            return
        self.idle_time += dt
        if self.hint is None and self.idle_time > config.HINT_DELAY:
            self.hint = self.board.find_hint()

    def _after_swap(self) -> None:
        self.offsets.clear()
        assert self.pending is not None
        a, b = self.pending
        if not self.swap_legal:
            self.board.swap(a, b)
            ax, ay = self.L.cell_rect(a).topleft
            bx, by = self.L.cell_rect(b).topleft
            self.offsets = {a: (bx - ax, by - ay), b: (ax - bx, ay - by)}
            self._set_phase(Phase.REVERT, config.REVERT_TIME)
            self.audio.play("invalid")
            return

        self.moves_made += 1
        self.cascade = 0
        ta, tb = self.board.at(a), self.board.at(b)
        if ta is not None and ta.power is Power.ROOSTER:
            result = self.board.activate_rooster(a, b)
        elif tb is not None and tb.power is Power.ROOSTER:
            result = self.board.activate_rooster(b, a)
        else:
            result = self.board.resolve_matches(prefer=b)
        self.pending = None
        if result:
            self._begin_clear(result)
        else:
            self._set_phase(Phase.IDLE, 0.0)

    def _resolve_or_settle(self) -> None:
        result = self.board.resolve_matches()
        if result:
            self._begin_clear(result)
            return
        self.cascade = 0
        if not self.board.has_moves():
            self._shuffle_board()
            return
        self._set_phase(Phase.IDLE, 0.0)
        self.idle_time = 0.0

    def _shuffle_board(self) -> None:
        self.board.shuffle()
        self._say("No moves - shuffling the barnyard!")
        self.audio.play("shuffle")
        self.offsets = {cell: (0, -self.L.board_h)
                        for cell in self.board.cells()}
        self._set_phase(Phase.SHUFFLE, config.FALL_TIME * 1.6)

    # ---------------------------------------------------------------- scoring
    def _begin_clear(self, result) -> None:
        self.cascade += 1
        self.best_cascade = max(self.best_cascade, self.cascade)
        multiplier = min(self.cascade, config.MAX_CASCADE_MULT)
        gained = len(result.cleared) * config.POINTS_PER_TILE * multiplier

        for _cell, _kind, power in result.specials:
            gained += config.SPECIAL_BONUS[power.value]

        for name, cell in result.effects:
            x, y = self.L.cell_rect(cell).center
            if name == "egg":
                self.effects.ring(x, y, (250, 214, 110), 26, 460)
                self.effects.kick(7)
            elif name == "hay":
                self.effects.ring(x, y, (240, 226, 150), 30, 560)
                self.effects.kick(9)
            else:
                self.effects.feathers(x, y, 26)
                self.effects.kick(12)
            self.audio.play(name)

        for cell, tile in result.cleared.items():
            x, y = self.L.cell_rect(cell).center
            self.effects.burst(x, y, config.ANIMALS[tile.kind][1], 7)
            self.dying.append((cell, self.sprites[(tile.kind, tile.power)]))

        focus = result.focus
        if focus is not None:
            fx, fy = self.L.cell_rect(focus).center
            self.effects.popup(fx, fy - 6, f"+{gained:,}", config.CREAM,
                               self.L.fs(26 + min(10, multiplier)))
            if multiplier > 1:
                self.effects.popup(fx, fy - self.L.tile * 0.5,
                                   f"x{multiplier} CHAIN", config.GOLD,
                                   self.L.fs(22))
        for _cell, _kind, power in result.specials:
            self._say({
                "egg": "Golden Egg!",
                "hay": "Hay Bale!",
                "rooster": "Prize Rooster!",
            }[power.value])

        self.score += gained
        self.audio.match(self.cascade - 1)
        self.effects.kick(2 + multiplier)
        self._set_phase(Phase.CLEAR, config.CLEAR_TIME)

    def _collapse(self) -> None:
        moves, spawns = self.board.collapse()
        self.offsets = {}
        for col, from_row, to_row in moves:
            self.offsets[(to_row, col)] = (0, (from_row - to_row) * self.L.tile)
        for col, row, height in spawns:
            self.offsets[(row, col)] = (0, -height * self.L.tile)
        self._set_phase(Phase.FALL, config.FALL_TIME)

    # ----------------------------------------------------------------- finale
    def _start_finale(self) -> None:
        self._say("Last hurrah!")
        self._set_phase(Phase.FINALE, config.FINALE_STEP)

    def _finale_step(self) -> None:
        result = self.board.detonate_all_specials()
        if not result:
            self._end_round()
            return
        bonus = len(result.cleared) * config.POINTS_PER_TILE * 3
        self.score += bonus
        for name, cell in result.effects:
            x, y = self.L.cell_rect(cell).center
            self.effects.ring(x, y, config.GOLD, 24, 480)
            self.audio.play(name)
        for cell, tile in result.cleared.items():
            x, y = self.L.cell_rect(cell).center
            self.effects.burst(x, y, config.ANIMALS[tile.kind][1], 6)
        cells = sorted(result.cleared)
        if cells:
            fx, fy = self.L.cell_rect(cells[len(cells) // 2]).center
            self.effects.popup(fx, fy, f"+{bonus:,}", config.GOLD,
                               self.L.fs(30))
        self.effects.kick(10)
        self._set_phase(Phase.FINALE, config.FINALE_STEP)

    def _end_round(self) -> None:
        self.new_record = self.scores.submit(self.mode, self.score)
        self.state = Screen.GAME_OVER
        self.audio.play("over")

    def _say(self, text: str) -> None:
        self.banner = text
        self.banner_time = 1.6

    # ------------------------------------------------------------------ update
    def update(self, dt: float) -> None:
        self.effects.update(dt)
        self.banner_time = max(0.0, self.banner_time - dt)
        if self.state is not Screen.PLAYING or self.paused:
            return
        self.elapsed += dt
        if self.mode == "blitz" and not self.time_over:
            self.time_left = max(0.0, self.time_left - dt)
            if self.time_left <= 10 and int(self.time_left) < self.next_tick:
                self.next_tick = int(self.time_left)
                self.audio.play("tick", 0.6)
            if self.time_left <= 0:
                self.time_over = True
        self._advance(dt)

    def _tile_offset(self, cell) -> tuple[float, float]:
        base = self.offsets.get(cell)
        if base is None:
            return (0.0, 0.0)
        remaining = 1.0 - ease_out(min(1.0, self.phase_t / self.phase_len))
        return (base[0] * remaining, base[1] * remaining)

    # -------------------------------------------------------------------- draw
    def draw(self) -> None:
        self.canvas.blit(self.background, (0, 0))
        self.hitboxes.clear()
        if self.state is Screen.MENU:
            self._draw_menu()
        else:
            self._draw_board()
            self._draw_hud()
            self.effects.draw(self.canvas, self.font)
            self._draw_banner()
            if self.paused:
                self._draw_overlay("Paused", "Tap pause again to play on")
            if self.state is Screen.GAME_OVER:
                self._draw_game_over()
        dx, dy = self.effects.offset()
        self.screen.fill(config.WOOD_DARK)
        self.screen.blit(self.canvas, (dx, dy))
        pygame.display.flip()

    # ----------------------------------------------------------------- widgets
    def _plank(self, rect, color=config.WOOD, radius=None) -> None:
        # Derive the trim from the shorter side and cap it: a board frame is
        # hundreds of pixels tall and a proportional shadow would run off the
        # bottom of the screen.
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

    def _text(self, text, size, color, center=None, topleft=None, right=None,
              bold=False, shadow=True):
        font = self.font(size, bold)
        label = font.render(text, True, color)
        rect = label.get_rect()
        if center:
            rect.center = center
        elif topleft:
            rect.topleft = topleft
        elif right:
            rect.midright = right
        if shadow:
            dark = font.render(text, True, (44, 34, 28))
            dark.set_alpha(110)
            self.canvas.blit(dark, rect.move(2, 2))
        self.canvas.blit(label, rect)
        return rect

    def _button(self, key, rect, label, color=config.BARN_RED,
                size: int = 27) -> None:
        self.hitboxes[key] = rect
        # On a touch screen the pointer parks wherever the last tap landed, so
        # a hover highlight would just stick to a random button.
        hovered = (not platform.touch_first()
                   and rect.collidepoint(pygame.mouse.get_pos()))
        shade = art.shade(color, 0.18) if hovered else color
        self._plank(rect, shade)
        self._text(label, self.L.fs(size), config.CREAM, center=rect.center,
                   bold=True)

    def _card(self, rect, title, value, value_color=config.INK,
              bar: float | None = None, bar_color=config.GOLD) -> None:
        self._plank(rect, config.CREAM)
        self._text(title, self.L.fs(15), config.INK_SOFT,
                   center=(rect.centerx, rect.y + rect.h * 0.20), shadow=False)
        offset = 0.52 if bar is None else 0.48
        self._text(value, self.L.fs(38), value_color,
                   center=(rect.centerx, rect.y + rect.h * offset + rect.h * 0.06),
                   bold=True)
        if bar is None:
            return
        track = pygame.Rect(rect.x + rect.w * 0.10, rect.bottom - rect.h * 0.20,
                            rect.w * 0.80, max(4, rect.h * 0.09))
        pygame.draw.rect(self.canvas, (206, 196, 176), track,
                         border_radius=int(track.h / 2))
        fill = pygame.Rect(track.x, track.y, int(track.w * max(0.0, min(1.0, bar))),
                           track.h)
        pygame.draw.rect(self.canvas, bar_color, fill,
                         border_radius=int(track.h / 2))

    # -------------------------------------------------------------------- menu
    def _draw_menu(self) -> None:
        L = self.L
        cx = L.w // 2
        lines = [
            "Line up three or more of the same critter.",
            "Match 4 for a Golden Egg, an L or T for a Hay Bale,",
            "5 in a row for a Prize Rooster that clears a species.",
            "Tap two neighbours, or swipe one into the other.",
        ]

        # Measure the whole stack first so it can be centred - a 2:1 phone has
        # far more height than the content needs.
        gap = L.fs(18)
        head_h = round(min(L.h * 0.14, L.w * 0.26))
        barn_h = self.barn.get_height()
        row_h = L.tile
        btn_h = max(40, min(round(L.h * 0.075), round(L.tile * 1.15)))
        best_h = L.fs(24)
        help_h = len(lines) * L.fs(24)
        total = (head_h + round(gap * 1.4) + barn_h + gap + row_h
                 + round(gap * 1.2) + btn_h + best_h + gap + help_h)
        y = max(L.margin, (L.h - total) // 2)

        header = pygame.Rect(0, 0, min(L.w - 2 * L.margin, round(L.w * 0.86)),
                             head_h)
        header.midtop = (cx, y)
        self._plank(header, config.BARN_RED)
        self._text("BARNYARD BLITZ", L.fs(48), config.CREAM,
                   center=(cx, header.centery - header.h * 0.14), bold=True)
        self._text("a farm-fresh match-3 romp", L.fs(19), config.GOLD,
                   center=(cx, header.centery + header.h * 0.26))
        y = header.bottom + round(gap * 1.4)

        self.canvas.blit(self.barn,
                         self.barn.get_rect(midtop=(cx, y)))
        y += barn_h + gap

        step = round(L.tile * 0.95)
        for i in range(len(config.ANIMALS)):
            sprite = self.sprites[(i, Power.NONE)]
            x = cx - (len(config.ANIMALS) * step) // 2 + i * step + step // 2
            bob = math.sin(self.elapsed * 3 + i * 0.7) * (L.tile * 0.08)
            self.canvas.blit(sprite, sprite.get_rect(
                center=(x, int(y + row_h / 2 + bob))))
        y += row_h + round(gap * 1.2)

        bw = min(round(L.w * 0.42), round(L.tile * 4.2))
        self._button("blitz", pygame.Rect(cx - bw - L.gap // 2, y, bw, btn_h),
                     "Blitz  60s")
        self._button("relaxed", pygame.Rect(cx + L.gap // 2, y, bw, btn_h),
                     "Relaxed", config.WOOD)
        y += btn_h + round(btn_h * 0.10)  # clear the plank's drop shadow
        for i, mode in enumerate(("blitz", "relaxed")):
            self._text(f"best {self.scores.best(mode):,}", L.fs(18), config.INK,
                       center=(cx + (i * 2 - 1) * (bw + L.gap) // 2,
                               y + best_h // 2))
        y += best_h + gap

        for line in lines:
            self._text(line, L.fs(17), config.INK, center=(cx, y + L.fs(12)),
                       shadow=False)
            y += L.fs(24)

    # ------------------------------------------------------------------- board
    def _draw_board(self) -> None:
        L = self.L
        self._plank(L.frame, config.WOOD, radius=max(8, L.frame_pad * 2))
        board = L.board
        for r in range(self.board.rows):
            for c in range(self.board.cols):
                color = config.CELL_LIGHT if (r + c) % 2 == 0 else config.CELL_DARK
                pygame.draw.rect(self.canvas, color, L.cell_rect((r, c)))

        self.canvas.set_clip(board)

        if self.hint and self.phase is Phase.IDLE:
            pulse = (math.sin(self.elapsed * 7) + 1) / 2
            for cell in self.hint:
                rect = L.cell_rect(cell)
                glow = pygame.Surface(rect.size, pygame.SRCALPHA)
                pygame.draw.rect(glow, (255, 246, 190, int(70 + 90 * pulse)),
                                 glow.get_rect(),
                                 border_radius=round(L.tile * 0.18))
                self.canvas.blit(glow, rect.topleft)

        for cell in self.board.cells():
            tile = self.board.at(cell)
            if tile is None:
                continue
            ox, oy = self._tile_offset(cell)
            cx, cy = L.cell_rect(cell).center
            sprite = self.sprites[(tile.kind, tile.power)]
            if tile.power is Power.ROOSTER:
                pulse = 1.0 + 0.05 * math.sin(self.elapsed * 6)
                size = int(sprite.get_width() * pulse)
                sprite = pygame.transform.smoothscale(sprite, (size, size))
            self.canvas.blit(sprite,
                             sprite.get_rect(center=(cx + ox, cy + oy)))

        if self.selected is not None:
            rect = L.cell_rect(self.selected).inflate(-4, -4)
            pulse = (math.sin(self.elapsed * 9) + 1) / 2
            pygame.draw.rect(self.canvas, (255, 255, 255), rect,
                             width=max(2, round(L.tile * 0.05 + pulse * 3)),
                             border_radius=round(L.tile * 0.2))

        progress = min(1.0, self.phase_t / self.phase_len) \
            if self.phase is Phase.CLEAR else 0.0
        for cell, sprite in self.dying:
            scale = max(0.05, 1.0 - ease_in(progress))
            size = max(2, int(sprite.get_width() * scale))
            shrunk = pygame.transform.smoothscale(sprite, (size, size))
            shrunk.set_alpha(int(255 * (1.0 - progress)))
            self.canvas.blit(shrunk,
                             shrunk.get_rect(center=L.cell_rect(cell).center))

        self.canvas.set_clip(None)

    # --------------------------------------------------------------------- HUD
    def _draw_hud(self) -> None:
        L = self.L
        self._plank(L.header, config.BARN_RED)
        pad = round(L.header.w * 0.025)
        if L.portrait:
            self._text("BARNYARD BLITZ", L.fs(24), config.CREAM,
                       topleft=(L.header.x + pad,
                                L.header.centery - L.fs(24) * 0.62), bold=True)
            self._text(f"{self.score:,}", L.fs(30), config.GOLD,
                       right=(L.header.right - pad, L.header.centery),
                       bold=True)
        else:
            self._text("BARNYARD BLITZ", L.fs(34), config.CREAM,
                       topleft=(L.header.x + pad,
                                L.header.centery - L.fs(34) * 0.6), bold=True)
            anchor = L.header.right - L.header.w * 0.13
            self._text(f"{self.score:,}", L.fs(40), config.GOLD,
                       center=(anchor, L.header.centery - L.fs(8)), bold=True)
            self._text("SCORE", L.fs(15), config.CREAM,
                       center=(anchor, L.header.bottom - L.fs(14)))

        if L.panel is not None:
            self._plank(L.panel, config.WOOD_LIGHT)

        time_card, chain_card, best_card = L.cards
        if self.mode == "blitz":
            left = max(0.0, self.time_left)
            frac = left / config.BLITZ_SECONDS
            urgent = left <= 10
            tone = (196, 62, 48) if frac < 0.2 else \
                (232, 168, 52) if frac < 0.5 else (108, 176, 96)
            self._card(time_card, "TIME", f"{left:0.1f}",
                       (196, 62, 48) if urgent else config.INK, frac, tone)
        else:
            mins, secs = divmod(int(self.elapsed), 60)
            self._card(time_card, "TIME PLAYED", f"{mins}:{secs:02d}")

        chain = max(1, self.cascade)
        self._card(chain_card, "CHAIN", f"x{min(chain, config.MAX_CASCADE_MULT)}",
                   config.BARN_RED if chain > 1 else config.INK_SOFT,
                   min(1.0, self.cascade / config.MAX_CASCADE_MULT))
        self._card(best_card, "BEST",
                   f"{max(self.scores.best(self.mode), self.score):,}")

        if L.info is not None and L.info.h > L.fs(90):
            self._draw_panel_info(L.info)
        self._draw_controls()

    def _draw_panel_info(self, area: pygame.Rect) -> None:
        L = self.L
        # Landscape draws this on the wooden panel, portrait on open sky, so
        # the ink has to flip to stay legible.
        on_wood = not L.portrait
        label = config.CREAM if on_wood else config.INK
        accent = config.GOLD if on_wood else config.BARN_RED
        y = area.y
        self._text(MODES[self.mode][0].upper() + " MODE", L.fs(18), label,
                   center=(area.centerx, y + L.fs(10)))
        y += L.fs(28)
        rows = [("4 in a row", "Golden Egg"), ("L or T shape", "Hay Bale"),
                ("5 in a row", "Prize Rooster")]
        for shape, name in rows:
            if y + L.fs(20) > area.bottom:
                return
            self._text(shape, L.fs(16), label, topleft=(area.x, y),
                       shadow=False)
            self._text(name, L.fs(16), accent,
                       topleft=(area.x + area.w * 0.44, y), shadow=False)
            y += L.fs(22)
        y += L.fs(8)
        for line in (f"Moves  {self.moves_made}",
                     f"Best chain  x{self.best_cascade}"):
            if y + L.fs(20) > area.bottom:
                return
            self._text(line, L.fs(16), label, topleft=(area.x, y),
                       shadow=False)
            y += L.fs(22)

    def _draw_controls(self) -> None:
        """On-screen buttons - a phone has no keyboard to fall back on."""
        labels = {
            "pause": "Play" if self.paused else "Pause",
            "restart": "Restart",
            "sound": "Unmute" if self.audio.muted else "Mute",
            "menu": "Menu",
        }
        colors = {"pause": config.BARN_RED, "restart": config.WOOD_DARK,
                  "sound": config.WOOD_DARK, "menu": config.WOOD_DARK}
        for key, rect in self.L.buttons.items():
            self._button(key, rect, labels[key], colors[key], size=18)

    def _draw_banner(self) -> None:
        if self.banner_time <= 0 or not self.banner:
            return
        L = self.L
        alpha = min(1.0, self.banner_time / 0.4)
        font = self.font(L.fs(28), True)
        label = font.render(self.banner, True, config.CREAM)
        rect = label.get_rect(center=(L.board.centerx,
                                      L.board.y + L.tile * 0.45))
        pad = rect.inflate(L.fs(30), L.fs(16))
        plate = pygame.Surface(pad.size, pygame.SRCALPHA)
        pygame.draw.rect(plate, (*config.BARN_RED, int(215 * alpha)),
                         plate.get_rect(), border_radius=round(pad.h * 0.3))
        self.canvas.blit(plate, pad.topleft)
        label.set_alpha(int(255 * alpha))
        self.canvas.blit(label, rect)

    def _veil(self, alpha: int) -> None:
        veil = pygame.Surface((self.L.w, self.L.h), pygame.SRCALPHA)
        veil.fill((30, 24, 20, alpha))
        self.canvas.blit(veil, (0, 0))

    def _draw_overlay(self, title: str, subtitle: str) -> None:
        self._veil(170)
        cx = self.L.w // 2
        self._text(title, self.L.fs(50), config.CREAM,
                   center=(cx, self.L.h // 2 - self.L.fs(30)), bold=True)
        self._text(subtitle, self.L.fs(21), config.GOLD,
                   center=(cx, self.L.h // 2 + self.L.fs(20)))
        self._draw_controls()

    def _draw_game_over(self) -> None:
        L = self.L
        self._veil(185)
        cx = L.w // 2
        card = L.centre_card(0.82 if L.portrait else 0.58, 0.56)
        self._plank(card, config.WOOD)
        inner = card.inflate(-round(card.w * 0.05), -round(card.h * 0.06))
        self._plank(inner, config.CREAM)

        self._text("That's all, folks!", L.fs(36), config.BARN_RED,
                   center=(cx, inner.y + inner.h * 0.12), bold=True)
        self._text(f"{self.score:,}", L.fs(62), config.INK,
                   center=(cx, inner.y + inner.h * 0.32), bold=True)
        self._text("final score", L.fs(17), config.INK_SOFT,
                   center=(cx, inner.y + inner.h * 0.44), shadow=False)
        if self.new_record:
            pulse = (math.sin(self.elapsed * 6) + 1) / 2
            self._text("NEW BARN RECORD!", L.fs(23),
                       art.shade(config.GOLD, pulse * 0.3),
                       center=(cx, inner.y + inner.h * 0.55), bold=True)
        else:
            self._text(f"best  {self.scores.best(self.mode):,}", L.fs(19),
                       config.INK_SOFT, center=(cx, inner.y + inner.h * 0.55),
                       shadow=False)
        self._text(f"{self.moves_made} moves    best chain x{self.best_cascade}",
                   L.fs(18), config.INK_SOFT,
                   center=(cx, inner.y + inner.h * 0.66), shadow=False)

        bw = round(inner.w * 0.40)
        bh = max(40, round(inner.h * 0.16))
        by = inner.bottom - bh - round(inner.h * 0.07)
        self._button("again", pygame.Rect(cx - bw - L.gap // 2, by, bw, bh),
                     "Play again", size=24)
        self._button("menu", pygame.Rect(cx + L.gap // 2, by, bw, bh),
                     "Menu", config.WOOD_DARK, size=24)
