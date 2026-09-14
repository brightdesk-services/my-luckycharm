"""Regenerate the launcher icon, monochrome layer, splash art and store asset
from one source PNG.

`branding/dark.png` is a *finished* icon — the charm and its rounded-square
background together — so it is split into adaptive-icon layers rather than used
whole. Run this after replacing that file:

    python tools/make_icon.py

Why each layer is built the way it is:

- **background** is the artwork's own gradient, sampled from the content-free
  bands down its left and right edges and extended to fill all 108dp. The
  gradient runs diagonally (the upper left is lighter than the lower right by
  about 12 levels of blue), so a single vertical ramp is not enough and the
  model interpolates horizontally between the two edge samples. Sampling one
  edge only leaves the opposite corners visibly off against the square's fill.
- **foreground** is the artwork scaled so its square spans 78 of 108 units —
  deliberately past the 72dp viewport, so it reads full-bleed rather than as a
  small square floating inside the mask. Its corners are transparent, and the
  background layer showing through them is what makes the seam invisible.
- **monochrome** (themed icons, Android 13+) is the motif alone, extracted by
  differencing the source against that same background model, then fitted to
  the 66dp safe zone.
- **splash** is the motif on transparency so it sits on @color/splash_background
  and reads as a continuation of the icon.

The source square is not perfectly square (1100x1048 in the current art), so
every placement preserves its aspect ratio rather than assuming 1:1.
"""

import os
import statistics

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "branding", "dark.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# Adaptive icons are authored on a 108dp canvas; 72dp of it is the visible
# viewport and 66dp the safe zone every mask is guaranteed to keep.
CANVAS_DP = 108
FOREGROUND_SPAN_DP = 78
SAFE_ZONE_DP = 66
SPLASH_SPAN_DP = 88

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# Alpha at or above this is "solid square"; below it is the soft outer shadow.
SOLID = 250
# How far a pixel must sit from the modelled background before it counts as
# motif. The floor is set by the soft violet glow the artwork paints on the
# ground under the charm: it peaks around 62 levels away from the background,
# while the true motif — clover, ribbon, motion arcs — never comes in under
# about 100. A lower threshold drags that ellipse into the monochrome layer and
# the splash, where it reads as a stray smudge rather than as a shadow, because
# both are composited on something other than the artwork's own background.
DIFF_LO, DIFF_HI = 80, 110


def solid_bounds(px, w, h):
    """Bounds of the rounded square itself, excluding its soft drop shadow."""
    xs, ys = [], []
    for y in range(h):
        row = [x for x in range(w) if px[x, y][3] >= SOLID]
        if row:
            ys.append(y)
            xs.append((row[0], row[-1]))
    return min(s[0] for s in xs), max(s[1] for s in xs), ys[0], ys[-1]


def edge_samples(px, left, right, top, bottom):
    """Median background colour in a band down each edge, per row.

    Rows inside the rounded corners have too few opaque pixels to sample, so
    they are filled by carrying the nearest sampled row outwards — the corners
    are clipped by the mask anyway, and guessing a colour there would show up
    as a band across the top and bottom of the background layer.
    """
    lo, hi = {}, {}
    for y in range(top, bottom + 1):
        for store, x0, x1 in ((lo, left + 20, left + 100), (hi, right - 100, right - 20)):
            vals = [px[x, y][:3] for x in range(x0, x1) if px[x, y][3] >= SOLID]
            if len(vals) >= 25:
                store[y] = tuple(int(statistics.median(v[i] for v in vals)) for i in range(3))
    out = []
    for store in (lo, hi):
        keys = sorted(store)
        first, last = keys[0], keys[-1]
        out.append([store[min(max(y, first), last)] for y in range(top, bottom + 1)])
    return out[0], out[1]


