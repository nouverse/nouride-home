#!/usr/bin/env python3
"""The app's icons, from the Android app's cat (assets/icon_cat.webp, the dashboard's head with its sparkles cut).

macOS draws an app icon as a rounded square on Apple's grid (an 824-pixel body on a 1024 canvas), so the cat's head
sits on its lavender there, as it sits on a disc on Android; the Dev build's is the same cat on yellow. The menu bar's
icon is the head's silhouette as a template image (black and alpha only), which macOS draws in the menu bar's colour.

Android TV's launcher shows an app by its banner, 320x180 at xhdpi: the cat's head on its lavender at the left and the
app's name in Fredoka, ink on lavender; the Dev build's on yellow, with its own name.

Run from the repository's root: python3 scripts/make-icons.py, then `bunx tauri icon` on each source (README).
"""
from PIL import Image, ImageDraw, ImageFont

CANVAS = 1024
BODY = 824
RADIUS = 185
RELEASE = "#EFEAFB"  # Android's icon_background, the cat's hoodie lavender
DEV = "#FFD84D"  # Android's debug icon_background


def app_icon(color: str, out: str) -> None:
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    plate = Image.new("RGBA", (BODY, BODY), (0, 0, 0, 0))
    ImageDraw.Draw(plate).rounded_rectangle((0, 0, BODY - 1, BODY - 1), RADIUS, fill=color)
    cat = Image.open("assets/icon_cat.webp").convert("RGBA")
    size = int(BODY * 0.84)
    cat = cat.resize((size, size), Image.LANCZOS)
    plate.alpha_composite(cat, ((BODY - size) // 2, (BODY - size) // 2))
    off = (CANVAS - BODY) // 2
    canvas.alpha_composite(plate, (off, off))
    canvas.save(out)


def tray_icon(out: str, px: int) -> None:
    cat = Image.open("assets/icon_cat.webp").convert("RGBA")
    box = cat.getbbox()
    cat = cat.crop(box)
    side = max(cat.size)
    square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    square.alpha_composite(cat, ((side - cat.size[0]) // 2, (side - cat.size[1]) // 2))
    alpha = square.split()[3].resize((px, px), Image.LANCZOS)
    template = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    template.putalpha(alpha)
    template.save(out)


BANNER = (320, 180)
INK = "#221E2E"  # Android's ink
FREDOKA = "apps/android/app/src/main/assets/fonts/fredoka_medium.ttf"


def tv_banner(color: str, name: str, out: str) -> None:
    """Drawn at four times its size and scaled down, so the cat's edge and the words stay smooth."""
    scale = 4
    w, h = BANNER[0] * scale, BANNER[1] * scale
    banner = Image.new("RGBA", (w, h), color)
    cat = Image.open("assets/icon_cat.webp").convert("RGBA")
    side = int(h * 0.78)
    cat = cat.resize((side, side), Image.LANCZOS)
    left = int(h * 0.12)
    banner.alpha_composite(cat, (left, (h - side) // 2))
    draw = ImageDraw.Draw(banner)
    x = left + side + int(h * 0.06)
    room = w - x - int(h * 0.12)
    lines = name.split(" ", 1)
    size = int(h * 0.30)
    while True:
        font = ImageFont.truetype(FREDOKA, size)
        if max(draw.textlength(line, font=font) for line in lines) <= room:
            break
        size -= 2
    gap = int(size * 0.15)
    tall = len(lines) * size + (len(lines) - 1) * gap
    y = (h - tall) // 2
    for line in lines:
        draw.text((x, y), line, font=font, fill=INK, anchor="lt")
        y += size + gap
    banner.convert("RGB").resize(BANNER, Image.LANCZOS).save(out)


if __name__ == "__main__":
    tv_banner(RELEASE, "Nouride Home", "apps/android/app/src/main/res/drawable-xhdpi/banner.png")
    tv_banner(DEV, "Nouride Home Dev", "apps/android/app/src/debug/res/drawable-xhdpi/banner.png")
    app_icon(RELEASE, "apps/desktop/src-tauri/icons/source.png")
    app_icon(DEV, "apps/desktop/src-tauri/icons-dev/source.png")
    tray_icon("apps/desktop/src-tauri/icons/tray.png", 44)
    print("icons written")
