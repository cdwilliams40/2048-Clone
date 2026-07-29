"""Platform probes and per-platform paths.

Android builds run under python-for-android, which exports a handful of
environment variables we can key off. Everything here degrades to sensible
desktop behaviour when those are absent.
"""

from __future__ import annotations

import os
from pathlib import Path


def is_android() -> bool:
    return "ANDROID_ARGUMENT" in os.environ or "ANDROID_APP_PATH" in os.environ


def data_dir() -> Path:
    """Where the game may write its save data."""
    if is_android():
        # p4a points ANDROID_PRIVATE at the app's private storage, which is the
        # only location writable without asking for a runtime permission.
        base = (os.environ.get("ANDROID_PRIVATE")
                or os.environ.get("ANDROID_APP_PATH") or ".")
        return Path(base)
    root = os.environ.get("XDG_DATA_HOME")
    base = Path(root) if root else Path.home() / ".local" / "share"
    return base / "barnyard-blitz"


def touch_first() -> bool:
    """True when the primary input is a finger rather than a mouse."""
    return is_android()
