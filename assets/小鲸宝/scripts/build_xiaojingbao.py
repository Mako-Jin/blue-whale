# -*- coding: utf-8 -*-
"""
小鲸宝 / Little Whale Treasure —— 漂浮姿态 Q 版幼鲸，Blender 程序化建模
用法:
  blender -b --factory-startup -P build_xiaojingbao.py -- --quick   # 只渲主图
  blender -b --factory-startup -P build_xiaojingbao.py -- --views   # 只渲四视图
  blender -b --factory-startup -P build_xiaojingbao.py              # 全量 + 导出
"""
import bpy, bmesh, math, os, sys
import numpy as np
from mathutils import Vector, Matrix, Euler

# 工作目录：默认脚本所在目录；可用 XJB_DIR 指向含 out/ 的工作目录
HERE = os.environ.get("XJB_DIR") or os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
os.makedirs(OUT, exist_ok=True)
QUICK = "--quick" in sys.argv
VIEWS_ONLY = "--views" in sys.argv

# ================================================================ palette
ICE, STAR, CREAM = "#62ccf2", "#96e7ff", "#fdfaf3"
DEEP, BELLC = "#3ea8dd", "#54d1f7"


def hex2lin(h, a=1.0):
    h = h.lstrip("#")
    c = [int(h[i:i+2], 16) / 255.0 for i in (0, 2, 4)]
    return tuple(((v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4) for v in c)) + (a,)


# ================================================================ helpers
def new_mat(name):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        if n.type not in ("BSDF_PRINCIPLED", "OUTPUT_MATERIAL"):
            nt.nodes.remove(n)
    bsdf = nt.nodes.get("Principled BSDF")
    return m, nt, bsdf


def set_in(bsdf, keys, val):
    for k in keys:
        if k in bsdf.inputs:
            bsdf.inputs[k].default_value = val
            return True
    return False


def simple_mat(name, color, rough=0.3, metal=0.0, emit=None, emit_str=0.0,
               transmission=0.0, ior=1.45, coat=0.0, sss=0.0):
    m, nt, b = new_mat(name)
    set_in(b, ["Base Color"], hex2lin(color))
    set_in(b, ["Roughness"], rough)
    set_in(b, ["Metallic"], metal)
    set_in(b, ["IOR"], ior)
    set_in(b, ["Transmission Weight", "Transmission"], transmission)
    if coat:
        set_in(b, ["Coat Weight", "Clearcoat"], coat)
        set_in(b, ["Coat Roughness", "Clearcoat Roughness"], 0.03)
    if sss:
        set_in(b, ["Subsurface Weight", "Subsurface"], sss)
        set_in(b, ["Subsurface Radius"], (0.14, 0.09, 0.06))
        set_in(b, ["Subsurface Scale"], 0.07)
    if emit:
        set_in(b, ["Emission Color", "Emission"], hex2lin(emit))
        set_in(b, ["Emission Strength"], emit_str)
    m.diffuse_color = hex2lin(color)
    return m


def shade_smooth(ob):
    for p in ob.data.polygons:
        p.use_smooth = True


def add_subsurf(ob, lv=2):
    md = ob.modifiers.new("Subsurf", "SUBSURF")
    md.levels = lv
    md.render_levels = lv


def mesh_from(name, verts, faces):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.validate()
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    return ob


def catmull(pts, n):
    P = [Vector(p) for p in pts]
    P = [P[0] + (P[0] - P[1])] + P + [P[-1] + (P[-1] - P[-2])]
    segs = len(P) - 3
    out = []
    for i in range(n):
        u = i / (n - 1) * segs
        k = min(int(u), segs - 1)
        t = u - k
        p0, p1, p2, p3 = P[k], P[k+1], P[k+2], P[k+3]
        t2, t3 = t * t, t * t * t
        out.append(0.5 * ((2 * p1) + (-p0 + p2) * t +
                          (2*p0 - 5*p1 + 4*p2 - p3) * t2 +
                          (-p0 + 3*p1 - 3*p2 + p3) * t3))
    return out


def tube(name, pts, radii, seg=24, cap=True):
    """沿空间曲线放样圆管，带平行传输标架避免扭转"""
    n = len(pts)
    rings = []
    prev_n = Vector((0, 0, 1))
    for i in range(n):
        t = (pts[min(i+1, n-1)] - pts[max(i-1, 0)])
        if t.length < 1e-9:
            t = Vector((0, 1, 0))
        t.normalize()
        if i == 0:
            ref = Vector((0, 0, 1)) if abs(t.dot(Vector((0, 0, 1)))) < 0.9 else Vector((1, 0, 0))
            nrm = (ref - t * ref.dot(t)).normalized()
        else:
            nrm = (prev_n - t * prev_n.dot(t))
            nrm = nrm.normalized() if nrm.length > 1e-9 else Vector((1, 0, 0))
        prev_n = nrm
        bnr = t.cross(nrm).normalized()
        ring = []
        for j in range(seg):
            a = 2 * math.pi * j / seg
            ring.append(pts[i] + nrm * (math.cos(a) * radii[i]) + bnr * (math.sin(a) * radii[i]))
        rings.append(ring)
    verts, faces = [], []
    for r in rings:
        verts.extend([tuple(v) for v in r])
    for i in range(n - 1):
        for j in range(seg):
            a = i * seg + j
            b = i * seg + (j + 1) % seg
            c = (i + 1) * seg + (j + 1) % seg
            d = (i + 1) * seg + j
            faces.append((a, b, c, d))
    if cap:
        verts.append(tuple(pts[0])); ci = len(verts) - 1
        for j in range(seg):
            faces.append((ci, (j + 1) % seg, j))
        verts.append(tuple(pts[-1])); ci = len(verts) - 1
        base = (n - 1) * seg
        for j in range(seg):
            faces.append((ci, base + j, base + (j + 1) % seg))
    return mesh_from(name, verts, faces)


