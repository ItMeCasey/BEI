"""Draws the BEI icon as 32x32 pixel art (a closed book at an angle with BEI on the cover) and writes it at several sizes.

Run from the repository root: python branding/source/make_icon.py
Writes branding/icon/icon_<size>.png and branding/icon/icon.svg.
"""
import math
from pathlib import Path

from PIL import Image

N = 32
OUT = Path(__file__).resolve().parent.parent / "icon"

BG = (24, 22, 38)
BG_EDGE = (46, 42, 70)
BG_TOP = (58, 54, 88)

px = [[None] * N for _ in range(N)]


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[y][x] = c


# Background tile with stepped rounded corners.
R = 4
for y in range(N):
    for x in range(N):
        cx = min(max(x, R - 0.5), N - R - 0.5)
        cy = min(max(y, R - 0.5), N - R - 0.5)
        if math.hypot(x - cx, y - cy) <= R:
            px[y][x] = BG
for y in range(N):
    for x in range(N):
        if px[y][x] is None:
            continue
        edge = any(
            not (0 <= x + dx < N and 0 <= y + dy < N) or px[y + dy][x + dx] is None
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
        )
        if edge:
            px[y][x] = BG_TOP if y < N // 2 else BG_EDGE


def stamp(rows, pal, x0, y0):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal:
                put(x0 + i, y0 + j, pal[ch])


# A closed book seen at an angle (cover sheared upward to the right) with BEI on the cover.
COVER, COVER_HI, COVER_LO = (62, 66, 150), (92, 98, 190), (38, 40, 96)
SPINE_C = (34, 36, 88)
PAGE, PAGE_LINE = (238, 230, 208), (200, 190, 164)
OUTLINE = (12, 11, 22)


def sy(x):
    return -round((x - 5) * 0.17)


def col(x, y0, y1, c):
    for y in range(y0 + sy(x), y1 + sy(x) + 1):
        put(x, y, c)


for x in range(4, 29):
    if x == 4:
        col(x, 10, 28, OUTLINE)
    elif x <= 24:
        col(x, 10, 29, OUTLINE)
        col(x, 11, 25, SPINE_C if x <= 6 else COVER)
        put(x, 11 + sy(x), COVER_HI)                  # top edge highlight
        col(x, 26, 27, PAGE if x > 6 else SPINE_C)     # page block under the cover
        if x > 6 and x % 2 == 0:
            put(x, 27 + sy(x), PAGE_LINE)
        col(x, 28, 28, COVER_LO)                      # back cover
    elif x <= 27:
        col(x, 10, 29, OUTLINE)
        col(x, 12, 27, PAGE)
        if x % 2 == 0:
            col(x, 14, 14, PAGE_LINE)
            col(x, 18, 18, PAGE_LINE)
            col(x, 22, 22, PAGE_LINE)
        col(x, 28, 28, COVER_LO)
    else:
        col(x, 11, 29, OUTLINE)

# BEI on the cover: upright letters with a bevel and a drop shadow.
GLYPHS = {
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "I": ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
}
BEVEL = [(255, 244, 190), (255, 226, 140), (247, 200, 80), (242, 179, 61), (216, 149, 46), (178, 116, 32), (178, 116, 32)]
x = 9
LY = 12
letters = []
for ch in "BEI":
    g = GLYPHS[ch]
    for j, row in enumerate(g):
        for i, c in enumerate(row):
            if c == "#":
                letters.append((x + i, LY + j, BEVEL[j]))
    x += len(g[0]) + 1
for lx, ly, _ in letters:
    put(lx + 1, ly + 1, OUTLINE)
for lx, ly, c in letters:
    put(lx, ly, c)

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
for y in range(N):
    for x in range(N):
        if px[y][x] is not None:
            img.putpixel((x, y), px[y][x] + (255,))

OUT.mkdir(parents=True, exist_ok=True)
for size in (32, 64, 128, 256, 512, 1024):
    img.resize((size, size), Image.NEAREST).save(OUT / f"icon_{size}.png", optimize=True)

rects = []
for y in range(N):
    for x in range(N):
        c = px[y][x]
        if c is not None:
            rects.append(f'<rect x="{x}" y="{y}" width="1.02" height="1.02" fill="#{c[0]:02x}{c[1]:02x}{c[2]:02x}"/>')
(OUT / "icon.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}" shape-rendering="crispEdges">'
    + "".join(rects) + "</svg>\n",
    encoding="utf-8",
)
print("wrote", OUT)
