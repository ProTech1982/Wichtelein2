"""Rendert die Wichtel-Sprites (Bilder + Farbmasken) fuer Wichtelein2.

Aufruf:
  blender --factory-startup -b -P render_sprites.py -- <Ausgabeordner> [--views side,top] [--anims walk,sweep]
                                                       [--dirs 0,1,2,3,4,5,6,7] [--res 448]

Je Ansicht (side = seitlich, top = von oben), Animation, Bild und Blickrichtung entstehen zwei PNG:
  <view>/<anim>_<bild>_<richtung>.png    Wichtel mit Textur und Licht
  <view>/<anim>_<bild>_<richtung>_m.png  Farbmaske (rot Muetze, gruen Jacke, blau Hose, gelb Schuhe)
Richtungen: 0 = zum Betrachter, dann im Uhrzeigersinn je 45 Grad (2 = rechts, 4 = weg, 6 = links).
"""
import math
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bpy
from mathutils import Matrix, Vector

import wichtel_lib as L
import wichtel_poses as P

argv = sys.argv[sys.argv.index("--") + 1:]
out_dir = argv[0]


def opt(name, default):
    return argv[argv.index(name) + 1] if name in argv else default


VIEWS = opt("--views", "side,top").split(",")
ANIMS = opt("--anims", ",".join(P.ANIMS)).split(",")
DIRS = [int(x) for x in opt("--dirs", "0,1,2,3,4,5,6,7").split(",")]
RES = int(opt("--res", "448"))
ELEV = {"side": 10.0, "top": 68.0}
SCALE = 1.40               # Meter ueber die Zellbreite
FEET = (RES * 0.5, RES * 0.80)

L.reset()
arm, mesh = L.import_wichtel()
print("REGIONS", L.build_region_attribute(mesh))
mask = L.make_mask_object(mesh, arm)

# --- Besen
before = set(bpy.data.objects)
bpy.ops.import_scene.fbx(filepath=L.BESEN_FBX())
broom = next(o for o in bpy.data.objects if o not in before and o.type == "MESH")
for o in list(bpy.data.objects):
    if o not in before and o is not broom:
        bpy.data.objects.remove(o)
mod = broom.modifiers.new("dec", "DECIMATE")
mod.ratio = 0.05
bpy.context.view_layer.objects.active = broom
broom.select_set(True)
bpy.ops.object.modifier_apply(modifier="dec")
bb = [Vector(c) for c in broom.bound_box]
cx = sum(v.x for v in bb) / 8
cy = sum(v.y for v in bb) / 8
zmin = min(v.z for v in bb)
broom.data.transform(Matrix.Translation((-cx, -cy, -zmin)))
BROOM_SCALE = 0.68
broom.scale = (BROOM_SCALE,) * 3
BROOM_LEN = 0.984 * BROOM_SCALE
print("BROOM verts", len(broom.data.vertices))

# --- Wurzel zum Drehen in die 8 Blickrichtungen
root = bpy.data.objects.new("root", None)
bpy.context.scene.collection.objects.link(root)
for o in (arm, broom):
    if o.parent is None:
        o.parent = root
        o.matrix_parent_inverse = Matrix.Identity(4)
if mesh.parent is None:
    mesh.parent = root
if mask.parent is None:
    mask.parent = root

posing = L.Posing(arm)
sc, cam = L.setup_scene(RES)


def hand_point(side):
    pb = arm.pose.bones["mixamorig:%sHand" % side]
    return pb.head + (pb.tail - pb.head) * 0.45


def broom_matrix(b):
    fx, fy, fz = b["foot"]
    return (Matrix.Translation((fx, fy, fz)) @ Matrix.Rotation(math.radians(b["yaw"]), 4, "Z")
            @ Matrix.Rotation(math.radians(-b["tilt"]), 4, "X")
            @ Matrix.Scale(BROOM_SCALE, 4))


def solve_arm(spec, side, target):
    """Mini-IK: stellt Arm- und Unterarmwinkel so ein, dass die Hand am Ziel liegt (Koordinatenabstieg)."""
    sign = 1.0 if side == "Left" else -1.0
    params = [spec[side + "Arm"][0][1], 0.0, 0.0, spec[side + "ForeArm"][0][1], 0.0]
    def build(p):
        spec[side + "Arm"] = [("y", p[0]), ("x", p[1]), ("z", p[2])]
        spec[side + "ForeArm"] = [("y", p[3]), ("x", p[4])]
    def err(p):
        build(p)
        posing.apply(spec, hips)
        return (hand_point(side) - target).length
    best = err(params)
    step = 20.0
    for _ in range(26):
        improved = False
        for i in range(len(params)):
            for d in (step, -step):
                q = list(params)
                q[i] += d
                e = err(q)
                if e < best - 1e-6:
                    best, params, improved = e, q, True
        if not improved:
            step *= 0.6
    build(params)
    posing.apply(spec, hips)
    return best


frames = []
for a in ANIMS:
    for i in range(P.ANIMS[a]):
        frames.append((a, i))

t0 = time.time()
n_render = 0
for a, i in frames:
    spec, hips, br = P.GENERATORS[a](i)
    posing.apply(spec, hips)
    if br is not None:
        m = broom_matrix(br)
        broom.matrix_world = m
        broom.hide_render = False
        lh = m @ Vector((0, 0, br["hands"][0] * BROOM_LEN / BROOM_SCALE))
        rh = m @ Vector((0, 0, br["hands"][1] * BROOM_LEN / BROOM_SCALE))
        e1 = solve_arm(spec, "Left", lh)
        e2 = solve_arm(spec, "Right", rh)
        print("IK", a, i, round(e1, 3), round(e2, 3))
    else:
        broom.hide_render = True
    for view in VIEWS:
        L.aim_camera(cam, ELEV[view], SCALE, FEET, RES)
        os.makedirs(os.path.join(out_dir, view), exist_ok=True)
        for d in DIRS:
            root.rotation_euler = (0, 0, math.radians(45 * d))
            bpy.context.view_layer.update()
            for tag, vis in (("", (True, False, True)), ("_m", (False, True, False))):
                mesh.hide_render = not vis[0]
                mask.hide_render = not vis[1]
                if br is not None:
                    broom.hide_render = not vis[2]
                sc.render.filepath = os.path.join(out_dir, view, "%s_%02d_%d%s.png" % (a, i, d, tag))
                bpy.ops.render.render(write_still=True)
                n_render += 1
print("FERTIG", n_render, "Bilder in", round(time.time() - t0), "s")