def sphere(name, loc=(0, 0, 0), scale=(1, 1, 1), seg=48, ring=32, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=ring, radius=1.0, location=loc)
    ob = bpy.context.object
    ob.name = name
    ob.scale = scale
    ob.rotation_euler = rot
    shade_smooth(ob)
    return ob


def blade(name, pts, widths, thick, seg=22, ref_axis=(0, 1, 0)):
    """沿中心线放样柳叶形薄片：宽度沿第一个垂直方向，厚度沿第二个"""
    n = len(pts)
    rings, prev_u = [], None
    for i in range(n):
        t = (pts[min(i + 1, n - 1)] - pts[max(i - 1, 0)])
        if t.length < 1e-9:
            t = Vector((0, 0, 1))
        t.normalize()
        ref = Vector(ref_axis)
        if abs(t.dot(ref)) > 0.92:
            ref = Vector((0, 0, 1)) if abs(t.z) < 0.9 else Vector((1, 0, 0))
        u = (ref - t * ref.dot(t)).normalized()
        v = t.cross(u).normalized()
        k = i / max(1, n - 1)
        th = thick * (1.0 - 0.35 * k)
        ring = []
        for j in range(seg):
            a = 2 * math.pi * j / seg
            ring.append(pts[i] + u * (math.cos(a) * widths[i]) + v * (math.sin(a) * th))
        rings.append(ring)
    verts, faces = [], []
    for r in rings:
        verts.extend([tuple(q) for q in r])
    for i in range(n - 1):
        for j in range(seg):
            a0 = i * seg + j
            b0 = i * seg + (j + 1) % seg
            c0 = (i + 1) * seg + (j + 1) % seg
            d0 = (i + 1) * seg + j
            faces.append((a0, b0, c0, d0))
    verts.append(tuple(pts[0])); ci = len(verts) - 1
    for j in range(seg):
        faces.append((ci, (j + 1) % seg, j))
    verts.append(tuple(pts[-1])); ci = len(verts) - 1
    base = (n - 1) * seg
    for j in range(seg):
        faces.append((ci, base + j, base + (j + 1) % seg))
    ob = mesh_from(name, verts, faces)
    shade_smooth(ob)
    return ob


# ================================================================ body
# 竖直剖面 (r, z)：z=0 尾梢（下）→ z=1.54 头顶（上）
PROFILE = [
    (0.030, 0.000), (0.052, 0.038), (0.076, 0.076), (0.102, 0.114),
    (0.130, 0.152), (0.160, 0.190), (0.190, 0.228), (0.218, 0.266),
    (0.242, 0.304), (0.262, 0.342), (0.276, 0.380), (0.284, 0.418),
    (0.286, 0.456), (0.282, 0.494), (0.274, 0.532), (0.262, 0.570),
    (0.248, 0.608), (0.232, 0.646), (0.218, 0.684), (0.210, 0.720),
    (0.218, 0.752), (0.238, 0.786), (0.260, 0.820), (0.282, 0.854),
    (0.298, 0.888), (0.308, 0.924), (0.312, 0.960), (0.310, 0.996),
    (0.304, 1.032), (0.294, 1.070), (0.282, 1.108), (0.264, 1.144),
    (0.242, 1.178), (0.216, 1.212), (0.184, 1.244), (0.146, 1.272),
    (0.100, 1.296), (0.052, 1.312), (0.000, 1.322),
]
CENTER = 0.661


def lathe_prof(name, prof, seg=112, xs=0.965):
    verts, faces, ridx = [], [], []
    for (r, z) in prof:
        if r < 1e-7:
            ridx.append(("p", len(verts)))
            verts.append((0, 0, z))
        else:
            base = len(verts)
            for j in range(seg):
                a = 2 * math.pi * j / seg
                verts.append((math.cos(a) * r * xs, math.sin(a) * r, z))
            ridx.append(("r", base))
    for i in range(len(ridx) - 1):
        t0, v0 = ridx[i]
        t1, v1 = ridx[i + 1]
        if t0 == "p" and t1 == "r":
            for j in range(seg):
                faces.append((v0, v1 + j, v1 + (j + 1) % seg))
        elif t0 == "r" and t1 == "p":
            for j in range(seg):
                faces.append((v0 + j, v1, v0 + (j + 1) % seg))
        else:
            for j in range(seg):
                faces.append((v0 + j, v1 + j, v1 + (j + 1) % seg, v0 + (j + 1) % seg))
    return mesh_from(name, verts, faces)


def build_body():
    ob = lathe_prof("WhaleBody", PROFILE)

    for v in ob.data.vertices:
        x, y, z = v.co
        # 尾巴向后上方卷起（悬浮摆尾）
        if z < 0.31:
            t = (0.31 - z) / 0.31
            y += 0.255 * t ** 1.7
            z += 0.030 * t ** 2.4
        # 颈部略收
        if 0.84 < z < 0.94:
            y *= 0.97
        v.co = (x, y, z - CENTER)
    ob.data.update()

    shade_smooth(ob)
    md = ob.modifiers.new("Smooth", "SMOOTH")
    md.factor = 0.6
    md.iterations = 3
    ob.modifiers.new("Subsurf", "SUBSURF").levels = 1
    print("BODY verts=%d dims=%s" % (len(ob.data.vertices), tuple(round(d, 3) for d in ob.dimensions)))
    return ob


