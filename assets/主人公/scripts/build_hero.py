"""主人公游戏版：以扫描件为形体基准，重做游戏资产。

流程：导入 -> 减面 -> Smart UV -> 从 1.5M 高模反烘 2048 图集 -> 人形骨骼绑定 -> 6 套动画 -> glb
坐标：X 右、Y 后（正面朝 -Y）、Z 上，身高归一为 1.0，脚底 z=0。
复用 build_pet.py 的工具函数口径（含 wq/wloc 世界轴动画写法）。
骨骼备忘：角色正面朝 -Y，+X 是角色左手（.L），-X 是右手（.R，结印抬手侧）。
"""
import bpy, bmesh, os, sys, math
import numpy as np
from mathutils import Vector, Quaternion

HERE = os.environ.get("HERO_DIR") or os.path.dirname(os.path.abspath(__file__))
# 中间网格：已由 meshopt 减面（保 UV 接缝，脸部不糊），Y-up
REF_GLB = os.path.join(HERE, "hero_mid.glb")
TEXSRC = os.path.join(HERE, "ref2", "texture_pbr_20250901.png")
OUT = os.path.join(HERE, "out")
TEX = os.path.join(OUT, "tex")
os.makedirs(TEX, exist_ok=True)

RATIO = 0.03       # meshopt 减面比例（在 mid-simplify.cjs 里用，Blender 不再减面）
ATLAS = 2048
TARGET_H = 1.0

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SKIP_BAKE = "--nobake" in argv
SKIP_RENDER = "--norender" in argv


# ---------------------------------------------------------------- 1. 几何
def import_mesh():
    """导入已由 meshopt 减面的中间 GLB（原 UV 完整，Y-up 自动转 Z-up）"""
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.gltf(filepath=REF_GLB)
    meshes = [x for x in bpy.context.scene.objects if x.type == "MESH"]
    for o in meshes:
        bpy.ops.object.select_all(action="DESELECT")
        o.select_set(True)
        bpy.context.view_layer.objects.active = o
        bpy.ops.object.parent_clear(type="CLEAR_KEEP_TRANSFORM")
    for x in list(bpy.context.scene.objects):
        if x.type == "EMPTY":
            bpy.data.objects.remove(x, do_unlink=True)
    o = meshes[0]
    bpy.context.view_layer.objects.active = o
    o.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    print("MID faces=%d verts=%d" % (len(o.data.polygons), len(o.data.vertices)))
    return o


def normalize(objs):
    """高度归一到 TARGET_H、底面 z=0、xy 居中"""
    co = np.empty(len(objs[0].data.vertices) * 3)
    objs[0].data.vertices.foreach_get("co", co); co = co.reshape(-1, 3)
    lo = co.min(axis=0); hi = co.max(axis=0)
    s = TARGET_H / (hi[2] - lo[2])
    cx = (lo[0] + hi[0]) / 2.0
    cy = (lo[1] + hi[1]) / 2.0
    for o in objs:
        o.scale = (s, s, s)
        o.location = (-cx * s, -cy * s, -lo[2] * s)
    bpy.ops.object.select_all(action="DESELECT")
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)


def make_low(hi):
    low = hi.copy()
    low.data = hi.data.copy()
    low.name = "Hero"
    low.data.name = "Hero"
    bpy.context.scene.collection.objects.link(low)
    bpy.context.view_layer.objects.active = low
    bpy.ops.object.select_all(action="DESELECT")
    low.select_set(True)
    d = low.modifiers.new("dec", "DECIMATE")
    d.decimate_type = "COLLAPSE"
    d.ratio = RATIO
    d.use_collapse_triangulate = True
    bpy.ops.object.modifier_apply(modifier=d.name)
    bpy.ops.object.shade_smooth()
    print("LOW faces=%d verts=%d" % (len(low.data.polygons), len(low.data.vertices)))
    return low


def smart_uv(low):
    bpy.ops.object.select_all(action="DESELECT")
    low.select_set(True)
    bpy.context.view_layer.objects.active = low
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(89.0), island_margin=0.0025,
                             correct_aspect=True, scale_to_bounds=False)
    bpy.ops.uv.select_all(action="SELECT")
    bpy.ops.uv.pack_islands(rotate=True, margin=0.0025)
    bpy.ops.object.mode_set(mode="OBJECT")
    print("UV done layers=%s" % [u.name for u in low.data.uv_layers])


# ---------------------------------------------------------------- 2. 烘焙
def make_emit_src(hi):
    img = bpy.data.images.load(TEXSRC)
    img.colorspace_settings.name = "sRGB"
    mat = bpy.data.materials.new("BakeSrc")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    em = nt.nodes.new("ShaderNodeEmission")
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = img
    nt.links.new(tx.outputs["Color"], em.inputs["Color"])
    nt.links.new(em.outputs["Emission"], out.inputs["Surface"])
    em.inputs["Strength"].default_value = 1.0
    hi.data.materials.clear()
    hi.data.materials.append(mat)
    return img


