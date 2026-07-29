"""Tuning constants and the colour palette for Barnyard Blitz.

Kept free of pygame imports so the rules modules can be imported headlessly.
"""

# --- board ------------------------------------------------------------------
ROWS = 8
COLS = 8
KINDS = 6

# --- layout -----------------------------------------------------------------
TILE = 66
BOARD_X = 32
BOARD_Y = 112
BOARD_W = COLS * TILE
BOARD_H = ROWS * TILE

PANEL_X = BOARD_X + BOARD_W + 24
PANEL_W = 264

WIDTH = PANEL_X + PANEL_W + 32
HEIGHT = BOARD_Y + BOARD_H + 34

FPS = 60

# --- animation timings (seconds) -------------------------------------------
SWAP_TIME = 0.13
REVERT_TIME = 0.16
CLEAR_TIME = 0.22
FALL_TIME = 0.20
FINALE_STEP = 0.30
HINT_DELAY = 5.0

# --- rules ------------------------------------------------------------------
BLITZ_SECONDS = 60
POINTS_PER_TILE = 40
MAX_CASCADE_MULT = 10
SPECIAL_BONUS = {"egg": 250, "hay": 500, "rooster": 1000}
RUN4_LEN = 4
RUN5_LEN = 5

# --- colours ----------------------------------------------------------------
SKY_TOP = (150, 205, 235)
SKY_BOTTOM = (206, 233, 240)
FIELD = (124, 176, 96)
FIELD_DARK = (104, 154, 80)
BARN_RED = (166, 58, 52)
BARN_RED_DARK = (128, 42, 38)
WOOD = (122, 84, 56)
WOOD_DARK = (92, 62, 41)
WOOD_LIGHT = (150, 108, 74)
CREAM = (247, 240, 224)
INK = (48, 38, 32)
INK_SOFT = (96, 80, 68)
GOLD = (240, 190, 62)
WHITE = (255, 255, 255)

CELL_LIGHT = (168, 205, 138)
CELL_DARK = (152, 191, 124)

# Each animal owns a distinct hue *and* a distinct silhouette, so the board
# stays readable for colour-blind players.
ANIMALS = [
    # name,      pad colour,      body,            accent
    ("Cow", (91, 127, 181), (246, 244, 238), (232, 150, 165)),
    ("Pig", (226, 106, 158), (244, 154, 193), (214, 112, 155)),
    ("Chicken", (238, 173, 52), (255, 246, 214), (206, 62, 52)),
    ("Sheep", (94, 178, 146), (238, 234, 226), (92, 80, 76)),
    ("Duck", (128, 100, 200), (252, 214, 76), (240, 142, 46)),
    ("Horse", (196, 88, 62), (162, 106, 62), (78, 50, 34)),
]

ANIMAL_NAMES = [a[0] for a in ANIMALS]
