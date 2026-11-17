"""Rendert die Innenraeume hinter der Wichteltuer (Stube, Hoehle) fuer Wichtelein2.

Aufruf: blender --factory-startup -b -P render_interior.py -- <Ausgabeordner>
Ergebnis: interior_stube.png, interior_hoehle.png (je 512 x 512, ohne Transparenz)
"""
import math
import os
import random
import sys

import bpy
import bmesh

out = sys.argv[sys.argv.index("--") + 1]
os.makedirs(out, exist_ok=True)


def engine_id():
    ids = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    return next((i for i in ids if "EEVEE" in i), ids[0])


def mat(color, rough=0.7, emit=None, strength=0.0, metal=0.0):
    m = bpy.data.materials.new("m")
    m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = color
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if emit:
        b.inputs["Emission Color"].default_value = emit
        b.inputs["Emission Strength"].default_value = strength
    return m


def box(name, loc, size, m):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    ob.scale = size
    ob.data.materials.append(m)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def sphere(name, loc, r, m, scale=(1, 1, 1)):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=24, v_segments=14, radius=r)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    ob.location = loc
    ob.scale = scale
    ob.data.materials.append(m)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def light(kind, loc, energy, color, size=0.4):
    ld = bpy.data.lights.new("l", kind)
    ld.energy = energy
    ld.color = color
    if hasattr(ld, "shadow_soft_size"):
        ld.shadow_soft_size = size
    ob = bpy.data.objects.new("l", ld)
    ob.location = loc
    bpy.context.scene.collection.objects.link(ob)
    return ob


def scene_base(world_color):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc = bpy.context.scene
    sc.render.engine = engine_id()
    sc.render.resolution_x = sc.render.resolution_y = 512
    sc.render.image_settings.file_format = "PNG"
    try:
        sc.view_settings.view_transform = "Standard"
    except TypeError:
        pass
    w = bpy.data.worlds.new("w")
    w.use_nodes = True
    sc.world = w
    bg = w.node_tree.nodes["Background"]
    bg.inputs[0].default_value = world_color
    bg.inputs[1].default_value = 0.4
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.lens = 28
    cam.location = (0, -3.2, 1.35)
    cam.rotation_euler = (math.radians(86), 0, 0)
    sc.collection.objects.link(cam)
    sc.camera = cam
    return sc


def stube():
    sc = scene_base((0.05, 0.04, 0.03, 1))
    wood_w = mat((0.45, 0.27, 0.13, 1))
    wood_f = mat((0.33, 0.19, 0.09, 1))
    box("floor", (0, 1.2, -0.05), (4, 5, 0.1), wood_f)
    box("back", (0, 3.0, 1.5), (4, 0.1, 3), wood_w)
    box("left", (-2, 1.2, 1.5), (0.1, 5, 3), wood_w)
    box("right", (2, 1.2, 1.5), (0.1, 5, 3), wood_w)
    box("ceil", (0, 1.2, 3.05), (4, 5, 0.1), wood_w)
    # Fenster mit Mondlicht
    box("window", (0.9, 2.93, 1.7), (0.9, 0.05, 0.9), mat((0.1, 0.15, 0.35, 1), emit=(0.35, 0.5, 0.95, 1), strength=2.0))
    box("winframe", (0.9, 2.9, 1.7), (1.05, 0.04, 1.05), wood_f)
    # Teppich, Bett, Regal, Tisch
    box("rug", (0.2, 1.4, 0.01), (1.9, 1.5, 0.02), mat((0.55, 0.12, 0.1, 1)))
    box("bed", (-1.2, 2.3, 0.3), (1.2, 1.2, 0.5), mat((0.3, 0.45, 0.65, 1)))
    box("pillow", (-1.2, 2.75, 0.62), (0.9, 0.3, 0.14), mat((0.95, 0.92, 0.85, 1)))
    box("shelf", (-0.4, 2.9, 1.9), (1.4, 0.2, 0.08), wood_f)
    for k, c in enumerate(((0.8, 0.2, 0.2, 1), (0.2, 0.55, 0.3, 1), (0.9, 0.75, 0.2, 1))):
        box("book%d" % k, (-0.9 + 0.2 * k, 2.88, 2.05), (0.14, 0.12, 0.28), mat(c))
    box("table", (1.1, 1.7, 0.42), (0.8, 0.8, 0.06), wood_f)
    box("tleg", (1.1, 1.7, 0.2), (0.12, 0.12, 0.4), wood_f)
    sphere("lamp", (1.1, 1.7, 0.62), 0.12, mat((1, 0.9, 0.6, 1), emit=(1, 0.8, 0.4, 1), strength=25))
    light("POINT", (1.1, 1.7, 0.7), 250, (1.0, 0.72, 0.4))
    light("POINT", (0.0, 0.5, 2.4), 120, (1.0, 0.8, 0.55), 0.8)
    light("POINT", (0.9, 2.2, 1.7), 60, (0.45, 0.6, 1.0))
    sc.render.filepath = os.path.join(out, "interior_stube.png")
    bpy.ops.render.render(write_still=True)


def hoehle():
    sc = scene_base((0.01, 0.015, 0.03, 1))
    rock = mat((0.16, 0.14, 0.15, 1), rough=0.95)
    box("floor", (0, 1.5, -0.1), (5, 6, 0.2), rock)
    box("back", (0, 3.4, 1.5), (5, 0.3, 3.2), rock)
    box("left", (-2.4, 1.5, 1.5), (0.3, 6, 3.2), rock)
    box("right", (2.4, 1.5, 1.5), (0.3, 6, 3.2), rock)
    box("ceil", (0, 1.5, 3.1), (5, 6, 0.2), rock)
    random.seed(7)
    cols = [((0.3, 0.7, 1.0, 1)), ((0.6, 0.3, 1.0, 1)), ((0.3, 1.0, 0.7, 1))]
    for k in range(9):
        c = cols[k % 3]
        x = random.uniform(-1.8, 1.8)
        y = random.uniform(1.2, 3.1)
        h = random.uniform(0.3, 0.9)
        cr = box("crystal%d" % k, (x, y, h / 2), (0.15, 0.15, h), mat(c, emit=c, strength=1.6, rough=0.2))
        cr.rotation_euler = (random.uniform(-0.25, 0.25), random.uniform(-0.25, 0.25), random.uniform(0, 3))
        light("POINT", (x, y - 0.2, h), 30, c[:3], 0.2)
    for k in range(3):
        x = random.uniform(-1.5, 1.5)
        y = random.uniform(1.0, 2.2)
        sphere("shroom%d" % k, (x, y, 0.22), 0.22, mat((1, 0.5, 0.2, 1), emit=(1, 0.45, 0.15, 1), strength=1.2), (1, 1, 0.5))
        light("POINT", (x, y - 0.1, 0.3), 25, (1.0, 0.55, 0.25), 0.2)
    sc.render.filepath = os.path.join(out, "interior_hoehle.png")
    bpy.ops.render.render(write_still=True)


stube()
hoehle()
print("FERTIG Innenraeume")
