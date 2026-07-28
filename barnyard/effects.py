"""Particles, floating score popups and screen shake."""

from __future__ import annotations

import math
import random
from dataclasses import dataclass

import pygame

from . import config


@dataclass
class Particle:
    x: float
    y: float
    vx: float
    vy: float
    life: float
    max_life: float
    size: float
    color: tuple
    spin: float = 0.0
    angle: float = 0.0


@dataclass
class Popup:
    x: float
    y: float
    text: str
    color: tuple
    life: float = 0.9
    max_life: float = 0.9
    size: int = 26


class Effects:
    def __init__(self, rng: random.Random | None = None):
        self.rng = rng or random.Random()
        self.particles: list[Particle] = []
        self.popups: list[Popup] = []
        self.shake = 0.0

    # ------------------------------------------------------------------ spawn
    def burst(self, x: float, y: float, color, count: int = 12,
              speed: float = 260.0) -> None:
        for _ in range(count):
            angle = self.rng.uniform(0, math.tau)
            mag = self.rng.uniform(0.35, 1.0) * speed
            self.particles.append(Particle(
                x, y,
                math.cos(angle) * mag, math.sin(angle) * mag - 60,
                life=self.rng.uniform(0.35, 0.7),
                max_life=0.7,
                size=self.rng.uniform(3, 7),
                color=color,
                spin=self.rng.uniform(-8, 8),
            ))

    def ring(self, x: float, y: float, color, count: int = 22,
             speed: float = 420.0) -> None:
        for i in range(count):
            angle = math.tau * i / count
            self.particles.append(Particle(
                x, y,
                math.cos(angle) * speed, math.sin(angle) * speed,
                life=0.45, max_life=0.45,
                size=self.rng.uniform(4, 8),
                color=color,
                spin=self.rng.uniform(-6, 6),
            ))

    def feathers(self, x: float, y: float, count: int = 16) -> None:
        palette = [(255, 250, 235), (250, 226, 150), (240, 158, 42)]
        for _ in range(count):
            self.particles.append(Particle(
                x, y,
                self.rng.uniform(-160, 160), self.rng.uniform(-260, -60),
                life=self.rng.uniform(0.6, 1.1), max_life=1.1,
                size=self.rng.uniform(4, 8),
                color=self.rng.choice(palette),
                spin=self.rng.uniform(-10, 10),
            ))

    def popup(self, x: float, y: float, text: str, color=config.CREAM,
              size: int = 26) -> None:
        self.popups.append(Popup(x, y, text, color, size=size))

    def kick(self, amount: float) -> None:
        self.shake = min(14.0, self.shake + amount)

    # ----------------------------------------------------------------- update
    def update(self, dt: float) -> None:
        alive: list[Particle] = []
        for p in self.particles:
            p.life -= dt
            if p.life <= 0:
                continue
            p.vy += 900 * dt
            p.vx *= 0.98
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            alive.append(p)
        self.particles = alive

        live_popups: list[Popup] = []
        for pop in self.popups:
            pop.life -= dt
            if pop.life > 0:
                pop.y -= 46 * dt
                live_popups.append(pop)
        self.popups = live_popups

        self.shake = max(0.0, self.shake - 44 * dt)

    def offset(self) -> tuple[int, int]:
        if self.shake <= 0.1:
            return (0, 0)
        return (self.rng.randint(-1, 1) * int(self.shake),
                self.rng.randint(-1, 1) * int(self.shake))

    # ------------------------------------------------------------------- draw
    def draw(self, surface: pygame.Surface, font_for) -> None:
        for p in self.particles:
            alpha = max(0.0, min(1.0, p.life / p.max_life))
            size = max(2, int(p.size * (0.4 + 0.6 * alpha)))
            chip = pygame.Surface((size * 2, size * 2), pygame.SRCALPHA)
            pygame.draw.rect(chip, (*p.color, int(255 * alpha)),
                             chip.get_rect(), border_radius=size // 2)
            chip = pygame.transform.rotate(chip, math.degrees(p.angle))
            surface.blit(chip, chip.get_rect(center=(int(p.x), int(p.y))))

        for pop in self.popups:
            t = pop.life / pop.max_life
            font = font_for(pop.size)
            alpha = int(255 * min(1.0, t * 2.2))
            shadow = font.render(pop.text, True, (40, 30, 26))
            label = font.render(pop.text, True, pop.color)
            shadow.set_alpha(int(alpha * 0.5))
            label.set_alpha(alpha)
            rect = label.get_rect(center=(int(pop.x), int(pop.y)))
            surface.blit(shadow, rect.move(2, 2))
            surface.blit(label, rect)
