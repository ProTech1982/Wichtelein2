"""Packt die Blender-Renderings fuer die App (WebP-Bogen, JSON, Symbole).

Aufruf: python pack_assets.py <Renderordner> <app/src/main>
Renderordner enthaelt: sprites/ (render_sprites.py), door/ (render_door.py), interior/ (render_interior.py), icon.png
"""
import json
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wichtel_poses as P  # reine Python-Datei, kein bpy

src, main = sys.argv[1:3]
# --only climb,stairs: nur diese Bewegungen packen und in sprites.json ergaenzen (Rest bleibt unveraendert)
ONLY = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
assets = os.path.join(main, "assets")
res = os.path.join(main, "res")
CELL = 224
RENDER = 448
FEET = (RENDER * 0.5 / 2, RENDER * 0.80 / 2)
SCALE_M = 1.40                      # Meter ueber die Zellbreite
PX_PER_M = CELL / SCALE_M
HEIGHT_M = 0.984                    # Wichtel samt Muetze
DIRS = 8


def luma(r, g, b):
    return (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0


def pack_sprites():
    out = os.path.join(assets, "sprites")
    ref_sum = {"hat": [0.0, 0.0], "jacket": [0.0, 0.0], "pants": [0.0, 0.0], "shoes": [0.0, 0.0]}
    total = 0
    for view in ("side", "top"):
        os.makedirs(os.path.join(out, view), exist_ok=True)
        for anim, n in P.ANIMS.items():
            if ONLY is not None and anim not in ONLY:
                continue
            sheet = Image.new("RGBA", (CELL * n, CELL * DIRS), (0, 0, 0, 0))
            msheet = Image.new("RGBA", (CELL * n, CELL * DIRS), (0, 0, 0, 0))
            for f in range(n):
                for d in range(DIRS):
                    b = Image.open(os.path.join(src, "sprites", view, "%s_%02d_%d.png" % (anim, f, d))).convert("RGBA")
                    m = Image.open(os.path.join(src, "sprites", view, "%s_%02d_%d_m.png" % (anim, f, d))).convert("RGBA")
                    b = b.resize((CELL, CELL), Image.LANCZOS)
                    m = m.resize((CELL, CELL), Image.BOX)
                    # Maske nur dort, wo auch der Wichtel sichtbar ist (Besen o. Ae. ausgenommen)
                    sheet.paste(b, (f * CELL, d * CELL))
                    msheet.paste(m, (f * CELL, d * CELL))
                    if view == "side" and anim in ("idle", "walk") and d in (0, 2, 4, 6):
                        bp, mp = b.load(), m.load()
                        for y in range(0, CELL, 3):
                            for x in range(0, CELL, 3):
                                r, g, bl, a = bp[x, y]
                                mr, mg, mb, ma = mp[x, y]
                                if a < 250 or ma < 250:
                                    continue
                                y_ = luma(r, g, bl)
                                sh = min(mr, mg) / 255.0
                                for key, w in (("hat", mr / 255.0 - sh), ("jacket", mg / 255.0 - sh),
                                               ("pants", mb / 255.0), ("shoes", sh)):
                                    if w > 0.95:
                                        ref_sum[key][0] += y_
                                        ref_sum[key][1] += 1
            p1 = os.path.join(out, view, anim + ".webp")
            p2 = os.path.join(out, view, anim + "_m.webp")
            sheet.save(p1, "WEBP", quality=86, method=6, alpha_quality=100)
            msheet.save(p2, "WEBP", lossless=True, quality=100, method=6, exact=True)
            total += os.path.getsize(p1) + os.path.getsize(p2)
    if ONLY is not None:
        mp = os.path.join(out, "sprites.json")
        meta = json.load(open(mp))
        meta["anims"] = P.ANIMS
        json.dump(meta, open(mp, "w"), indent=1)
        print("Sprites (nur %s):" % ",".join(ONLY), round(total / 1e6, 1), "MB")
        return
    ref = {k: round(v[0] / max(1, v[1]), 4) for k, v in ref_sum.items()}
    meta = {
        "cell": CELL, "feet": [round(FEET[0], 1), round(FEET[1], 1)], "pxPerMeter": round(PX_PER_M, 3),
        "heightM": HEIGHT_M, "dirs": DIRS, "anims": P.ANIMS, "refLuma": ref,
        "views": {"side": {"elevation": 10}, "top": {"elevation": 68}},
    }
    json.dump(meta, open(os.path.join(out, "sprites.json"), "w"), indent=1)
    print("Sprites:", round(total / 1e6, 1), "MB", ref)


def pack_doors():
    out = os.path.join(assets, "door")
    os.makedirs(out, exist_ok=True)
    meta = json.load(open(os.path.join(src, "door", "door.json")))
    total = 0
    for shape in meta["shapes"]:
        for ang in meta["angles"] + ["hole"]:
            im = Image.open(os.path.join(src, "door", "door_%s_%s.png" % (shape, ang))).convert("RGBA")
            p = os.path.join(out, "door_%s_%s.webp" % (shape, ang))
            if ang == "hole":
                im.save(p, "WEBP", lossless=True, quality=100, method=6)
            else:
                im.save(p, "WEBP", quality=90, method=6, alpha_quality=100)
            total += os.path.getsize(p)
    json.dump(meta, open(os.path.join(out, "door.json"), "w"), indent=1)
    print("Tuer:", round(total / 1e6, 2), "MB")


def pack_interiors():
    out = os.path.join(assets, "interior")
    os.makedirs(out, exist_ok=True)
    for name in ("stube", "hoehle"):
        im = Image.open(os.path.join(src, "interior", "interior_%s.png" % name)).convert("RGB")
        im.save(os.path.join(out, "interior_%s.webp" % name), "WEBP", quality=88, method=6)
    print("Innenraeume ok")


def pack_icon():
    icon = Image.open(os.path.join(src, "icon.png")).convert("RGBA")
    os.makedirs(os.path.join(res, "drawable-nodpi"), exist_ok=True)
    icon.save(os.path.join(res, "drawable-nodpi", "ic_launcher_foreground.png"))
    bg = (0x2E, 0x6B, 0x4A, 255)
    sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dens, px in sizes.items():
        d = os.path.join(res, "mipmap-" + dens)
        os.makedirs(d, exist_ok=True)
        base = Image.new("RGBA", (432, 432), bg)
        base.alpha_composite(icon)
        sq = base.resize((px, px), Image.LANCZOS)
        sq.save(os.path.join(d, "ic_launcher.png"))
        # rund
        mask = Image.new("L", (px * 4, px * 4), 0)
        from PIL import ImageDraw
        ImageDraw.Draw(mask).ellipse((0, 0, px * 4 - 1, px * 4 - 1), fill=255)
        mask = mask.resize((px, px), Image.LANCZOS)
        rd = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        rd.paste(sq, (0, 0), mask)
        rd.save(os.path.join(d, "ic_launcher_round.png"))
    print("Symbol ok")


pack_sprites()
if ONLY is None:
    pack_doors()
    pack_interiors()
    pack_icon()
