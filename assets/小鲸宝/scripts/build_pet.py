"""小鲸宝宠物版：以用户提供的扫描塑像为形体基准，重做游戏资产。

流程：切底座 -> 减面 -> Smart UV -> 从 1.5M 高模反烘 2048 图集 -> 骨骼绑定 -> 12 套动画 -> glb
坐标：X 右、Y 后（正面朝 -Y）、Z 上，身高归一为 1.0，脚底 z=0。
"""
import bpy, bmesh, os, sys, math
import numpy as np
from mathutils import Vector, Quaternion, Matrix

# 工作目录：默认脚本所在目录；可用 XJB_DIR 指向含 ref2/ 与 out/ 的工作目录
HERE = os.environ.get("XJB_DIR") or os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(HERE, "ref2", "4d80cc1c297f6fc57fb253076b0539c3.obj")
TEXSRC = os.path.join(HERE, "ref2", "texture_pbr_20250901.png")
OUT = os.path.join(HERE, "out")
TEX = os.path.join(OUT, "tex")
os.makedirs(TEX, exist_ok=True)

ZCUT = 0.05
RATIO = 0.0135
ATLAS = 2048
TARGET_H = 1.0

argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
SKIP_BAKE = "--nobake" in argv
SKIP_RENDER = "--norender" in argv


# ---------------------------------------------------------------- 1. 几何
def import_and_clean():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.wm.obj_import(filepath=REF, forward_axis="NEGATIVE_Z", up_axis="Y")
    o = [x for x in bpy.context.scene.objects if x.type == "MESH"][0]
    bpy.context.view_layer.objects.active = o
    o.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    bm = bmesh.new(); bm.from_mesh(o.data)
    bmesh.ops.bisect_plane(bm, geom=bm.verts[:] + bm.edges[:] + bm.faces[:],
                           plane_co=(0, 0, ZCUT), plane_no=(0, 0, 1),
                           clear_outer=False, clear_inner=True)
    bm.verts.ensure_lookup_table(); bm.faces.ensure_lookup_table()
    bnd = [e for e in bm.edges if len(e.link_faces) == 1]
    if bnd:
        bmesh.ops.holes_fill(bm, edges=bnd, sides=0)
    bm.to_mesh(o.data); bm.free(); o.data.update()
    print("HI cut faces=%d" % len(o.data.polygons))
    return o


def normalize(objs):
    """把两个物体的高度都归一到 TARGET_H、底面到 z=0、xy 居中"""
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
    low.name = "WhalePet"
    low.data.name = "WhalePet"
    bpy.context.scene.collection.objects.link(low)
    bpy.context.view_layer.objects.active = low
    bpy.ops.object.select_all(action="DESELECT")
    low.select_set(True)
    d = low.modifiers.new("dec", "DECIMATE")
    d.decimate_type = "COLLAPSE"
    d.ratio = RATIO
    d.use_collapse_triangulate = True
    bpy.ops.object.modifier_apply(modifier=d.name)
    d = low.modifiers.new("tn", "TRIANGULATE")
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
    """高模换成纯 Emission + 原贴图（用它自己的 UV），烘焙只取颜色，无光照参与"""
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
    mat = bpy.data.materials.new("PetMat")
    mat.use_nodes = True
    nt = mat.node_tree
    bsdf = nt.nodes.get("Principled BSDF")
    tx = nt.nodes.new("ShaderNodeTexImage")
    tx.image = img
    tx.interpolation = "Linear"
    nt.links.new(tx.outputs["Color"], bsdf.inputs["Base Color"])
    if "Roughness" in bsdf.inputs:
        bsdf.inputs["Roughness"].default_value = 0.30
    if "Metallic" in bsdf.inputs:
        bsdf.inputs["Metallic"].default_value = 0.0
    if "IOR" in bsdf.inputs:
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
    img = bpy.data.images.new("PetAtlas", ATLAS, ATLAS, alpha=False, float_buffer=True)
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
    """用 save_render 走渲染图像设置压 JPEG（save() 的 quality 参数在 5.x 不生效）"""
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
BONES = [
    ("Root",    (0, 0, 0.00),        (0, 0, 0.10),          None),
    ("Spine",   (0, 0, 0.10),        (0, -0.02, 0.34),      "Root"),
    ("Chest",   (0, -0.02, 0.34),    (0, -0.05, 0.50),      "Spine"),
    ("Head",    (0, -0.05, 0.48),    (-0.02, -0.09, 0.86),  "Chest"),
    ("Tail1",   (0.03, 0.16, 0.22),  (0.15, 0.33, 0.45),    "Spine"),
    ("Tail2",   (0.15, 0.33, 0.45),  (0.20, 0.44, 0.76),    "Tail1"),
    ("Fluke_L", (0.20, 0.44, 0.76),  (0.44, 0.47, 0.90),    "Tail2"),
    ("Fluke_R", (0.18, 0.45, 0.78),  (0.15, 0.50, 0.96),    "Tail2"),
    ("Pec_L",   (-0.30, -0.16, 0.22), (-0.52, -0.20, 0.14), "Spine"),
    ("Pec_R",   (0.20, -0.05, 0.22), (0.33, -0.09, 0.12),   "Spine"),
    ("Bell",    (-0.001, -0.36, 0.41), (-0.001, -0.38, 0.30), "Chest"),
]


