"""Gemeinsame Hilfen fuer die Wichtelein2-Blender-Skripte (Wichtel laden, Farbmasken, Pose, Kamera).

Quellmodelle (nicht im Repo, zu gross): Downloads/3DWichtel (Wichtel mit Mixamo-Skelett) und
Downloads/3DWichtel/3DBesen. Pfade koennen mit den Umgebungsvariablen WICHTEL_FBX und BESEN_FBX
ueberschrieben werden.
"""
import glob
import math
import os

import bpy
from mathutils import Euler, Matrix, Vector

HOME = os.path.expanduser("~")
DL = os.path.join(HOME, "Downloads", "3DWichtel")


def find_fbx(env, pattern):
    p = os.environ.get(env)
    if p:
        return p
    hits = glob.glob(os.path.join(DL, pattern), recursive=True)
    if not hits:
        raise SystemExit("FBX nicht gefunden: " + pattern)
    return hits[0]


WICHTEL_FBX = lambda: find_fbx("WICHTEL_FBX", "Mit Animation Wichtel/*.fbx")
BESEN_FBX = lambda: find_fbx("BESEN_FBX", "3DBesen/*.fbx")

BEAUTY_ENGINE_HINT = "EEVEE"


def engine_id():
    ids = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    for i in ids:
        if "EEVEE" in i:
            return i
    return ids[0]


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_wichtel():
    """Importiert den Wichtel, liefert (Armatur, Mesh)."""
    bpy.ops.import_scene.fbx(filepath=WICHTEL_FBX())
    arm = next(o for o in bpy.data.objects if o.type == "ARMATURE")
    mesh = next(o for o in bpy.data.objects if o.type == "MESH")
    return arm, mesh


# ---------------------------------------------------------------- Regionen (Farbmaske)
REGION_NONE, REGION_HAT, REGION_JACKET, REGION_PANTS, REGION_SHOES = 0, 1, 2, 3, 4
# Maskenfarben: Muetze rot, Jacke gruen, Hose blau, Schuhe gelb (= rot+gruen)
REGION_COLORS = {
    REGION_NONE: (0, 0, 0),
    REGION_HAT: (1, 0, 0),
    REGION_JACKET: (0, 1, 0),
    REGION_PANTS: (0, 0, 1),
    REGION_SHOES: (1, 1, 0),
}


def _hsv(r, g, b):
    import colorsys
    return colorsys.rgb_to_hsv(r, g, b)


def classify_color(h, s, v, z):
    """Region aus Farbton und Hoehe (Rest-Pose, Meter ab Boden)."""
    if z > 0.50 and (h < 0.045 or h > 0.93) and s > 0.45 and v > 0.25:
        return REGION_HAT
    if z < 0.075 and 0.03 < h < 0.13 and s > 0.35:
        return REGION_SHOES
    if 0.075 <= z < 0.255 and 0.0 <= h < 0.14 and s > 0.12 and v < 0.75:
        return REGION_PANTS
    if 0.50 < h < 0.68 and s > 0.2 and 0.22 < z < 0.52:
        return REGION_JACKET
    return REGION_NONE