def build_fins(mat_skin):
    P = []
    def tail_pt(zloc):
        t = max(0.0, (0.31 - zloc) / 0.31)
        return Vector((0, 0.255 * t ** 1.7, zloc + 0.030 * t ** 2.4 - CENTER))

    tip = tail_pt(0.030)
    FL = [(0.00, 0.020), (0.12, 0.038), (0.30, 0.052), (0.48, 0.056),
          (0.66, 0.048), (0.82, 0.034), (0.93, 0.019), (1.00, 0.005)]

    # 尾鳍：两片竖直 V 形尾叶
    for s, nm in ((1, "Fluke_L"), (-1, "Fluke_R")):
        pts, ws = [], []
        for (u, w) in FL:
            x = s * (0.028 + 0.250 * u)
            y = tip.y + 0.006 + 0.042 * u
            z = tip.z - 0.010 - 0.205 * u ** 1.02
            pts.append(Vector((x, y, z)))
            ws.append(w)
        fl = blade(nm, pts, ws, 0.018, seg=24, ref_axis=(0, 0, 1))
        add_subsurf(fl, 1)
        fl.data.materials.append(mat_skin)
        P.append(fl)

    # 背鳍：上背后掠圆鳍
    BF = [(0.00, 0.062), (0.22, 0.068), (0.45, 0.062), (0.66, 0.048),
          (0.84, 0.030), (1.00, 0.008)]
    pts, ws = [], []
    for (u, w) in BF:
        pts.append(Vector((0, 0.205 + 0.115 * u, 0.010 + 0.165 * u)))
        ws.append(w)
    fin = blade("Fin_Back", pts, ws, 0.020, seg=20)
    add_subsurf(fin, 1)
    fin.data.materials.append(mat_skin)
    P.append(fin)

    # 胸鳍：两侧小鳍，向前下外展开
    for s, nm in ((1, "Pec_L"), (-1, "Pec_R")):
        f = sphere(nm, loc=(s * 0.238, -0.072, -0.170),
                   scale=(0.046, 0.080, 0.132), seg=44, ring=28,
                   rot=(math.radians(-12), math.radians(s * -22), math.radians(s * 16)))
        for v in f.data.vertices:
            t = -v.co.z
            if t > 0:
                v.co.x *= 1.0 - 0.34 * t
                v.co.y -= 0.12 * t
        f.data.update()
        add_subsurf(f, 2)
        f.data.materials.append(mat_skin)
        P.append(f)
    return P


# ================================================================ materials
def mat_body():
    m, nt, b = new_mat("M_WhaleBody")
    tc = nt.nodes.new("ShaderNodeTexCoord")
    osep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Object"], osep.inputs["Vector"])
    geo = nt.nodes.new("ShaderNodeNewGeometry")
    nsep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(geo.outputs["Normal"], nsep.inputs["Vector"])

    # 高度归一 (z 0.30..0.95)
    hz = nt.nodes.new("ShaderNodeMapRange")
    hz.inputs["From Min"].default_value = -0.54
    hz.inputs["From Max"].default_value = 0.60
    nt.links.new(osep.outputs["Z"], hz.inputs["Value"])

    # 法线朝上程度 0..1
    nz = nt.nodes.new("ShaderNodeMapRange")
    nz.inputs["From Min"].default_value = -1.0
    nz.inputs["From Max"].default_value = 1.0
    nt.links.new(nsep.outputs["Z"], nz.inputs["Value"])

    nw = nt.nodes.new("ShaderNodeMath"); nw.operation = "MULTIPLY"
    nw.inputs[1].default_value = 0.42
    nt.links.new(nz.outputs["Result"], nw.inputs[0])
    hw = nt.nodes.new("ShaderNodeMath"); hw.operation = "MULTIPLY"
    hw.inputs[1].default_value = 0.58
    nt.links.new(hz.outputs["Result"], hw.inputs[0])
    add = nt.nodes.new("ShaderNodeMath"); add.operation = "ADD"
    nt.links.new(hw.outputs["Value"], add.inputs[0])
    nt.links.new(nw.outputs["Value"], add.inputs[1])

    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = "EASE"
    el = ramp.color_ramp.elements
    el[0].position = 0.12; el[0].color = hex2lin("#fffdf7")
    el[1].position = 1.00; el[1].color = hex2lin("#2e91cc")
    for pos, col in ((0.32, "#eefaff"), (0.48, "#b4ebfa"),
                     (0.64, "#7ed4f4"), (0.80, "#55c4ed")):
        e = ramp.color_ramp.elements.new(pos); e.color = hex2lin(col)
    nt.links.new(add.outputs["Value"], ramp.inputs["Fac"])

    # 腮红：左右脸颊圆形遮罩（object 空间距离场）
    def dist_node(center):
        sub = nt.nodes.new("ShaderNodeVectorMath"); sub.operation = "SUBTRACT"
        sub.inputs[1].default_value = center
        nt.links.new(tc.outputs["Object"], sub.inputs[0])
        ln = nt.nodes.new("ShaderNodeVectorMath"); ln.operation = "LENGTH"
        nt.links.new(sub.outputs["Vector"], ln.inputs[0])
        return ln.outputs["Value"]

    mun = nt.nodes.new("ShaderNodeMath"); mun.operation = "MINIMUM"
    nt.links.new(dist_node((0.242, -0.238, 0.300)), mun.inputs[0])
    nt.links.new(dist_node((-0.242, -0.238, 0.300)), mun.inputs[1])
    bmask = nt.nodes.new("ShaderNodeMapRange")
    bmask.inputs["From Min"].default_value = 0.090
    bmask.inputs["From Max"].default_value = 0.044
    bmask.inputs["To Min"].default_value = 0.0
    bmask.inputs["To Max"].default_value = 1.0
    nt.links.new(mun.outputs["Value"], bmask.inputs["Value"])

    blush = nt.nodes.new("ShaderNodeMixRGB")
    blush.blend_type = "MIX"
    blush.inputs[2].default_value = hex2lin("#ff9fb6")
    nt.links.new(bmask.outputs["Result"], blush.inputs[0])
    nt.links.new(ramp.outputs["Color"], blush.inputs[1])
    nt.links.new(blush.outputs["Color"], b.inputs["Base Color"])

    set_in(b, ["Roughness"], 0.30)
    set_in(b, ["Specular IOR Level", "Specular"], 0.42)
    set_in(b, ["Subsurface Weight", "Subsurface"], 0.09)
    set_in(b, ["Subsurface Radius"], (0.16, 0.10, 0.07))
    set_in(b, ["Subsurface Scale"], 0.06)
    set_in(b, ["Coat Weight", "Clearcoat"], 0.26)
    set_in(b, ["Coat Roughness", "Clearcoat Roughness"], 0.06)
    m.diffuse_color = hex2lin(ICE)
    return m


