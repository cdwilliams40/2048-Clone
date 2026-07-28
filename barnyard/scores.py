"""High score persistence, stored in the user's home directory."""

from __future__ import annotations

import json
import os
from pathlib import Path


def _store_path() -> Path:
    root = os.environ.get("XDG_DATA_HOME")
    base = Path(root) if root else Path.home() / ".local" / "share"
    return base / "barnyard-blitz" / "highscores.json"


class HighScores:
    def __init__(self, path: Path | None = None):
        self.path = path or _store_path()
        self.data: dict[str, int] = {}
        self.load()

    def load(self) -> None:
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
            self.data = {str(k): int(v) for k, v in raw.items()}
        except (OSError, ValueError, TypeError):
            self.data = {}

    def best(self, mode: str) -> int:
        return self.data.get(mode, 0)

    def submit(self, mode: str, score: int) -> bool:
        """Record ``score``; returns True when it beats the previous best."""
        if score <= self.best(mode):
            return False
        self.data[mode] = score
        self.save()
        return True

    def save(self) -> None:
        try:
            self.path.parent.mkdir(parents=True, exist_ok=True)
            self.path.write_text(json.dumps(self.data, indent=2),
                                 encoding="utf-8")
        except OSError:
            pass  # A read-only home is not worth crashing a game over.
