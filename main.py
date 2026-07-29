"""Entry point for Barnyard Blitz.

    python main.py

The same file is the Android entry point: python-for-android runs main.py, so
the display is opened full screen at the device's native resolution there and
as a resizable window everywhere else.
"""

import sys

import pygame

from barnyard import config, platform


def open_display() -> pygame.Surface:
    if platform.is_android():
        # (0, 0) asks SDL for the device's own resolution.
        return pygame.display.set_mode((0, 0), pygame.FULLSCREEN)
    return pygame.display.set_mode((config.WIDTH, config.HEIGHT),
                                   pygame.RESIZABLE)


def main() -> int:
    # Ask for the mono 16-bit mixer the synthesised sound effects are built for.
    try:
        pygame.mixer.pre_init(frequency=22050, size=-16, channels=1, buffer=512)
    except pygame.error:
        pass
    pygame.init()
    pygame.font.init()

    from barnyard.game import Game

    Game(open_display()).run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