def mat_eye():
    m, nt, b = new_mat("M_Eye")
    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Object"], sep.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = "EASE"
    ramp.color_ramp.elements[0].position = 0.18
    ramp.color_ramp.elements[0].color = hex2lin("#0d2a44")
    ramp.color_ramp.elements[1].position = 0.92
    ramp.color_ramp.elements[1].color = hex2lin("#2f83b8")
    nt.links.new(sep.outputs["Z"], ramp.inputs["Fac"])
    nt.links.new(ramp.outputs["Color"], b.inputs["Base Color"])
    set_in(b, ["Roughness"], 0.10)
    set_in(b, ["Specular IOR Level", "Specular"], 0.75)
    set_in(b, ["Coat Weight", "Clearcoat"], 1.0)
    set_in(b, ["Coat Roughness", "Clearcoat Roughness"], 0.02)
    m.diffuse_color = hex2lin("#12324e")
    return m


# ================================================================ face
def surf_hit(body, origin, direction):
    ok, loc, nor, idx = body.ray_cast(Vector(origin), Vector(direction).normalized())
    return (loc, nor) if ok else (None, None)


def radial_hit(body, p0):
    """在过 p0 的水平截面内，把点吸附到体表"""
    c = Vector((0.0, 0.0, p0[2]))
    d = Vector((p0[0], p0[1], 0.0))
    if d.length < 1e-6:
        return None, None
    d.normalize()
    return surf_hit(body, c + d * 2.4, -d)


def build_face(body, mat_eye_m, mat_dark, mat_white):
    P = []
    EYE_Z = 0.356
    for s in (1, -1):
        nm = "Eye_L" if s > 0 else "Eye_R"
        loc, nor = surf_hit(body, (s * 0.138, -2.4, EYE_Z), (0, 1, 0))
        if loc is None:
            loc = Vector((s * 0.138, -0.28, EYE_Z))
        c = loc + Vector((0, 0.046, 0))
        eye = sphere(nm, loc=c, scale=(0.092, 0.088, 0.098), seg=56, ring=36,
                     rot=(0, 0, math.radians(s * -9)))
        add_subsurf(eye, 1)
        eye.data.materials.append(mat_eye_m)
        P.append(eye)

        ey = c.y - 0.086
        h1 = sphere(nm + "_HL1", loc=(c.x - s * 0.026, ey - 0.010, c.z + 0.036),
                    scale=(0.028, 0.015, 0.030), seg=28, ring=18)
        h1.data.materials.append(mat_white); P.append(h1)
        h2 = sphere(nm + "_HL2", loc=(c.x + s * 0.029, ey - 0.004, c.z - 0.036),
                    scale=(0.012, 0.009, 0.013), seg=22, ring=14)
        h2.data.materials.append(mat_white); P.append(h2)

        for i in range(3):
            t = (i - 1) * 0.44
            bx = c.x + s * (t * 0.076)
            bz = c.z + 0.076 - abs(t) * 0.020
            by = c.y - 0.056 + abs(t) * 0.020
            p1 = Vector((bx, by, bz))
            p2 = Vector((bx + s * (0.010 + t * 0.019), by - 0.003, bz + 0.026))
            p3 = Vector((bx + s * (0.017 + t * 0.029), by + 0.005, bz + 0.048))
            cp = catmull([tuple(p1), tuple(p2), tuple(p3)], 12)
            rad = [0.0054 * (1 - (k / 11) ** 1.25) + 0.0003 for k in range(12)]
            lash = tube(nm + "_Lash%d" % i, cp, rad, seg=10)
            lash.data.materials.append(mat_dark); P.append(lash)

    arc = []
    for i in range(13):
        t = i / 12.0
        x = (t - 0.5) * 2 * 0.078
        z = 0.216 - 0.018 * math.cos(math.pi * (t - 0.5))
        loc, nor = surf_hit(body, (x, -2.4, z), (0, 1, 0))
        if loc:
            arc.append(loc + Vector((0, -0.003, 0)))
    if len(arc) >= 3:
        cp = catmull([tuple(q) for q in arc], 26)
        rad = [0.0050 * (1 - 0.55 * abs(k / 25 - 0.5) * 2) for k in range(26)]
        mouth = tube("Mouth", cp, rad, seg=12)
        mouth.data.materials.append(mat_dark)
        P.append(mouth)
    return P