def prep_bake_target(low, img):
    mat = bpy.data.materials.new("HeroMat")
    mat.use_nodes = True
    mat.use_backface_culling = True   # 发片/内衬薄壳背面会显示错乱纹理（three.js 单面渲染正常，此处对齐）
    nt = mat.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = img
    tx.interpolation = "Linear"
    nt.links.new(tx.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.35
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["IOR"].default_value = 1.45
    for n in nt.nodes:
        n.select = False
    tx.select = True
    nt.nodes.active = tx
    low.data.materials.clear()
    low.data.materials.append(mat)
    return mat


def bake_atlas(hi, low):
    scn = bpy.context.scene
    scn.render.engine = "CYCLES"
    scn.cycles.device = "CPU"
    scn.cycles.samples = 4
    try:
        scn.cycles.use_denoising = False
    except Exception:
        pass
    img = bpy.data.images.new("HeroAtlas", ATLAS, ATLAS, alpha=False, float_buffer=True)
    prep_bake_target(low, img)
    bpy.ops.object.select_all(action="DESELECT")
    hi.select_set(True)
    low.select_set(True)
    bpy.context.view_layer.objects.active = low
    scn.render.bake.use_pass_direct = False
    scn.render.bake.use_pass_indirect = False
    scn.render.bake.use_pass_color = True
    bpy.ops.object.bake(type="EMIT", use_selected_to_active=True,
                        cage_extrusion=0.02, max_ray_distance=0.06,
                        margin=10, use_clear=True)
    print("BAKE done")
    return img


def save_jpeg(img, path, quality=90):
    scn = bpy.context.scene
    vt = scn.view_settings.view_transform
    lk = scn.view_settings.look
    exp = scn.view_settings.exposure
    gam = scn.view_settings.gamma
    scn.view_settings.view_transform = "Standard"
    try:
        scn.view_settings.look = "None"
    except Exception:
        pass
    scn.view_settings.exposure = 0.0
    scn.view_settings.gamma = 1.0
    isr = scn.render.image_settings
    of, oq = isr.file_format, isr.quality
    isr.file_format = "JPEG"
    isr.quality = quality
    img.save_render(filepath=path, scene=scn)
    isr.file_format = of
    isr.quality = oq
    scn.view_settings.view_transform = vt
    try:
        scn.view_settings.look = lk
    except Exception:
        pass
    scn.view_settings.exposure = exp
    scn.view_settings.gamma = gam
    print("tex jpg", path, os.path.getsize(path))


def swap_material_image(mat, new_img):
    for n in mat.node_tree.nodes:
        if n.type == "TEX_IMAGE":
            n.image = new_img


def save_img(img, path):
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    print("tex saved", path, os.path.getsize(path))


# ---------------------------------------------------------------- 3. 骨骼
# 人形 20 骨。角色朝 -Y：+X=.L（垂手侧），-X=.R（结印抬手侧）。数值来自切片探查。
BONES = [
    ("Root",      (0, 0, 0.52),          (0, 0, 0.60),           None),
    ("Spine",     (0, 0, 0.56),          (0, -0.01, 0.68),       "Root"),
    ("Chest",     (0, -0.01, 0.68),      (0, -0.02, 0.77),       "Spine"),
    ("Neck",      (0, -0.02, 0.78),      (0, -0.03, 0.82),       "Chest"),
    ("Head",      (0, -0.03, 0.82),      (0, -0.04, 0.96),       "Neck"),
    ("Arm_L",     (0.10, 0, 0.74),       (0.13, -0.005, 0.56),   "Chest"),
    ("Forearm_L", (0.13, -0.005, 0.56),  (0.08, -0.10, 0.49),    "Arm_L"),
    ("Hand_L",    (0.08, -0.10, 0.49),   (0.06, -0.14, 0.47),    "Forearm_L"),
    ("Arm_R",     (-0.10, 0, 0.74),      (-0.14, -0.01, 0.56),   "Chest"),
    ("Forearm_R", (-0.14, -0.01, 0.56),  (-0.08, -0.14, 0.50),   "Arm_R"),
    ("Hand_R",    (-0.08, -0.14, 0.50),  (-0.06, -0.19, 0.50),   "Forearm_R"),
    ("Thigh_L",   (0.055, 0, 0.52),      (0.06, 0, 0.28),        "Root"),
    ("Shin_L",    (0.06, 0, 0.28),       (0.06, 0, 0.07),        "Thigh_L"),
    ("Foot_L",    (0.06, 0, 0.06),       (0.06, -0.10, 0.02),    "Shin_L"),
    ("Thigh_R",   (-0.055, 0, 0.52),     (-0.06, 0, 0.28),       "Root"),
    ("Shin_R",    (-0.06, 0, 0.28),      (-0.06, 0, 0.07),       "Thigh_R"),
    ("Foot_R",    (-0.06, 0, 0.06),      (-0.06, -0.10, 0.02),   "Shin_R"),
]


def shell_verts(ob):
    bm = bmesh.new(); bm.from_mesh(ob.data)
    bm.verts.ensure_lookup_table()
    seen = set(); shells = []
    for f in bm.faces:
        if f.index in seen:
            continue
        stack = [f]; vs = set()
        while stack:
            cf = stack.pop()
            if cf.index in seen:
                continue
            seen.add(cf.index)
            for v in cf.verts:
                vs.add(v.index)
            for e in cf.edges:
                for nf in e.link_faces:
                    if nf.index not in seen:
                        stack.append(nf)
        shells.append(sorted(vs))
    bm.free()
    shells.sort(key=len, reverse=True)
    return shells


def build_armature(low):
    arm_data = bpy.data.armatures.new("HeroRig")
    arm = bpy.data.objects.new("HeroRig", arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.object.mode_set(mode="EDIT")
    eb = arm_data.edit_bones
    for (nm, h, t, par) in BONES:
        b = eb.new(nm)
        b.head = Vector(h)
        b.tail = Vector(t)
        b.use_connect = False
        if par:
            b.parent = eb[par]
    bpy.ops.object.mode_set(mode="OBJECT")

    # ARMATURE_AUTO 在该扫描件上退化成单骨权重 + 千余零权重顶点，弃用；
    # 手动挂骨架修饰器 + 平滑距离权重（对全部骨骼反距离加权，top-4 归一）
    mod = low.modifiers.new("Armature", "ARMATURE")
    mod.object = arm
    low.parent = arm
    for nm, _, _, _ in BONES:
        if nm not in low.vertex_groups:
            low.vertex_groups.new(name=nm)
    distance_weights(low, arm)
    print("RIG bones=%d vgroups=%d" % (len(arm_data.bones), len(low.vertex_groups)))
    return arm


def distance_weights(low, arm, top=4, power=3.0):
    """平滑距离权重：w=1/d^3 对全部骨段，取最近的 top 根归一化（每根骨骼都能影响到邻近区域）"""
    segs = []
    for b in arm.data.bones:
        segs.append((b.name, b.head_local.copy(), b.tail_local.copy()))
    vgs = {nm: low.vertex_groups[nm] for nm, _, _ in segs}
    n_multi = 0
    for v in low.data.vertices:
        p = v.co
        ds = []
        for nm, h, t in segs:
            ab = t - h
            L = max(ab.length_squared, 1e-9)
            u = max(0.0, min(1.0, (p - h).dot(ab) / L))
            d = ((h + ab * u) - p).length
            ds.append((d, nm))
        ds.sort()
        picked = ds[:top]
        ws = [(nm, 1.0 / max(d, 0.004) ** power) for d, nm in picked]
        tot = sum(w for _, w in ws) or 1.0
        for i, (nm, w) in enumerate(ws):
            vgs[nm].add([v.index], w / tot, "REPLACE")
        if len([1 for _, w in ws if w / tot > 0.05]) > 1:
            n_multi += 1
    print("smooth distance weights: multi-influence verts=%d / %d" % (n_multi, len(low.data.vertices)))


def clean_weights(low, max_infl=2, min_w=0.08):
    """自动权重串区收拾：每顶点只保留最大的 max_infl 个权重并归一化，微小权重清零"""
    n = 0
    for v in low.data.vertices:
        gs = sorted(((g.weight, g.group) for g in v.groups if g.weight > 1e-4), reverse=True)
        if not gs:
            continue
        keep = gs[:max_infl]
        dropped = gs[max_infl:] + [(w, g) for (w, g) in gs[:max_infl] if w < min_w and len(keep) > 1]
        dropped = [(w, g) for (w, g) in dropped if (w, g) not in keep]
        if not dropped:
            continue
        tot = sum(w for w, _ in keep) or 1.0
        for _, gidx in dropped:
            low.vertex_groups[gidx].remove([v.index])
        for w, gidx in keep:
            low.vertex_groups[gidx].add([v.index], w / tot, "REPLACE")
        n += 1
    print("clean weights on %d verts" % n)


def region_lock(low):
    """区域锁：袖子只归同侧手臂骨，头顶/发髻只归头颈骨（防大幅旋转拽飞）"""
    ARM_L = {"Arm_L", "Forearm_L", "Hand_L"}
    ARM_R = {"Arm_R", "Forearm_R", "Hand_R"}
    HEAD = {"Neck", "Head"}
    vg_index = {vg.name: vg.index for vg in low.vertex_groups}
    bone_names = set(vg_index.keys())
    arm_l = {vg_index[b] for b in ARM_L if b in vg_index}
    arm_r = {vg_index[b] for b in ARM_R if b in vg_index}
    head = {vg_index[b] for b in HEAD if b in vg_index}
    n_sl, n_hd = 0, 0
    for v in low.data.vertices:
        p = v.co
        gs = {g.group: g.weight for g in v.groups if g.weight > 1e-4}
        if not gs:
            continue
        new = None
        # 袖子区（左右外侧、腰到肩的高度）
        if 0.28 < p.z < 0.74 and abs(p.x) > 0.10:
            allow = arm_l if p.x > 0 else arm_r
            flt = {g: w for g, w in gs.items() if g in allow}
            if flt and sum(flt.values()) > 0.25:
                new = flt
                n_sl += 1
        # 头顶/发髻区
        elif p.z > 0.80 and abs(p.x) < 0.10:
            flt = {g: w for g, w in gs.items() if g in head}
            if flt and sum(flt.values()) > 0.3:
                new = flt
                n_hd += 1
        if new:
            tot = sum(new.values()) or 1.0
            for g in list(gs.keys()):
                if g not in new:
                    low.vertex_groups[g].remove([v.index])
            for g, w in new.items():
                low.vertex_groups[g].add([v.index], w / tot, "REPLACE")
    print("region lock: sleeve verts=%d head verts=%d" % (n_sl, n_hd))


def bind_small_shells(low, arm):
    """独立小壳（发髻/玉穗等）按中心最近骨段刚性绑定"""
    shells = shell_verts(low)
    body = shells[0]
    segs = [(b.name, b.head_local.copy(), b.tail_local.copy()) for b in arm.data.bones]
    n = 0
    for sh in shells[1:]:
        if len(sh) > 4000:
            continue
        co = np.array([list(low.data.vertices[i].co) for i in sh])
        c = co.mean(axis=0)
        best, bd = None, 1e9
        for nm, h, t in segs:
            ab = t - h
            L = max(ab.length_squared, 1e-9)
            u = max(0.0, min(1.0, (Vector(c) - h).dot(ab) / L))
            d = ((h + ab * u) - Vector(c)).length
            if d < bd:
                bd, best = d, nm
        for vg in low.vertex_groups:
            vg.remove(sh)
        (low.vertex_groups.get(best) or low.vertex_groups.new(name=best)).add(sh, 1.0, "REPLACE")
        n += 1
        print("  small shell verts=%d center=(%.3f,%.3f,%.3f) -> %s (d=%.3f)" % (len(sh), c[0], c[1], c[2], best, bd))
    print("SMALL SHELLS bound=%d (body verts=%d)" % (n, len(body)))


# ---------------------------------------------------------------- 4. 动画
def rest_rot(pb):
    return pb.bone.matrix_local.to_3x3()


def wq(pb, rx=0.0, ry=0.0, rz=0.0):
    """世界轴旋转 -> 骨骼局部四元数。X=俯仰 Y=侧倾 Z=偏航"""
    R = rest_rot(pb)
    q = (Quaternion((1, 0, 0), math.radians(rx)) @
         Quaternion((0, 1, 0), math.radians(ry)) @
         Quaternion((0, 0, 1), math.radians(rz)))
    return ((R.inverted() @ q.to_matrix() @ R).to_quaternion())


def wloc(pb, v):
    return rest_rot(pb).inverted() @ Vector(v)


def key_bone(arm, bone, frame, loc=None, rot=None, scale=None):
    pb = arm.pose.bones[bone]
    pb.rotation_mode = "QUATERNION"
    if loc is not None:
        pb.location = wloc(pb, loc)
        pb.keyframe_insert("location", frame=frame)
    if rot is not None:
        pb.rotation_quaternion = wq(pb, *rot)
        pb.keyframe_insert("rotation_quaternion", frame=frame)
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
    """待机：呼吸起伏、重心微移、头部缓慢环视、袖摆"""
    new_action(arm, "Idle_Stand")
    for f in (1, 25, 49, 73, 97):
        ph = (f - 1) / 96.0 * 2 * math.pi
        s, c = math.sin(ph), math.cos(ph)
        key_bone(arm, "Root", f, loc=(0, 0, 0.006 * s), rot=(0, 0.8 * s, 1.5 * s))
        key_bone(arm, "Spine", f, rot=(1.2 * s, 1.5 * math.sin(ph + 0.5), 0), scale=(1, 1, 1 + 0.008 * s))
        key_bone(arm, "Chest", f, rot=(-0.9 * s, -1.0 * math.sin(ph + 0.5), 0), scale=(1 + 0.01 * c, 1, 1))
        key_bone(arm, "Neck", f, rot=(0.6 * s, 0, 0))
        key_bone(arm, "Head", f, rot=(1.8 * math.sin(ph + 1.1), 1.5 * math.sin(ph + 0.3), 3.0 * math.sin(ph + 0.3)))
        key_bone(arm, "Arm_L", f, rot=(1.2 * s, 1.5 * s, 1.5 * s))
        key_bone(arm, "Forearm_L", f, rot=(2.0 * math.sin(ph + 0.7), 1.5 * s, 2.0 * s))
        key_bone(arm, "Hand_L", f, rot=(2.0 * s, 0, 0))
        key_bone(arm, "Arm_R", f, rot=(1.2 * s, -1.5 * s, -1.5 * s))
        key_bone(arm, "Forearm_R", f, rot=(2.2 * math.sin(ph + 0.9), -1.5 * s, -2.5 * s))
        key_bone(arm, "Hand_R", f, rot=(2.5 * math.sin(ph + 1.2), 0, 0))
        key_bone(arm, "Thigh_L", f, rot=(0, 0.6 * s, 0))
        key_bone(arm, "Thigh_R", f, rot=(0, -0.6 * s, 0))


def anim_walk(arm):
    """走路（原地）：袍摆、重心左右移、双臂前后摆、膝踝交替（下摆处可见）"""
    new_action(arm, "Loco_Walk")
    N = 49
    for f in range(1, N + 1, 4):
        ph = (f - 1) / (N - 1) * 2 * math.pi
        s, c = math.sin(ph), math.cos(ph)
        key_bone(arm, "Root", f, loc=(0, 0, 0.014 * abs(c) - 0.007), rot=(1.5, 2.2 * s, 3.0 * s))
        key_bone(arm, "Spine", f, rot=(2.0, 2.0 * s, -2.5 * s))
        key_bone(arm, "Chest", f, rot=(1.0, 1.5 * s, -2.0 * s))
        key_bone(arm, "Neck", f, rot=(-1.5, 0, 1.5 * s))
        key_bone(arm, "Head", f, rot=(-2.0, 0, 2.0 * s))
        key_bone(arm, "Arm_L", f, rot=(-9 * s, 2.0 * s, 3.0))
        key_bone(arm, "Forearm_L", f, rot=(-7 * s - 4, 0, 0))
        key_bone(arm, "Hand_L", f, rot=(-4 * s, 0, 0))
        key_bone(arm, "Arm_R", f, rot=(9 * s, -2.0 * s, -3.0))
        key_bone(arm, "Forearm_R", f, rot=(7 * s - 4, 0, 0))
        key_bone(arm, "Hand_R", f, rot=(4 * s, 0, 0))
        key_bone(arm, "Thigh_L", f, rot=(16 * s, 1.5 * s, 0))
        key_bone(arm, "Shin_L", f, rot=(-max(0.0, 22 * s) - 6, 0, 0))
        key_bone(arm, "Foot_L", f, rot=(8 * s, 0, 0))
        key_bone(arm, "Thigh_R", f, rot=(-16 * s, -1.5 * s, 0))
        key_bone(arm, "Shin_R", f, rot=(-max(0.0, -22 * s) - 6, 0, 0))
        key_bone(arm, "Foot_R", f, rot=(-8 * s, 0, 0))


def anim_run(arm):
    """跑步（原地）：前倾、起伏大、摆臂快"""
    new_action(arm, "Loco_Run")
    N = 33
    for f in range(1, N + 1, 3):
        ph = (f - 1) / (N - 1) * 2 * math.pi
        s, c = math.sin(ph), math.cos(ph)
        key_bone(arm, "Root", f, loc=(0, 0, 0.026 * abs(c) - 0.013), rot=(5.0, 3.0 * s, 4.0 * s))
        key_bone(arm, "Spine", f, rot=(4.0, 3.0 * s, -3.5 * s))
        key_bone(arm, "Chest", f, rot=(3.0, 2.0 * s, -3.0 * s))
        key_bone(arm, "Neck", f, rot=(-3.5, 0, 2.0 * s))
        key_bone(arm, "Head", f, rot=(-3.5, 0, 2.5 * s))
        key_bone(arm, "Arm_L", f, rot=(-18 * s, 3.0 * s, 4.0))
        key_bone(arm, "Forearm_L", f, rot=(-16 * s - 20, 0, 0))
        key_bone(arm, "Hand_L", f, rot=(-6 * s, 0, 0))
        key_bone(arm, "Arm_R", f, rot=(18 * s, -3.0 * s, -4.0))
        key_bone(arm, "Forearm_R", f, rot=(16 * s - 20, 0, 0))
        key_bone(arm, "Hand_R", f, rot=(6 * s, 0, 0))
        key_bone(arm, "Thigh_L", f, rot=(28 * s, 2.0 * s, 0))
        key_bone(arm, "Shin_L", f, rot=(-max(0.0, 38 * s) - 14, 0, 0))
        key_bone(arm, "Foot_L", f, rot=(12 * s, 0, 0))
        key_bone(arm, "Thigh_R", f, rot=(-28 * s, -2.0 * s, 0))
        key_bone(arm, "Shin_R", f, rot=(-max(0.0, -38 * s) - 14, 0, 0))
        key_bone(arm, "Foot_R", f, rot=(-12 * s, 0, 0))


def anim_jump(arm):
    """跳跃：下蹲 -> 蹬地腾空 -> 收腿 -> 落地缓冲 -> 回正（带竖直 root motion）"""
    new_action(arm, "Action_Jump")
    keys = [
        (1,  0.000, 0, 0, 0),      # 起始
        (9,  -0.075, 8, -30, -22), # 下蹲
        (14, 0.000, -4, 8, 4),     # 蹬地伸展
        (22, 0.240, -6, -35, -30), # 腾空收腿
        (30, 0.260, -4, -30, -24), # 空中顶点
        (38, 0.010, 4, -24, -14),  # 触地
        (44, -0.045, 6, -26, -18), # 缓冲
        (52, 0.000, 0, 0, 0),      # 回正
    ]
    for (f, dz, lean, thigh, shin) in keys:
        air = dz > 0.05
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(lean * 0.4, 0, 0))
        key_bone(arm, "Spine", f, rot=(lean, 0, 0))
        key_bone(arm, "Chest", f, rot=(lean * 0.6, 0, 0))
        key_bone(arm, "Neck", f, rot=(-lean * 0.7, 0, 0))
        key_bone(arm, "Head", f, rot=(-lean * 0.6 if not air else -8, 0, 0))
        key_bone(arm, "Arm_L", f, rot=(-28 if air else (-18 if lean < 0 else 10), 0, 8 if air else 3))
        key_bone(arm, "Forearm_L", f, rot=(-14 if air else -8, 0, 0))
        key_bone(arm, "Arm_R", f, rot=(-28 if air else (-18 if lean < 0 else 10), 0, -8 if air else -3))
        key_bone(arm, "Forearm_R", f, rot=(-14 if air else -8, 0, 0))
        key_bone(arm, "Thigh_L", f, rot=(thigh, 0, 0))
        key_bone(arm, "Shin_L", f, rot=(shin, 0, 0))
        key_bone(arm, "Foot_L", f, rot=(-10 if air else 6, 0, 0))
        key_bone(arm, "Thigh_R", f, rot=(thigh, 0, 0))
        key_bone(arm, "Shin_R", f, rot=(shin, 0, 0))
        key_bone(arm, "Foot_R", f, rot=(-10 if air else 6, 0, 0))


def anim_wave(arm):
    """打招呼：右手抬起摆动两下，头部微歪（单次）"""
    new_action(arm, "Action_Wave")
    keys = [
        (1,  0, 0, 0),
        (8,  -8, -12, -18),   # 抬臂张开
        (14, -5, -25, 14),    # 摆 1
        (20, -5, -12, -16),   # 摆 2
        (26, -5, -25, 14),    # 摆 3
        (32, -6, -14, -12),
        (40, 0, 0, 0),
    ]
    for (f, armx, fry, hrz) in keys:
        key_bone(arm, "Root", f, loc=(0, 0, 0), rot=(0, 0, -2 if f in (14, 26) else 0))
        key_bone(arm, "Spine", f, rot=(-1.5 if f else 0, 0, 0))
        key_bone(arm, "Chest", f, rot=(-1.0, 0, -2 if f in (14, 26) else 0))
        key_bone(arm, "Neck", f, rot=(-2, 2.5 if f else 0, 2 if f else 0))
        key_bone(arm, "Head", f, rot=(-3, 3 if f else 0, 4 if f else 0))
        key_bone(arm, "Arm_L", f, rot=(1.2, 0, 0))
        key_bone(arm, "Forearm_L", f, rot=(-6, 0, 0))
        key_bone(arm, "Arm_R", f, rot=(armx, 0, 0))
        key_bone(arm, "Forearm_R", f, rot=(-22, fry, 0))
        key_bone(arm, "Hand_R", f, rot=(0, 0, hrz))


def anim_cast(arm):
    """施法：右手结印前推、左臂展开平衡、袍袖飞扬、身体后仰再前送（单次）"""
    new_action(arm, "Action_Cast")
    keys = [
        (1,  0.000, 0, 0, 0),
        (10, -0.010, 3, -18, -14),   # 蓄力收手
        (20, 0.008, -6, 24, 10),     # 推掌发出
        (28, 0.014, -8, 34, 16),     # 掌到极点
        (38, 0.006, -4, 26, 10),
        (50, 0.000, 0, 0, 0),        # 收势
        (60, 0.000, 0, 0, 0),
    ]
    for (f, dz, lean, frx, fry) in keys:
        push = frx > 10
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(lean * 0.5, 0, -3 if push else 0))
        key_bone(arm, "Spine", f, rot=(lean, 0, -2 if push else 0))
        key_bone(arm, "Chest", f, rot=(lean * 0.7, 0, -3 if push else 0))
        key_bone(arm, "Neck", f, rot=(-lean * 0.8, 0, 0))
        key_bone(arm, "Head", f, rot=(-lean * 0.8, 0, 3 if push else 0))
        key_bone(arm, "Arm_L", f, rot=(-16 if push else 6, 14 if push else 0, 10 if push else 0))
        key_bone(arm, "Forearm_L", f, rot=(-10 if push else -12, 8 if push else 0, 0))
        key_bone(arm, "Hand_L", f, rot=(6 if push else 0, 0, 0))
        key_bone(arm, "Arm_R", f, rot=(-6 if push else 10, 0, -4 if push else 0))
        key_bone(arm, "Forearm_R", f, rot=(frx - 20, fry, 0))
        key_bone(arm, "Hand_R", f, rot=(8 if push else 0, 0, 6 if push else 0))
        key_bone(arm, "Thigh_L", f, rot=(-4 if push else 0, 0, 3 if push else 0))
        key_bone(arm, "Thigh_R", f, rot=(4 if push else 0, 0, -3 if push else 0))