def shell_verts(ob):
    """返回 [{vert indices}] 每个连通壳的顶点索引"""
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
    arm_data = bpy.data.armatures.new("WhaleRig")
    arm = bpy.data.objects.new("WhaleRig", arm_data)
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

    # 自动权重
    bpy.ops.object.select_all(action="DESELECT")
    low.select_set(True)
    arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    ok = True
    try:
        bpy.ops.object.parent_set(type="ARMATURE_AUTO")
    except Exception as e:
        print("AUTO WEIGHT FAILED:", e)
        ok = False
    if not ok or not low.vertex_groups:
        print("fallback: distance weights")
        low.parent = arm
        distance_weights(low, arm)
    print("RIG bones=%d vgroups=%d" % (len(arm_data.bones), len(low.vertex_groups)))
    return arm


def distance_weights(low, arm):
    """回退方案：按到各骨段的距离做反距离权重（取最近 2 根）"""
    segs = []
    for b in arm.data.bones:
        segs.append((b.name, b.head_local.copy(), b.tail_local.copy()))
    for nm, _, _ in segs:
        if nm not in low.vertex_groups:
            low.vertex_groups.new(name=nm)
    for v in low.data.vertices:
        p = v.co
        d = []
        for nm, h, t in segs:
            ab = t - h
            L = max(ab.length_squared, 1e-9)
            u = max(0.0, min(1.0, (p - h).dot(ab) / L))
            d.append((((h + ab * u) - p).length, nm))
        d.sort()
        best = d[:2]
        w = [1.0 / max(x[0], 1e-3) ** 2 for x in best]
        tot = sum(w)
        for (dist, nm), wi in zip(best, w):
            low.vertex_groups[nm].add([v.index], wi / tot, "REPLACE")


def bind_pendants(low, arm):
    """吊坠（铃铛+水晶）单独按壳识别，刚性绑到 Bell 骨"""
    shells = shell_verts(low)
    body = shells[0]
    bc = np.array([list(low.data.vertices[i].co) for i in body])
    bell_vg = low.vertex_groups.get("Bell") or low.vertex_groups.new(name="Bell")
    n = 0
    for sh in shells[1:]:
        if len(sh) > 500:
            continue
        co = np.array([list(low.data.vertices[i].co) for i in sh])
        c = co.mean(axis=0)
        # 吊坠特征：在身体前方(y<-0.25)且高度 0.2~0.5
        if c[1] > -0.20 or not (0.10 < c[2] < 0.55):
            continue
        for vg in low.vertex_groups:
            vg.remove(sh)
        bell_vg.add(sh, 1.0, "REPLACE")
        n += 1
        print("  pendant shell verts=%d center=(%.3f,%.3f,%.3f) -> Bell" % (len(sh), c[0], c[1], c[2]))
    print("PENDANTS bound=%d" % n)


# ---------------------------------------------------------------- 4. 动画
def rest_rot(pb):
    return pb.bone.matrix_local.to_3x3()


