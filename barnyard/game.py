"""Barnyard Blitz - the pygame application layer.

Holds the state machine that turns player input and :mod:`barnyard.board`
results into animation, scoring and sound.
"""

from __future__ import annotations

import math
import random
from enum import Enum, auto

import pygame

from . import art, config
from .audio import Audio
from .board import Board, Power
from .effects import Effects
from .scores import HighScores

MODES = {
    "blitz": ("Blitz", "60 seconds. Match fast, cascade faster."),
    "relaxed": ("Relaxed", "No clock. Play until the barn runs dry."),
}


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
    def __init__(self):
        pygame.display.set_caption("Barnyard Blitz")
        self.screen = pygame.display.set_mode((config.WIDTH, config.HEIGHT))
        self.canvas = pygame.Surface((config.WIDTH, config.HEIGHT))
        self.clock = pygame.time.Clock()
        self.rng = random.Random()

        self.audio = Audio()
        self.scores = HighScores()
        self.effects = Effects(self.rng)

        self._fonts: dict[tuple[int, bool], pygame.font.Font] = {}
        self.sprites = art.build_tile_sprites(config.TILE - 6)
        self.background = art.build_background(config.WIDTH, config.HEIGHT)
        self.barn = art.build_barn(190, 150)

        self.state = Screen.MENU
        self.mode = "blitz"
        self.board = Board(rng=self.rng)
        self.buttons: dict[str, pygame.Rect] = {}
        self.running = True
        self._reset_round()

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
        self.dying: list[tuple[pygame.Rect, pygame.Surface]] = []
        self.selected: tuple[int, int] | None = None
        self.pending: tuple[tuple[int, int], tuple[int, int]] | None = None
        self.swap_legal = False
        self.idle_time = 0.0
        self.hint: tuple[tuple[int, int], tuple[int, int]] | None = None
        self.banner = ""
        self.banner_time = 0.0
        self.next_tick = int(config.BLITZ_SECONDS)
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
        pygame.quit()

    def handle_events(self) -> None:
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                self.running = False
            elif event.type == pygame.KEYDOWN:
                self._on_key(event)
            elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
                self._on_click(event.pos)
            elif event.type == pygame.MOUSEBUTTONUP and event.button == 1:
                self._on_release(event.pos)

    def _on_key(self, event) -> None:
        if event.key == pygame.K_m:
            muted = self.audio.toggle_mute()
            self._say("Sound off" if muted else "Sound on")
            return
        if event.key == pygame.K_ESCAPE:
            if self.state is Screen.PLAYING:
                self.state = Screen.MENU
            else:
                self.running = False
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

    # ------------------------------------------------------------------ input
    def _cell_at(self, pos) -> tuple[int, int] | None:
        x, y = pos
        c = (x - config.BOARD_X) // config.TILE
        r = (y - config.BOARD_Y) // config.TILE
        if 0 <= r < self.board.rows and 0 <= c < self.board.cols:
            return (int(r), int(c))
        return None

    def _on_click(self, pos) -> None:
        if self.state is Screen.MENU:
            if self.buttons.get("blitz", pygame.Rect(0, 0, 0, 0)).collidepoint(pos):
                self.start("blitz")
            elif self.buttons.get("relaxed", pygame.Rect(0, 0, 0, 0)).collidepoint(pos):
                self.start("relaxed")
            return
        if self.state is Screen.GAME_OVER:
            if self.buttons.get("again", pygame.Rect(0, 0, 0, 0)).collidepoint(pos):
                self.start(self.mode)
            elif self.buttons.get("menu", pygame.Rect(0, 0, 0, 0)).collidepoint(pos):
                self.state = Screen.MENU
            return
        if self.paused or self.time_over or self.phase is not Phase.IDLE:
            return
        cell = self._cell_at(pos)
        if cell is None:
            self.selected = None
            return
        if self.selected is None:
            self.selected = cell
            self.audio.play("select")
        elif self.selected == cell:
            self.selected = None
        elif Board.adjacent(self.selected, cell):
            self._attempt_swap(self.selected, cell)
        else:
            self.selected = cell
            self.audio.play("select")

    def _on_release(self, pos) -> None:
        """Support dragging: press a tile and release on its neighbour."""
        if self.state is not Screen.PLAYING or self.paused or self.time_over:
            return
        if self.phase is not Phase.IDLE or self.selected is None:
            return
        cell = self._cell_at(pos)
        if cell is not None and cell != self.selected \
                and Board.adjacent(self.selected, cell):
            self._attempt_swap(self.selected, cell)

    def _attempt_swap(self, a, b) -> None:
        self.swap_legal = self.board.swap_is_legal(a, b)
        self.board.swap(a, b)
        self.pending = (a, b)
        self.selected = None
        self.hint = None
        self.idle_time = 0.0
        ax, ay = self._cell_pos(a)
        bx, by = self._cell_pos(b)
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
            ax, ay = self._cell_pos(a)
            bx, by = self._cell_pos(b)
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
        self.offsets = {cell: (0, -config.BOARD_H) for cell in self.board.cells()}
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
            x, y = self._cell_center(cell)
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
            pad = config.ANIMALS[tile.kind][1]
            x, y = self._cell_center(cell)
            self.effects.burst(x, y, pad, 7)
            sprite = self.sprites[(tile.kind, tile.power)]
            self.dying.append((sprite.get_rect(center=(x, y)), sprite))

        focus = result.focus
        if focus is not None:
            fx, fy = self._cell_center(focus)
            self.effects.popup(fx, fy - 6, f"+{gained:,}", config.CREAM,
                               26 + min(10, multiplier))
            if multiplier > 1:
                self.effects.popup(fx, fy - 34, f"x{multiplier} CHAIN",
                                   config.GOLD, 22)
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
            self.offsets[(to_row, col)] = (0, (from_row - to_row) * config.TILE)
        for col, row, height in spawns:
            self.offsets[(row, col)] = (0, -height * config.TILE)
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
            x, y = self._cell_center(cell)
            self.effects.ring(x, y, config.GOLD, 24, 480)
            self.audio.play(name)
        for cell, tile in result.cleared.items():
            x, y = self._cell_center(cell)
            self.effects.burst(x, y, config.ANIMALS[tile.kind][1], 6)
        cells = sorted(result.cleared)
        if cells:
            fx, fy = self._cell_center(cells[len(cells) // 2])
            self.effects.popup(fx, fy, f"+{bonus:,}", config.GOLD, 30)
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

    # ------------------------------------------------------------------ layout
    def _cell_pos(self, cell) -> tuple[int, int]:
        r, c = cell
        return (config.BOARD_X + c * config.TILE,
                config.BOARD_Y + r * config.TILE)

    def _cell_center(self, cell) -> tuple[int, int]:
        x, y = self._cell_pos(cell)
        return (x + config.TILE // 2, y + config.TILE // 2)

    def _tile_offset(self, cell) -> tuple[float, float]:
        base = self.offsets.get(cell)
        if base is None:
            return (0.0, 0.0)
        remaining = 1.0 - ease_out(min(1.0, self.phase_t / self.phase_len))
        return (base[0] * remaining, base[1] * remaining)

    # -------------------------------------------------------------------- draw
    def draw(self) -> None:
        self.canvas.blit(self.background, (0, 0))
        if self.state is Screen.MENU:
            self._draw_menu()
        else:
            self._draw_board()
            self._draw_hud()
            self.effects.draw(self.canvas, self.font)
            self._draw_banner()
            if self.paused:
                self._draw_overlay("Paused", "Press P to keep playing")
            if self.state is Screen.GAME_OVER:
                self._draw_game_over()
        dx, dy = self.effects.offset()
        self.screen.fill(config.WOOD_DARK)
        self.screen.blit(self.canvas, (dx, dy))
        pygame.display.flip()

    # ----------------------------------------------------------------- widgets
    def _plank(self, rect, color=config.WOOD, radius=14) -> None:
        pygame.draw.rect(self.canvas, art.shade(color, -0.4),
                         rect.move(0, 4), border_radius=radius)
        pygame.draw.rect(self.canvas, color, rect, border_radius=radius)
        pygame.draw.rect(self.canvas, art.shade(color, 0.22), rect,
                         width=2, border_radius=radius)

    def _text(self, text, size, color, center=None, topleft=None, bold=False,
              shadow=True):
        font = self.font(size, bold)
        label = font.render(text, True, color)
        rect = label.get_rect()
        if center:
            rect.center = center
        elif topleft:
            rect.topleft = topleft
        if shadow:
            dark = font.render(text, True, (44, 34, 28))
            dark.set_alpha(110)
            self.canvas.blit(dark, rect.move(2, 2))
        self.canvas.blit(label, rect)
        return rect

    def _button(self, key, rect, label, color=config.BARN_RED) -> None:
        self.buttons[key] = rect
        hovered = rect.collidepoint(pygame.mouse.get_pos())
        shade = art.shade(color, 0.18) if hovered else color
        self._plank(rect, shade, radius=16)
        self._text(label, 27, config.CREAM, center=rect.center, bold=True)

    # -------------------------------------------------------------------- menu
    def _draw_menu(self) -> None:
        self.buttons.clear()
        cx = config.WIDTH // 2
        header = pygame.Rect(cx - 300, 44, 600, 108)
        self._plank(header, config.BARN_RED, radius=20)
        self._text("BARNYARD BLITZ", 52, config.CREAM,
                   center=(cx, header.centery - 14), bold=True)
        self._text("a farm-fresh match-3 romp", 20, config.GOLD,
                   center=(cx, header.centery + 28))

        self.canvas.blit(self.barn, self.barn.get_rect(center=(cx, 232)))

        preview_y = 322
        for i in range(len(config.ANIMALS)):
            sprite = self.sprites[(i, Power.NONE)]
            x = cx - (len(config.ANIMALS) * 62) // 2 + i * 62 + 31
            bob = math.sin(self.elapsed * 3 + i * 0.7) * 5
            self.canvas.blit(sprite, sprite.get_rect(center=(x, preview_y + bob)))

        self._button("blitz", pygame.Rect(cx - 250, 388, 240, 62),
                     "Blitz  60s")
        self._button("relaxed", pygame.Rect(cx + 10, 388, 240, 62),
                     "Relaxed", config.WOOD)

        for i, mode in enumerate(("blitz", "relaxed")):
            best = self.scores.best(mode)
            self._text(f"best {best:,}", 19, config.INK,
                       center=(cx - 130 + i * 260, 470))

        lines = [
            "Swap two neighbours to line up three or more of the same critter.",
            "Match 4 for a Golden Egg, an L or T for a Hay Bale,",
            "and 5 in a row for the Prize Rooster that clears a whole species.",
            "Click two tiles or drag one onto its neighbour.",
        ]
        for i, line in enumerate(lines):
            self._text(line, 18, config.INK_SOFT, center=(cx, 512 + i * 26),
                       shadow=False)
        self._text("P pause   R restart   M mute   Esc quit", 17,
                   config.INK_SOFT, center=(cx, config.HEIGHT - 26),
                   shadow=False)

    # ------------------------------------------------------------------- board
    def _draw_board(self) -> None:
        frame = pygame.Rect(config.BOARD_X - 10, config.BOARD_Y - 10,
                            config.BOARD_W + 20, config.BOARD_H + 20)
        self._plank(frame, config.WOOD, radius=18)
        for r in range(self.board.rows):
            for c in range(self.board.cols):
                x, y = self._cell_pos((r, c))
                color = config.CELL_LIGHT if (r + c) % 2 == 0 else config.CELL_DARK
                pygame.draw.rect(self.canvas, color,
                                 pygame.Rect(x, y, config.TILE, config.TILE))

        board_clip = pygame.Rect(config.BOARD_X, config.BOARD_Y,
                                 config.BOARD_W, config.BOARD_H)
        self.canvas.set_clip(board_clip)

        if self.hint and self.phase is Phase.IDLE:
            pulse = (math.sin(self.elapsed * 7) + 1) / 2
            for cell in self.hint:
                x, y = self._cell_pos(cell)
                glow = pygame.Surface((config.TILE, config.TILE),
                                      pygame.SRCALPHA)
                pygame.draw.rect(glow, (255, 246, 190, int(70 + 90 * pulse)),
                                 glow.get_rect(), border_radius=12)
                self.canvas.blit(glow, (x, y))

        for cell in self.board.cells():
            tile = self.board.at(cell)
            if tile is None:
                continue
            ox, oy = self._tile_offset(cell)
            cx, cy = self._cell_center(cell)
            sprite = self.sprites[(tile.kind, tile.power)]
            if tile.power is Power.ROOSTER:
                pulse = 1.0 + 0.05 * math.sin(self.elapsed * 6)
                size = int(sprite.get_width() * pulse)
                sprite = pygame.transform.smoothscale(sprite, (size, size))
            self.canvas.blit(sprite,
                             sprite.get_rect(center=(cx + ox, cy + oy)))

        if self.selected is not None:
            x, y = self._cell_pos(self.selected)
            pulse = (math.sin(self.elapsed * 9) + 1) / 2
            rect = pygame.Rect(x + 2, y + 2, config.TILE - 4, config.TILE - 4)
            pygame.draw.rect(self.canvas, (255, 255, 255), rect,
                             width=3 + int(pulse * 2), border_radius=14)

        progress = min(1.0, self.phase_t / self.phase_len) \
            if self.phase is Phase.CLEAR else 0.0
        for rect, sprite in self.dying:
            scale = max(0.05, 1.0 - ease_in(progress))
            size = max(2, int(sprite.get_width() * scale))
            shrunk = pygame.transform.smoothscale(sprite, (size, size))
            shrunk.set_alpha(int(255 * (1.0 - progress)))
            self.canvas.blit(shrunk, shrunk.get_rect(center=rect.center))

        self.canvas.set_clip(None)

    # --------------------------------------------------------------------- HUD
    def _draw_hud(self) -> None:
        self.buttons.clear()
        left = config.BOARD_X - 10
        header = pygame.Rect(left, 20, config.PANEL_X + config.PANEL_W - left,
                             74)
        self._plank(header, config.BARN_RED, radius=16)
        self._text("BARNYARD BLITZ", 34, config.CREAM,
                   topleft=(header.x + 22, header.y + 20), bold=True)
        self._text(f"{self.score:,}", 40, config.GOLD,
                   center=(header.right - 110, header.centery), bold=True)
        self._text("SCORE", 15, config.CREAM,
                   center=(header.right - 110, header.bottom - 12))

        panel = pygame.Rect(config.PANEL_X, config.BOARD_Y - 10,
                            config.PANEL_W, config.BOARD_H + 20)
        self._plank(panel, config.WOOD_LIGHT, radius=18)

        y = panel.y + 16
        card = pygame.Rect(panel.x + 14, y, panel.w - 28, 96)
        self._plank(card, config.CREAM, radius=14)
        if self.mode == "blitz":
            left = max(0.0, self.time_left)
            urgent = left <= 10
            color = (196, 62, 48) if urgent else config.INK
            wobble = int(math.sin(self.elapsed * 16) * 3) if urgent else 0
            self._text("TIME", 15, config.INK_SOFT,
                       center=(card.centerx, card.y + 18), shadow=False)
            self._text(f"{left:0.1f}", 44, color,
                       center=(card.centerx, card.y + 48 + wobble), bold=True)
            bar = pygame.Rect(card.x + 16, card.bottom - 20, card.w - 32, 10)
            pygame.draw.rect(self.canvas, (206, 196, 176), bar,
                             border_radius=5)
            frac = left / config.BLITZ_SECONDS
            fill = pygame.Rect(bar.x, bar.y, int(bar.w * frac), bar.h)
            tone = (196, 62, 48) if frac < 0.2 else \
                (232, 168, 52) if frac < 0.5 else (108, 176, 96)
            pygame.draw.rect(self.canvas, tone, fill, border_radius=5)
        else:
            self._text("TIME PLAYED", 15, config.INK_SOFT,
                       center=(card.centerx, card.y + 18), shadow=False)
            mins, secs = divmod(int(self.elapsed), 60)
            self._text(f"{mins}:{secs:02d}", 42, config.INK,
                       center=(card.centerx, card.y + 54), bold=True)

        y = card.bottom + 14
        card = pygame.Rect(panel.x + 14, y, panel.w - 28, 84)
        self._plank(card, config.CREAM, radius=14)
        self._text("CHAIN", 15, config.INK_SOFT,
                   center=(card.centerx, card.y + 18), shadow=False)
        chain = max(1, self.cascade)
        self._text(f"x{min(chain, config.MAX_CASCADE_MULT)}", 34,
                   config.BARN_RED if chain > 1 else config.INK_SOFT,
                   center=(card.centerx, card.y + 50), bold=True)
        pips = pygame.Rect(card.x + 16, card.bottom - 16, card.w - 32, 6)
        pygame.draw.rect(self.canvas, (206, 196, 176), pips, border_radius=3)
        frac = min(1.0, self.cascade / config.MAX_CASCADE_MULT)
        pygame.draw.rect(self.canvas, config.GOLD,
                         pygame.Rect(pips.x, pips.y, int(pips.w * frac),
                                     pips.h), border_radius=3)

        y = card.bottom + 14
        card = pygame.Rect(panel.x + 14, y, panel.w - 28, 76)
        self._plank(card, config.CREAM, radius=14)
        self._text("BEST", 15, config.INK_SOFT,
                   center=(card.centerx, card.y + 18), shadow=False)
        self._text(f"{max(self.scores.best(self.mode), self.score):,}", 30,
                   config.INK, center=(card.centerx, card.y + 50), bold=True)

        y = card.bottom + 18
        self._text(MODES[self.mode][0].upper() + " MODE", 18, config.CREAM,
                   center=(panel.centerx, y))
        legend = [
            ("4 in a row", "Golden Egg"),
            ("L or T shape", "Hay Bale"),
            ("5 in a row", "Prize Rooster"),
        ]
        y += 26
        for shape, name in legend:
            self._text(shape, 16, config.CREAM,
                       topleft=(panel.x + 20, y), shadow=False)
            self._text(name, 16, config.GOLD,
                       topleft=(panel.x + 132, y), shadow=False)
            y += 22

        y += 10
        for line in (f"Moves  {self.moves_made}",
                     f"Best chain  x{self.best_cascade}",
                     "P pause   R restart",
                     "M " + ("unmute" if self.audio.muted else "mute")
                     + "   Esc menu"):
            self._text(line, 16, config.CREAM, topleft=(panel.x + 20, y),
                       shadow=False)
            y += 22

    def _draw_banner(self) -> None:
        if self.banner_time <= 0 or not self.banner:
            return
        alpha = min(1.0, self.banner_time / 0.4)
        font = self.font(30, True)
        label = font.render(self.banner, True, config.CREAM)
        rect = label.get_rect(center=(config.BOARD_X + config.BOARD_W // 2,
                                      config.BOARD_Y + 30))
        pad = rect.inflate(34, 18)
        plate = pygame.Surface(pad.size, pygame.SRCALPHA)
        pygame.draw.rect(plate, (*config.BARN_RED, int(215 * alpha)),
                         plate.get_rect(), border_radius=14)
        self.canvas.blit(plate, pad.topleft)
        label.set_alpha(int(255 * alpha))
        self.canvas.blit(label, rect)

    def _draw_overlay(self, title: str, subtitle: str) -> None:
        veil = pygame.Surface((config.WIDTH, config.HEIGHT), pygame.SRCALPHA)
        veil.fill((30, 24, 20, 170))
        self.canvas.blit(veil, (0, 0))
        cx = config.WIDTH // 2
        self._text(title, 52, config.CREAM, center=(cx, config.HEIGHT // 2 - 30),
                   bold=True)
        self._text(subtitle, 22, config.GOLD,
                   center=(cx, config.HEIGHT // 2 + 20))

    def _draw_game_over(self) -> None:
        veil = pygame.Surface((config.WIDTH, config.HEIGHT), pygame.SRCALPHA)
        veil.fill((30, 24, 20, 185))
        self.canvas.blit(veil, (0, 0))
        cx = config.WIDTH // 2
        card = pygame.Rect(cx - 250, config.HEIGHT // 2 - 190, 500, 380)
        self._plank(card, config.WOOD, radius=22)
        inner = card.inflate(-24, -24)
        self._plank(inner, config.CREAM, radius=18)

        self._text("That's all, folks!", 38, config.BARN_RED,
                   center=(cx, inner.y + 46), bold=True)
        self._text(f"{self.score:,}", 66, config.INK,
                   center=(cx, inner.y + 118), bold=True)
        self._text("final score", 18, config.INK_SOFT,
                   center=(cx, inner.y + 158), shadow=False)
        if self.new_record:
            pulse = (math.sin(self.elapsed * 6) + 1) / 2
            self._text("NEW BARN RECORD!", 24,
                       art.shade(config.GOLD, pulse * 0.3),
                       center=(cx, inner.y + 192), bold=True)
        else:
            self._text(f"best  {self.scores.best(self.mode):,}", 20,
                       config.INK_SOFT, center=(cx, inner.y + 192),
                       shadow=False)
        self._text(f"{self.moves_made} moves    best chain x{self.best_cascade}",
                   19, config.INK_SOFT, center=(cx, inner.y + 224),
                   shadow=False)

        self._button("again", pygame.Rect(cx - 210, card.bottom - 104, 190, 56),
                     "Play again")
        self._button("menu", pygame.Rect(cx + 20, card.bottom - 104, 190, 56),
                     "Menu", config.WOOD_DARK)