ANIMS = [
    ("Idle_Stand", anim_idle, 97),
    ("Loco_Walk", anim_walk, 49),
    ("Loco_Run", anim_run, 33),
    ("Action_Jump", anim_jump, 52),
    ("Action_Wave", anim_wave, 40),
    ("Action_Cast", anim_cast, 60),
]


def build_anims(arm):
    for nm, fn, _ in ANIMS:
        fn(arm)
    arm.animation_data.action = bpy.data.actions.get("Idle_Stand")
    print("ACTIONS=%d %s" % (len(bpy.data.actions), [a.name for a in bpy.data.actions]))


# ---------------------------------------------------------------- 5. 场景
def setup_scene():
    scn = bpy.context.scene
    scn.render.engine = "BLENDER_EEVEE"
    scn.render.film_transparent = False
    scn.view_settings.view_transform = "AgX"
    scn.view_settings.exposure = 0.15
    scn.render.resolution_x = 900
    scn.render.resolution_y = 900
    w = bpy.data.worlds.new("W"); scn.world = w
    w.use_nodes = True
    nt = w.node_tree
    bg = nt.nodes["Background"]
    bg.inputs[0].default_value = (0.035, 0.06, 0.10, 1.0)
    bg.inputs[1].default_value = 1.0
    tc = nt.nodes.new("ShaderNodeTexCoord")
    grad = nt.nodes.new("ShaderNodeValToRGB")
    grad.color_ramp.elements[0].position = 0.25
    grad.color_ramp.elements[0].color = (0.02, 0.05, 0.09, 1)
    grad.color_ramp.elements[1].position = 0.85
    grad.color_ramp.elements[1].color = (0.09, 0.17, 0.26, 1)
    sep = nt.nodes.new("ShaderNodeSeparateXYZ")
    nt.links.new(tc.outputs["Window"], sep.inputs["Vector"])
    nt.links.new(sep.outputs["Y"], grad.inputs["Fac"])
    nt.links.new(grad.outputs["Color"], bg.inputs["Color"])

    def area(name, loc, energy, size, color=(1, 1, 1), tgt=(0, 0, 0.55)):
        d = bpy.data.lights.new(name, "AREA")
        d.energy = energy; d.size = size; d.color = color
        d.use_shadow = False   # 扫描件的发丝/薄袖内衬会投出硬阴影条纹盖住脸（EEVEE 阴影痤疮）
        ob = bpy.data.objects.new(name, d)
        scn.collection.objects.link(ob)
        ob.location = loc
        ob.rotation_euler = (Vector(tgt) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
        return ob
    area("Key", (2.2, -2.4, 2.6), 320, 2.2, (1.0, 0.98, 0.95))
    area("Fill", (-2.6, -1.6, 1.0), 110, 3.0, (0.85, 0.92, 1.0))
    area("Rim", (0.6, 2.4, 1.9), 260, 2.0, (0.8, 0.9, 1.0))
    area("Top", (0, 0.2, 3.2), 120, 2.5, (1, 1, 1))

    cd = bpy.data.cameras.new("Cam")
    cam = bpy.data.objects.new("Cam", cd)
    scn.collection.objects.link(cam)
    scn.camera = cam
    return cam


def aim(cam, loc, tgt, lens=85, ortho=None):
    cam.location = loc
    cam.rotation_euler = (Vector(tgt) - Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    cam.data.type = "ORTHO" if ortho else "PERSP"
    if ortho:
        cam.data.ortho_scale = ortho
    else:
        cam.data.lens = lens


def render_to(path, w=None, h=None):
    scn = bpy.context.scene
    if w:
        scn.render.resolution_x = w; scn.render.resolution_y = h or w
    scn.render.filepath = path
    bpy.ops.render.render(write_still=True)


def compose(paths, out_path, cols=2):
    imgs = [bpy.data.images.load(p) for p in paths]
    w, h = imgs[0].size
    rows = (len(imgs) + cols - 1) // cols
    W, H = w * cols, h * rows
    canvas = bpy.data.images.new("sheet", W, H, alpha=False)
    buf = np.zeros((H, W, 4), dtype=np.float32)
    buf[:, :, 3] = 1.0
    for i, im in enumerate(imgs):
        px = np.array(im.pixels[:], dtype=np.float32).reshape(h, w, 4)
        r, c = divmod(i, cols)
        y0 = H - (r + 1) * h
        buf[y0:y0 + h, c * w:(c + 1) * w, :] = px
    canvas.pixels.foreach_set(buf.ravel())
    canvas.filepath_raw = out_path
    canvas.file_format = "PNG"
    canvas.save()
    print("sheet saved", out_path)


# ---------------------------------------------------------------- 6. 主流程
def main():
    low = import_mesh()
    low.name = "Hero"
    low.data.name = "Hero"
    normalize([low])
    # 原样保留 meshopt 减面后的原 UV，不再减面/重展/烘焙：
    # 直接用降采样的原贴图（与原 UV 一一对应，脸部干净）。

    # 保留原 UV + 原贴图降采样（不烘焙）：烘焙在低模脸部会投射串色（脸花），
    # 原贴图与原 UV 一一对应，直接降采样最干净。
    tex = bpy.data.images.load(TEXSRC)
    tex.colorspace_settings.name = "sRGB"
    try:
        tex.scale(ATLAS, ATLAS)
    except Exception as e:
        print("scale failed:", e)
    save_jpeg(tex, os.path.join(TEX, "hero_diffuse_2k.jpg"), quality=92)
    jpg = bpy.data.images.load(os.path.join(TEX, "hero_diffuse_2k.jpg"))
    jpg.colorspace_settings.name = "sRGB"
    jpg.pack()
    prep_bake_target(low, jpg)
    swap_material_image(low.data.materials[0], jpg)

    arm = build_armature(low)
    clean_weights(low, max_infl=3, min_w=0.05)
    region_lock(low)
    # 注意：不要对这座网格做小壳刚性绑定——meshopt 减面后连通壳多达 3700+，
    # 全部刚性绑到单骨会让角色变成碎片飞散。平滑距离权重已全覆盖。
    # bind_small_shells(low, arm)
    build_anims(arm)

    bpy.context.scene.frame_set(1)
    cam = setup_scene()

    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "hero_game.blend"))

    bpy.ops.object.select_all(action="DESELECT")
    low.select_set(True)
    arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    glb = os.path.join(OUT, "hero_game.glb")
    bpy.ops.export_scene.gltf(filepath=glb, export_format="GLB",
                              use_selection=True, export_apply=False,
                              export_animations=True, export_animation_mode="ACTIONS",
                              export_bake_animation=False, export_optimize_animation_size=False)
    print("glb saved %s %d bytes" % (glb, os.path.getsize(glb)))

    if SKIP_RENDER:
        print("DONE (no render)")
        return

    # 主视图
    aim(cam, (1.42, -3.15, 0.78), (0, -0.02, 0.50), lens=80)
    render_to(os.path.join(OUT, "hero.png"), 900, 900)
    aim(cam, (0.0, -3.30, 0.62), (0, 0, 0.50), lens=80)
    render_to(os.path.join(OUT, "hero_front.png"), 900, 900)

    # 四视图
    d = 2.9
    views = [("front", (0, -d, 0.52)), ("side", (d, 0, 0.52)),
             ("back", (0, d, 0.52)), ("top", (0, -0.02, d + 0.9))]
    paths = []
    for nm, loc in views:
        aim(cam, loc, (0, 0, 0.52), ortho=1.28)
        p = os.path.join(OUT, "hero_view_%s.png" % nm)
        render_to(p, 600, 600)
        paths.append(p)
    print("views done")

    # 动画预览：6 套 x 3 帧
    aim(cam, (1.05, -2.75, 0.70), (0, 0, 0.50), lens=80)
    tiles = []
    for nm, fn, last in ANIMS:
        act = bpy.data.actions.get(nm)
        arm.animation_data.action = act
        for k, fr in enumerate((1, int(last * 0.42), int(last * 0.72))):
            bpy.context.scene.frame_set(fr)
            p = os.path.join(OUT, "_anim_%s_%d.png" % (nm, k))
            render_to(p, 440, 440)
            tiles.append(p)
    arm.animation_data.action = bpy.data.actions.get("Idle_Stand")
    compose(tiles, os.path.join(OUT, "hero_sheet_anim.png"), cols=3)
    compose(paths, os.path.join(OUT, "hero_sheet_views.png"), cols=4)
    print("DONE")


main()