def wq(pb, rx=0.0, ry=0.0, rz=0.0):
    """世界轴旋转 -> 骨骼局部四元数。X=俯仰(点头) Y=侧倾(翻滚) Z=转向(偏航)"""
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
    """待机漂浮：缓慢上下浮沉 + 呼吸 + 尾巴轻摆"""
    new_action(arm, "Idle_Float")
    for f in (1, 25, 49, 73, 97):
        ph = (f - 1) / 96.0 * 2 * math.pi
        s, c = math.sin(ph), math.cos(ph)
        key_bone(arm, "Root", f, loc=(0, 0, 0.030 * s))
        key_bone(arm, "Spine", f, rot=(1.0 * s, 1.8 * math.sin(ph + 0.5), 0))
        key_bone(arm, "Chest", f, rot=(-0.8 * s, -1.2 * math.sin(ph + 0.5), 0))
        key_bone(arm, "Head", f, rot=(2.2 * math.sin(ph + 1.1), 0, 3.0 * math.sin(ph + 0.3)))
        key_bone(arm, "Tail1", f, rot=(0, 0, 7 * math.sin(ph + 0.4)))
        key_bone(arm, "Tail2", f, rot=(0, 0, 11 * math.sin(ph + 1.0)))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, 6 * math.sin(ph + 1.6)))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, 6 * math.sin(ph + 1.6)))
        key_bone(arm, "Pec_L", f, rot=(-4 * math.sin(ph + 0.7), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-4 * math.sin(ph + 0.7), 0, 0))
        key_bone(arm, "Bell", f, rot=(2.0 * math.sin(ph + 1.4), 0, 1.5 * math.sin(ph + 0.9)))


def anim_swim_fast(arm):
    """快速游动：尾部大幅摆动传导到身体"""
    new_action(arm, "Swim_Fast")
    N = 29
    for f in range(1, N + 1, 4):
        ph = (f - 1) / (N - 1) * 2 * math.pi
        key_bone(arm, "Root", f, loc=(0, 0, 0.012 * math.sin(ph)), rot=(-4, 0, -5 * math.sin(ph)))
        key_bone(arm, "Spine", f, rot=(-2, 5 * math.sin(ph + 0.3), 0))
        key_bone(arm, "Chest", f, rot=(-3, -5 * math.sin(ph), 0))
        key_bone(arm, "Head", f, rot=(-6, 0, -4 * math.sin(ph - 0.3)))
        key_bone(arm, "Tail1", f, rot=(4, 0, 22 * math.sin(ph + 0.9)))
        key_bone(arm, "Tail2", f, rot=(6, 0, 30 * math.sin(ph + 1.6)))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, 13 * math.sin(ph + 2.2)))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, 13 * math.sin(ph + 2.2)))
        key_bone(arm, "Pec_L", f, rot=(-14 * math.sin(ph + 1.0), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-14 * math.sin(ph + 1.0), 0, 0))


def anim_happy(arm):
    """开心：下蹲 -> 弹起转一圈 -> 落回"""
    new_action(arm, "Action_Happy")
    keys = [(1, 0.000, 0, 0.0), (8, -0.040, 0, 0.0), (17, 0.085, 170, -10.0),
            (25, 0.090, 330, -12.0), (33, -0.016, 360, 5.0), (40, 0.000, 360, 0.0)]
    for (f, dz, ry, tail) in keys:
        up = dz > 0.03
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(0, 0, ry))
        key_bone(arm, "Spine", f, rot=(tail * 0.5, 0, 0))
        key_bone(arm, "Chest", f, rot=(tail * 0.3, 0, 0))
        key_bone(arm, "Head", f, rot=(-9 if up else 3, 0, 0))
        key_bone(arm, "Tail1", f, rot=(tail, 0, 8 * math.sin(f * 0.35)))
        key_bone(arm, "Tail2", f, rot=(tail * 1.3, 0, 11 * math.sin(f * 0.35)))
        key_bone(arm, "Fluke_L", f, rot=(tail * 0.6, 0, 13 if up else 0))
        key_bone(arm, "Fluke_R", f, rot=(tail * 0.6, 0, 13 if up else 0))
        key_bone(arm, "Pec_L", f, rot=(-24 if up else 0, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-24 if up else 0, 0, 0))
        key_bone(arm, "Bell", f, rot=(tail * 0.8, 0, 0))


def anim_snuggle(arm):
    """撒娇：身体前倾、头部左右轻蹭、胸鳍向前抱"""
    new_action(arm, "Action_Snuggle")
    for (f, lean, dz, tilt) in ((1, 0, 0.000, 0), (11, 8, -0.014, 5), (21, 9, -0.018, -5),
                                (31, 7, -0.010, 4), (42, 0, 0.000, 0)):
        key_bone(arm, "Root", f, loc=(0, 0, dz))
        key_bone(arm, "Spine", f, rot=(lean, 0, 0))
        key_bone(arm, "Chest", f, rot=(lean * 0.5, 0, 0))
        key_bone(arm, "Head", f, rot=(lean * 0.8, tilt * 0.6, tilt))
        key_bone(arm, "Pec_L", f, rot=(-18 * lean / 9, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-18 * lean / 9, 0, 0))
        key_bone(arm, "Tail1", f, rot=(-lean * 0.8, 0, 0))


