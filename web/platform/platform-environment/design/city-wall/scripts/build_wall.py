# =============================================================
#  City wall — straight-section unit module
#  古城墙 · 直段单元（可沿 X 轴无缝阵列）
#
#  由 WorkBuddy 通过 MCP-for-Blender socket 驱动实时生成。
#  运行方式见同目录 README；改动参数后重跑即可重建。
#
#  坐标约定：X = 墙体走向（长度），Y = 墙厚方向（外侧为 -Y），Z = 高度；单位 m。
#
#  ── 拼接约定（TILING CONTRACT）────────────────────────────────
#  单元最终作为模块沿 X 轴阵列拼成整墙，因此**所有构件的 X 范围必须
#  严格等于 [-L/2, +L/2]**，出沿只能留在 Y 方向：
#    · 条石基础：Y 向出沿（勒脚），X 向齐平
#    · 马道铺砖：Y 向挑檐，X 向齐平
#    · 垛口：两端各留半个垛口，阵列后跨缝自动接成一个完整垛口
#  违反任一条都会在接缝处出现凸起、重叠面（z-fighting）或断续的垛口节奏。
# =============================================================
import bpy, bmesh, math, os, json
from mathutils import Vector

# ---------------------------------------------------------------
# 1. 参数（模数化）
# ---------------------------------------------------------------
L       = 10.0   # 单元段长度（阵列步长）
BD      = 6.0    # 墙身底宽
BU      = 4.6    # 墙身顶宽（收分 0.7m/侧）
Z_BASE  = 0.4    # 墙身起始标高 = 条石基础顶面
HB      = 8.0    # 墙身高度
WALK_T  = 0.06   # 马道铺砖层厚
WALK_OVH = 0.03  # 马道 Y 向挑檐
FND_OVH = 0.40   # 条石基础 Y 向出沿
PAR_T   = 0.60   # 外侧垛口墙厚度
PAR_B   = 0.80   # 垛口墙下段高（垛口底距马道面）
MER_H   = 1.00   # 垛身高度
IN_T    = 0.35   # 内侧宇墙厚度
IN_H    = 1.00   # 内侧宇墙高度
CYCLE   = 2.00   # 垛口模数：一垛一口
GAP     = 0.60   # 垛口宽度
SLIT_W  = 0.14   # 射孔宽度

Z_TOP  = Z_BASE + HB            # 8.40  墙顶结构面
Z_WALK = Z_TOP + WALK_T         # 8.46  马道面
Z_PB   = Z_WALK + PAR_B         # 9.26  垛口底（垛身起）
Z_MER  = Z_PB + MER_H           # 10.26 垛口墙顶
Z_IN   = Z_WALK + IN_H          # 9.46  宇墙顶
HX     = L / 2.0                # 5.00
YTOP   = BU / 2.0               # 2.30  墙身顶部半宽
YBOT   = BD / 2.0               # 3.00  墙身底部半宽
OUTY0  = -YTOP                  # 外侧垛口墙外皮
OUTY1  = -YTOP + PAR_T          # 外侧垛口墙内皮
SLIT0  = Z_PB + 0.40            # 射孔下沿
SLIT1  = Z_MER - 0.22           # 射孔上沿
MERW   = CYCLE - GAP            # 垛身宽 1.40
N_MER  = int(round(L / CYCLE))  # 每单元垛数 5

OUT = r"F:\workspace\blue-whale\web\platform\platform-environment\design\city-wall"
RENDER_DIR = os.path.join(OUT, "renders")
os.makedirs(RENDER_DIR, exist_ok=True)

QUAD = [(0, 1, 2, 3), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)]


# ---------------------------------------------------------------
# 2. 场景清理（幂等）
# ---------------------------------------------------------------
COLL_NAME = "city_wall_unit"
for cname in (COLL_NAME, "city_wall_tile_check"):
    old = bpy.data.collections.get(cname)
    if old:
        for ob in list(old.objects):
            bpy.data.objects.remove(ob, do_unlink=True)
        bpy.data.collections.remove(old)
