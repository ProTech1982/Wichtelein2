"""Rendert die Wichteltuer (3 Formen, Oeffnungswinkel nach aussen und innen) fuer Wichtelein2.

Aufruf: blender --factory-startup -b -P render_door.py -- <Ausgabeordner>
Ergebnis je Form (rect = Rechteck, arch = Rundbogen, round = Rund):
  door_<form>_<winkel>.png  Rahmen + Tuerblatt (Angelseite LINKS; in der App fuer rechts gespiegelt),
                            Winkel: 0 zu, o18..o90 nach aussen (zum Betrachter), i18..i90 nach innen
  door_<form>_hole.png      Tueroeffnung (weiss) als Maske fuer den Innenraum
  door.json                 Bildgroesse und Lage der geschlossenen Tuer im Bild
Die geschlossene Tuer fuellt den mittleren Teil des Bildes (Rand ringsum fuer das aufschwingende Blatt).
"""
import json
import math
import os
import sys

import bpy
import bmesh
from mathutils import Matrix, Vector

out = sys.argv[sys.argv.index("--") + 1]
os.makedirs(out, exist_ok=True)

MARGIN = 1.24
SHAPES = {"rect": (400, 640), "arch": (400, 640), "round": (512, 512)}
ANGLES = [("0", 0)] + [("o%d" % a, -a) for a in (18, 36, 54, 72, 90)] + [("i%d" % a, a) for a in (18, 36, 54, 72, 90)]
DIST, LENS, SENSOR = 12.0, 50.0, 36.0
WORLD_W = SENSOR * DIST / LENS            # sichtbare Breite an der Tuerebene
TH_FRAME, TH_LEAF = 0.22, 0.14


def engine_id():
    ids = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    return next((i for i in ids if "EEVEE" in i), ids[0])


def outline(shape, w, h, n=28):
    """Umriss (x, z) der Tuer, Mitte unten bei x=0, z=0."""
    pts = []
    if shape == "rect":
        pts = [(-w / 2, 0), (w / 2, 0), (w / 2, h), (-w / 2, h)]
    elif shape == "arch":
        r = w / 2
        pts = [(-r, 0), (r, 0)]
        for k in range(n + 1):
            a = math.pi * k / n
            pts.append((r * math.cos(a), h - r + r * math.sin(a)))
    else:
        for k in range(n * 2):
            a = 2 * math.pi * k / (n * 2) - math.pi / 2
            pts.append((w / 2 * math.cos(a), h / 2 + h / 2 * math.sin(a)))
    return pts


