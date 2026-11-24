"""Compare two screenshots below the status bar: prints the box that differs, or None when they are the same.
Usage: python3 -I compare.py <main build.png> <branch build.png> [status bar height in px, 110]"""
import sys
from PIL import Image, ImageChops
a, b = (Image.open(p).convert("RGB") for p in sys.argv[1:3])
top = int(sys.argv[3]) if len(sys.argv) > 3 else 110
box = (0, top, a.size[0], a.size[1])
print(a.size, b.size, ImageChops.difference(a.crop(box), b.crop(box)).getbbox())