def build_region_attribute(mesh, name="region"):
    """Schreibt je Ecke (Loop) eine Maskenfarbe als Farb-Attribut `name` und liefert Flaechenzahlen."""
    import numpy as np
    img = None
    for im in bpy.data.images:
        if im.name.lower().endswith("basecolor"):
            img = im
            break
    if img is None:
        raise SystemExit("Basecolor-Textur nicht gefunden")
    work = img.copy()
    work.scale(512, 512)
    w, h = work.size
    px = np.empty(w * h * 4, dtype=np.float32)
    work.pixels.foreach_get(px)
    px = px.reshape(h, w, 4)
    bpy.data.images.remove(work)

    me = mesh.data
    uv = me.uv_layers.active.data
    mw = mesh.matrix_world
    regs = []
    for poly in me.polygons:
        loops = poly.loop_indices
        u = sum(uv[i].uv[0] for i in loops) / len(loops)
        v = sum(uv[i].uv[1] for i in loops) / len(loops)
        x = min(w - 1, max(0, int(u * w)))
        y = min(h - 1, max(0, int(v * h)))
        r, g, b, _ = px[y, x]
        z = (mw @ poly.center).z
        regs.append(classify_color(*_hsv(float(r), float(g), float(b)), z))
    # Muster (Blumen auf dem Aermel, Kanten) glaetten: Flaeche ohne Region uebernimmt die Region,
    # die mindestens zwei ihrer Nachbarn haben (zwei Durchgaenge)
    nb = [[] for _ in me.polygons]
    edge_polys = {}
    for poly in me.polygons:
        for li in poly.loop_indices:
            edge_polys.setdefault(me.loops[li].edge_index, []).append(poly.index)
    for ps in edge_polys.values():
        if len(ps) == 2:
            nb[ps[0]].append(ps[1])
            nb[ps[1]].append(ps[0])
    for _ in range(3):
        new = list(regs)
        for i, r0 in enumerate(regs):
            if r0 != REGION_NONE:
                continue
            votes = {}
            for j in nb[i]:
                if regs[j] != REGION_NONE:
                    votes[regs[j]] = votes.get(regs[j], 0) + 1
            if votes:
                best = max(votes, key=votes.get)
                if votes[best] >= 2:
                    new[i] = best
        regs = new
    counts = {}
    cols = [None] * len(me.loops)
    for poly in me.polygons:
        reg = regs[poly.index]
        counts[reg] = counts.get(reg, 0) + 1
        c = REGION_COLORS[reg] + (1.0,)
        for i in poly.loop_indices:
            cols[i] = c
    # Attribut erst jetzt anlegen: das ordnet die Netzdaten um und macht `uv` ungueltig
    attr = me.color_attributes.new(name=name, type="FLOAT_COLOR", domain="CORNER")
    flat = []
    for c in cols:
        flat.extend(c)
    attr.data.foreach_set("color", flat)
    return counts


