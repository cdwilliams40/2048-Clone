"""Entry point for Barnyard Blitz.

    python main.py
"""

import sys

import pygame


def main() -> int:
    # Ask for the mono 16-bit mixer the synthesised sound effects are built for.
    try:
        pygame.mixer.pre_init(frequency=22050, size=-16, channels=1, buffer=512)
    except pygame.error:
        pass
    pygame.init()
    pygame.font.init()

    from barnyard.game import Game

    Game().run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