# ================================================================ necklace
def build_necklace(body, mat_bead, mat_bell, mat_metal, mat_pearl, mat_text):
    P = []
    NB = 26
    ring = []
    for i in range(NB):
        a = 2 * math.pi * i / NB
        R = 0.220
        front = max(0.0, -math.cos(a))
        p0 = Vector((math.sin(a) * R,
                     -math.cos(a) * R,
                     0.062 - 0.020 * front ** 1.4))
        loc, nor = radial_hit(body, p0)
        if loc is None:
            continue
        n = nor if nor is not None else Vector((0, 0, 1))
        ring.append(loc + n * 0.009)
    if not ring:
        return P

    for i, pt in enumerate(ring):
        b = sphere("Bead_%02d" % i, loc=pt, scale=(0.0212, 0.0212, 0.0212), seg=24, ring=16)
        b.data.materials.append(mat_bead)
        P.append(b)

    low = min(ring, key=lambda q: q.y)
    bx, by, bz = low.x, low.y - 0.026, low.z - 0.044

    # 铃铛：水晶球铃
    SEG = 44
    bell = sphere("Bell", loc=(bx, by, bz), scale=(0.054, 0.052, 0.060),
                  seg=48, ring=32)
    bell.data.materials.append(mat_bell)
    P.append(bell)

    bpy.ops.mesh.primitive_torus_add(major_radius=0.0385, minor_radius=0.0055,
                                     major_segments=44, minor_segments=10,
                                     location=(bx, by, bz - 0.049))
    rim = bpy.context.object; rim.name = "Bell_Rim"; shade_smooth(rim)
    rim.data.materials.append(mat_metal); P.append(rim)

    bpy.ops.mesh.primitive_torus_add(major_radius=0.0140, minor_radius=0.0034,
                                     major_segments=26, minor_segments=8,
                                     location=(bx, by, bz + 0.062))
    loop = bpy.context.object; loop.name = "Bell_Loop"; shade_smooth(loop)
    loop.data.materials.append(mat_metal); P.append(loop)

    gl = sphere("Bell_Glow", loc=(bx, by - 0.006, bz - 0.002),
                scale=(0.021, 0.021, 0.021), seg=22, ring=14)
    gl.data.materials.append(simple_mat("M_BellGlow", "#bff3ff", rough=0.2,
                                        emit="#8fe9ff", emit_str=2.6))
    P.append(gl)

    # 吊牌
    tag_prof = [(0.000, -0.076), (0.018, -0.064), (0.034, -0.046), (0.045, -0.022),
                (0.050, 0.000), (0.045, 0.021), (0.034, 0.038), (0.018, 0.051),
                (0.000, 0.059)]
    tv, tf, tidx = [], [], []
    for (r, z) in tag_prof:
        if r < 1e-6:
            tidx.append(("p", len(tv))); tv.append((0, 0, z))
        else:
            base = len(tv)
            for j in range(SEG):
                a = 2 * math.pi * j / SEG
                tv.append((math.cos(a) * r, math.sin(a) * r, z))
            tidx.append(("r", base))
    for i in range(len(tidx) - 1):
        t0, v0 = tidx[i]; t1, v1 = tidx[i + 1]
        if t0 == "p" and t1 == "r":
            for j in range(SEG):
                tf.append((v0, v1 + j, v1 + (j + 1) % SEG))
        elif t0 == "r" and t1 == "p":
            for j in range(SEG):
                tf.append((v0 + j, v1, v0 + (j + 1) % SEG))
        else:
            for j in range(SEG):
                tf.append((v0 + j, v1 + j, v1 + (j + 1) % SEG, v0 + (j + 1) % SEG))
    tag = mesh_from("Tag_Pearl", tv, tf)
    tag.location = (bx, by - 0.012, bz - 0.092)
    tag.scale = (1.0, 0.62, 1.0)
    shade_smooth(tag)
    tag.data.materials.append(mat_pearl)
    P.append(tag)

    try:
        bpy.ops.object.text_add(location=(bx, by - 0.036, bz - 0.112),
                                rotation=(math.radians(90), 0, 0))
        txt = bpy.context.object
        txt.name = "Tag_Text"
        txt.data.body = "\u5c0f\u9cb8\u5b9d"
        txt.data.align_x = "CENTER"
        txt.data.align_y = "CENTER"
        txt.data.size = 0.026
        txt.data.extrude = 0.0012
        txt.data.materials.append(mat_text)
        bpy.ops.object.convert(target="MESH")
        P.append(bpy.context.object)
    except Exception as ex:
        print("text skip", ex)
    return P


# ================================================================ build
def build():
    mat_skin = simple_mat("M_WhaleSkin", ICE, rough=0.32)
    set_in(mat_skin.node_tree.nodes["Principled BSDF"], ["Subsurface Weight", "Subsurface"], 0.12)
    set_in(mat_skin.node_tree.nodes["Principled BSDF"], ["Subsurface Radius"], (0.16, 0.10, 0.07))
    set_in(mat_skin.node_tree.nodes["Principled BSDF"], ["Subsurface Scale"], 0.06)

    body = build_body()
    body.data.materials.append(mat_body())

    P = [body]
    P += build_fins(mat_skin)

    mat_eye_m = mat_eye()
    mat_dark = simple_mat("M_Dark", "#13364f", rough=0.30)
    mat_white = simple_mat("M_HL", "#ffffff", rough=0.05, emit="#ffffff", emit_str=0.55)
    P += build_face(body, mat_eye_m, mat_dark, mat_white)

    mat_bead = simple_mat("M_Bead", "#d8f4ff", rough=0.05, transmission=0.30,
                          ior=1.45, emit="#bfeeff", emit_str=0.20, coat=0.9)
    mat_bell = simple_mat("M_CrystalBell", "#3fc9f7", rough=0.04, transmission=0.30,
                          ior=1.46, emit="#7fe3ff", emit_str=0.85, coat=1.0)
    mat_metal = simple_mat("M_Metal", "#a6dfff", rough=0.20, metal=0.90)
    mat_pearl = simple_mat("M_Pearl", "#fdf7ec", rough=0.16, sss=0.20, coat=0.8)
    mat_text = simple_mat("M_TagText", "#2c6d94", rough=0.35)
    P += build_necklace(body, mat_bead, mat_bell, mat_metal, mat_pearl, mat_text)

    # 环境小气泡
    for i in range(9):
        a = i * 2.399963
        rr = 0.86 + (i % 4) * 0.20
        zz = -0.24 + ((i * 0.41) % 1.15)
        bs = sphere("Bubble_%d" % i,
                    loc=(math.cos(a) * rr * 0.92, math.sin(a) * rr * 0.62 + 0.06, zz),
                    scale=(0.018 + (i % 3) * 0.009,) * 3, seg=20, ring=14)
        bs.data.materials.append(simple_mat("M_Bubble", "#eaffff", rough=0.02,
                                            transmission=0.85, ior=1.33,
                                            emit="#dff8ff", emit_str=0.10))
        P.append(bs)
    return P


