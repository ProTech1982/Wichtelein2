"""Erzeugt die Startbilder (Hintergruende) fuer Wichtelein2: 9 weihnachtliche Szenen im Hochformat.

Aufruf:  python tools/make_backgrounds.py <app/src/main>   (schreibt assets/backgrounds/*.webp und backgrounds.json)
         python tools/make_backgrounds.py <Ausgabeordner> --preview   (nur PNG-Vorschau, ohne Metadaten)

backgrounds.json beschreibt je Bild, was die App darueber animiert (Schneefall, flackernde Leuchten, funkelnde Lichter)
und welche Melodie standardmaessig dazu spielt (1 Schneeflocke, 2 O Tannenbaum, 3 Jingle Bells, 4 Stille Nacht).
Koordinaten der Animationen sind Bruchteile der Bildbreite/-hoehe, Radien Bruchteile der Breite.
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bg_lib import H, W, Scene, mix, rgb  # noqa: E402


def frac(x, y):
    return round(x / W, 4), round(y / H, 4)


def glow_meta(x, y, r, color, flicker=1.0, speed=1.0):
    fx, fy = frac(x, y)
    return {"x": fx, "y": fy, "r": round(r / W, 4), "color": color, "flicker": flicker, "speed": speed}


def twinkle_meta(points, color, r=7):
    return [{"x": frac(x, y)[0], "y": frac(x, y)[1], "r": round(r / W, 4), "color": color, "phase": round((i * 0.37) % 1, 2)} for i, (x, y) in enumerate(points)]


def flame(s, cx, by, h, w, outer, inner, sway=0.0):
    def prof(t):
        return w * (math.sin(math.pi * min(1, t * 1.05)) ** 0.75) * (1 - t) ** 0.25

    left = []
    right = []
    for i in range(0, 21):
        t = i / 20
        off = sway * math.sin(t * math.pi) * h * 0.18
        left.append((cx + off - prof(t) / 2, by - t * h))
        right.append((cx + off + prof(t) / 2, by - t * h))
    s.poly(left + right[::-1], outer)
    left2 = [(cx + (x - cx) * 0.55, by - (by - y) * 0.7) for x, y in left]
    right2 = [(cx + (x - cx) * 0.55, by - (by - y) * 0.7) for x, y in right]
    s.poly(left2 + right2[::-1], inner)


def stocking(s, cx, top, color, trim="#f7f4ec", pattern=None, flip=1):
    # Strumpf: weisser Rand, Schaft, Fuss nach rechts
    s.rrect(cx - 46, top, cx + 46, top + 46, 14, trim)
    s.poly([(cx - 40, top + 40), (cx + 40, top + 40), (cx + 40, top + 190), (cx + 100 * flip, top + 235), (cx + 92 * flip, top + 295),
            (cx - 36, top + 292), (cx - 40, top + 200)], color)
    s.ellipse(cx + 70 * flip, top + 268, 40, 32, mix(rgb(color), (255, 255, 255), 0.18))
    s.rect(cx - 40, top + 150, cx + 40, top + 164, mix(rgb(color), (255, 255, 255), 0.22))
    if pattern == "stripes":
        for k in range(4):
            s.rect(cx - 40, top + 60 + k * 26, cx + 40, top + 72 + k * 26, mix(rgb(color), (255, 255, 255), 0.3))
    s.circle(cx - 28, top + 2, 6, "#c9a24a")


def boot(s, cx, top, color="#b01e23"):
    # Nikolausstiefel mit Pelzrand, ein Zuckerstab und ein Geschenk schauen heraus
    s.line([(cx + 8, top + 30), (cx + 8, top - 70), (cx + 40, top - 100), (cx + 66, top - 74)], "#f4f4f0", 14)
    s.line([(cx + 8, top - 50), (cx + 12, top - 70)], "#d8262a", 14)
    s.rrect(cx - 44, top - 8, cx + 44, top + 44, 14, "#fbfaf4")
    for k in range(5):
        s.circle(cx - 36 + k * 18, top + 36, 11, "#fbfaf4")
    s.poly([(cx - 38, top + 36), (cx + 38, top + 36), (cx + 38, top + 200), (cx + 112, top + 232), (cx + 112, top + 300), (cx - 38, top + 300)], color)
    s.rect(cx - 38, top + 252, cx + 112, top + 300, "#701418")
    s.rect(cx - 38, top + 150, cx + 38, top + 160, "#d9a83a")


def candle(s, x, y, h=120, w=30, color="#f5ecd6"):
    s.rrect(x - w / 2, y - h, x + w / 2, y, 5, color)
    s.rect(x - 2, y - h - 14, x + 2, y - h, "#2a2220")
    flame(s, x, y - h - 12, 46, 20, (255, 170, 40, 235), (255, 238, 150, 250))


def bauble_row(s, pts, colors, r=14):
    for i, (x, y) in enumerate(pts):
        s.rgrad_ellipse(x, y, r, r, mix(rgb(colors[i % len(colors)]), (255, 255, 255), 0.5), colors[i % len(colors)])


def scene_kamin():
    s = Scene([(0, "#3a1d1a"), (0.6, "#2b1513"), (1, "#1c0e0c")])
    # Tapete: feine senkrechte Streifen
    for x in range(0, W, 120):
        s.rect(x, 0, x + 56, 1500, (255, 220, 190, 10))
    # Fussboden
    s.vband(1500, H, "#5a361e", "#2c170b")
    for k, y in enumerate(range(1500, H, 70)):
        s.line([(0, y), (W, y)], (20, 8, 2, 120), 3)
        for x in range(((k * 130) % 260), W, 260):
            s.line([(x, y), (x, y + 70)], (20, 8, 2, 90), 3)
    s.rect(0, 1490, W, 1504, "#2a150c")
    # Teppich
    s.ellipse(540, 1690, 470, 150, "#7c1f22")
    s.ellipse(540, 1690, 440, 128, "#9b2a2d")
    s.ellipse(540, 1690, 330, 92, "#7c1f22")
    s.ellipse(540, 1690, 300, 80, "#b5393b")
    # Kamin: Mauerwerk
    s.rect(190, 880, 890, 1500, "#8a4a36")
    for r, y in enumerate(range(880, 1500, 52)):
        s.line([(190, y), (890, y)], (50, 20, 14, 200), 4)
        for x in range(190 + (r % 2) * 55, 890, 110):
            s.line([(x, y), (x, y + 52)], (50, 20, 14, 200), 4)
    # leichte Lichtverfaerbung der Steine durch das Feuer
    s.glow(540, 1360, 520, "#ff8a2a", 0.3)
    # Feuerraum mit Bogen
    s.poly(s.arc_poly(540, 1170, 220, 150, 180, 360) + [(760, 1500), (320, 1500)], "#150907")
    s.glow(540, 1400, 300, "#ff6a10", 0.9)
    s.glow(540, 1430, 160, "#ffd070", 0.8)
    # Holzscheite und Flammen
    s.rrect(380, 1420, 700, 1470, 24, "#4a2815")
    s.ellipse(545, 1415, 170, 30, "#5b3219")
    s.ellipse(430, 1425, 40, 26, "#6a3b1d")
    s.ellipse(655, 1428, 40, 26, "#6a3b1d")
    for cx, h, w, sw in [(450, 150, 120, -0.6), (640, 170, 130, 0.7), (545, 250, 170, 0.0), (500, 190, 100, 0.4), (595, 200, 110, -0.5)]:
        flame(s, cx, 1425, h, w, (255, 110, 20, 230), (255, 214, 90, 245), sw)
    flame(s, 545, 1425, 120, 80, (255, 190, 70, 255), (255, 250, 200, 255))
    # Rahmen des Feuerraums
    s.line(s.arc_poly(540, 1170, 220, 150, 180, 360), "#3a1a12", 14)
    s.rect(312, 1170, 326, 1500, "#3a1a12")
    s.rect(754, 1170, 768, 1500, "#3a1a12")
    # Kaminsims
    s.rrect(130, 820, 950, 892, 14, "#7a4a28")
    s.rect(130, 820, 950, 834, "#a06a3a")
    s.rect(130, 880, 950, 892, "#4a2a14")
    # Sockel / Herdplatte
    s.rrect(150, 1488, 930, 1536, 10, "#7a7873")
    s.rect(150, 1488, 930, 1498, "#9a9892")
    # Girlande auf dem Sims
    s.line([(150, 812), (270, 842), (400, 818), (540, 846), (680, 818), (810, 842), (930, 812)], "#1f6b36", 34)
    s.line([(150, 806), (270, 836), (400, 812), (540, 840), (680, 812), (810, 836), (930, 806)], "#2f8a49", 22)
    pts = [(210, 840), (330, 828), (470, 836), (610, 836), (750, 828), (870, 840)]
    bauble_row(s, pts, ["#d8262a", "#e8b53a", "#e8e8f0", "#d8262a"], 15)
    # Kerzen
    candle(s, 215, 812, 130, 34)
    candle(s, 865, 812, 130, 34)
    # Kranz ueber dem Kamin
    cx, cy = 540, 560
    s.ellipse(cx, cy, 165, 165, "#1f6b36")
    s.ellipse(cx, cy, 108, 108, "#2a1411")
    for i in range(46):
        a = i / 46 * 2 * math.pi
        rr = 137 + 22 * math.sin(i * 1.7)
        s.ellipse(cx + rr * math.cos(a), cy + rr * math.sin(a), 24, 17, mix(rgb("#1f6b36"), rgb("#3fa35b"), (i % 5) / 5))
    for i in range(10):
        a = (i + 0.4) / 10 * 2 * math.pi
        s.circle(cx + 137 * math.cos(a), cy + 137 * math.sin(a), 8, "#c0231f")
    s.poly([(cx - 8, cy + 120), (cx - 70, cy + 190), (cx - 24, cy + 202), (cx + 0, cy + 150)], "#c0231f")
    s.poly([(cx + 8, cy + 120), (cx + 70, cy + 190), (cx + 24, cy + 202), (cx + 0, cy + 150)], "#c0231f")
    s.ellipse(cx, cy + 128, 22, 20, "#e33a35")
    # Strümpfe und Stiefel am Sims
    stocking(s, 300, 890, "#c3222a", pattern="stripes")
    boot(s, 455, 890)
    stocking(s, 640, 890, "#2d7f46", pattern="stripes")
    stocking(s, 800, 890, "#c3222a")
    s.vignette(0.55)
    twinkle = [(p[0], p[1] + 2) for p in pts]
    return s, {
        "id": "kamin", "label": "Kamin mit Feuer", "music": 4, "snow": 0.0,
        "glows": [glow_meta(540, 1360, 520, "#ff7a1a", 0.35, 2.4), glow_meta(215, 690, 70, "#ffb347", 0.3, 3.1), glow_meta(865, 690, 70, "#ffb347", 0.3, 2.7)],
        "twinkles": twinkle_meta(twinkle, "#ffe9a8"),
    }


def wood_floor(s, y0, c0="#6a4326", c1="#2f1b0d", plank=64, seed=3):
    s.vband(y0, H, c0, c1)
    rnd = random.Random(seed)
    for k, y in enumerate(range(y0, H, plank)):
        s.line([(0, y), (W, y)], (20, 8, 2, 110), 3)
        for x in range((k * 170) % 340, W, 340):
            s.line([(x, y), (x, y + plank)], (20, 8, 2, 90), 3)
        if rnd.random() < 0.7:
            x = rnd.uniform(0, W)
            s.line([(x, y + plank * 0.5), (x + rnd.uniform(60, 160), y + plank * 0.5 + rnd.uniform(-3, 3))], (255, 220, 170, 22), 3)


def gift(s, x, y, w, h, color, ribbon="#f0d36a"):
    s.rect(x, y - h, x + w, y, color)
    s.rect(x, y - h, x + w, y - h + h * 0.18, mix(rgb(color), (255, 255, 255), 0.22))
    s.rect(x + w / 2 - w * 0.07, y - h, x + w / 2 + w * 0.07, y, ribbon)
    s.rect(x, y - h / 2 - h * 0.06, x + w, y - h / 2 + h * 0.06, ribbon)
    bx, by = x + w / 2, y - h
    s.ellipse(bx - w * 0.14, by - h * 0.07, w * 0.16, h * 0.10, ribbon)
    s.ellipse(bx + w * 0.14, by - h * 0.07, w * 0.16, h * 0.10, ribbon)
    s.circle(bx, by - h * 0.02, h * 0.05, mix(rgb(ribbon), (120, 80, 0), 0.3))


def scene_sessel():
    s = Scene([(0, "#2b2740"), (0.55, "#3d2f3a"), (1, "#2a1a14")])
    for x in range(0, W, 140):
        s.rect(x, 0, x + 64, 1400, (255, 230, 200, 7))
    # Fenster links mit Winternacht
    wx0, wy0, wx1, wy1 = 70, 250, 470, 930
    s.rect(wx0 - 22, wy0 - 22, wx1 + 22, wy1 + 22, "#e9e1d2")
    s.vband(wy0, wy1, "#0b1233", "#2c4a7a", wx0, wx1)
    s.circle(330, 420, 62, "#fff3c8")
    s.glow(330, 420, 200, "#bcd0ff", 0.55)
    random.seed(4)
    for _ in range(40):
        x, y = random.uniform(wx0, wx1), random.uniform(wy0, wy1)
        s.circle(x, y, random.uniform(1.5, 4), (255, 255, 255, 200))
    s.poly([(wx0, wy1), (wx0, 800), (200, 760), (330, 790), (470, 740), (wx1, wy1)], "#dfe8f5")
    s.rect(wx0 + 190, wy0, wx0 + 210, wy1, "#e9e1d2")
    s.rect(wx0, 540, wx1, 560, "#e9e1d2")
    s.rect(wx0 - 40, wy1 + 22, wx1 + 40, wy1 + 54, "#d2c8b4")
    # Vorhaenge
    s.poly([(wx0 - 60, 190), (wx0 + 110, 190), (wx0 + 70, 960), (wx0 - 60, 980)], "#8c1f2c")
    s.poly([(wx1 - 90, 190), (wx1 + 60, 190), (wx1 + 60, 980), (wx1 - 40, 960)], "#8c1f2c")
    for k in range(4):
        s.line([(wx0 - 30 + k * 34, 200), (wx0 - 34 + k * 30, 960)], (60, 8, 14, 110), 5)
    s.rect(wx0 - 80, 168, wx1 + 80, 196, "#5a3a1c")
    # Regal rechts
    s.rect(640, 230, 1040, 1010, "#5a3a22")
    for ry in (230, 470, 710):
        s.rect(640, ry, 1040, ry + 18, "#8a5d36")
    cols = ["#9c2a33", "#2f6b8a", "#c9a24a", "#3e7a4a", "#6a3f8a", "#d9d2c0", "#a14f2a"]
    rnd = random.Random(7)
    for shelf in range(3):
        y1 = 230 + shelf * 240 + 232
        x = 656
        while x < 1020:
            bw = rnd.randint(22, 44)
            bh = rnd.randint(130, 205)
            s.rect(x, y1 - bh, x + bw, y1, cols[rnd.randrange(len(cols))])
            s.rect(x, y1 - bh, x + 4, y1, (255, 255, 255, 40))
            x += bw + 3
    # kleine Lichterkette am Regal
    pts = [(660 + i * 52, 250 + 12 * math.sin(i * 0.9)) for i in range(8)]
    s.line(pts, "#20301f", 3)
    for i, (x, y) in enumerate(pts):
        s.circle(x, y + 10, 8, ["#ffd34a", "#ff6a4a", "#6ad0ff", "#9bff7a"][i % 4])
    # Boden und Teppich
    wood_floor(s, 1400)
    s.ellipse(540, 1660, 520, 170, "#3c5a7a")
    s.ellipse(540, 1660, 480, 146, "#e6dcc8")
    s.ellipse(540, 1660, 440, 128, "#3c5a7a")
    s.ellipse(540, 1660, 330, 94, "#a43a3c")
    # Stehlampe links
    s.rect(238, 1090, 246, 1560, "#2a1d12")
    s.ellipse(242, 1566, 70, 18, "#2a1d12")
    s.glow(242, 1020, 360, "#ffc060", 0.55)
    s.poly([(170, 1090), (314, 1090), (288, 940), (196, 940)], "#f2c984")
    s.rect(170, 1086, 314, 1098, "#c89a52")
    # Sessel
    s.rrect(330, 900, 800, 1480, 90, "#7a1f2b")
    s.rrect(372, 940, 758, 1440, 70, "#8f2a37")
    for rr in range(3):
        for cc in range(3):
            s.circle(430 + cc * 100 + (rr % 2) * 50, 1000 + rr * 100, 7, "#4a0f17")
    # Wichtel liest im Sessel
    s.gnome(560, 1422, 1.12, sitting=True, book=True)
    # Armlehnen und Sitz vorn
    s.rrect(300, 1130, 400, 1500, 46, "#6a1a25")
    s.rrect(730, 1130, 830, 1500, 46, "#6a1a25")
    s.rrect(380, 1420, 750, 1520, 30, "#9a2f3c")
    s.rect(400, 1520, 420, 1570, "#2a1d12")
    s.rect(710, 1520, 730, 1570, "#2a1d12")
    # Fusshocker
    s.rrect(760, 1500, 960, 1580, 26, "#5a3a22")
    s.rrect(770, 1490, 950, 1530, 20, "#8a5d36")
    # Beistelltisch mit Kerze
    s.rect(930, 1330, 1050, 1342, "#7a5230")
    s.rect(986, 1342, 994, 1560, "#5a3a22")
    candle(s, 990, 1330, 70, 24)
    s.glow(990, 1220, 120, "#ffb347", 0.5)
    s.vignette(0.5)
    return s, {
        "id": "sessel", "label": "Wichtel im Sessel", "music": 1, "snow": 0.15,
        "glows": [glow_meta(242, 1020, 360, "#ffc060", 0.12, 1.2), glow_meta(990, 1220, 120, "#ffb347", 0.35, 3.3)],
        "twinkles": twinkle_meta([(p[0], p[1] + 10) for p in pts], "#ffe9a8", 9),
    }


def scene_baum():
    s = Scene([(0, "#10243a"), (0.6, "#1a3350"), (1, "#0d1624")])
    # Bokeh-Lichter im Hintergrund
    rnd = random.Random(11)
    for _ in range(26):
        x, y = rnd.uniform(0, W), rnd.uniform(100, 1300)
        r = rnd.uniform(24, 70)
        s.soft(lambda x=x, y=y, r=r: s.circle(x, y, r, rnd.choice([(255, 200, 100, 90), (255, 120, 110, 80), (140, 200, 255, 80)])), 18)
    wood_floor(s, 1560, "#4a2e1a", "#1c1008")
    s.ellipse(540, 1620, 520, 80, (0, 0, 0, 90))
    # Baum
    s.fir(540, 1590, 1180, 860, dark="#0b3a22", light="#2f8f50", snow=False, layers=6)
    # Lichterkette
    colors = ["#ff4a4a", "#ffd34a", "#5ab4ff", "#ff7ad0", "#7aff8a"]
    pts = []
    for row in range(14):
        y = 700 + row * 58
        half = 40 + (y - 700) * 0.42
        n = 2 + row // 3
        for k in range(n):
            x = 540 + (-half + (2 * half) * (k + 0.5 + 0.35 * math.sin(row * 1.7)) / n)
            pts.append((x, y + 18 * math.sin(k + row)))
    for i, (x, y) in enumerate(pts):
        c = colors[i % len(colors)]
        s.glow(x, y, 46, c, 0.7)
        s.circle(x, y, 8.5, mix(rgb(c), (255, 255, 255), 0.45))
    # Kugeln
    rnd = random.Random(21)
    for _ in range(16):
        y = rnd.uniform(820, 1500)
        half = 30 + (y - 700) * 0.40
        x = 540 + rnd.uniform(-half, half)
        c = rnd.choice(["#d8262a", "#e8b53a", "#d9d9ea", "#3f6fd0"])
        s.rgrad_ellipse(x, y, 24, 24, mix(rgb(c), (255, 255, 255), 0.55), c)
        s.rect(x - 3, y - 32, x + 3, y - 22, "#d9b54a")
    # Stern
    s.glow(540, 590, 200, "#ffe48a", 0.85)
    s.star5(540, 600, 76, "#ffd24a", inner=0.45)
    s.star5(540, 600, 52, "#fff0a8", inner=0.45)
    # Geschenke
    gift(s, 190, 1640, 190, 150, "#c3222a")
    gift(s, 360, 1650, 150, 110, "#2f7a4a", "#f4f0e0")
    gift(s, 630, 1646, 210, 170, "#2f5fb0")
    gift(s, 840, 1640, 140, 120, "#a3418c", "#f0d36a")
    gift(s, 70, 1670, 120, 90, "#e8b53a", "#c3222a")
    s.vignette(0.5)
    tw = [(x, y) for x, y in pts]
    return s, {
        "id": "baum", "label": "Weihnachtsbaum", "music": 2, "snow": 0.0,
        "glows": [glow_meta(540, 600, 260, "#ffe48a", 0.2, 1.7)],
        "twinkles": twinkle_meta(tw, "#fff2c0", 11),
    }


def snow_hill(s, pts, top="#e8f0fb", bottom="#aebfdc", shade=None):
    s.poly(pts, top)
    if shade:
        s.poly([(x, y + 26) for x, y in pts[1:-1]] + [pts[-1], pts[0]], shade)


def cabin(s, x, y, w=330, h=210, lit="#ffcf6a"):
    """Kleine Blockhuette mit Schneedach, Kamin und beleuchteten Fenstern (x = Mitte, y = Boden)."""
    s.rect(x - w / 2, y - h, x + w / 2, y, "#6a4326")
    for k in range(1, 7):
        s.line([(x - w / 2, y - h + k * h / 7), (x + w / 2, y - h + k * h / 7)], (30, 16, 6, 150), 3)
    s.rect(x + w * 0.22, y - h - 80, x + w * 0.36, y - h, "#5b3a22")
    s.rect(x + w * 0.20, y - h - 96, x + w * 0.38, y - h - 76, "#e8eef8")
    s.poly([(x - w / 2 - 36, y - h + 6), (x, y - h - 120), (x + w / 2 + 36, y - h + 6)], "#4a2f1c")
    s.poly([(x - w / 2 - 40, y - h + 12), (x, y - h - 134), (x + w / 2 + 40, y - h + 12), (x + w / 2 + 40, y - h - 4), (x, y - h - 150), (x - w / 2 - 40, y - h - 4)], "#f3f7ff")
    s.poly([(x - w / 2 - 40, y - h + 12), (x, y - h - 140), (x + w / 2 + 40, y - h + 12), (x + w / 2 + 16, y - h + 26), (x, y - h - 100), (x - w / 2 - 16, y - h + 26)], "#e6eefa")
    for wx in (x - w * 0.26, x + w * 0.2):
        s.glow(wx, y - h * 0.55, 150, lit, 0.55)
        s.rect(wx - 34, y - h * 0.8, wx + 34, y - h * 0.3, lit)
        s.rect(wx - 3, y - h * 0.8, wx + 3, y - h * 0.3, "#5a3a22")
        s.rect(wx - 34, y - h * 0.55 - 3, wx + 34, y - h * 0.55 + 3, "#5a3a22")
    s.rrect(x - 30, y - h * 0.62, x + 30, y, 4, "#3a2412")


def scene_winternacht():
    s = Scene([(0, "#070b24"), (0.35, "#14264f"), (0.62, "#3e6aa0"), (1, "#9db8e0")])
    s.stars(130, 900, seed=5)
    s.glow(760, 380, 420, "#cfe0ff", 0.6)
    s.circle(760, 380, 112, "#fdf6dc")
    s.circle(730, 350, 24, (225, 215, 190, 120))
    s.circle(800, 420, 18, (225, 215, 190, 120))
    # ferne Berge
    s.poly([(0, 1010), (120, 860), (230, 960), (380, 790), (520, 940), (660, 830), (800, 960), (940, 850), (W, 960), (W, 1100), (0, 1100)], "#7e97bd")
    s.poly([(0, 1060), (170, 930), (300, 1010), (460, 900), (620, 1010), (790, 920), (920, 1000), (W, 940), (W, 1120), (0, 1120)], "#9fb3d3")
    # Huegel
    snow_hill(s, [(0, 1150), (240, 1090), (520, 1120), (800, 1080), (W, 1110), (W, 1300), (0, 1300)], "#d9e5f6")
    for cx, by, h, w in [(90, 1190, 300, 170), (210, 1200, 220, 120), (990, 1180, 330, 180), (880, 1190, 230, 130), (790, 1170, 160, 90)]:
        s.fir(cx, by, h, w, dark="#0f3326", light="#2c6a50")
    snow_hill(s, [(0, 1300), (260, 1230), (560, 1290), (840, 1240), (W, 1280), (W, 1500), (0, 1500)], "#eaf2fc")
    cabin(s, 340, 1330)
    snow_hill(s, [(0, 1500), (300, 1420), (640, 1490), (W, 1430), (W, H), (0, H)], "#f6faff")
    # Weg zur Huette
    s.poly([(300, 1340), (380, 1340), (640, 1700), (160, 1700)], "#dbe6f6")
    s.poly([(335, 1345), (365, 1345), (430, 1500), (270, 1500)], (190, 205, 232, 160))
    for cx, by, h, w in [(130, 1720, 560, 330), (960, 1780, 640, 380)]:
        s.fir(cx, by, h, w, dark="#0c2a20", light="#26604a")
    s.soft(lambda: s.ellipse(540, 1860, 560, 70, (130, 150, 190, 130)), 24)
    s.snowflakes(110, seed=9, y0=0, y1=H)
    s.vignette(0.35)
    return s, {
        "id": "winternacht", "label": "Verschneite Winternacht", "music": 1, "snow": 1.0,
        "glows": [glow_meta(340, 1250, 160, "#ffcf6a", 0.25, 2.2), glow_meta(760, 380, 260, "#cfe0ff", 0.08, 0.6)],
        "twinkles": twinkle_meta([(95, 130), (300, 220), (480, 90), (620, 300), (960, 160), (180, 520), (900, 620), (440, 520)], "#fff6d8", 6),
    }


def house(s, x, y, w, h, wall, roof, lit=True, floors=2):
    s.rect(x, y - h, x + w, y, wall)
    s.rect(x, y - h, x + w, y - h + 10, mix(rgb(wall), (255, 255, 255), 0.18))
    s.poly([(x - 24, y - h + 4), (x + w / 2, y - h - w * 0.42), (x + w + 24, y - h + 4)], roof)
    s.poly([(x - 28, y - h + 8), (x + w / 2, y - h - w * 0.42 - 14), (x + w + 28, y - h + 8), (x + w + 14, y - h + 24), (x + w / 2, y - h - w * 0.42 + 14), (x - 14, y - h + 24)], "#f4f8ff")
    cols = 2 if w < 220 else 3
    for f in range(floors):
        for c in range(cols):
            wx = x + (c + 0.5) * w / cols
            wy = y - h + (f + 0.45) * h / floors
            if lit:
                s.glow(wx, wy, 70, "#ffbf5a", 0.5)
            s.rect(wx - 20, wy - 26, wx + 20, wy + 26, "#ffc766" if lit else "#27324a")
            s.rect(wx - 2, wy - 26, wx + 2, wy + 26, "#2a1a10")
            s.rect(wx - 20, wy - 2, wx + 20, wy + 2, "#2a1a10")
    s.rect(x + w * 0.42, y - 78, x + w * 0.58, y, "#4a2a18")


def scene_dorf():
    s = Scene([(0, "#14123a"), (0.35, "#3b2a68"), (0.55, "#c4638a"), (0.64, "#f0a05a"), (1, "#f4c88a")])
    s.stars(70, 600, seed=12)
    s.glow(540, 1190, 600, "#ffb070", 0.55, 2.2)
    s.poly([(0, 1180), (200, 1090), (420, 1170), (620, 1070), (860, 1160), (W, 1100), (W, 1300), (0, 1300)], "#5a3f78")
    s.poly([(0, 1240), (260, 1160), (520, 1240), (780, 1170), (W, 1230), (W, 1400), (0, 1400)], "#493262")
    # Haeuser (hinten)
    house(s, 40, 1330, 200, 170, "#8a5a50", "#5a2f3a")
    house(s, 700, 1320, 230, 190, "#7a6a4a", "#40343a")
    house(s, 880, 1340, 190, 150, "#6a5070", "#3a2a48")
    # Kirche
    s.rect(300, 1330 - 230, 460, 1330, "#9a8a78")
    s.poly([(296, 1100), (380, 1100), (464, 1100), (380, 880)], "#4a3a52")
    s.poly([(290, 1100), (380, 872), (470, 1100), (460, 1090), (380, 900), (300, 1090)], "#f4f8ff")
    s.rect(354, 940 + 100, 406, 1010 + 100, "#2a2234")
    s.circle(380, 1030, 22, "#f4ecd8")
    s.line([(380, 1030), (380, 1014)], "#2a2234", 3)
    s.line([(380, 1030), (392, 1032)], "#2a2234", 3)
    s.glow(380, 1190, 120, "#ffbf5a", 0.5)
    s.poly([(350, 1180), (410, 1180), (410, 1330), (350, 1330)], "#ffc766")
    s.poly([(350, 1180), (380, 1146), (410, 1180)], "#ffc766")
    s.line([(380, 1146), (380, 1330)], "#5a3a22", 4)
    # Schneeboden
    snow_hill(s, [(0, 1330), (300, 1300), (700, 1330), (W, 1290), (W, H), (0, H)], "#eef3fb")
    s.poly([(240, 1430), (700, 1430), (900, H), (-160, H)], "#d4deef")
    s.poly([(300, 1450), (640, 1450), (700, H), (80, H)], (255, 232, 200, 70))
    for cx, by, h, w in [(60, 1420, 360, 200), (1010, 1440, 420, 230), (700, 1350, 230, 130), (560, 1360, 170, 100)]:
        s.fir(cx, by, h, w, dark="#0f2c26", light="#2a6a50")
    # Strassenlaterne
    s.rect(846, 1180, 858, 1660, "#1d1d28")
    s.rrect(818, 1110, 886, 1190, 10, "#1d1d28")
    s.glow(852, 1150, 280, "#ffc86a", 0.75)
    s.rrect(826, 1120, 878, 1180, 8, "#ffe9a8")
    s.ellipse(852, 1670, 70, 16, (0, 0, 0, 60))
    s.snowflakes(120, seed=13)
    s.vignette(0.35)
    return s, {
        "id": "dorf", "label": "Verschneites Dorf", "music": 3, "snow": 1.0,
        "glows": [glow_meta(852, 1150, 280, "#ffc86a", 0.25, 1.4), glow_meta(540, 1190, 520, "#ffb070", 0.05, 0.5)],
        "twinkles": twinkle_meta([(80, 160), (240, 340), (520, 120), (700, 260), (910, 140), (440, 480), (140, 560), (1000, 520)], "#fff6d8", 5),
    }


def frost(s, cx, cy, r, rot=0.0):
    for k in range(6):
        a = math.radians(rot + k * 60)
        s.line([(cx, cy), (cx + r * math.cos(a), cy + r * math.sin(a))], (235, 248, 255, 200), 3)
        for f in (0.35, 0.6, 0.82):
            bx, by = cx + r * f * math.cos(a), cy + r * f * math.sin(a)
            for da in (-35, 35):
                a2 = a + math.radians(da)
                s.line([(bx, by), (bx + r * 0.25 * math.cos(a2), by + r * 0.25 * math.sin(a2))], (235, 248, 255, 180), 2)


def scene_fenster():
    s = Scene([(0, "#1e1620"), (1, "#120c12")])
    # Aussicht
    wx0, wy0, wx1, wy1 = 110, 190, 970, 1300
    s.vband(wy0, wy1, "#0a1030", "#294676", wx0, wx1)
    s.stars(40, 760, seed=17)
    s.glow(760, 380, 260, "#cfe0ff", 0.4)
    s.circle(760, 380, 58, "#fdf6dc")
    s.poly([(wx0, 1080), (300, 1000), (520, 1060), (760, 990), (wx1, 1050), (wx1, wy1), (wx0, wy1)], "#cfdcf0")
    # Doerfchen in der Ferne
    for i, (x, w) in enumerate([(180, 70), (290, 90), (430, 70), (560, 100), (700, 80), (820, 90)]):
        y = 1090 + (i % 2) * 20
        s.rect(x, y - 54, x + w, y, "#33406a")
        s.poly([(x - 6, y - 54), (x + w / 2, y - 98), (x + w + 6, y - 54)], "#eef3fc")
        s.glow(x + w / 2, y - 26, 50, "#ffc766", 0.5)
        s.rect(x + w / 2 - 9, y - 38, x + w / 2 + 9, y - 14, "#ffcf70")
    s.fir(230, 1130, 240, 120, dark="#0f2c26", light="#2a6a50")
    s.fir(860, 1140, 280, 140, dark="#0f2c26", light="#2a6a50")
    s.snowflakes(90, seed=19, y0=wy0, y1=wy1)
    # Fensterrahmen
    fr = "#efe7d8"
    s.rect(wx0 - 44, wy0 - 44, wx1 + 44, wy0, fr)
    s.rect(wx0 - 44, wy1, wx1 + 44, wy1 + 44, fr)
    s.rect(wx0 - 44, wy0 - 44, wx0, wy1 + 44, fr)
    s.rect(wx1, wy0 - 44, wx1 + 44, wy1 + 44, fr)
    mx = (wx0 + wx1) // 2
    s.rect(mx - 18, wy0, mx + 18, wy1, fr)
    s.rect(wx0, 720, wx1, 756, fr)
    s.rect(wx0 - 44, wy0 - 44, wx1 + 44, wy0 - 30, (255, 255, 255, 60))
    # Eisblumen in den Ecken
    for cx, cy, r, rot in [(wx0 + 80, wy0 + 80, 170, 10), (wx1 - 80, wy0 + 80, 150, 25), (wx0 + 70, wy1 - 80, 150, 40), (wx1 - 70, wy1 - 70, 180, 5)]:
        s.soft(lambda cx=cx, cy=cy, r=r: s.circle(cx, cy, r * 0.9, (230, 245, 255, 70)), 30)
        frost(s, cx, cy, r, rot)
    # Fensterbank
    s.rrect(wx0 - 90, wy1 + 44, wx1 + 90, wy1 + 100, 12, "#8a5d36")
    s.rect(wx0 - 90, wy1 + 44, wx1 + 90, wy1 + 58, "#b07f4c")
    s.rect(0, wy1 + 100, W, H, "#1a1117")
    # Adventskranz mit vier Kerzen
    cy = wy1 + 20
    s.ellipse(540, cy + 10, 260, 60, "#1f6b36")
    s.ellipse(540, cy + 4, 190, 38, "#2a1a14")
    for i in range(34):
        a = i / 34 * 2 * math.pi
        s.ellipse(540 + 226 * math.cos(a), cy + 6 + 44 * math.sin(a), 30, 17, mix(rgb("#1f6b36"), rgb("#44a85f"), (i % 5) / 5))
    for i in range(9):
        a = (i + 0.3) / 9 * 2 * math.pi
        s.circle(540 + 226 * math.cos(a), cy + 6 + 44 * math.sin(a), 8, "#c0231f")
    flames = []
    for x in (360, 470, 610, 720):
        candle(s, x, cy + 2, 150, 30, "#c9262a")
        s.glow(x, cy - 190, 150, "#ffb347", 0.5)
        flames.append((x, cy - 164))
    # Laterne und Kugeln
    s.rrect(110, wy1 - 150, 220, wy1 + 44, 10, "#2a2320")
    s.rrect(124, wy1 - 134, 206, wy1 + 30, 6, "#ffd88a")
    s.glow(165, wy1 - 60, 150, "#ffb347", 0.55)
    s.rgrad_ellipse(860, wy1 - 8, 40, 40, "#f6d070", "#b8862a")
    s.rgrad_ellipse(930, wy1 + 12, 30, 30, "#ff8a8a", "#b8262a")
    s.vignette(0.55)
    return s, {
        "id": "fenster", "label": "Fenster mit Adventskranz", "music": 1, "snow": 0.7,
        "glows": [glow_meta(x, cy - 180, 150, "#ffb347", 0.35, 2.0 + i * 0.3) for i, x in enumerate((360, 470, 610, 720))] + [glow_meta(165, wy1 - 60, 150, "#ffb347", 0.2, 1.6)],
        "twinkles": twinkle_meta([(300, 300), (700, 260), (860, 440), (200, 540), (420, 200)], "#fff6d8", 5),
    }


def reindeer(s, x, y, sc=1.0, col=(14, 12, 28)):
    c = col
    s.ellipse(x, y, 62 * sc, 30 * sc, c)
    s.poly([(x + 44 * sc, y - 12 * sc), (x + 86 * sc, y - 52 * sc), (x + 108 * sc, y - 40 * sc), (x + 66 * sc, y + 4 * sc)], c)
    s.ellipse(x + 100 * sc, y - 46 * sc, 20 * sc, 14 * sc, c)
    for dx, up in ((-34, 1), (-12, -1), (20, 1), (42, -1)):
        s.line([(x + dx * sc, y + 14 * sc), (x + (dx + 22 * up) * sc, y + 58 * sc)], c, 8 * sc)
    s.line([(x + 98 * sc, y - 58 * sc), (x + 92 * sc, y - 100 * sc), (x + 112 * sc, y - 124 * sc)], c, 5 * sc)
    s.line([(x + 92 * sc, y - 100 * sc), (x + 74 * sc, y - 118 * sc)], c, 5 * sc)
    s.line([(x + 106 * sc, y - 58 * sc), (x + 120 * sc, y - 96 * sc), (x + 144 * sc, y - 108 * sc)], c, 5 * sc)
    s.line([(x - 60 * sc, y - 6 * sc), (x - 84 * sc, y - 18 * sc)], c, 7 * sc)


def scene_schlitten():
    s = Scene([(0, "#050a22"), (0.4, "#0e2a4a"), (0.7, "#1c4a62"), (1, "#0a1a2a")])
    # Polarlicht
    def aurora():
        for k, (col, y0, amp) in enumerate([((60, 255, 150, 120), 560, 120), ((120, 120, 255, 90), 640, 100), ((60, 230, 200, 80), 480, 140)]):
            top = [(x, y0 + amp * math.sin(x / 190 + k * 1.3) + 40 * math.sin(x / 60 + k)) for x in range(-40, W + 80, 40)]
            s.poly(top + [(W + 40, y0 - 520 - 80 * k), (-40, y0 - 520 - 80 * k)], col)
    s.soft(aurora, 70, 0.9)
    s.stars(150, 1000, seed=23)
    s.glow(650, 520, 460, "#cfe0ff", 0.5)
    s.circle(650, 520, 150, "#fdf6dc")
    s.circle(610, 480, 30, (225, 215, 190, 110))
    s.circle(700, 570, 22, (225, 215, 190, 110))
    s.circle(690, 470, 16, (225, 215, 190, 110))
    # Schlitten mit Rentieren (Silhouette vor dem Mond)
    c = (12, 10, 26)
    x0, y0 = 150, 600
    for i, (dx, dy) in enumerate([(0, 0), (150, -30), (300, -60)]):
        reindeer(s, x0 + dx - 50, y0 + dy, 0.72, c)
    s.line([(x0 + 340, y0 - 60), (x0 + 480, y0 - 10), (x0 + 520, y0 + 8)], c, 4)
    s.poly([(x0 + 470, y0 - 10), (x0 + 640, y0 - 36), (x0 + 700, y0 - 120), (x0 + 720, y0 - 120), (x0 + 700, y0 + 20), (x0 + 480, y0 + 40)], c)
    s.line([(x0 + 460, y0 + 62), (x0 + 700, y0 + 56), (x0 + 740, y0 + 20)], c, 8)
    s.ellipse(x0 + 600, y0 - 70, 36, 44, c)
    s.poly([(x0 + 582, y0 - 100), (x0 + 618, y0 - 100), (x0 + 604, y0 - 150)], c)
    s.ellipse(x0 + 560, y0 - 36, 40, 26, (30, 14, 18))
    # Daecher
    snow = (222, 232, 248)
    s.poly([(0, 1520), (180, 1440), (380, 1520), (560, 1400), (760, 1520), (900, 1430), (W, 1500), (W, H), (0, H)], "#0a1220")
    for x, w, h in [(20, 300, 240), (360, 360, 300), (740, 320, 260)]:
        yb = 1740
        s.rect(x, yb - h, x + w, yb + 300, "#101a30")
        s.poly([(x - 20, yb - h), (x + w / 2, yb - h - 140), (x + w + 20, yb - h)], "#1a2540")
        s.poly([(x - 24, yb - h + 6), (x + w / 2, yb - h - 152), (x + w + 24, yb - h + 6), (x + w + 10, yb - h + 22), (x + w / 2, yb - h - 118), (x - 10, yb - h + 22)], snow)
        s.rect(x + w * 0.72, yb - h - 160, x + w * 0.84, yb - h - 20, "#101a30")
        s.rect(x + w * 0.70, yb - h - 176, x + w * 0.86, yb - h - 156, snow)
        for k in range(2):
            wx = x + w * (0.3 + 0.4 * k)
            s.glow(wx, yb - h * 0.45, 70, "#ffbf5a", 0.5)
            s.rect(wx - 22, yb - h * 0.62, wx + 22, yb - h * 0.28, "#ffc766")
    s.snowflakes(70, seed=24)
    s.vignette(0.4)
    return s, {
        "id": "schlitten", "label": "Weihnachtsmann im Polarlicht", "music": 3, "snow": 0.6,
        "glows": [glow_meta(650, 520, 460, "#cfe0ff", 0.08, 0.7)],
        "twinkles": twinkle_meta([(120, 140), (330, 300), (520, 160), (880, 220), (970, 480), (210, 560), (60, 760), (900, 800), (480, 760)], "#fff6d8", 6),
    }


def mushroom(s, x, y, r, stem=38):
    s.rect(x - r * 0.22, y - stem, x + r * 0.22, y, "#f1e6cf")
    s.ellipse(x, y - stem, r, r * 0.72, "#d02a2a")
    s.rect(x - r, y - stem, x + r, y - stem + r * 0.25, "#f1e6cf")
    s.rect(x - r, y - stem, x + r, y - stem + 1, "#f1e6cf")
    for dx, dy, rr in [(-0.45, -0.3, 0.16), (0.1, -0.55, 0.14), (0.5, -0.2, 0.15), (-0.05, -0.1, 0.1)]:
        s.circle(x + dx * r, y - stem + dy * r, rr * r, "#fbf6ea")
    s.rect(x - r, y - stem - 2, x + r, y - stem + 4, "#d02a2a")


def scene_wichteltuer():
    s = Scene([(0, "#0b1d22"), (0.5, "#143238"), (1, "#0a1618")])
    rnd = random.Random(31)
    for _ in range(22):
        x, y = rnd.uniform(0, W), rnd.uniform(60, 1100)
        r = rnd.uniform(18, 60)
        col = rnd.choice([(255, 220, 130, 80), (140, 220, 255, 70), (255, 160, 140, 60)])
        s.soft(lambda x=x, y=y, r=r, col=col: s.circle(x, y, r, col), 16)
    # Baeume im Hintergrund
    for cx, w in [(80, 110), (250, 90), (860, 120), (1010, 100)]:
        s.rect(cx - w / 2, 0, cx + w / 2, 1500, "#10272b")
        s.rect(cx - w / 2, 0, cx - w / 2 + 14, 1500, (255, 255, 255, 14))
    # Grosser Stamm
    tx0, tx1 = 250, 830
    trunk = [(tx0 + 40, 0), (tx1 - 40, 0), (tx1 - 10, 700), (tx1 + 60, 1500), (tx0 - 60, 1500), (tx0 + 10, 700)]
    s.poly(trunk, "#5b3a22")
    s.poly([(tx0 + 40, 0), (tx0 + 260, 0), (tx0 + 230, 900), (tx0 - 20, 1500), (tx0 - 60, 1500), (tx0 + 10, 700)], (255, 220, 170, 26))
    s.poly([(tx1 - 40, 0), (tx1 - 160, 0), (tx1 - 130, 900), (tx1 + 40, 1500), (tx1 + 60, 1500), (tx1 - 10, 700)], (0, 0, 0, 60))
    rnd = random.Random(33)
    for _ in range(46):
        x = rnd.uniform(tx0 + 20, tx1 - 20)
        y = rnd.uniform(60, 1350)
        l = rnd.uniform(60, 190)
        s.line([(x, y), (x + rnd.uniform(-12, 12), y + l)], (30, 16, 8, 150), rnd.uniform(3, 6))
    # Wurzeln
    for pts in [[(tx0 - 40, 1460), (tx0 - 160, 1540), (tx0 - 330, 1560)], [(tx1 + 40, 1460), (tx1 + 150, 1540), (tx1 + 330, 1560)], [(540, 1480), (520, 1580), (470, 1640)]]:
        s.line(pts, "#4a2f1c", 54)
    # Wichtelhaus-Tuer
    dcx, dtop, dbot = 540, 1050, 1470
    s.glow(dcx, dbot - 100, 520, "#ffb347", 0.5)
    s.rrect(dcx - 150, dtop - 30, dcx + 150, dbot, 140, "#7a7a82")
    s.rrect(dcx - 130, dtop - 10, dcx + 130, dbot, 125, "#c9363a")
    s.rect(dcx - 130, dtop + 220, dcx + 130, dbot, "#c9363a")
    for k in range(3):
        s.line([(dcx - 120 + k * 80, dtop + 120), (dcx - 120 + k * 80, dbot)], (80, 10, 14, 120), 5)
    s.circle(dcx, dtop + 110, 46, "#ffe29a")
    s.circle(dcx, dtop + 110, 46, "#ffe29a")
    s.line([(dcx - 46, dtop + 110), (dcx + 46, dtop + 110)], "#7a4a1c", 5)
    s.line([(dcx, dtop + 64), (dcx, dtop + 156)], "#7a4a1c", 5)
    s.glow(dcx, dtop + 110, 150, "#ffd070", 0.8)
    s.circle(dcx + 78, dtop + 290, 14, "#e8b53a")
    s.rrect(dcx - 190, dbot, dcx + 190, dbot + 36, 8, "#8a8a92")
    s.rrect(dcx - 230, dbot + 36, dcx + 230, dbot + 76, 8, "#7a7a82")
    # Laterne am Ast
    s.line([(dcx + 200, 900), (dcx + 280, 900), (dcx + 280, 960)], "#2a2320", 6)
    s.rrect(dcx + 250, 960, dcx + 310, 1050, 8, "#2a2320")
    s.glow(dcx + 280, 1005, 220, "#ffb347", 0.7)
    s.rrect(dcx + 260, 972, dcx + 300, 1040, 6, "#ffd88a")
    # Pilze
    mushroom(s, 150, 1640, 80, 64)
    mushroom(s, 280, 1690, 52, 42)
    mushroom(s, 930, 1650, 74, 60)
    mushroom(s, 820, 1710, 46, 36)
    # Schnee am Boden
    snow_hill(s, [(0, 1560), (260, 1520), (540, 1560), (820, 1510), (W, 1550), (W, H), (0, H)], "#e9f0fb")
    snow_hill(s, [(0, 1700), (300, 1650), (640, 1710), (W, 1660), (W, H), (0, H)], "#f6faff")
    mushroom(s, 150, 1700, 70, 56)
    mushroom(s, 940, 1730, 64, 52)
    # Tannenzweige oben
    for x, y, d in [(0, 0, 1), (W, 0, -1)]:
        for k in range(5):
            s.poly([(x, y + 70 + k * 80), (x + d * (260 - k * 24), y + 20 + k * 80), (x + d * (170 - k * 16), y + 140 + k * 80)], mix(rgb("#0f3326"), rgb("#2a7a4a"), k / 6))
            s.poly([(x + d * (260 - k * 24), y + 20 + k * 80), (x + d * (180 - k * 16), y + 42 + k * 80), (x + d * (210 - k * 18), y + 60 + k * 80)], (245, 250, 255, 230))
    s.snowflakes(80, seed=35)
    # Glueh-Lichter
    fly = [(180, 900), (300, 1200), (820, 1250), (920, 820), (130, 1300), (980, 1150), (460, 800), (640, 760)]
    for x, y in fly:
        s.glow(x, y, 26, "#fff2a0", 0.9)
    s.vignette(0.5)
    return s, {
        "id": "wichteltuer", "label": "Wichteltür im Wald", "music": 1, "snow": 0.6,
        "glows": [glow_meta(540, 1370, 520, "#ffb347", 0.18, 2.0), glow_meta(dcx + 280, 1005, 220, "#ffb347", 0.2, 2.8)],
        "twinkles": twinkle_meta(fly, "#fff2a0", 10),
    }


def scene_schneemann():
    s = Scene([(0, "#0c1a3e"), (0.45, "#27508a"), (0.75, "#8fb4e0"), (1, "#dce8f8")])
    s.stars(80, 760, seed=41)
    s.glow(250, 330, 300, "#dbe8ff", 0.5)
    s.circle(250, 330, 78, "#fdf6dc")
    snow_hill(s, [(0, 1130), (260, 1060), (560, 1120), (860, 1050), (W, 1100), (W, 1300), (0, 1300)], "#c6d8ef")
    for cx, by, h, w in [(100, 1180, 380, 210), (260, 1200, 270, 150), (960, 1180, 420, 230), (820, 1210, 280, 160), (700, 1190, 190, 110)]:
        s.fir(cx, by, h, w, dark="#0f3326", light="#2c6a50")
    snow_hill(s, [(0, 1300), (300, 1240), (640, 1300), (W, 1250), (W, H), (0, H)], "#e9f1fc")
    snow_hill(s, [(0, 1560), (340, 1500), (700, 1570), (W, 1520), (W, H), (0, H)], "#f7fbff")
    s.soft(lambda: s.ellipse(540, 1720, 320, 60, (110, 135, 180, 140)), 22)
    # Schneemann
    def ball(x, y, r):
        s.rgrad_ellipse(x, y, r, r, "#ffffff", "#bcd0ea")
    ball(540, 1640, 230)
    ball(540, 1330, 175)
    ball(540, 1085, 128)
    # Arme
    s.line([(385, 1330), (250, 1250), (200, 1170)], "#5a3a22", 14)
    s.line([(250, 1250), (215, 1290)], "#5a3a22", 10)
    s.line([(695, 1330), (830, 1240), (880, 1160)], "#5a3a22", 14)
    s.line([(830, 1240), (870, 1274)], "#5a3a22", 10)
    # Knoepfe
    for y in (1270, 1340, 1410):
        s.circle(540, y, 14, "#2a2a32")
    for y in (1620, 1700):
        s.circle(540, y, 16, "#2a2a32")
    # Gesicht
    s.circle(505, 1060, 11, "#1c1c24")
    s.circle(575, 1060, 11, "#1c1c24")
    s.poly([(540, 1088), (700, 1112), (540, 1126)], "#f08a2c")
    for k in range(7):
        a = math.radians(30 + k * 20)
        s.circle(540 + 62 * math.cos(a) - 0, 1118 + 36 * math.sin(a) * 0.9, 5.5, "#1c1c24")
    # Schal
    s.rrect(396, 1180, 684, 1244, 30, "#c8222a")
    s.poly([(600, 1230), (666, 1230), (690, 1380), (630, 1390)], "#c8222a")
    for k in range(4):
        s.line([(412 + k * 78, 1186), (402 + k * 78, 1238)], "#f4f1e8", 8)
    # Muetze
    s.rrect(420, 975, 660, 1010, 10, "#2a2a34")
    s.rrect(452, 880, 628, 985, 22, "#2a2a34")
    s.rect(452, 940, 628, 966, "#c8222a")
    s.snowflakes(120, seed=43)
    s.vignette(0.3)
    return s, {
        "id": "schneemann", "label": "Schneemann", "music": 3, "snow": 1.0,
        "glows": [glow_meta(250, 330, 300, "#dbe8ff", 0.08, 0.7)],
        "twinkles": twinkle_meta([(100, 120), (400, 220), (620, 100), (780, 300), (940, 160), (520, 400), (60, 480), (960, 520)], "#fff6d8", 6),
    }


SCENES = [scene_kamin, scene_sessel, scene_baum, scene_winternacht, scene_dorf, scene_fenster, scene_schlitten, scene_wichteltuer, scene_schneemann]


def main():
    out = sys.argv[1]
    preview = "--preview" in sys.argv
    only = sys.argv[sys.argv.index("--only") + 1].split(",") if "--only" in sys.argv else None
    adir = out if preview else os.path.join(out, "assets", "backgrounds")
    os.makedirs(adir, exist_ok=True)
    metas = []
    for fn in SCENES:
        s, meta = fn()
        if only and meta["id"] not in only:
            continue
        if preview:
            s.img.convert("RGB").resize((W // 2, H // 2)).save(os.path.join(adir, meta["id"] + ".png"))
        else:
            s.save(os.path.join(adir, meta["id"] + ".webp"))
        meta["file"] = "backgrounds/%s.webp" % meta["id"]
        metas.append(meta)
        print("ok", meta["id"])
    if not preview and not only:
        json.dump({"designs": metas}, open(os.path.join(adir, "backgrounds.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)


main()
