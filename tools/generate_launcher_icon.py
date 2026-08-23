"""Generate adaptive launcher icon PNGs from Mars planner source image."""
from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/icon/mars_launcher_source.jpg"
RES = ROOT / "app/src/main/res"

# Adaptive icon: 108dp canvas, 66dp safe zone (center).
CANVAS_DP = 108
SAFE_DP = 66
PADDING_DP = (CANVAS_DP - SAFE_DP) // 2  # 21dp each side

DENSITIES = {
    "mdpi": 1,
    "hdpi": 1.5,
    "xhdpi": 2,
    "xxhdpi": 3,
    "xxxhdpi": 4,
}

# Dark graphite matching the source background.
BACKGROUND_COLOR = (0x14, 0x14, 0x17, 0xFF)


def fit_in_square(img: Image.Image, side: int) -> Image.Image:
    w, h = img.size
    scale = min(side / w, side / h)
    nw, nh = max(1, int(w * scale)), max(1, int(h * scale))
    return img.resize((nw, nh), Image.Resampling.LANCZOS)


def make_foreground(source: Image.Image, canvas_px: int) -> Image.Image:
    safe_px = int(canvas_px * SAFE_DP / CANVAS_DP)
    fitted = fit_in_square(source.convert("RGBA"), safe_px)
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    x = (canvas_px - fitted.width) // 2
    y = (canvas_px - fitted.height) // 2
    canvas.paste(fitted, (x, y), fitted)
    return canvas


def make_background(canvas_px: int) -> Image.Image:
    return Image.new("RGBA", (canvas_px, canvas_px), BACKGROUND_COLOR)


def make_legacy_launcher(foreground: Image.Image, background: Image.Image) -> Image.Image:
    """Flat launcher icon for previews / legacy mipmap."""
    base = background.copy()
    base.alpha_composite(foreground)
    return base


def make_round_preview(launcher: Image.Image, size: int = 512) -> Image.Image:
    """Circle-mask preview simulating home-screen icon."""
    launcher = launcher.resize((size, size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (size, size), 0)
    from PIL import ImageDraw

    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(launcher, (0, 0), mask)
    return out


def main() -> None:
    if not SOURCE.exists():
        raise SystemExit(f"Source image missing: {SOURCE}")

    source = Image.open(SOURCE)
    preview_dir = ROOT / "docs/verification/visual"
    preview_dir.mkdir(parents=True, exist_ok=True)

    legacy_xxx = None
    for name, mult in DENSITIES.items():
        canvas_px = int(CANVAS_DP * mult)
        fg = make_foreground(source, canvas_px)
        bg = make_background(canvas_px)

        fg_dir = RES / f"drawable-{name}"
        fg_dir.mkdir(parents=True, exist_ok=True)
        fg.save(fg_dir / "ic_launcher_foreground.png", optimize=True)

        bg_dir = RES / f"drawable-{name}"
        bg.save(bg_dir / "ic_launcher_background.png", optimize=True)

        mip_dir = RES / f"mipmap-{name}"
        mip_dir.mkdir(parents=True, exist_ok=True)
        legacy = make_legacy_launcher(fg, bg)
        legacy.save(mip_dir / "ic_launcher.png", optimize=True)
        legacy.save(mip_dir / "ic_launcher_round.png", optimize=True)
        if name == "xxxhdpi":
            legacy_xxx = legacy

    if legacy_xxx:
        preview = make_round_preview(legacy_xxx, 512)
        preview.save(preview_dir / "launcher_icon_round_preview.png", optimize=True)
        legacy_xxx.resize((512, 512), Image.Resampling.LANCZOS).save(
            preview_dir / "launcher_icon_square_preview.png", optimize=True
        )

    print("Launcher icons generated.")


if __name__ == "__main__":
    main()