# ================================================================ rig
BONES = [
    # name,      head,                  tail,                    parent
    ("Root",     (0, 0, 0.00),          (0, 0, 0.10),            None),
    ("Spine",    (0, 0, -0.52),         (0, 0, -0.10),           "Root"),
    ("Chest",    (0, 0, -0.10),         (0, 0, 0.10),            "Spine"),
    ("Head",     (0, 0, 0.10),          (0, 0, 0.52),            "Chest"),
    ("Tail1",    (0, 0.015, -0.38),     (0, 0.105, -0.53),       "Spine"),
    ("Tail2",    (0, 0.105, -0.53),     (0, 0.225, -0.655),      "Tail1"),
    ("Fluke_L",  (0.03, 0.225, -0.655), (0.27, 0.27, -0.85),     "Tail2"),
    ("Fluke_R",  (-0.03, 0.225, -0.655), (-0.27, 0.27, -0.85),   "Tail2"),
    ("Pec_L",    (0.17, -0.05, -0.12),  (0.32, -0.18, -0.31),    "Chest"),
    ("Pec_R",    (-0.17, -0.05, -0.12), (-0.32, -0.18, -0.31),   "Chest"),
    ("Bell",     (0, -0.26, -0.06),     (0, -0.30, -0.16),       "Chest"),
]


