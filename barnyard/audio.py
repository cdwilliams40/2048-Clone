"""Synthesised sound effects.

Waveforms are built with the stdlib ``array`` module and handed straight to
``pygame.mixer.Sound(buffer=...)`` so the game needs no audio files and no
numpy. If the mixer cannot start (headless box, no audio device) every call
quietly becomes a no-op.
"""

from __future__ import annotations

import array
import math

import pygame

RATE = 22050


def _envelope(i: int, n: int, attack: float = 0.02) -> float:
    a = max(1, int(n * attack))
    if i < a:
        return i / a
    return (1.0 - (i - a) / max(1, n - a)) ** 1.6


def _render(notes, volume=0.35, wave="sine") -> bytes:
    """``notes`` is a list of (frequency, start_ms, length_ms)."""
    total_ms = max(start + length for _f, start, length in notes)
    n = int(RATE * total_ms / 1000)
    samples = [0.0] * n
    for freq, start, length in notes:
        first = int(RATE * start / 1000)
        count = int(RATE * length / 1000)
        for i in range(count):
            idx = first + i
            if idx >= n:
                break
            phase = 2 * math.pi * freq * (i / RATE)
            value = math.sin(phase)
            if wave == "square":
                value = 1.0 if value > 0 else -1.0
            elif wave == "triangle":
                value = 2 / math.pi * math.asin(max(-1.0, min(1.0, value)))
            samples[idx] += value * _envelope(i, count)
    buf = array.array("h")
    peak = max(1.0, max(abs(s) for s in samples)) if samples else 1.0
    for s in samples:
        buf.append(int(max(-1.0, min(1.0, s / peak)) * volume * 32767))
    return buf.tobytes()


_SCALE = [523.25, 587.33, 659.25, 783.99, 880.00, 1046.50, 1174.66, 1318.51]


class Audio:
    def __init__(self):
        self.enabled = False
        self.muted = False
        self.sounds: dict[str, pygame.mixer.Sound] = {}
        try:
            # pygame.init() may already have opened the mixer with defaults;
            # our buffers are mono 16-bit at RATE, so re-open if they differ.
            current = pygame.mixer.get_init()
            if current and current[:3] != (RATE, -16, 1):
                pygame.mixer.quit()
                current = None
            if not current:
                pygame.mixer.init(frequency=RATE, size=-16, channels=1,
                                  buffer=512)
        except pygame.error:
            return
        try:
            self._build()
        except (pygame.error, ValueError):
            self.sounds.clear()
            return
        self.enabled = True

    def _build(self) -> None:
        add = self.sounds.__setitem__
        add("swap", pygame.mixer.Sound(
            buffer=_render([(660, 0, 60), (880, 30, 70)], 0.22)))
        add("invalid", pygame.mixer.Sound(
            buffer=_render([(150, 0, 110), (120, 60, 120)], 0.25, "square")))
        add("select", pygame.mixer.Sound(
            buffer=_render([(1046, 0, 45)], 0.16)))
        for level in range(8):
            base = _SCALE[level]
            add(f"match{level}", pygame.mixer.Sound(
                buffer=_render([(base, 0, 130), (base * 1.5, 45, 150)], 0.30)))
        add("egg", pygame.mixer.Sound(buffer=_render(
            [(300, 0, 240), (180, 40, 260), (90, 90, 300)], 0.38, "triangle")))
        add("hay", pygame.mixer.Sound(buffer=_render(
            [(880, 0, 90), (620, 60, 120), (440, 130, 200)], 0.34)))
        add("rooster", pygame.mixer.Sound(buffer=_render(
            [(523, 0, 120), (659, 90, 120), (784, 180, 140), (1046, 270, 260)],
            0.40)))
        add("shuffle", pygame.mixer.Sound(buffer=_render(
            [(392, 0, 110), (523, 90, 110), (659, 180, 160)], 0.30)))
        add("tick", pygame.mixer.Sound(buffer=_render([(1200, 0, 45)], 0.20)))
        add("start", pygame.mixer.Sound(buffer=_render(
            [(523, 0, 130), (659, 120, 130), (784, 240, 130),
             (1046, 360, 280)], 0.38)))
        add("over", pygame.mixer.Sound(buffer=_render(
            [(784, 0, 200), (587, 180, 220), (392, 380, 420)], 0.38,
            "triangle")))

    def play(self, name: str, volume: float = 1.0) -> None:
        if not self.enabled or self.muted:
            return
        sound = self.sounds.get(name)
        if sound is None:
            return
        sound.set_volume(max(0.0, min(1.0, volume)))
        sound.play()

    def match(self, cascade: int) -> None:
        """Matches climb the scale as a cascade builds, like Blitz does."""
        self.play(f"match{min(cascade, 7)}")

    def toggle_mute(self) -> bool:
        self.muted = not self.muted
        return self.muted
