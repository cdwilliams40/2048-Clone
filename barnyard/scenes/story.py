"""Chapters, renovation tasks and the dialogue scenes between them."""

from __future__ import annotations

import pygame

from .. import config
from ..app import Scene
from ..merge.story import CHAPTERS


class StoryScene(Scene):
    layout_kind = "farm"

    def __init__(self, app):
        super().__init__(app)
        self.lines: tuple = ()
        self.line_index = 0
        self.after: str | None = None
        self.elapsed = 0.0

    # ------------------------------------------------------------------ enter
    def on_enter(self, auto_scene: str | None = None, **kwargs) -> None:
        self.elapsed = 0.0
        self.lines = ()
        self.line_index = 0
        self.after = None
        story = self.app.session.story
        chapter = story.current
        if auto_scene == "intro" and chapter is not None:
            story.seen_intro.add(chapter.key)
            self._play(chapter.intro, after="farm")
        elif chapter is not None and chapter.key not in story.seen_intro:
            story.seen_intro.add(chapter.key)
            self._play(chapter.intro)

    def _play(self, lines, after: str | None = None) -> None:
        self.lines = tuple(lines)
        self.line_index = 0
        self.after = after

    @property
    def in_dialogue(self) -> bool:
        return bool(self.lines) and self.line_index < len(self.lines)

    # ------------------------------------------------------------------ input
    def handle(self, event) -> None:
        if event.type == pygame.KEYDOWN:
            back = getattr(pygame, "K_AC_BACK", None)
            if event.key == pygame.K_ESCAPE or (back and event.key == back):
                self._leave()
            elif self.in_dialogue:
                self._advance()
        elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
            self._on_press(event.pos)

    def _on_press(self, pos) -> None:
        if self.in_dialogue:
            self._advance()
            return
        if self.ui.hit("back", pos):
            self._leave()
            return
        story = self.app.session.story
        for i, task in enumerate(story.tasks()):
            if self.ui.hit(f"task{i}", pos):
                self._start_task(task)
                return

    def _leave(self) -> None:
        self.app.go("farm")

    def _advance(self) -> None:
        self.line_index += 1
        if self.in_dialogue:
            return
        self.lines = ()
        self.line_index = 0
        if self.after is not None:
            target, self.after = self.after, None
            self.app.go(target)

    def _start_task(self, task) -> None:
        session = self.app.session
        if session.story.is_done(task):
            return
        if not session.economy.can_afford(task.cost):
            self.app.toast("Not enough coins yet", (232, 148, 62))
            self.app.audio.play("invalid")
            return
        session.complete_task(task)
        self.app.audio.play("hay")
        self.app.effects.kick(6)
        self.app.save()
        if session.story.chapter_complete:
            chapter = session.story.current
            outro = chapter.outro if chapter else ()
            if chapter is not None:
                session.story.seen_outro.add(chapter.key)
            session.advance_chapter()
            nxt = session.story.current
            if nxt is not None:
                session.story.seen_intro.add(nxt.key)
                self._play(tuple(outro) + tuple(nxt.intro))
            else:
                self._play(outro, after="farm")

    # ----------------------------------------------------------------- update
    def update(self, dt: float) -> None:
        self.elapsed += dt

    # ------------------------------------------------------------------- draw
    def draw(self) -> None:
        self._draw_chapter()
        if self.in_dialogue:
            self._draw_dialogue()

    def _draw_chapter(self) -> None:
        L = self.layout
        session = self.app.session
        story = session.story
        m = L.margin

        header = pygame.Rect(m, m, L.w - 2 * m,
                             max(56, min(round(L.h * 0.12), L.fs(86))))
        self.ui.plank(header, config.BARN_RED)
        chapter = story.current
        if chapter is None:
            title, blurb = "Hollow Creek Farm", "Every chapter finished. Nice."
            step = f"{len(CHAPTERS)} of {len(CHAPTERS)}"
        else:
            title, blurb = chapter.title, chapter.blurb
            step = f"Chapter {story.chapter + 1} of {len(CHAPTERS)}"
        self.ui.text(title, L.fs(27), config.CREAM,
                     topleft=(header.x + L.fs(14), header.y + L.fs(10)),
                     bold=True)
        self.ui.text(step, L.fs(15), config.GOLD,
                     topleft=(header.x + L.fs(14), header.y + L.fs(42)))
        self.ui.text(f"{session.economy.coins:,} coins", L.fs(20), config.GOLD,
                     right=(header.right - L.fs(14), header.centery), bold=True)

        y = header.bottom + L.gap * 2
        y += self.ui.wrapped(blurb, L.fs(17), config.INK,
                             pygame.Rect(m + L.fs(6), y,
                                         L.w - 2 * m - L.fs(12), L.fs(60)))
        y += L.gap

        bar_h = max(44, round(L.h * 0.07))
        back = pygame.Rect(m, L.h - m - bar_h, L.w - 2 * m, bar_h)
        tasks = story.tasks()
        available = back.top - L.gap - y
        if tasks:
            # Size the card to its text rather than to the screen, or a tall
            # phone gives every task a near-empty slab.
            card_h = max(70, min(L.fs(86), available // len(tasks) - L.gap))
            block = card_h * len(tasks) + L.gap * (len(tasks) - 1)
            top = y + max(0, (available - block) // 3)
            for i, task in enumerate(tasks):
                rect = pygame.Rect(m, top + i * (card_h + L.gap),
                                   L.w - 2 * m, card_h)
                self._draw_task(i, rect, task)
        else:
            self.ui.text("The farm is finished. Gran would be proud.",
                         L.fs(20), config.INK,
                         center=(L.w // 2, y + available // 2), bold=True)

        self.ui.button("back", back, "Back to the yard", config.WOOD_DARK,
                       size=20)

    def _draw_task(self, index: int, rect: pygame.Rect, task) -> None:
        L = self.layout
        session = self.app.session
        done = session.story.is_done(task)
        affordable = session.economy.can_afford(task.cost)
        face = (214, 238, 210) if done else config.CREAM
        self.ui.plank(rect, face)

        pad = L.fs(12)
        self.ui.text(task.title, L.fs(21), config.INK,
                     topleft=(rect.x + pad, rect.y + pad), bold=True,
                     shadow=False)
        self.ui.wrapped(task.detail, L.fs(15), config.INK_SOFT,
                        pygame.Rect(rect.x + pad, rect.y + pad + L.fs(26),
                                    rect.w * 0.58, rect.h))

        bw = round(rect.w * 0.28)
        bh = min(rect.h - pad * 2, max(36, L.fs(46)))
        button = pygame.Rect(rect.right - bw - pad,
                             rect.centery - bh // 2, bw, bh)
        if done:
            self.ui.text("Done", L.fs(20), (58, 140, 66),
                         center=button.center, bold=True, shadow=False)
        else:
            self.ui.button(f"task{index}", button, f"{task.cost:,}",
                           config.GOLD if affordable else config.WOOD,
                           size=19, enabled=affordable,
                           text_color=config.INK if affordable
                           else config.CREAM)

    def _draw_dialogue(self) -> None:
        L = self.layout
        line = self.lines[self.line_index]
        self.ui.veil(190)

        portrait = self.app.portrait_big[line.portrait]
        # Measure first so the box is only as tall as the speech needs.
        text_w = round((L.w - 2 * L.margin) * 0.86)
        lines = self.ui.wrap_lines(line.text, L.fs(19), text_w)
        box_h = round(L.fs(30) + len(lines) * L.fs(19) * 1.25 + L.fs(46))
        box_h = max(box_h, L.fs(120))
        box = pygame.Rect(L.margin, L.h - L.margin - box_h,
                          L.w - 2 * L.margin, box_h)
        inner = self.ui.panel(box)

        self.ui.canvas.blit(portrait, portrait.get_rect(
            midbottom=(box.x + portrait.get_width() * 0.7, box.y + L.fs(8))))
        self.ui.text(line.speaker, L.fs(22), config.BARN_RED,
                     topleft=(inner.x, inner.y), bold=True)
        self.ui.wrapped(line.text, L.fs(19), config.INK,
                        pygame.Rect(inner.x, inner.y + L.fs(30), inner.w,
                                    inner.h))
        hint = "tap to continue" if self.line_index < len(self.lines) - 1 \
            else "tap to finish"
        alpha = 0.5 + 0.5 * abs((self.elapsed * 1.4) % 2 - 1)
        self.ui.text(hint, L.fs(14),
                     tuple(int(c * alpha) for c in config.INK_SOFT),
                     right=(inner.right, inner.bottom), shadow=False)