def rig(body, parts):
    arm_data = bpy.data.armatures.new("WhaleRig")
    arm = bpy.data.objects.new("WhaleRig", arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    for (nm, h, t, par) in BONES:
        b = eb.new(nm)
        b.head = Vector(h)
        b.tail = Vector(t)
        if par:
            b.parent = eb[par]
    bpy.ops.object.mode_set(mode="OBJECT")

    # body：自动权重
    for o in bpy.context.selected_objects:
        o.select_set(False)
    body.select_set(True)
    arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.parent_set(type="ARMATURE_AUTO")
    # 让 armature 在 subsurf 之前生效
    for md in body.modifiers:
        if md.type == "ARMATURE":
            bpy.context.view_layer.objects.active = body
            bpy.ops.object.modifier_move_to_index(modifier=md.name, index=0)
            break

    # 配件：按名字刚性绑到骨骼
    def target(name):
        if name.startswith(("Eye", "Lash", "Mouth")):
            return "Head"
        if name.startswith(("Bead", "Bell", "Tag")):
            return "Chest"
        if name.startswith("Fluke_L"):
            return "Fluke_R" if False else "Tail2"
        if name.startswith("Fluke"):
            return "Tail2"
        if name.startswith("Pec_L"):
            return "Pec_L"
        if name.startswith("Pec_R"):
            return "Pec_R"
        if name.startswith(("Fin_Back",)):
            return "Chest"
        return None

    for o in parts:
        if o is body:
            continue
        bn = target(o.name)
        if not bn:
            continue
        vg = o.vertex_groups.new(name=bn)
        vg.add(list(range(len(o.data.vertices))), 1.0, "REPLACE")
        o.parent = arm
        o.matrix_parent_inverse = arm.matrix_world.inverted()
        md = o.modifiers.new("Armature", "ARMATURE")
        md.object = arm
    print("RIG ok bones=%d" % len(arm_data.bones))
    return arm


def key_bone(arm, bone, frame, loc=None, rot=None, scale=None):
    pb = arm.pose.bones[bone]
    pb.rotation_mode = "XYZ"
    if loc is not None:
        pb.location = loc
        pb.keyframe_insert("location", frame=frame)
    if rot is not None:
        pb.rotation_euler = rot
        pb.keyframe_insert("rotation_euler", frame=frame)
    if scale is not None:
        pb.scale = scale
        pb.keyframe_insert("scale", frame=frame)


def new_action(arm, name):
    if not arm.animation_data:
        arm.animation_data_create()
    act = bpy.data.actions.new(name)
    act.use_fake_user = True
    arm.animation_data.action = act
    return act


def anim_idle(arm):
    """待机漂浮：缓缓上下浮动 + 呼吸 + 尾巴左右轻摆
       轴向：X=俯仰(点头)  Y=转身(左右看)  Z=侧倾/摆尾"""
    new_action(arm, "Idle_Float")
    R = math.radians
    for f in (1, 25, 49, 73, 97):
        ph = (f - 1) / 96.0 * 2 * math.pi
        key_bone(arm, "Root", f, loc=(0, 0, 0.028 * math.sin(ph)))
        key_bone(arm, "Spine", f, rot=(R(1.2 * math.sin(ph)), 0, R(2.2 * math.sin(ph + 0.5))))
        key_bone(arm, "Chest", f, rot=(R(-1.0 * math.sin(ph)), 0, R(-1.4 * math.sin(ph + 0.5))))
        key_bone(arm, "Head", f, rot=(R(2.4 * math.sin(ph + 1.1)), R(3.0 * math.sin(ph + 0.3)), 0))
        key_bone(arm, "Tail1", f, rot=(0, 0, R(8 * math.sin(ph + 0.4))))
        key_bone(arm, "Tail2", f, rot=(0, 0, R(12 * math.sin(ph + 1.0))))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, R(7 * math.sin(ph + 1.6))))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, R(7 * math.sin(ph + 1.6))))
        key_bone(arm, "Pec_L", f, rot=(R(-4 * math.sin(ph + 0.7)), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(R(4 * math.sin(ph + 0.7)), 0, 0))


def anim_swim(arm):
    """快速游动：尾巴左右大幅摆动，身体 S 形传导"""
    new_action(arm, "Swim_Fast")
    R = math.radians
    N = 29
    for f in range(1, N + 1, 4):
        ph = (f - 1) / (N - 1) * 2 * math.pi
        key_bone(arm, "Root", f, loc=(0, 0, 0.010 * math.sin(ph)), rot=(0, R(-5 * math.sin(ph)), 0))
        key_bone(arm, "Spine", f, rot=(R(-2), 0, R(5 * math.sin(ph + 0.3))))
        key_bone(arm, "Chest", f, rot=(R(-4), 0, R(-5 * math.sin(ph))))
        key_bone(arm, "Head", f, rot=(R(-6), R(-4 * math.sin(ph - 0.3)), 0))
        key_bone(arm, "Tail1", f, rot=(R(6), 0, R(24 * math.sin(ph + 0.9))))
        key_bone(arm, "Tail2", f, rot=(R(8), 0, R(32 * math.sin(ph + 1.6))))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, R(14 * math.sin(ph + 2.2))))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, R(14 * math.sin(ph + 2.2))))
        key_bone(arm, "Pec_L", f, rot=(R(-14 * math.sin(ph + 1.0)), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(R(14 * math.sin(ph + 1.0)), 0, 0))


def anim_happy(arm):
    """开心跳：下蹲 → 弹起转一圈 → 落回"""
    new_action(arm, "Action_Happy")
    R = math.radians
    keys = [
        (1, 0.000, 0, 0.0),
        (8, -0.034, 0, 0.0),
        (17, 0.080, 170, -12.0),
        (25, 0.086, 330, -14.0),
        (33, -0.014, 360, 6.0),
        (40, 0.000, 360, 0.0),
    ]
    for (f, dz, ry, tail) in keys:
        up = dz > 0.03
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(0, R(ry), 0))
        key_bone(arm, "Spine", f, rot=(R(tail * 0.5), 0, 0))
        key_bone(arm, "Head", f, rot=(R(-8 if up else 3), 0, 0))
        key_bone(arm, "Tail1", f, rot=(R(tail), 0, R(8 * math.sin(f * 0.35))))
        key_bone(arm, "Tail2", f, rot=(R(tail * 1.3), 0, R(11 * math.sin(f * 0.35))))
        key_bone(arm, "Fluke_L", f, rot=(R(tail * 0.6), 0, R(14 if up else 0)))
        key_bone(arm, "Fluke_R", f, rot=(R(tail * 0.6), 0, R(14 if up else 0)))
        key_bone(arm, "Pec_L", f, rot=(R(-22 if up else 0), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(R(-22 if up else 0), 0, 0))


def anim_snuggle(arm):
    """撒娇蹭蹭：身体前倾、头部左右轻蹭、胸鳍向前抱"""
    new_action(arm, "Action_Snuggle")
    R = math.radians
    for (f, lean, dz, tilt) in ((1, 0, 0.000, 0), (11, 8, -0.012, 5),
                                (21, 9, -0.016, -5), (31, 7, -0.010, 4),
                                (42, 0, 0.000, 0)):
        key_bone(arm, "Root", f, loc=(0, 0, dz))
        key_bone(arm, "Spine", f, rot=(R(lean), 0, 0))
        key_bone(arm, "Chest", f, rot=(R(lean * 0.5), 0, 0))
        key_bone(arm, "Head", f, rot=(R(lean * 0.9), R(tilt), R(tilt * 0.6)))
        key_bone(arm, "Pec_L", f, rot=(R(-18 * lean / 9), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(R(-18 * lean / 9), 0, 0))
        key_bone(arm, "Tail1", f, rot=(R(-lean * 0.8), 0, 0))


# ================================================================ scene
def setup_world():
    scn = bpy.context.scene
    w = bpy.data.worlds.new("W_Studio"); scn.world = w; w.use_nodes = True
    nt = w.node_tree
    bg = nt.nodes.get("Background"); bg.inputs[1].default_value = 1.0

    tc = nt.nodes.new("ShaderNodeTexCoord")
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Window"], sep.inputs["Vector"])
    ramp = nt.nodes.new("ShaderNodeValToRGB")
    ramp.color_ramp.interpolation = "EASE"
    ramp.color_ramp.elements[0].position = 0.38
    ramp.color_ramp.elements[0].color = hex2lin("#e6f5ff")
    ramp.color_ramp.elements[1].position = 0.94
    ramp.color_ramp.elements[1].color = hex2lin("#8fbdd9")
    nt.links.new(sep.outputs["Y"], ramp.inputs["Fac"])

    # 暗角：屏幕空间到中心距离
    cx = nt.nodes.new("ShaderNodeMath"); cx.operation = "SUBTRACT"
    cx.inputs[1].default_value = 0.5
    nt.links.new(sep.outputs["X"], cx.inputs[0])
    cy = nt.nodes.new("ShaderNodeMath"); cy.operation = "SUBTRACT"
    cy.inputs[1].default_value = 0.5
    nt.links.new(sep.outputs["Y"], cy.inputs[0])
    comb = nt.nodes.new("ShaderNodeCombineXYZ")
    nt.links.new(cx.outputs["Value"], comb.inputs["X"])
    nt.links.new(cy.outputs["Value"], comb.inputs["Y"])
    ln = nt.nodes.new("ShaderNodeVectorMath"); ln.operation = "LENGTH"
    nt.links.new(comb.outputs["Vector"], ln.inputs[0])
    vig = nt.nodes.new("ShaderNodeMapRange")
    vig.inputs["From Min"].default_value = 0.34
    vig.inputs["From Max"].default_value = 0.78
    vig.inputs["To Min"].default_value = 1.0
    vig.inputs["To Max"].default_value = 0.80
    nt.links.new(ln.outputs["Value"], vig.inputs["Value"])
    mul = nt.nodes.new("ShaderNodeMixRGB")
    mul.blend_type = "MULTIPLY"
    mul.inputs[0].default_value = 1.0
    nt.links.new(ramp.outputs["Color"], mul.inputs[1])
    nt.links.new(vig.outputs["Result"], mul.inputs[2])
    nt.links.new(mul.outputs["Color"], bg.inputs[0])

    def area(name, loc, target, size, energy, color, sy=None):
        d = bpy.data.lights.new(name, "AREA"); d.energy = energy
        d.size = size
        if sy: d.size_y = sy
        d.color = hex2lin(color)[:3]
        o = bpy.data.objects.new(name, d); o.location = loc
        o.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
        bpy.context.collection.objects.link(o)

    area("Key",  (-1.30, -1.75, 1.95), (0, -0.05, 0.08), 1.5, 92, "#ffffff", 1.5)
    area("Fill", (2.30, -1.20, 0.55), (0, 0.05, 0.02), 2.6, 32, "#d8ecff", 2.6)
    area("Rim",  (1.10, 1.70, 1.70), (0, 0.10, 0.15), 0.8, 230, "#c8f2ff", 1.3)
    area("Top",  (0.0, 0.05, 2.60), (0, 0.02, 0.10), 2.0, 34, "#ffffff", 2.0)


def setup_render():
    scn = bpy.context.scene
    for eng in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scn.render.engine = eng; break
        except Exception:
            continue
    print("ENGINE:", scn.render.engine)
    if scn.render.engine.startswith("BLENDER_EEVEE"):
        for attr, val in (("taa_render_samples", 96), ("use_raytracing", True),
                          ("use_shadows", True), ("use_bloom", True)):
            if hasattr(scn.eevee, attr):
                try: setattr(scn.eevee, attr, val)
                except Exception: pass
    else:
        scn.cycles.samples = 96; scn.cycles.use_denoising = True
    scn.render.resolution_x = 1100
    scn.render.resolution_y = 1100
    scn.render.image_settings.file_format = "PNG"
    try:
        scn.view_settings.view_transform = "Standard"
        scn.view_settings.look = "None"
    except Exception:
        pass
    scn.view_settings.exposure = -0.50


def add_camera():
    cd = bpy.data.cameras.new("Cam")
    cam = bpy.data.objects.new("Cam", cd)
    bpy.context.collection.objects.link(cam)
    bpy.context.scene.camera = cam
    return cam


def aim(cam, loc, target, ortho=None, lens=70):
    cam.location = loc
    cam.rotation_euler = (Vector(target) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    if ortho:
        cam.data.type = "ORTHO"; cam.data.ortho_scale = ortho
    else:
        cam.data.type = "PERSP"; cam.data.lens = lens


def render_to(p):
    bpy.context.scene.render.filepath = p
    bpy.ops.render.render(write_still=True)
    print("saved", p)


def compose_sheet(paths, out_path, cols=2):
    imgs = []
    for p in paths:
        im = bpy.data.images.load(p); w, h = im.size
        px = np.empty(w*h*4, dtype=np.float32)
        im.pixels.foreach_get(px)
        imgs.append((px.reshape(h, w, 4), w, h))
        bpy.data.images.remove(im)
    w, h = imgs[0][1], imgs[0][2]
    rows = (len(imgs) + cols - 1) // cols
    gap = 10
    W = cols*w + (cols+1)*gap; H = rows*h + (rows+1)*gap
    sheet = np.ones((H, W, 4), dtype=np.float32)
    for c in range(3):
        sheet[:, :, c] = 0.92
    for i, (arr, iw, ih) in enumerate(imgs):
        r, c = divmod(i, cols)
        rr = rows - 1 - r
        y0 = gap + rr*(h+gap); x0 = gap + c*(w+gap)
        sheet[y0:y0+h, x0:x0+w, :] = arr
    out = bpy.data.images.new("sheet", width=W, height=H, alpha=True)
    out.pixels.foreach_set(sheet.reshape(-1))
    out.filepath_raw = out_path; out.file_format = "PNG"; out.save()
    print("sheet saved", out_path)


def main():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    build(); setup_world(); setup_render()
    cam = add_camera()

    if not VIEWS_ONLY:
        aim(cam, (1.08, -3.05, 0.34), (0, 0.00, -0.01), lens=72)
        render_to(os.path.join(OUT, "render_hero.png"))
    if QUICK:
        return

    if not VIEWS_ONLY:
        aim(cam, (0.56, -1.34, 0.42), (0, -0.22, 0.34), lens=132)
        render_to(os.path.join(OUT, "render_face.png"))

    views = {
        "view_front": ((0, -3.4, 0.0), (0, 0, 0.0)),
        "view_side":  ((3.4, 0.0, 0.0), (0, 0, 0.0)),
        "view_back":  ((0, 3.4, 0.0), (0, 0, 0.0)),
        "view_top":   ((0, 0.0, 3.4), (0, 0.0, 0.0)),
    }
    paths = []
    for n, (loc, tgt) in views.items():
        aim(cam, loc, tgt, ortho=1.70)
        p = os.path.join(OUT, n + ".png")
        render_to(p); paths.append(p)
    compose_sheet(paths, os.path.join(OUT, "sheet_fourview.png"))

    # ---- 骨骼与动画
    parts = [o for o in bpy.context.scene.objects if o.type == "MESH"]
    body = bpy.data.objects.get("WhaleBody")
    arm = rig(body, parts)
    anim_idle(arm); anim_swim(arm); anim_happy(arm); anim_snuggle(arm)
    arm.animation_data.action = bpy.data.actions.get("Idle_Float")
    bpy.context.scene.frame_start = 1
    bpy.context.scene.frame_end = 97
    bpy.context.scene.frame_set(1)

    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "xiaojingbao.blend"))
    glb = os.path.join(OUT, "xiaojingbao.glb")
    for kw in (dict(export_format="GLB", export_apply=True, use_selection=False),
               dict(export_format="GLB", use_selection=False)):
        try:
            bpy.ops.export_scene.gltf(filepath=glb, **kw); print("glb ok"); break
        except Exception as ex:
            print("glb retry:", ex)
    print("DONE")


if __name__ == "__main__":
    main()