for nm in ("Cube", "Light", "Camera"):
    ob = bpy.data.objects.get(nm)
    if ob is not None:
        bpy.data.objects.remove(ob, do_unlink=True)

coll = bpy.data.collections.new(COLL_NAME)
bpy.context.scene.collection.children.link(coll)


# ---------------------------------------------------------------
# 3. 建模工具
# ---------------------------------------------------------------
def _mesh(name, verts, target):
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], QUAD)
    me.validate()
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(me)
    bm.free()
    me.update()
    ob = bpy.data.objects.new(name, me)
    target.objects.link(ob)
    return ob


def box(name, x0, x1, y0, y1, z0, z1, target):
    v = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
         (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    return _mesh(name, v, target)


def frustum(name, x0, x1, yb, yt, z0, z1, target):
    v = [(x0, -yb, z0), (x1, -yb, z0), (x1, yb, z0), (x0, yb, z0),
         (x0, -yt, z1), (x1, -yt, z1), (x1, yt, z1), (x0, yt, z1)]
    return _mesh(name, v, target)


def merge(name, objs, target):
    """合并为单个 mesh。不用 bpy.ops，避免上下文依赖。"""
    bm = bmesh.new()
    for o in objs:
        bm.from_mesh(o.data)
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    me.update()
    ob = bpy.data.objects.new(name, me)
    target.objects.link(ob)
    for o in objs:
        bpy.data.objects.remove(o, do_unlink=True)
    return ob


# ---------------------------------------------------------------
# 4. 生成一个单元
# ---------------------------------------------------------------
def build_unit(target, tag="", xoff=0.0):
    """生成一个完整单元，返回 {部件名: 物体}。xoff 用于阵列验证。"""
    p = tag
    x0, x1 = -HX + xoff, HX + xoff          # ★ X 向严格齐平，全程复用

    # 条石基础：两级，Y 向出沿，X 向不出沿
    f1 = box(p + "f1", x0, x1, -YBOT - FND_OVH, YBOT + FND_OVH, -0.60, 0.0, target)
    f2 = box(p + "f2", x0, x1, -YBOT - 0.20, YBOT + 0.20, 0.0, Z_BASE, target)
    foundation = merge(p + "wall_foundation", [f1, f2], target)

    # 墙身：青砖，两侧收分
    body = frustum(p + "wall_body", x0, x1, YBOT, YTOP, Z_BASE, Z_TOP, target)

    # 马道铺砖：Y 向挑檐，X 向齐平
    walk = box(p + "wall_walkway", x0, x1,
               -YTOP - WALK_OVH, YTOP + WALK_OVH, Z_TOP, Z_WALK, target)

    # 外侧垛口墙：墙脚 + 5 垛（两端各留半个垛口，保证跨缝连续）
    parts = [box(p + "pb_base", x0, x1, OUTY0, OUTY1, Z_WALK, Z_PB, target)]
    half_gap = GAP / 2.0
    hw = (MERW - SLIT_W) / 2.0
    for i in range(N_MER):
        # 第 i 个垛居中于 x0 + half_gap + MERW/2 + i*CYCLE
        mx0 = x0 + half_gap + i * CYCLE
        mx1 = mx0 + MERW
        s0 = mx0 + hw           # 射孔左边
        s1 = mx1 - hw           # 射孔右边
        parts.append(box(p + "pb%d_sill" % i, mx0, mx1, OUTY0, OUTY1, Z_PB, SLIT0, target))
        parts.append(box(p + "pb%d_colL" % i, mx0, s0, OUTY0, OUTY1, SLIT0, SLIT1, target))
        parts.append(box(p + "pb%d_colR" % i, s1, mx1, OUTY0, OUTY1, SLIT0, SLIT1, target))
        parts.append(box(p + "pb%d_cap" % i, mx0, mx1, OUTY0, OUTY1, SLIT1, Z_MER, target))
    parapet = merge(p + "wall_parapet_outer", parts, target)

    # 内侧宇墙
    inner = box(p + "wall_parapet_inner", x0, x1, YTOP - IN_T, YTOP, Z_WALK, Z_IN, target)

    return {"foundation": foundation, "body": body, "walkway": walk,
            "parapet": parapet, "inner": inner}


# 场地地面（不属于单元，单独一份）
ground = box("ground", -46, 46, -46, 46, -0.64, -0.60, coll)
unit = build_unit(coll)


# ---------------------------------------------------------------
# 5. 立方体平面投影 UV（砖纹是 2D 节点，只取 XY 分量，必须给 UV）
# ---------------------------------------------------------------
def add_cube_uvs(ob):
    me = ob.data
    if not me.uv_layers:
        me.uv_layers.new(name="UVMap")
    bm = bmesh.new()
    bm.from_mesh(me)
    uvl = bm.loops.layers.uv.verify()
    for f in bm.faces:
        n = f.normal
        ax = max(('x', 'y', 'z'), key=lambda a: abs(getattr(n, a)))
        for lp in f.loops:
            c = lp.vert.co
            lp[uvl].uv = (c.y, c.z) if ax == 'x' else ((c.x, c.z) if ax == 'y' else (c.x, c.y))
    bm.to_mesh(me)
    bm.free()
    me.update()


for ob in list(coll.objects):
    add_cube_uvs(ob)


# ---------------------------------------------------------------
# 6. 材质
# ---------------------------------------------------------------
def drop_mat(name):
    m = bpy.data.materials.get(name)
    if m:
        bpy.data.materials.remove(m)


def plain_mat(name, color, rough):
    drop_mat(name)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    b = mat.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (color[0], color[1], color[2], 1.0)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = 0.0
    return mat


def brick_mat(name, c1, c2, mortar, bw, rh, rough=0.86):
    drop_mat(name)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nt = mat.node_tree
    nd, lk = nt.nodes, nt.links
    bsdf = nd["Principled BSDF"]
    tc = nd.new("ShaderNodeTexCoord")
    br = nd.new("ShaderNodeTexBrick")
    noise = nd.new("ShaderNodeTexNoise")
    mr = nd.new("ShaderNodeMapRange")
    bump = nd.new("ShaderNodeBump")
    br.inputs["Color1"].default_value = (c1[0], c1[1], c1[2], 1.0)
    br.inputs["Color2"].default_value = (c2[0], c2[1], c2[2], 1.0)
    br.inputs["Mortar"].default_value = (mortar[0], mortar[1], mortar[2], 1.0)
    br.inputs["Scale"].default_value = 1.0
    br.inputs["Mortar Size"].default_value = 0.010
    br.inputs["Mortar Smooth"].default_value = 0.10
    br.inputs["Brick Width"].default_value = bw
    br.inputs["Row Height"].default_value = rh
    noise.inputs["Scale"].default_value = 3.0
    noise.inputs["Detail"].default_value = 6.0
    mr.inputs["To Min"].default_value = rough - 0.10
    mr.inputs["To Max"].default_value = min(1.0, rough + 0.12)
    bump.inputs["Strength"].default_value = 0.30
    bump.inputs["Distance"].default_value = 0.003
    lk.new(tc.outputs["UV"], br.inputs["Vector"])
    lk.new(tc.outputs["UV"], noise.inputs["Vector"])
    lk.new(br.outputs["Color"], bsdf.inputs["Base Color"])
    lk.new(noise.outputs["Fac"], mr.inputs["Value"])
    lk.new(mr.outputs["Result"], bsdf.inputs["Roughness"])
    lk.new(br.outputs["Factor"], bump.inputs["Height"])
    lk.new(bump.outputs["Normal"], bsdf.inputs["Normal"])
    return mat


M_STONE = plain_mat("mat_stone", (0.315, 0.303, 0.278), 0.80)
M_BRICK = brick_mat("mat_grey_brick", (0.238, 0.248, 0.266),
                    (0.182, 0.192, 0.210), (0.292, 0.290, 0.280),
                    bw=0.40, rh=0.16, rough=0.86)
M_TOP = plain_mat("mat_walkway_top", (0.335, 0.318, 0.282), 0.94)
M_GND = plain_mat("mat_ground", (0.230, 0.202, 0.158), 0.98)

for ob, mat in ((unit["foundation"], M_STONE), (unit["body"], M_BRICK),
                (unit["walkway"], M_TOP), (unit["parapet"], M_BRICK),
                (unit["inner"], M_BRICK), (ground, M_GND)):
    ob.data.materials.clear()
    ob.data.materials.append(mat)


# ---------------------------------------------------------------
# 7. 世界 / 灯光 / 相机 / 渲染设置
# ---------------------------------------------------------------
def look_at(ob, target):
    d = Vector(target) - ob.location
    ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()


sc = bpy.context.scene
sc.render.engine = 'BLENDER_EEVEE'
sc.render.film_transparent = False
sc.render.image_settings.file_format = 'PNG'
sc.render.image_settings.color_mode = 'RGB'
sc.render.resolution_percentage = 100
for attr, val in (("taa_render_samples", 64), ("use_raytracing", True)):
    try:
        setattr(sc.eevee, attr, val)
    except Exception:
        pass
try:
    sc.view_settings.view_transform = 'AgX'
except Exception:
    pass
sc.view_settings.exposure = -0.10

# 天空：Blender 5.2 的枚举已改名，逐项回退
world = sc.world or bpy.data.worlds.new("World")
sc.world = world
world.use_nodes = True
wnt = world.node_tree
wnd, wlk = wnt.nodes, wnt.links
wout = next(n for n in wnd if n.type == 'OUTPUT_WORLD')
for n in [n for n in wnd if n.type in ('TEX_SKY', 'BACKGROUND')]:
    wnd.remove(n)
sky = wnd.new("ShaderNodeTexSky")
sky.location = (-300.0, 0.0)
for t in ('MULTIPLE_SCATTERING', 'SINGLE_SCATTERING', 'PREETHAM'):
    try:
        sky.sky_type = t
        break
    except TypeError:
        continue
sky.sun_disc = False
for attr, val in (("sun_elevation", math.radians(38.0)), ("sun_rotation", math.radians(128.0)),
                  ("altitude", 60.0), ("air_density", 1.2), ("dust_density", 1.6)):
    try:
        setattr(sky, attr, val)
    except AttributeError:
        pass
bg = wnd.new("ShaderNodeBackground")
bg.location = (0.0, 0.0)
bg.inputs["Strength"].default_value = 0.38
wlk.new(sky.outputs["Color"], bg.inputs["Color"])
wlk.new(bg.outputs["Background"], wout.inputs["Surface"])

for nm in ("Sun_key", "Area_fill"):
    o = bpy.data.objects.get(nm)
    if o:
        bpy.data.objects.remove(o, do_unlink=True)

sd = bpy.data.lights.new("Sun_key", type='SUN')
sd.energy = 3.4
sd.angle = math.radians(2.0)
sd.color = (1.0, 0.955, 0.885)
sun = bpy.data.objects.new("Sun_key", sd)
sc.collection.objects.link(sun)
sun.location = (20.0, -18.0, 26.0)
look_at(sun, (0.0, 0.0, 4.0))

fd = bpy.data.lights.new("Area_fill", type='AREA')
fd.energy = 620.0
fd.size = 14.0
fd.color = (0.78, 0.86, 1.0)
fill = bpy.data.objects.new("Area_fill", fd)
sc.collection.objects.link(fill)
fill.location = (-16.0, 12.0, 10.0)
look_at(fill, (0.0, 0.0, 5.0))

for nm in ("Cam_elevation", "Cam_three_quarter", "Cam_crenellation", "Cam_tiling"):
    o = bpy.data.objects.get(nm)
    if o:
        bpy.data.objects.remove(o, do_unlink=True)

CAMS = {
    "Cam_three_quarter":   ((-15.5, -18.5, 12.5), (0.60, -0.20, 4.60), 52.0),
    "Cam_elevation":       ((0.0, -30.0, 4.85),   (0.0, 0.0, 4.85),    85.0),
    "Cam_crenellation":    ((-5.2, -6.4, 11.6),   (0.30, -1.70, 9.30), 85.0),
    "Cam_tiling":          ((-14.0, -26.0, 14.0), (0.0, -0.50, 5.00),  35.0),
}
cams = {}
for nm, (loc, tgt, lens) in CAMS.items():
    cd = bpy.data.cameras.new(nm)
    cd.lens = lens
    co = bpy.data.objects.new(nm, cd)
    sc.collection.objects.link(co)
    co.location = loc
    look_at(co, tgt)
    cams[nm] = co


def shoot(cam_name, rx, ry, filename):
    sc.camera = cams[cam_name]
    sc.render.resolution_x = rx
    sc.render.resolution_y = ry
    path = os.path.join(RENDER_DIR, filename)
    sc.render.filepath = path
    bpy.ops.render.render(write_still=True)
    return {"file": filename, "bytes": os.path.getsize(path)}


# ---------------------------------------------------------------
# 8. 渲染：单元三视图
# ---------------------------------------------------------------
shots = [
    shoot("Cam_elevation",      1200, 1280, "unit-elevation.png"),
    shoot("Cam_three_quarter",  1800, 1100, "unit-three-quarter.png"),
    shoot("Cam_crenellation",   1800, 1100, "unit-crenellation-detail.png"),
]


# ---------------------------------------------------------------
# 9. 拼接验证：临时阵列 3 个单元 → 渲染 → 拆除
# ---------------------------------------------------------------
tile_coll = bpy.data.collections.new("city_wall_tile_check")
sc.collection.children.link(tile_coll)
# 主单元暂时移出渲染（避免与中间那格重叠）
for ob in list(coll.objects):
    if ob is not ground:
        ob.hide_render = True
tiled = []
for k in (-1, 0, 1):
    tiled += list(build_unit(tile_coll, tag="t%d_" % (k + 1), xoff=k * L).values())
for ob in tiled:
    add_cube_uvs(ob)
    ob.data.materials.clear()
    ob.data.materials.append(M_BRICK if ob.name.endswith(("wall_body", "parapet_outer", "parapet_inner"))
                             else (M_STONE if ob.name.endswith("wall_foundation") else M_TOP))
    ob.hide_render = False
shots.append(shoot("Cam_tiling", 2200, 820, "tiling-check.png"))

# 拆除阵列，恢复单单元
for ob in list(tile_coll.objects):
    bpy.data.objects.remove(ob, do_unlink=True)
bpy.data.collections.remove(tile_coll)
for ob in list(coll.objects):
    ob.hide_render = False


# ---------------------------------------------------------------
# 10. 保存 + 报告
# ---------------------------------------------------------------
blend = os.path.join(OUT, "city-wall-unit.blend")
saved = True
try:
    bpy.ops.wm.save_as_mainfile(filepath=blend)
except Exception as e:
    saved = "ERR: %s" % e

report = {
    "module": {"L": L, "tiling_step": L, "merlons_per_unit": N_MER,
               "cycle": CYCLE, "gap": GAP},
    "x_range_all_parts": [-HX, HX],
    "elevations": {"base_top": Z_BASE, "wall_top": Z_TOP, "walkway": Z_WALK,
                   "crenel_bottom": Z_PB, "parapet_top": Z_MER, "inner_top": Z_IN},
    "objects": [(o.name, len(o.data.vertices), len(o.data.polygons)) for o in coll.objects],
    "shots": shots,
    "blend": blend,
    "blend_saved": saved,
}
print("@@WALL@@" + json.dumps(report, ensure_ascii=False))