def anim_sleep(arm):
    """睡觉：整体下沉、低头、尾鳍缓慢轻摆，58-67 帧一次小抖"""
    new_action(arm, "Idle_Sleep")
    for f in (1, 25, 49, 73, 97):
        ph = (f - 1) / 96.0 * 2 * math.pi
        key_bone(arm, "Root", f, loc=(0, 0, -0.045 + 0.012 * math.sin(ph)))
        key_bone(arm, "Spine", f, rot=(6, 0, 1.2 * math.sin(ph)))
        key_bone(arm, "Chest", f, rot=(3, 0, 0))
        key_bone(arm, "Head", f, rot=(10, 0, 2.0 * math.sin(ph + 0.8)))
        key_bone(arm, "Tail1", f, rot=(0, 0, 4 * math.sin(ph)))
        key_bone(arm, "Tail2", f, rot=(0, 0, 6 * math.sin(ph + 0.9)))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, 4 * math.sin(ph + 1.6)))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, 4 * math.sin(ph + 1.6)))
        key_bone(arm, "Pec_L", f, rot=(3, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(3, 0, 0))
    for f, tw in ((58, 0), (61, 4), (64, -3), (67, 0)):
        key_bone(arm, "Head", f, rot=(10, tw * 0.5, tw))
        key_bone(arm, "Tail2", f, rot=(0, 0, tw * 1.5))


def anim_swim_slow(arm):
    """慢游：柔和 S 形，胸鳍轻划"""
    new_action(arm, "Swim_Slow")
    N = 63
    for f in range(1, N + 1, 8):
        ph = (f - 1) / (N - 1) * 2 * math.pi
        key_bone(arm, "Root", f, loc=(0, 0, 0.020 * math.sin(ph)))
        key_bone(arm, "Spine", f, rot=(1.5, 3 * math.sin(ph), 0))
        key_bone(arm, "Chest", f, rot=(-1, -2 * math.sin(ph + 0.4), 0))
        key_bone(arm, "Head", f, rot=(2, 0, -2.5 * math.sin(ph)))
        key_bone(arm, "Tail1", f, rot=(2, 0, 10 * math.sin(ph + 0.7)))
        key_bone(arm, "Tail2", f, rot=(3, 0, 15 * math.sin(ph + 1.3)))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, 8 * math.sin(ph + 2.0)))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, 8 * math.sin(ph + 2.0)))
        key_bone(arm, "Pec_L", f, rot=(-8 * math.sin(ph), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-8 * math.sin(ph + 0.3), 0, 0))


