"""Persistence for the farm save."""

from __future__ import annotations

import json
import random
from pathlib import Path

from .. import platform
from .session import Session


def save_path() -> Path:
    return platform.data_dir() / "farm.json"


def load(rng: random.Random | None = None) -> Session:
    """Load the farm, falling back to a fresh one if anything is off."""
    try:
        data = json.loads(save_path().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return Session.new(rng)
    try:
        return Session.from_dict(data, rng)
    except (KeyError, TypeError, ValueError, AttributeError):
        # A corrupt or older save should not brick the game.
        return Session.new(rng)


def save(session: Session) -> bool:
    path = save_path()
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(json.dumps(session.to_dict()), encoding="utf-8")
        tmp.replace(path)
        return True
    except OSError:
        return False


def wipe() -> None:
    try:
        save_path().unlink()
    except OSError:
        pass
