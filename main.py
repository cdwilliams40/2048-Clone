"""Entry point for Barnyard Blitz.

    python main.py

The same file is the Android entry point: python-for-android runs main.py, so
the display is opened full screen at the device's native resolution there and
as a resizable window everywhere else.
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

    from barnyard.app import App, open_display

    App(open_display()).run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