def anim_sad(arm):
    """委屈：缩小下沉、低头、尾鳍耷拉"""
    new_action(arm, "Action_Sad")
    for (f, dz, lean, sc) in ((1, 0.000, 0, 1.00), (12, -0.040, 8, 0.96),
                              (30, -0.055, 12, 0.94), (46, -0.035, 9, 0.96),
                              (58, 0.000, 0, 1.00)):
        key_bone(arm, "Root", f, loc=(0, 0, dz), scale=(sc, sc, sc))
        key_bone(arm, "Spine", f, rot=(lean * 0.6, 0, 0))
        key_bone(arm, "Chest", f, rot=(lean * 0.4, 0, 0))
        key_bone(arm, "Head", f, rot=(lean * 1.1, 0, 0))
        key_bone(arm, "Tail1", f, rot=(-lean * 0.5, 0, 0))
        key_bone(arm, "Tail2", f, rot=(-lean * 0.7, 0, 0))
        key_bone(arm, "Fluke_L", f, rot=(-lean * 0.4, 0, 0))
        key_bone(arm, "Fluke_R", f, rot=(-lean * 0.4, 0, 0))
        key_bone(arm, "Pec_L", f, rot=(lean * 0.8, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(lean * 0.8, 0, 0))


def anim_heal(arm):
    """治愈：升起转一圈、胸鳍展开（光环粒子留给引擎）"""
    new_action(arm, "Action_Heal")
    for (f, dz, ry, pec, glow) in ((1, 0.000, 0, 0, 1.00), (18, 0.060, 120, -26, 1.03),
                                   (36, 0.085, 250, -32, 1.05), (54, 0.040, 360, -18, 1.02),
                                   (72, 0.000, 360, 0, 1.00)):
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(0, 0, ry), scale=(glow, glow, glow))
        key_bone(arm, "Spine", f, rot=(-4, 0, 0))
        key_bone(arm, "Chest", f, rot=(-3, 0, 0))
        key_bone(arm, "Head", f, rot=(-7, 0, 0))
        key_bone(arm, "Pec_L", f, rot=(pec, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(pec, 0, 0))
        key_bone(arm, "Tail1", f, rot=(6, 0, 10 * math.sin(f * 0.3)))
        key_bone(arm, "Tail2", f, rot=(8, 0, 14 * math.sin(f * 0.3 + 0.6)))


def anim_startle(arm):
    """受惊：猛缩 + 快速抖两下 + 恢复"""
    new_action(arm, "Action_Startle")
    for (f, dz, sc, jit) in ((1, 0.000, 1.00, 0), (4, -0.030, 0.90, 0), (8, 0.020, 1.06, 0),
                             (11, -0.006, 0.99, 6), (14, 0.010, 1.02, -6),
                             (18, 0.000, 1.00, 3), (23, 0.000, 1.00, 0), (29, 0.000, 1.00, 0)):
        key_bone(arm, "Root", f, loc=(0, 0, dz), scale=(sc, sc, sc))
        key_bone(arm, "Spine", f, rot=(-jit * 0.6, 0, jit))
        key_bone(arm, "Chest", f, rot=(-jit * 0.4, 0, -jit * 0.5))
        key_bone(arm, "Head", f, rot=(-jit * 1.2, 0, jit * 1.5))
        key_bone(arm, "Tail1", f, rot=(0, 0, jit * 2.0))
        key_bone(arm, "Tail2", f, rot=(0, 0, jit * 2.6))
        key_bone(arm, "Pec_L", f, rot=(-jit * 2.0, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-jit * 2.0, 0, 0))


def anim_follow(arm):
    """跟随：绕圈游动 + 途中探头偷看（带 root motion）"""
    new_action(arm, "Action_Follow")
    N = 73
    for f in range(1, N + 1, 6):
        ph = (f - 1) / (N - 1) * 2 * math.pi
        key_bone(arm, "Root", f, loc=(0, 0, 0.018 * math.sin(ph * 3)),
                 rot=(-3, 0, ph * 180.0 / math.pi))
        key_bone(arm, "Spine", f, rot=(2, 4 * math.sin(ph * 3), 0))
        key_bone(arm, "Chest", f, rot=(-2, -3 * math.sin(ph * 3 + 0.5), 0))
        key_bone(arm, "Head", f, rot=(3, 0, 12 * math.sin(ph * 2)))
        key_bone(arm, "Tail1", f, rot=(0, 0, 14 * math.sin(ph * 3 + 0.6)))
        key_bone(arm, "Tail2", f, rot=(0, 0, 20 * math.sin(ph * 3 + 1.2)))
        key_bone(arm, "Fluke_L", f, rot=(0, 0, 9 * math.sin(ph * 3 + 1.8)))
        key_bone(arm, "Fluke_R", f, rot=(0, 0, 9 * math.sin(ph * 3 + 1.8)))
        key_bone(arm, "Pec_L", f, rot=(-10 * math.sin(ph * 3), 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-10 * math.sin(ph * 3 + 0.4), 0, 0))


def anim_appear(arm):
    """出场：从深处带旋升起、过冲落定"""
    new_action(arm, "Action_Appear")
    for (f, dz, ry, sc) in ((1, -1.10, -140, 0.72), (18, -0.30, -60, 0.90),
                            (32, 0.075, 10, 1.04), (44, -0.012, 0, 0.99),
                            (56, 0.006, 0, 1.00), (67, 0.000, 0, 1.00)):
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(0, 0, ry), scale=(sc, sc, sc))
        key_bone(arm, "Head", f, rot=(-6 if f < 45 else 0, 0, 0))
        key_bone(arm, "Tail1", f, rot=(6, 0, 12))
        key_bone(arm, "Tail2", f, rot=(8, 0, 16))
        key_bone(arm, "Pec_L", f, rot=(-18 if f < 45 else 0, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(-18 if f < 45 else 0, 0, 0))


def anim_disappear(arm):
    """收回：边旋边沉出画面并缩小"""
    new_action(arm, "Action_Disappear")
    for (f, dz, ry, sc) in ((1, 0.000, 0, 1.00), (14, 0.055, 70, 1.02),
                            (30, -0.10, 190, 0.94), (46, -0.55, 300, 0.80),
                            (62, -1.20, 360, 0.55)):
        key_bone(arm, "Root", f, loc=(0, 0, dz), rot=(0, 0, ry), scale=(sc, sc, sc))
        key_bone(arm, "Head", f, rot=(8 if f > 20 else -4, 0, 0))
        key_bone(arm, "Tail1", f, rot=(-6, 0, 10))
        key_bone(arm, "Tail2", f, rot=(-8, 0, 14))
        key_bone(arm, "Pec_L", f, rot=(12 if f > 30 else -14, 0, 0))
        key_bone(arm, "Pec_R", f, rot=(12 if f > 30 else -14, 0, 0))


ANIMS = [
    ("Idle_Float", anim_idle, 97), ("Idle_Sleep", anim_sleep, 97),
    ("Swim_Slow", anim_swim_slow, 63), ("Swim_Fast", anim_swim_fast, 29),
    ("Action_Happy", anim_happy, 40), ("Action_Snuggle", anim_snuggle, 42),
    ("Action_Sad", anim_sad, 58), ("Action_Heal", anim_heal, 72),
    ("Action_Startle", anim_startle, 29), ("Action_Follow", anim_follow, 73),
    ("Action_Appear", anim_appear, 67), ("Action_Disappear", anim_disappear, 62),
]


def build_anims(arm):
    for nm, fn, _ in ANIMS:
        fn(arm)
    arm.animation_data.action = bpy.data.actions.get("Idle_Float")
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
    # 渐变背景
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


# ---------------------------------------------------------------- 6. 主流程
def main():
    hi = import_and_clean()
    low = make_low(hi)
    normalize([hi, low])
    smart_uv(low)

    if not SKIP_BAKE:
        make_emit_src(hi)
        img = bake_atlas(hi, low)
        save_img(img, os.path.join(TEX, "pet_diffuse_2k.png"))
        # 另存一份 JPEG 用于入库（PNG 18MB -> JPEG 约 1MB）
        try:
            jpg = os.path.join(TEX, "pet_diffuse_2k.jpg")
            save_jpeg(img, jpg, quality=92)
            src = bpy.data.images.load(jpg)
            src.colorspace_settings.name = "sRGB"
            src.pack()
            swap_material_image(low.data.materials[0], src)
        except Exception as e:
            print("jpeg swap failed:", e)
    else:
        img = bpy.data.images.new("PetAtlas", ATLAS, ATLAS, alpha=False, float_buffer=True)
        prep_bake_target(low, img)

    arm = build_armature(low)
    bind_pendants(low, arm)
    build_anims(arm)

    bpy.context.scene.frame_set(1)
    cam = setup_scene()

    # 保存工程（去掉百万面高模）
    bpy.data.objects.remove(hi, do_unlink=True)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, "pet_game.blend"))

    # 导出 glb
    bpy.ops.object.select_all(action="DESELECT")
    low.select_set(True)
    arm.select_set(True)
    bpy.context.view_layer.objects.active = arm
    glb = os.path.join(OUT, "pet_game.glb")
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
    render_to(os.path.join(OUT, "pet_hero.png"), 900, 900)
    aim(cam, (0.0, -3.30, 0.62), (0, 0, 0.50), lens=80)
    render_to(os.path.join(OUT, "pet_hero_front.png"), 900, 900)

    # 四视图
    d = 2.9
    views = [("front", (0, -d, 0.52)), ("side", (d, 0, 0.52)),
             ("back", (0, d, 0.52)), ("top", (0, -0.02, d + 0.9))]
    paths = []
    for nm, loc in views:
        aim(cam, loc, (0, 0, 0.52), ortho=1.28)
        p = os.path.join(OUT, "pet_view_%s.png" % nm)
        render_to(p, 600, 600)
        paths.append(p)
    print("views done")

    # 动画预览：12 套 x 3 帧
    aim(cam, (1.05, -2.75, 0.70), (0, 0, 0.50), lens=80)
    import bpy as _b
    tiles = []
    for nm, fn, last in ANIMS:
        act = bpy.data.actions.get(nm)
        arm.animation_data.action = act
        for k, fr in enumerate((1, int(last * 0.42), int(last * 0.72))):
            bpy.context.scene.frame_set(fr)
            p = os.path.join(OUT, "_anim_%s_%d.png" % (nm, k))
            render_to(p, 440, 440)
            tiles.append(p)
    arm.animation_data.action = bpy.data.actions.get("Idle_Float")
    compose(tiles, os.path.join(OUT, "pet_sheet_anim.png"), cols=3)
    compose(paths, os.path.join(OUT, "pet_sheet_views.png"), cols=4)
    print("DONE")


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


main()