def background_at(left_col, right_col, left, right, top, bottom, x, y):
    """The modelled background colour, extrapolated outside the square."""
    row = int(round(min(max(y - top, 0), bottom - top)))
    a, b = left_col[row], right_col[row]
    t = (x - (left + 60)) / float((right - 60) - (left + 60))
    t = min(max(t, 0.0), 1.0)
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def build_background(im, left, right, top, bottom, left_col, right_col, size):
    """The gradient alone, covering the whole 108dp canvas."""
    sq_w, sq_h = right - left + 1, bottom - top + 1
    span = size * FOREGROUND_SPAN_DP / CANVAS_DP
    scale = span / sq_w
    off_x = (size - span) / 2.0
    off_y = (size - sq_h * scale) / 2.0
    out = Image.new("RGBA", (size, size))
    px = out.load()
    for y in range(size):
        sy = top + (y - off_y) / scale
        for x in range(size):
            sx = left + (x - off_x) / scale
            r, g, b = background_at(left_col, right_col, left, right, top, bottom, sx, sy)
            px[x, y] = (int(round(r)), int(round(g)), int(round(b)), 255)
    return out


def motif(im, left, right, top, bottom, left_col, right_col):
    """The charm, ribbon and motion arcs, lifted off their background."""
    src = im.crop((left, top, right + 1, bottom + 1))
    w, h = src.size
    sp, out = src.load(), Image.new("RGBA", (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = sp[x, y]
            if a < SOLID:
                continue
            br, bg, bb = background_at(
                left_col, right_col, left, right, top, bottom, left + x, top + y
            )
            diff = max(abs(r - br), abs(g - bg), abs(b - bb))
            if diff <= DIFF_LO:
                continue
            k = min((diff - DIFF_LO) / float(DIFF_HI - DIFF_LO), 1.0)
            op[x, y] = (r, g, b, int(round(255 * k)))
    return out.crop(out.split()[3].getbbox())


def fit(art, size, span_dp):
    """Centre `art` on a transparent size x size canvas at the given span."""
    span = size * span_dp / CANVAS_DP
    scale = min(span / art.width, span / art.height)
    w, h = max(1, round(art.width * scale)), max(1, round(art.height * scale))
    out = Image.new("RGBA", (size, size))
    out.paste(art.resize((w, h), Image.LANCZOS), ((size - w) // 2, (size - h) // 2))
    return out


def main():
    im = Image.open(SRC).convert("RGBA")
    w, h = im.size
    px = im.load()
    left, right, top, bottom = solid_bounds(px, w, h)
    print("source %dx%d, square x %d..%d y %d..%d" % (w, h, left, right, top, bottom))

    left_col, right_col = edge_samples(px, left, right, top, bottom)
    square = im.crop((left, top, right + 1, bottom + 1))
    art = motif(im, left, right, top, bottom, left_col, right_col)
    print("motif %dx%d" % art.size)

    # Monochrome is tinted by the system, so only its coverage matters.
    silhouette = Image.new("RGBA", art.size, (255, 255, 255, 0))
    silhouette.putalpha(art.split()[3])

    for name, mult in DENSITIES.items():
        size = int(CANVAS_DP * mult)
        mip = os.path.join(RES, "mipmap-" + name)
        drw = os.path.join(RES, "drawable-" + name)
        os.makedirs(mip, exist_ok=True)
        os.makedirs(drw, exist_ok=True)

        build_background(im, left, right, top, bottom, left_col, right_col, size).save(
            os.path.join(mip, "ic_launcher_background.png"), optimize=True
        )
        fit(square, size, FOREGROUND_SPAN_DP).save(
            os.path.join(mip, "ic_launcher_foreground.png"), optimize=True
        )
        fit(silhouette, size, SAFE_ZONE_DP).save(
            os.path.join(mip, "ic_launcher_monochrome.png"), optimize=True
        )
        fit(art, size, SPLASH_SPAN_DP).save(
            os.path.join(drw, "ic_splash_charm.png"), optimize=True
        )
        print("wrote %s at %dpx" % (name, size))

    # Play listing asset: the finished square, flattened, no transparency.
    store = Image.new("RGB", (512, 512))
    bg = build_background(im, left, right, top, bottom, left_col, right_col, 512)
    store.paste(bg.convert("RGB"), (0, 0))
    sq = square.resize((512, round(512 * square.height / square.width)), Image.LANCZOS)
    store.paste(sq, (0, (512 - sq.height) // 2), sq)
    store.save(os.path.join(ROOT, "branding", "play_store_512_dark.png"), optimize=True)
    print("wrote branding/play_store_512_dark.png")


if __name__ == "__main__":
    main()
