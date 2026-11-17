"""Posen und Animationen des Wichtels (Laufen, Schleichen, Rennen, Fegen, Erschrecken, Ducken).

Alle Funktionen liefern (spec, hips_move, broom) fuer wichtel_lib.Posing.apply:
  spec        {Knochen: Drehung}  (Grad, Armatur-Achsen; vorwaerts = -Y, links des Wichtels = +X)
  hips_move   Verschiebung der Huefte in Metern
  broom       None oder Besen-Zielpose (Fusspunkt, Neigung, Gier) -> Haende werden per Mini-IK dorthin gesetzt
"""
import math

S = lambda deg: math.sin(math.radians(deg))
C = lambda deg: math.cos(math.radians(deg))


def arms_down(spec, spread=0.0, elbow=0.0):
    """Arme locker neben dem Koerper (Ausgangslage ist 'Haende hoch')."""
    spec["LeftArm"] = [("y", 78 - spread)]
    spec["RightArm"] = [("y", -78 + spread)]
    spec["LeftForeArm"] = [("y", 32), ("x", -elbow)]
    spec["RightForeArm"] = [("y", -32), ("x", -elbow)]


ANIMS = {
    # name: (Bilder, Schleife)
    "idle": 1,
    "walk": 8,
    "sneak": 8,
    "run": 8,
    "sweep": 8,
    "scare": 6,
    "duck": 4,
}


def idle(i=0):
    spec = {}
    arms_down(spec)
    return spec, (0, 0, 0), None


def walk(i):
    p = i / ANIMS["walk"] * 360.0
    s, c = S(p), C(p)
    spec = {}
    arms_down(spec, elbow=12)
    spec["LeftUpLeg"] = [("x", -30 * s)]
    spec["RightUpLeg"] = [("x", 30 * s)]
    spec["LeftLeg"] = [("x", 32 * max(0.0, c))]
    spec["RightLeg"] = [("x", 32 * max(0.0, -c))]
    spec["LeftFoot"] = [("x", -8 * s)]
    spec["RightFoot"] = [("x", 8 * s)]
    # Arme gegen die Beine schwingen
    spec["LeftArm"] += [("x", 24 * s)]
    spec["RightArm"] += [("x", -24 * s)]
    spec["Hips"] = [("z", 5 * s), ("x", 3)]
    spec["Spine1"] = [("z", -6 * s)]
    spec["Head"] = [("z", 3 * s)]
    bob = -0.012 + 0.012 * abs(c) * 0 - 0.014 * (1 - abs(S(2 * p)) ** 0.5) * 0
    bob = 0.010 * C(2 * p) - 0.010
    return spec, (0, 0, bob), None


def run(i):
    """Panik-Lauf: Arme hoch und wedeln, vorgelehnt."""
    p = i / ANIMS["run"] * 360.0
    s, c = S(p), C(p)
    spec = {}
    spec["LeftUpLeg"] = [("x", -45 * s)]
    spec["RightUpLeg"] = [("x", 45 * s)]
    spec["LeftLeg"] = [("x", 55 * max(0.0, c))]
    spec["RightLeg"] = [("x", 55 * max(0.0, -c))]
    spec["LeftFoot"] = [("x", -12 * s)]
    spec["RightFoot"] = [("x", 12 * s)]
    # Arme oben (Ausgangslage), wedeln abwechselnd
    spec["LeftArm"] = [("y", -10), ("x", -8 * s), ("z", 6 * s)]
    spec["RightArm"] = [("y", 10), ("x", 8 * s), ("z", -6 * s)]
    spec["LeftForeArm"] = [("x", -14 * s)]
    spec["RightForeArm"] = [("x", 14 * s)]
    spec["Hips"] = [("z", 7 * s), ("x", 10)]
    spec["Spine"] = [("x", 6)]
    spec["Spine1"] = [("z", -8 * s)]
    spec["Head"] = [("x", -8), ("z", 4 * s)]
    bob = 0.018 * C(2 * p) - 0.012
    return spec, (0, 0, bob), None


def crouch_legs(spec, depth):
    """Hocke: depth 0 (steht) .. 1 (tief)."""
    spec["LeftUpLeg"] = [("x", -62 * depth)]
    spec["RightUpLeg"] = [("x", -62 * depth)]
    spec["LeftLeg"] = [("x", 100 * depth)]
    spec["RightLeg"] = [("x", 100 * depth)]
    spec["LeftFoot"] = [("x", -38 * depth)]
    spec["RightFoot"] = [("x", -38 * depth)]
    spec["Spine"] = [("x", 14 * depth)]
    spec["Spine1"] = [("x", 10 * depth)]
    spec["Head"] = [("x", -12 * depth)]
    return -0.105 * depth


