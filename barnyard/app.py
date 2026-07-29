"""The application shell: display, shared services and the scene switch.

Scenes (farm, blitz, story) borrow the app's canvas, layout, audio, particle
system and save session rather than owning any of it, so switching screens is
cheap and the save stays authoritative.
"""

from __future__ import annotations

import random

import pygame

from . import art, config, mergeart, platform
from .audio import Audio
from .effects import Effects
from .layout import FarmLayout, Layout
from .merge import save as mergesave
from .merge.board import COLS, ROWS
from .ui import UI

APP_PAUSING = getattr(pygame, "APP_WILLENTERBACKGROUND", None)
APP_RESUMING = getattr(pygame, "APP_DIDENTERFOREGROUND", None)
BACK_KEY = getattr(pygame, "K_AC_BACK", None)

AUTOSAVE_SECONDS = 20.0
PORTRAIT_KINDS = range(-1, len(config.ANIMALS))


class Scene:
    """Base class. ``layout_kind`` picks which layout ``app.layout`` returns."""

    layout_kind = "farm"

    def __init__(self, app: "App"):
        self.app = app
        self.ui = app.ui

    @property
    def layout(self):
        return self.app.layout

    def on_enter(self, **kwargs) -> None:
        pass

    def on_layout(self) -> None:
        pass

    def handle(self, event) -> None:
        pass

    def update(self, dt: float) -> None:
        pass

    def draw(self) -> None:
        pass


class Toast:
    __slots__ = ("text", "color", "life", "max_life")

    def __init__(self, text, color, life=2.4):
        self.text = text
        self.color = color
        self.life = life
        self.max_life = life


TOAST_COLORS = {
    "warn": (232, 148, 62),
    "coins": config.GOLD,
    "order": (126, 200, 126),
    "level": (255, 226, 120),
    "unlock": (255, 226, 120),
    "task": (146, 208, 240),
    "blitz": config.GOLD,
    "chapter": config.CREAM,
}


class App:
    def __init__(self, surface: pygame.Surface):
        pygame.display.set_caption("Barnyard Blitz")
        self.screen = surface
        self.rng = random.Random()
        self.audio = Audio()
        self.effects = Effects(self.rng)
        self.session = mergesave.load(self.rng)
        self.ui = UI(self)

        self.canvas = surface.copy()
        self.L: Layout | None = None
        self.FL: FarmLayout | None = None
        self.toasts: list[Toast] = []
        self.running = True
        self._save_timer = 0.0

        from .scenes.blitz import BlitzScene
        from .scenes.farm import FarmScene
        from .scenes.story import StoryScene

        self.scenes: dict[str, Scene] = {
            "farm": FarmScene(self),
            "blitz": BlitzScene(self),
            "story": StoryScene(self),
        }
        self.scene: Scene = self.scenes["farm"]
        self.relayout(surface.get_size())
        self.scene.on_enter()

    # ----------------------------------------------------------------- layout
    @property
    def layout(self):
        return self.FL if self.scene.layout_kind == "farm" else self.L

    def relayout(self, size) -> None:
        width, height = max(320, size[0]), max(400, size[1])
        self.canvas = pygame.Surface((width, height))
        self.L = Layout(width, height, config.ROWS, config.COLS)
        self.FL = FarmLayout(width, height, ROWS, COLS)
        self.background = art.build_background(width, height, self.FL.margin)
        self.tiles = art.build_tile_sprites(max(12, self.L.tile - 6))
        self.items = mergeart.build_item_sprites(
            max(14, round(self.FL.cell * 0.94)))
        self.barn = art.build_barn(round(self.L.tile * 2.9),
                                   round(self.L.tile * 2.3))
        self.portrait_small = {
            k: art.build_portrait(k, max(20, round(self.FL.cell * 0.72)))
            for k in PORTRAIT_KINDS
        }
        self.portrait_big = {
            k: art.build_portrait(k, max(48, round(min(width, height) * 0.16)))
            for k in PORTRAIT_KINDS
        }
        self.ui.forget_fonts()
        for scene in self.scenes.values():
            scene.on_layout()

    # ------------------------------------------------------------------ scenes
    def go(self, name: str, **kwargs) -> None:
        self.save()
        self.scene = self.scenes[name]
        self.scene.on_enter(**kwargs)

    # ------------------------------------------------------------------ toasts
    def toast(self, text: str, color=config.CREAM) -> None:
        self.toasts.append(Toast(text, color))
        del self.toasts[:-4]

    def pump_events(self) -> None:
        """Turn queued session events into toasts."""
        for event in self.session.drain():
            if event.kind == "merge":
                continue  # the board animates merges itself
            self.toast(event.text, TOAST_COLORS.get(event.kind, config.CREAM))

    def draw_toasts(self) -> None:
        """Snackbars stacked up from the button bar, clear of the playfield."""
        L = self.layout
        bar = getattr(L, "bar", None)
        y = (bar.top if bar is not None else L.h - L.margin) - L.gap
        for toast in reversed(self.toasts):
            alpha = min(1.0, toast.life / 0.5)
            font = self.ui.font(L.fs(17), True)
            label = font.render(toast.text, True, toast.color)
            plate = label.get_rect().inflate(L.fs(26), L.fs(12))
            plate.midbottom = (L.w // 2, y)
            surf = pygame.Surface(plate.size, pygame.SRCALPHA)
            pygame.draw.rect(surf, (46, 36, 30, int(220 * alpha)),
                             surf.get_rect(), border_radius=plate.h // 2)
            self.canvas.blit(surf, plate.topleft)
            label.set_alpha(int(255 * alpha))
            self.canvas.blit(label, label.get_rect(center=plate.center))
            y -= plate.h + L.fs(6)

    # -------------------------------------------------------------------- save
    def save(self) -> None:
        mergesave.save(self.session)
        self._save_timer = 0.0

    # -------------------------------------------------------------------- loop
    def run(self) -> None:
        clock = pygame.time.Clock()
        while self.running:
            dt = min(clock.tick(config.FPS) / 1000.0, 0.05)
            self.handle_events()
            self.update(dt)
            self.draw()
        self.save()
        pygame.quit()

    def handle_events(self) -> None:
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                self.running = False
            elif event.type == pygame.VIDEORESIZE:
                if (event.w, event.h) != self.canvas.get_size():
                    self.relayout((event.w, event.h))
            elif APP_PAUSING is not None and event.type == APP_PAUSING:
                self.save()
                self.scene.handle(event)
            elif event.type == pygame.KEYDOWN and event.key == pygame.K_m:
                muted = self.audio.toggle_mute()
                self.toast("Sound off" if muted else "Sound on")
            else:
                self.scene.handle(event)

    def update(self, dt: float) -> None:
        self.session.tick(dt)
        self.effects.update(dt)
        self.scene.update(dt)
        self.pump_events()
        for toast in self.toasts:
            toast.life -= dt
        self.toasts = [t for t in self.toasts if t.life > 0]
        self._save_timer += dt
        if self._save_timer >= AUTOSAVE_SECONDS:
            self.save()

    def draw(self) -> None:
        self.canvas.blit(self.background, (0, 0))
        self.ui.reset_frame()
        self.scene.draw()
        self.draw_toasts()
        dx, dy = self.effects.offset()
        self.screen.fill(config.WOOD_DARK)
        self.screen.blit(self.canvas, (dx, dy))
        pygame.display.flip()


def open_display() -> pygame.Surface:
    if platform.is_android():
        return pygame.display.set_mode((0, 0), pygame.FULLSCREEN)
    return pygame.display.set_mode((config.WIDTH, config.HEIGHT),
                                   pygame.RESIZABLE)