def poly_mesh(name, pts, thick, y0=0.0):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    vs = [bm.verts.new((x, y0, z)) for x, z in pts]
    f = bm.faces.new(vs)
    ext = bmesh.ops.extrude_face_region(bm, geom=[f])
    gv = [e for e in ext["geom"] if isinstance(e, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, vec=(0, thick, 0), verts=gv)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def ring_mesh(name, outer, inner, thick):
    """Rahmen: Flaeche zwischen zwei Umrissen, nach hinten extrudiert (Y+)."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    vo = [bm.verts.new((x, 0, z)) for x, z in outer]
    vi = [bm.verts.new((x, 0, z)) for x, z in inner]
    n = len(outer)
    assert n == len(inner)
    for k in range(n):
        k2 = (k + 1) % n
        bm.faces.new((vo[k], vo[k2], vi[k2], vi[k]))
    faces = list(bm.faces)
    ext = bmesh.ops.extrude_face_region(bm, geom=faces)
    gv = [e for e in ext["geom"] if isinstance(e, bmesh.types.BMVert)]
    bmesh.ops.translate(bm, vec=(0, thick, 0), verts=gv)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def wood(name, base, dark, scale=1.0, planks=True, pw=0.46):
    """Holz: Bretter (Brick-Textur als Fugen) mit Maserung (Wave + Noise)."""
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    tc = nt.nodes.new("ShaderNodeTexCoord")
    mp = nt.nodes.new("ShaderNodeMapping")
    nt.links.new(tc.outputs["Object"], mp.inputs["Vector"])
    wave = nt.nodes.new("ShaderNodeTexWave")
    wave.wave_type = "BANDS"
    wave.bands_direction = "X"
    wave.inputs["Scale"].default_value = 26.0 * scale
    wave.inputs["Distortion"].default_value = 5.0
    wave.inputs["Detail"].default_value = 4.0
    nt.links.new(mp.outputs["Vector"], wave.inputs["Vector"])
    cr = nt.nodes.new("ShaderNodeValToRGB")
    cr.color_ramp.elements[0].color = dark
    cr.color_ramp.elements[1].color = base
    nt.links.new(wave.outputs["Fac"], cr.inputs["Fac"])
    color_out = cr.outputs["Color"]
    if planks:
        # Bretter: zweites Streifenmuster, Fugen jeweils an den Streifengrenzen
        w2 = nt.nodes.new("ShaderNodeTexWave")
        w2.wave_type = "BANDS"
        w2.bands_direction = "X"
        w2.inputs["Scale"].default_value = 2 * math.pi / (10.0 * pw)
        w2.inputs["Distortion"].default_value = 0.0
        w2.inputs["Detail"].default_value = 0.0
        nt.links.new(mp.outputs["Vector"], w2.inputs["Vector"])
        cr2 = nt.nodes.new("ShaderNodeValToRGB")
        cr2.color_ramp.interpolation = "CONSTANT"
        cr2.color_ramp.elements[0].position = 0.0
        cr2.color_ramp.elements[0].color = (0.08, 0.08, 0.08, 1)
        cr2.color_ramp.elements[1].position = 0.05
        cr2.color_ramp.elements[1].color = (1, 1, 1, 1)
        nt.links.new(w2.outputs["Fac"], cr2.inputs["Fac"])
        mix = nt.nodes.new("ShaderNodeMix")
        mix.data_type = "RGBA"
        mix.blend_type = "MULTIPLY"
        mix.inputs["Factor"].default_value = 1.0
        nt.links.new(cr.outputs["Color"], mix.inputs["A"])
        nt.links.new(cr2.outputs["Color"], mix.inputs["B"])
        color_out = mix.outputs["Result"]
    nt.links.new(color_out, bsdf.inputs["Base Color"])
    bump = nt.nodes.new("ShaderNodeBump")
    bump.inputs["Strength"].default_value = 0.25
    nt.links.new(wave.outputs["Fac"], bump.inputs["Height"])
    nt.links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    bsdf.inputs["Roughness"].default_value = 0.75
    return m


def flat(name, color, metal=0.0, rough=0.45):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = next(n for n in m.node_tree.nodes if n.type == "BSDF_PRINCIPLED")
    b.inputs["Base Color"].default_value = color
    b.inputs["Metallic"].default_value = metal
    b.inputs["Roughness"].default_value = rough
    return m


def emission(name, color):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    o = nt.nodes.new("ShaderNodeOutputMaterial")
    e = nt.nodes.new("ShaderNodeEmission")
    e.inputs["Color"].default_value = color
    nt.links.new(e.outputs["Emission"], o.inputs["Surface"])
    return m


def build(shape):
    """Baut Tuer (Angelseite links) und liefert (Rahmen, Blatt-Wurzel, Loch-Objekt, Breite, Hoehe)."""
    cw, ch = SHAPES[shape]
    wc = WORLD_W
    hc = wc * ch / cw
    w, h = wc / MARGIN, hc / MARGIN
    # Tuer mittig im Bild: Mitte (0, h/2)
    out_pts = outline(shape, w, h)
    k = 0.80   # Oeffnung = 80 % des Umrisses, der Rest ist Rahmen
    inner = [(x * k, h / 2 + (z - h / 2) * k) for x, z in out_pts]
    frame = ring_mesh("frame", out_pts, inner, TH_FRAME)
    frame.data.materials.append(wood("frameWood", (0.26, 0.13, 0.055, 1), (0.14, 0.065, 0.025, 1), scale=0.6, planks=False))
    # Tuerblatt: knapp kleiner als die Oeffnung, Ursprung am Angelpunkt (linke Kante)
    minx = min(p[0] for p in inner) * 0.985
    maxx = max(p[0] for p in inner) * 0.985
    zs = [p[1] for p in inner]
    zmid = (min(zs) + max(zs)) / 2
    span = maxx - minx
    leaf = poly_mesh("leaf", [(x * 0.985 - minx, z) for x, z in inner], TH_LEAF, y0=0.0)
    leaf.data.materials.append(wood("leafWood", (0.66, 0.38, 0.15, 1), (0.40, 0.2, 0.07, 1), scale=1.0, planks=True, pw=span / 5.0))
    iron = flat("iron", (0.05, 0.05, 0.06, 1), 0.8, 0.4)
    gold = flat("gold", (0.9, 0.62, 0.2, 1), 1.0, 0.3)
    parts = [leaf]
    for fz in (0.28, 0.72):
        z = zmid + (fz - 0.5) * (max(zs) - min(zs)) * 0.85
        bar = bpy.data.objects.new("hingeBar", bpy.data.meshes.new("hingeBar"))
        bm = bmesh.new()
        bmesh.ops.create_cube(bm, size=1.0)
        bm.to_mesh(bar.data)
        bm.free()
        bar.scale = (span * 0.55, 0.05, 0.08)
        bar.location = (span * 0.275, -0.03, z)
        bar.data.materials.append(iron)
        bpy.context.scene.collection.objects.link(bar)
        parts.append(bar)
    knob = bpy.data.objects.new("knob", bpy.data.meshes.new("knob"))
    bm = bmesh.new()
    bmesh.ops.create_uvsphere(bm, u_segments=20, v_segments=12, radius=0.11)
    bm.to_mesh(knob.data)
    bm.free()
    knob.location = (span * 0.86, -0.08, zmid)
    knob.data.materials.append(gold)
    bpy.context.scene.collection.objects.link(knob)
    parts.append(knob)
    root = bpy.data.objects.new("leafRoot", None)
    root.location = (minx, 0, 0)
    bpy.context.scene.collection.objects.link(root)
    for o in parts:
        o.parent = root
    # Oeffnung als Loch-Objekt
    hole = poly_mesh("hole", inner, 0.01, y0=0.02)
    hole.data.materials.append(emission("holeMat", (1, 1, 1, 1)))
    return frame, root, hole, w, h, (cw, ch)


def setup():
    sc = bpy.context.scene
    sc.render.engine = engine_id()
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    try:
        sc.view_settings.view_transform = "Standard"
    except TypeError:
        pass
    w = bpy.data.worlds.new("w")
    w.use_nodes = True
    sc.world = w
    bg = w.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.9, 0.92, 1.0, 1)
    bg.inputs[1].default_value = 0.6
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    cam.data.lens = LENS
    cam.data.sensor_width = SENSOR
    cam.data.sensor_fit = "HORIZONTAL"
    sc.collection.objects.link(cam)
    sc.camera = cam
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 2.4
    sun.rotation_euler = (math.radians(55), math.radians(-5), math.radians(-30))
    sc.collection.objects.link(sun)
    return sc, cam


meta = {"margin": MARGIN, "angles": [a for a, _ in ANGLES], "shapes": {}}
for shape in SHAPES:
    bpy.ops.wm.read_factory_settings(use_empty=True)
    sc, cam = setup()
    frame, root, hole, w, h, (cw, ch) = build(shape)
    sc.render.resolution_x, sc.render.resolution_y = cw, ch
    sc.render.resolution_percentage = 100
    cam.location = (0, -DIST, h / 2)
    cam.rotation_euler = (math.radians(90), 0, 0)
    hole.hide_render = True
    for tag, ang in ANGLES:
        root.rotation_euler = (0, 0, math.radians(ang))
        sc.render.filepath = os.path.join(out, "door_%s_%s.png" % (shape, tag))
        bpy.ops.render.render(write_still=True)
    # Lochmaske: nur die Oeffnung
    root.hide_render = True
    for ch_ in root.children:
        ch_.hide_render = True
    frame.hide_render = True
    hole.hide_render = False
    sc.render.filepath = os.path.join(out, "door_%s_hole.png" % shape)
    bpy.ops.render.render(write_still=True)
    ix = round(cw * (1 - 1 / MARGIN) / 2)
    iy = round(ch * (1 - 1 / MARGIN) / 2)
    meta["shapes"][shape] = {"w": cw, "h": ch, "x": ix, "y": iy, "bw": cw - 2 * ix, "bh": ch - 2 * iy}
json.dump(meta, open(os.path.join(out, "door.json"), "w"), indent=1)
print("FERTIG Tuer")
