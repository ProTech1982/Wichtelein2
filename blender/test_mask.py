"""Pruefskript: Regionsmaske des Wichtels (Vorder- und Seitenansicht) -> PNG-Kontaktbogen."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
import wichtel_lib as L

out = sys.argv[sys.argv.index("--") + 1]
L.reset()
arm, mesh = L.import_wichtel()
print("REGIONS", L.build_region_attribute(mesh))
mask = L.make_mask_object(mesh, arm)
sc, cam = L.setup_scene(448)
L.aim_camera(cam, 10, 1.3, (224, 400), 448)
for name, obj_vis in (("beauty", (True, False)), ("mask", (False, True))):
    mesh.hide_render = not obj_vis[0]
    mask.hide_render = not obj_vis[1]
    sc.render.filepath = out + "_" + name + ".png"
    bpy.ops.render.render(write_still=True)
