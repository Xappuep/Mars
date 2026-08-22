# -*- coding: utf-8 -*-
"""Generate transparent WebP cutouts from Mars PNG sources (rembg)."""
from __future__ import annotations

import io
import sys
from pathlib import Path

from PIL import Image
from rembg import new_session, remove

BASE = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "assets" / "mars"
NAMES = [
    "mars_default",
    "mars_done",
    "mars_working",
    "mars_postponed",
    "mars_overdue",
    "mars_supportive",
    "mars_strict",
]


def alpha_stats(img: Image.Image) -> dict:
    a = img.split()[3]
    data = a.getdata()
    total = len(data)
    transparent = sum(1 for p in data if p < 16)
    opaque = sum(1 for p in data if p > 240)
    return {
        "transparent_pct": round(100 * transparent / total, 1),
        "opaque_pct": round(100 * opaque / total, 1),
    }


def main() -> int:
    session = new_session("isnet-general-use")
    ok = True
    for name in NAMES:
        src = BASE / f"{name}.png"
        dst = BASE / f"{name}.webp"
        if not src.exists():
            print(f"MISSING {src}", file=sys.stderr)
            ok = False
            continue
        print(f"processing {name}...", flush=True)
        with src.open("rb") as f:
            cutout = remove(f.read(), session=session)
        img = Image.open(io.BytesIO(cutout)).convert("RGBA")
        stats = alpha_stats(img)
        print(f"  {stats}", flush=True)
        if stats["transparent_pct"] < 25:
            print(f"  WARN: low transparency for {name}", file=sys.stderr)
            ok = False
        img.save(dst, "WEBP", lossless=True, method=6)
        print(f"  -> {dst.name} ({dst.stat().st_size // 1024} KB)", flush=True)
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
