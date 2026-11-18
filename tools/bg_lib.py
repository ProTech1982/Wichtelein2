"""Kleine Zeichenbibliothek fuer die Startbilder (Hintergruende) von Wichtelein2.

Alle Koordinaten sind Entwurfs-Einheiten (1080 x 1920, Hochformat); gezeichnet wird mit 2-fachem Supersampling.
Die Bilder sind eigene Illustrationen aus einfachen Formen, Farbverlaeufen und Leuchten (keine fremden Vorlagen).
"""
import math
import random

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

W, H = 1080, 1920
SS = 2


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Scene:
    def __init__(self, stops):
        """stops: Liste (Position 0..1, Farbe) fuer den senkrechten Hintergrund-Farbverlauf."""
        self.w, self.h = W * SS, H * SS
        self.img = Image.fromarray(self._vgrad(stops, self.w, self.h)).convert("RGB")
        self.d = ImageDraw.Draw(self.img, "RGBA")

    @staticmethod
    def _vgrad(stops, w, h):
        ys = np.linspace(0, 1, h)
        col = np.zeros((h, 3), dtype=np.float32)
        pos = [p for p, _ in stops]
        for c in range(3):
            col[:, c] = np.interp(ys, pos, [rgb(s)[c] if isinstance(s, str) else s[c] for _, s in stops])
        return np.repeat(col[:, None, :], w, axis=1).astype(np.uint8)

    # ---------------------------------------------------------------- Grundformen
    def _p(self, pts):
        return [(x * SS, y * SS) for x, y in pts]

    def poly(self, pts, fill):
        self.d.polygon(self._p(pts), fill=self._c(fill))

    def ellipse(self, cx, cy, rx, ry, fill, outline=None, width=0):
        self.d.ellipse([(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=self._c(fill) if fill else None,
                       outline=self._c(outline) if outline else None, width=int(width * SS))

    def circle(self, cx, cy, r, fill):
        self.ellipse(cx, cy, r, r, fill)

    def rect(self, x0, y0, x1, y1, fill):
        self.d.rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], fill=self._c(fill))

    def rrect(self, x0, y0, x1, y1, r, fill, outline=None, width=0):
        self.d.rounded_rectangle([x0 * SS, y0 * SS, x1 * SS, y1 * SS], radius=r * SS, fill=self._c(fill) if fill else None,
                                 outline=self._c(outline) if outline else None, width=int(width * SS))

    def line(self, pts, fill, width=2):
        self.d.line(self._p(pts), fill=self._c(fill), width=max(1, int(width * SS)), joint="curve")

    @staticmethod
    def _c(c):
        if isinstance(c, str):
            return rgb(c) + (255,)
        if len(c) == 3:
            return tuple(c) + (255,)
        return tuple(c)

    def arc_poly(self, cx, cy, rx, ry, a0, a1, n=40):
        return [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]

    # ---------------------------------------------------------------- Verlaeufe und Leuchten
    def glow(self, x, y, r, color, a=0.8, power=2.0):
        n = max(4, int(2 * r * SS))
        yy, xx = np.mgrid[-1:1:n * 1j, -1:1:n * 1j]
        m = np.clip(1 - np.sqrt(xx ** 2 + yy ** 2), 0, 1) ** power * a
        layer = Image.new("RGBA", (n, n), rgb(color) + (0,) if isinstance(color, str) else tuple(color) + (0,))
        layer.putalpha(Image.fromarray((m * 255).astype(np.uint8)))
        self._paste(layer, int(x * SS - n / 2), int(y * SS - n / 2))

    def _paste(self, layer, x, y):
        """Legt eine RGBA-Ebene mit ihrem Alphakanal ueber das Bild (Ausschnitte ausserhalb werden abgeschnitten)."""
        self.img.paste(layer.convert("RGB"), (x, y), layer.split()[3])

    def vband(self, y0, y1, c0, c1, x0=0, x1=W, a=1.0):
        """Rechteck mit senkrechtem Farbverlauf."""
        h = int((y1 - y0) * SS)
        w = int((x1 - x0) * SS)
        arr = self._vgrad([(0, c0), (1, c1)], w, h)
        layer = Image.fromarray(arr).convert("RGBA")
        if a < 1:
            layer.putalpha(int(255 * a))
        self._paste(layer, int(x0 * SS), int(y0 * SS))

    def rgrad_ellipse(self, cx, cy, rx, ry, c_in, c_out, a=1.0):
        """Ellipse mit Farbverlauf von innen nach aussen (Kugeln, Baumschmuck)."""
        n = int(2 * max(rx, ry) * SS)
        yy, xx = np.mgrid[-1:1:n * 1j, -1:1:n * 1j]
        d = np.sqrt((xx * max(rx, ry) / rx) ** 2 + (yy * max(rx, ry) / ry) ** 2)
        t = np.clip(d, 0, 1)
        ci, co = np.array(rgb(c_in) if isinstance(c_in, str) else c_in, dtype=np.float32), np.array(rgb(c_out) if isinstance(c_out, str) else c_out, dtype=np.float32)
        col = ci[None, None, :] * (1 - t[..., None]) + co[None, None, :] * t[..., None]
        alpha = ((d <= 1).astype(np.float32) * a * 255)
        arr = np.dstack([col, alpha]).astype(np.uint8)
        layer = Image.fromarray(arr, "RGBA")
        self._paste(layer, int((cx - max(rx, ry)) * SS), int((cy - max(rx, ry)) * SS))

    def soft(self, draw_fn, blur, a=1.0):
        """Zeichnet in eine eigene Ebene, weichzeichnet sie und legt sie darueber (Nebel, Schein, Schatten)."""
        layer = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        saved = self.d
        self.d = ImageDraw.Draw(layer, "RGBA")
        draw_fn()
        self.d = saved
        layer = layer.filter(ImageFilter.GaussianBlur(blur * SS))
        if a < 1:
            r, g, b, al = layer.split()
            layer = Image.merge("RGBA", (r, g, b, al.point(lambda v: int(v * a))))
        self._paste(layer, 0, 0)

    def vignette(self, strength=0.55):
        yy, xx = np.mgrid[-1:1:self.h * 1j, -1:1:self.w * 1j]
        d = np.clip((np.sqrt(xx ** 2 * 0.8 + yy ** 2 * 0.55) - 0.35) / 0.85, 0, 1) ** 1.6 * strength
        layer = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        layer.putalpha(Image.fromarray((d * 255).astype(np.uint8)))
        self._paste(layer, 0, 0)

    # ---------------------------------------------------------------- Bausteine
    def stars(self, n, y_max, seed=1, size=(1.0, 3.2), color="#fff6d8"):
        rnd = random.Random(seed)
        for _ in range(n):
            x, y = rnd.uniform(0, W), rnd.uniform(0, y_max)
            r = rnd.uniform(*size)
            a = int(rnd.uniform(120, 255))
            self.ellipse(x, y, r, r, rgb(color) + (a,))

    def snowflakes(self, n, seed=2, y0=0, y1=H, size=(1.5, 5.0), alpha=(120, 230)):
        rnd = random.Random(seed)
        for _ in range(n):
            x, y = rnd.uniform(0, W), rnd.uniform(y0, y1)
            r = rnd.uniform(*size)
            self.ellipse(x, y, r, r, (255, 255, 255, int(rnd.uniform(*alpha))))

    def star5(self, cx, cy, r, fill, rot=-90, inner=0.45):
        pts = []
        for i in range(10):
            rr = r if i % 2 == 0 else r * inner
            a = math.radians(rot + i * 36)
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        self.poly(pts, fill)

    def fir(self, cx, base_y, h, w, dark="#15432b", light="#2a7a48", snow=True, layers=5):
        """Tanne aus uebereinanderliegenden Dreiecken mit Schneehauben."""
        for i in range(layers):
            t = i / layers
            y_top = base_y - h + h * 0.62 * t
            lw = w * (0.35 + 0.65 * (i + 1) / layers)
            y_b = base_y - h * 0.12 - h * 0.78 * (1 - (i + 1) / layers)
            col = mix(rgb(dark), rgb(light), 0.25 + 0.6 * t)
            self.poly([(cx, y_top), (cx - lw / 2, y_b), (cx + lw / 2, y_b)], col)
            if snow:
                self.poly([(cx, y_top), (cx - lw * 0.30, y_top + (y_b - y_top) * 0.38), (cx - lw * 0.12, y_top + (y_b - y_top) * 0.30),
                           (cx + lw * 0.05, y_top + (y_b - y_top) * 0.42), (cx + lw * 0.30, y_top + (y_b - y_top) * 0.38)], (245, 250, 255, 235))
        self.rect(cx - w * 0.05, base_y - h * 0.13, cx + w * 0.05, base_y, "#4a2f1d")

    def gnome(self, cx, base_y, s=1.0, sitting=False, book=False):
        """Wichtel: rote Zipfelmuetze mit Bommel, weisser Bart, blaue Jacke, braune Hose."""
        # Beine/Schuhe
        if sitting:
            self.rrect(cx - 70 * s, base_y - 70 * s, cx + 40 * s, base_y - 30 * s, 18 * s, "#5a3a24")
            self.rrect(cx + 20 * s, base_y - 74 * s, cx + 100 * s, base_y - 40 * s, 16 * s, "#6b4429")
            self.ellipse(cx + 106 * s, base_y - 52 * s, 26 * s, 18 * s, "#8a4b1e")
        else:
            self.rrect(cx - 36 * s, base_y - 80 * s, cx - 6 * s, base_y - 10 * s, 12 * s, "#5a3a24")
            self.rrect(cx + 6 * s, base_y - 80 * s, cx + 36 * s, base_y - 10 * s, 12 * s, "#5a3a24")
            self.ellipse(cx - 26 * s, base_y - 6 * s, 24 * s, 12 * s, "#8a4b1e")
            self.ellipse(cx + 26 * s, base_y - 6 * s, 24 * s, 12 * s, "#8a4b1e")
        by = base_y - (110 if sitting else 170) * s
        # Koerper
        self.rrect(cx - 64 * s, by - 20 * s, cx + 64 * s, by + 120 * s, 40 * s, "#31587a")
        self.rect(cx - 64 * s, by + 56 * s, cx + 64 * s, by + 66 * s, "#20384f")
        # Kopf
        hy = by - 70 * s
        self.ellipse(cx, hy, 50 * s, 46 * s, "#f0c8a4")
        # Bart
        self.poly([(cx - 52 * s, hy + 6 * s), (cx + 52 * s, hy + 6 * s), (cx + 40 * s, hy + 80 * s), (cx, hy + 120 * s), (cx - 40 * s, hy + 80 * s)], "#f4f1ea")
        self.ellipse(cx - 26 * s, hy + 6 * s, 28 * s, 22 * s, "#f4f1ea")
        self.ellipse(cx + 26 * s, hy + 6 * s, 28 * s, 22 * s, "#f4f1ea")
        # Nase und Augen
        self.ellipse(cx, hy + 6 * s, 13 * s, 11 * s, "#e8a58a")
        self.ellipse(cx - 20 * s, hy - 12 * s, 4.5 * s, 5.5 * s, "#2a2a2a")
        self.ellipse(cx + 20 * s, hy - 12 * s, 4.5 * s, 5.5 * s, "#2a2a2a")
        # Muetze
        self.poly([(cx - 56 * s, hy - 18 * s), (cx + 56 * s, hy - 18 * s), (cx + 20 * s, hy - 150 * s), (cx + 62 * s, hy - 205 * s), (cx + 10 * s, hy - 170 * s), (cx - 24 * s, hy - 130 * s)], "#c8221f")
        self.poly([(cx - 56 * s, hy - 18 * s), (cx + 56 * s, hy - 18 * s), (cx + 50 * s, hy - 38 * s), (cx - 50 * s, hy - 38 * s)], "#a31a19")
        self.circle(cx + 62 * s, hy - 205 * s, 17 * s, "#fffdf8")
        # Arme
        if book:
            self.rrect(cx - 84 * s, by + 20 * s, cx - 40 * s, by + 80 * s, 20 * s, "#31587a")
            self.rrect(cx + 40 * s, by + 20 * s, cx + 84 * s, by + 80 * s, 20 * s, "#31587a")
            self.poly([(cx - 70 * s, by + 36 * s), (cx, by + 52 * s), (cx + 70 * s, by + 36 * s), (cx + 70 * s, by + 110 * s), (cx, by + 126 * s), (cx - 70 * s, by + 110 * s)], "#7a2a2a")
            self.poly([(cx - 62 * s, by + 42 * s), (cx - 4 * s, by + 56 * s), (cx - 4 * s, by + 116 * s), (cx - 62 * s, by + 102 * s)], "#f6efe0")
            self.poly([(cx + 62 * s, by + 42 * s), (cx + 4 * s, by + 56 * s), (cx + 4 * s, by + 116 * s), (cx + 62 * s, by + 102 * s)], "#efe6d2")
        else:
            self.ellipse(cx - 70 * s, by + 40 * s, 18 * s, 44 * s, "#31587a")
            self.ellipse(cx + 70 * s, by + 40 * s, 18 * s, 44 * s, "#31587a")
            self.circle(cx - 74 * s, by + 82 * s, 14 * s, "#f0c8a4")
            self.circle(cx + 74 * s, by + 82 * s, 14 * s, "#f0c8a4")

    def save(self, path, quality=82):
        out = self.img.convert("RGB").resize((W, H), Image.LANCZOS)
        out.save(path, "WEBP", quality=quality, method=6)
        return out