def sneak(i):
    """Geduckter Schleichgang."""
    p = i / ANIMS["sneak"] * 360.0
    s, c = S(p), C(p)
    spec = {}
    drop = crouch_legs(spec, 0.8)
    spec["LeftUpLeg"] = [("x", -38 - 22 * s)]
    spec["RightUpLeg"] = [("x", -38 + 22 * s)]
    spec["LeftLeg"] = [("x", 62 + 20 * max(0.0, c))]
    spec["RightLeg"] = [("x", 62 + 20 * max(0.0, -c))]
    # Arme nach vorn, tastend
    spec["LeftArm"] = [("y", 55), ("x", -28 + 10 * s)]
    spec["RightArm"] = [("y", -55), ("x", -28 - 10 * s)]
    spec["LeftForeArm"] = [("y", 40), ("x", -30)]
    spec["RightForeArm"] = [("y", -40), ("x", -30)]
    spec["Hips"] = [("z", 4 * s)]
    spec["Spine1"] = [("z", -5 * s), ("x", 10)]
    spec["Head"] = [("x", -22), ("z", 8 * s)]
    bob = drop + 0.008 * C(2 * p)
    return spec, (0, 0, bob), None


def duck(i):
    """Ducken: 0 steht, 3 tief gehockt, Arme um die Knie."""
    t = i / (ANIMS["duck"] - 1)
    spec = {}
    drop = crouch_legs(spec, 1.2 * t)
    arms_down(spec, elbow=20 * t)
    spec["LeftArm"] = [("y", 62 - 20 * t), ("x", -45 * t)]
    spec["RightArm"] = [("y", -62 + 20 * t), ("x", -45 * t)]
    spec["LeftForeArm"] = [("y", 38), ("x", -40 * t)]
    spec["RightForeArm"] = [("y", -38), ("x", -40 * t)]
    spec["Head"] = [("x", -12 * t - 10 * t)]
    return spec, (0, 0, drop), None


def scare(i):
    """Erschrecken: Zusammenzucken, Haende hoch neben den Kopf, Beben."""
    n = ANIMS["scare"]
    spec = {}
    hop = 0.0
    if i == 0:  # kurz vor dem Schreck, Arme unten
        arms_down(spec)
        return spec, (0, 0, 0), None
    if i == 1:  # Sprung zurueck
        hop = 0.045
        spec["Spine"] = [("x", -10)]
        spec["Spine1"] = [("x", -8)]
        spec["Head"] = [("x", -14)]
        spec["LeftArm"] = [("y", -20), ("x", 20)]
        spec["RightArm"] = [("y", 20), ("x", 20)]
        spec["LeftForeArm"] = [("y", 10)]
        spec["RightForeArm"] = [("y", -10)]
        spec["LeftUpLeg"] = [("x", 14), ("z", -6)]
        spec["RightUpLeg"] = [("x", -10), ("z", 6)]
        spec["LeftLeg"] = [("x", 20)]
        spec["RightLeg"] = [("x", 10)]
        return spec, (0, 0.03, hop), None
    # Haende hoch (Ausgangslage), Knie leicht gebeugt, Beben
    k = (i - 2) / max(1, n - 3)
    sh = S(k * 720)
    spec["LeftArm"] = [("y", -18), ("x", -22), ("z", 3 * sh)]
    spec["RightArm"] = [("y", 18), ("x", -22), ("z", -3 * sh)]
    spec["LeftForeArm"] = [("y", 6), ("x", -10 + 4 * sh)]
    spec["RightForeArm"] = [("y", -6), ("x", -10 - 4 * sh)]
    spec["Spine"] = [("x", -6)]
    spec["Spine1"] = [("x", -6)]
    spec["Head"] = [("x", -10), ("z", 5 * sh)]
    spec["LeftUpLeg"] = [("x", -16), ("z", -4)]
    spec["RightUpLeg"] = [("x", -16), ("z", 4)]
    spec["LeftLeg"] = [("x", 28)]
    spec["RightLeg"] = [("x", 28)]
    spec["LeftFoot"] = [("x", -12)]
    spec["RightFoot"] = [("x", -12)]
    return spec, (0, 0.015, -0.02 + 0.01 * sh), None


def sweep(i):
    """Fegen: Besen vor dem Wichtel, Haende per Mini-IK am Stiel, Schwung nach links und rechts."""
    n = ANIMS["sweep"]
    p = i / n * 360.0
    s = S(p)
    spec = {}
    arms_down(spec)
    spec["Hips"] = [("z", -4 * s)]
    spec["Spine"] = [("z", 12 * s), ("x", 12)]
    spec["Spine1"] = [("z", 10 * s), ("x", 10)]
    spec["Head"] = [("x", 12), ("z", -6 * s)]
    spec["LeftUpLeg"] = [("x", -6), ("z", -3)]
    spec["RightUpLeg"] = [("x", 6), ("z", 3)]
    spec["LeftLeg"] = [("x", 8)]
    spec["RightLeg"] = [("x", 4)]
    # Besen: Fusspunkt vor dem Wichtel, schwingt seitlich; beim Rueckschwung leicht angehoben
    broom = {
        "foot": (0.07 * s, -0.27, 0.010 + 0.012 * max(0.0, C(p))),
        "tilt": 24.0,           # Stiel neigt sich zum Wichtel
        "yaw": -12 * s,         # Schwung um die Hochachse
        "hands": (0.64, 0.50),  # Stielstellen (Anteil der Laenge) fuer linke/rechte Hand
    }
    return spec, (0, 0, 0), broom


GENERATORS = {
    "idle": idle, "walk": walk, "sneak": sneak, "run": run,
    "sweep": sweep, "scare": scare, "duck": duck,
}
