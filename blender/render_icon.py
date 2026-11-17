"""Rendert das App-Symbol (Wichtelkopf mit Muetze) fuer Wichtelein2.

Aufruf: blender --factory-startup -b -P render_icon.py -- <Ausgabe.png>
Ergebnis: 432 x 432 px, transparenter Hintergrund (Vordergrund eines adaptiven Symbols; Sicherheitszone = mittlere 61 %).
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import wichtel_lib as L
import wichtel_poses as P

out = sys.argv[sys.argv.index("--") + 1]
L.reset()
arm, mesh = L.import_wichtel()
posing = L.Posing(arm)
spec, hips, _ = P.idle(0)
posing.apply(spec, hips)
sc, cam = L.setup_scene(432)
cam.data.ortho_scale = 1.05
cam.location = (0.0, -6.0, 0.64)
cam.rotation_euler = (math.radians(90), 0, 0)
sc.render.filepath = out
bpy.ops.render.render(write_still=True)
print("FERTIG Symbol")