def make_mask_object(mesh, arm, attr="region"):
    """Kopie des Meshes mit Emissions-Material der Regionsfarbe (gleiche Armatur)."""
    dup = mesh.copy()
    dup.data = mesh.data.copy()
    bpy.context.scene.collection.objects.link(dup)
    dup.name = "mask_" + mesh.name
    for m in list(dup.modifiers):
        if m.type == "ARMATURE":
            m.object = arm
    mat = bpy.data.materials.new("maskmat")
    mat.use_nodes = True
    nt = mat.node_tree
    nt.nodes.clear()
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    ca = nt.nodes.new("ShaderNodeAttribute")
    ca.attribute_name = attr
    nt.links.new(ca.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    dup.data.materials.clear()
    dup.data.materials.append(mat)
    return dup


# ---------------------------------------------------------------- Pose
def short(b):
    return b.name.replace("mixamorig:", "")


def rotmat(v):
    """None -> keine Drehung; (rx, ry, rz) Grad; oder Liste [('y', 60), ('x', -20)] nacheinander (Armatur-Achsen)."""
    if v is None:
        return Matrix.Identity(3)
    if isinstance(v, tuple) and len(v) == 3 and all(isinstance(a, (int, float)) for a in v):
        return Euler([math.radians(a) for a in v], "XYZ").to_matrix()
    m = Matrix.Identity(3)
    for axis, deg in v:
        m = Matrix.Rotation(math.radians(deg), 3, axis.upper()) @ m
    return m


class Posing:
    """Setzt Posen ueber Drehungen im Armatur-Raum (Grad, XYZ), je Knochen relativ zur Rest-Pose.

    Vorwaerts ist -Y, links des Wichtels +X, oben +Z. Die Drehung jedes Knochens wirkt zusaetzlich zu
    der seiner Eltern (wie beim Schwingen eines Arms am Schultergelenk).
    """

    def __init__(self, arm):
        self.arm = arm
        self.order = []
        self.rest = {}
        for b in arm.data.bones:
            self.rest[short(b)] = b.matrix_local.to_3x3().copy()
        # Eltern vor Kindern
        def walk(b):
            self.order.append(b)
            for c in b.children:
                walk(c)
        for b in arm.data.bones:
            if b.parent is None:
                walk(b)
        for pb in arm.pose.bones:
            pb.rotation_mode = "QUATERNION"

    def apply(self, spec, hips_move=(0, 0, 0)):
        """spec: {Knochenname ohne Praefix: (rx, ry, rz)} in Grad; fehlende Knochen bleiben in Rest-Lage."""
        D = {}
        for b in self.order:
            n = short(b)
            d = rotmat(spec.get(n))
            Dp = D[short(b.parent)] if b.parent else Matrix.Identity(3)
            D[n] = d @ Dp
            Rc = self.rest[n]
            Bm = Rc.inverted() @ Dp.inverted() @ d @ Dp @ Rc
            pb = self.arm.pose.bones[b.name]
            pb.rotation_quaternion = Bm.to_quaternion()
            pb.location = (0, 0, 0)
        if any(hips_move):
            hips = self.arm.pose.bones["mixamorig:Hips"]
            t = Vector(hips_move)
            hips.location = self.rest["Hips"].inverted() @ t
        bpy.context.view_layer.update()


# ---------------------------------------------------------------- Szene
def setup_scene(res=448):
    sc = bpy.context.scene
    sc.render.engine = engine_id()
    sc.render.resolution_x = sc.render.resolution_y = res
    sc.render.resolution_percentage = 100
    sc.render.film_transparent = True
    sc.render.image_settings.file_format = "PNG"
    sc.render.image_settings.color_mode = "RGBA"
    try:
        sc.view_settings.view_transform = "Standard"
    except TypeError:
        pass
    try:
        sc.eevee.taa_render_samples = 16
    except Exception:
        pass
    w = bpy.data.worlds.new("w")
    w.use_nodes = True
    sc.world = w
    bg = w.node_tree.nodes["Background"]
    bg.inputs[0].default_value = (0.85, 0.87, 0.95, 1)
    bg.inputs[1].default_value = 0.9
    cam_data = bpy.data.cameras.new("cam")
    cam_data.type = "ORTHO"
    cam = bpy.data.objects.new("cam", cam_data)
    sc.collection.objects.link(cam)
    sc.camera = cam
    sun = bpy.data.objects.new("sun", bpy.data.lights.new("sun", "SUN"))
    sun.data.energy = 3.2
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(-35))
    sc.collection.objects.link(sun)
    fill = bpy.data.objects.new("fill", bpy.data.lights.new("fill", "SUN"))
    fill.data.energy = 1.0
    fill.rotation_euler = (math.radians(70), 0, math.radians(150))
    sc.collection.objects.link(fill)
    return sc, cam


def aim_camera(cam, elev_deg, scale, feet_px, cell):
    """Orthokamera, die den Ursprung (Fusspunkt) auf Pixel feet_px (von links/oben) der Zelle legt."""
    e = math.radians(elev_deg)
    cam.data.ortho_scale = scale
    dist = 6.0
    # Blickrichtung: von -Y nach +Y, geneigt um e nach unten
    cam.rotation_euler = (math.radians(90) - e, 0, 0)
    fwd = Vector((0, math.cos(e), -math.sin(e)))
    up = Vector((0, math.sin(e), math.cos(e)))
    right = Vector((1, 0, 0))
    # Verschiebung in der Bildebene, damit der Ursprung bei feet_px landet
    fx = (feet_px[0] / cell - 0.5) * scale
    fy = (0.5 - feet_px[1] / cell) * scale
    cam.location = -fwd * dist - right * fx - up * fy
