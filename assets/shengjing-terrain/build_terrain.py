# -*- coding: utf-8 -*-
"""虚拟都城（圣京）地形建模 —— 按「虚拟都城地形制作参考」分层规格
高度层：江面 0 / 平原 10-60 / 台地(城) 60 / 丘陵 48-165 / 南岭主脉 165-400
水系层：北江沿北部平原东西向（江湾在城东北，离城墙约 500m）；三条支流自南岭垭口向北汇流
地貌层：南岭主脉 / 东山丘陵(东关垭口) / 平原草地农田 / 滩湿地 / 城池体块 / 南岭密林
坐标：1 unit = 1m，x 东 / y 南 / z 上，北 = -y，地形 6000×6000m
运行：blender --background --python build_terrain.py
"""
import bpy
import bmesh
import math
import random
from mathutils import Vector, Matrix

OUT_BLEND = "F:/blue-whale/work/shengjing-terrain/shengjing-terrain.blend"
OUT_PREVIEW = "F:/blue-whale/tmp/shengjing-preview.png"

# ---------------------------------------------------------------- 帮助函数
def clamp(v, a, b):
    return min(b, max(a, v))

def smooth(x, a, b):
    t = clamp((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)

def gauss(t):
    return math.exp(-t * t)

def mix(a, b, t):
    return a + (b - a) * clamp(t, 0.0, 1.0)

# ---------------------------------------------------------------- 地形规格（与 city.ts 同源，尺度 ×8）
NORTH_RIVER = lambda x: -1600 + math.sin(x * 0.0011) * 128 + 272 * gauss((x - 1040) / 680)
# 支流名义汇入口 → 实算口：mouth = 主河在该支流中线处的 y（避免河槽越过主河北岸）
TRIBS_NOMINAL = [
    (lambda y: -1340 + math.sin(y * 0.0026) * 96, -1490),  # 西支·玉溪
    (lambda y: 1240 + math.sin(y * 0.0021) * 80, -1580),   # 中支·清溪（城东南侧，|x|≥1200 红线）
    (lambda y: 1810 + math.sin(y * 0.0030) * 112, -1390),  # 东支·云川
]
TRIBS = [(fx, NORTH_RIVER(fx(nominal))) for fx, nominal in TRIBS_NOMINAL]

# 运河（圣京北门 → 北江）与出城南渠（圣京南门 → 向南，规格图中的城河通道）
CANAL_Y0 = -760
CANAL_Y1 = NORTH_RIVER(0)  # ≈ -1574
CANAL_MX = lambda y: math.sin(y * 0.004) * 30  # 运河中线微弯
def canal_t(y):
    return clamp((CANAL_Y0 - y) / (CANAL_Y0 - CANAL_Y1), 0.0, 1.0)

def seg_dist(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    wx, wy = px - ax, py - ay
    tt = clamp((wx * vx + wy * vy) / (vx * vx + vy * vy or 1.0), 0.0, 1.0)
    return math.hypot(px - (ax + vx * tt), py - (ay + vy * tt))

SOUTH_XJ = (TRIBS_NOMINAL[0][0](1500), 1500)  # 南渠西折参考点（玉溪 y=1500 处）
def south_channel_dist(x, y):
    return seg_dist(x, y, 0, 760, 0, 2400)

BRAIDS = ((-2830, -700), (1070, 2830))
def braid_offset(x):
    """支汊偏离主河的距离：两端渐变为 0（分汊→汇合），中段 230m"""
    for bx0, bx1 in BRAIDS:
        if bx0 <= x <= bx1:
            return 230 * smooth(x, bx0, bx0 + 450) * (1 - smooth(x, bx1 - 450, bx1))
    return 0.0

def braid_dist(x, y):
    """北江北岸辫状支汊（规格图顶部两条分支）"""
    d = 1e9
    for bx0, bx1 in BRAIDS:
        if bx0 - 60 < x < bx1 + 60:
            d = min(d, abs(y - (NORTH_RIVER(x) - braid_offset(x))))
    return d

def river_dist(x, y):
    d = abs(y - NORTH_RIVER(x))
    for fx, join_y in TRIBS:
        if y > join_y - 160:
            d = min(d, abs(x - fx(y)))
    return d

def terrain_h(x, y):
    r = math.hypot(x, y)
    # 城外基底：冲积平原（10-60m 带）
    h = 25 + math.sin(x * 0.00625) * math.cos(y * 0.0054) * 14 + math.sin(x * 0.0016 - y * 0.0021) * 18
    # 南岭主脉（+y 南缘，规格带 200-600m）
    south_w = smooth(y, 1100, 2560)
    ridge = 1 - abs(math.sin(x * 0.002 + 2.1) * 0.72 + math.sin(x * 0.0052 + y * 0.0016) * 0.28)
    h += south_w * (180 + ridge * ridge * 260 + math.sin(x * 0.0063 + y * 0.0046) * 30)
    # 东山（+x 东缘，规格丘陵带偏高体量；东关垭口豁口 y≈-240）
    east_w = smooth(x, 1360, 2560) * (1 - 0.75 * gauss((y + 240) / 440))
    ridge_e = 1 - abs(math.sin(y * 0.0024 + 0.7) * 0.65 + math.sin(x * 0.0039 + y * 0.0026) * 0.35)
    h += east_w * (90 + ridge_e * ridge_e * 190 + math.cos(x * 0.0056) * 22)
    # 西缘缓丘（河湾阶地观感）
    west_w = smooth(-x, 1680, 2880)
    h += west_w * (32 + math.sin(x * 0.003 - 1.2) * math.cos(y * 0.0025) * 40)
    # 北江主槽
    rmask = 1 - smooth(river_dist(x, y), 64, 272)
    h -= rmask * 104
    # 支流槽（愈近汇入口愈深；只挖到实算汇入口为止，不越过主河北岸）
    for fx, join_y in TRIBS:
        if y < join_y - 40:
            continue
        tmask = 1 - smooth(abs(x - fx(y)), 32, 112)
        if tmask <= 0:
            continue
        deep = 1 - smooth(y, join_y, join_y + 1040)
        h -= tmask * (40 + 56 * deep)
    # 北江北侧辫状支汊（浅槽）
    bd = braid_dist(x, y)
    h -= (1 - smooth(bd, 50, 150)) * 46
    # —— 圣京台地：圆形三级环带（对应规格台地 40-80m 色环，城墙对角恰在顶面内）——
    t1 = smooth(r, 1050, 1120)   # 80 → 62
    t2 = smooth(r, 1230, 1300)   # 62 → 45
    t3 = smooth(r, 1400, 1720)   # 45 → 城外
    plat = 80 - 18 * t1 - 17 * t2
    h = mix(plat, h, t3)
    # —— 运河（北门 → 北江；切穿台地环带，越往江越深）——
    if CANAL_Y1 - 60 < y < CANAL_Y0 and abs(x) < 200:
        t = canal_t(y)
        h -= (1 - smooth(abs(x - CANAL_MX(y)), 25, 70)) * mix(12, 100, t)
    # —— 出城南渠（南门 → 向南渐没入南岭山麓）——
    scd = south_channel_dist(x, y)
    fade = 1 - smooth(y, 2000, 2400)
    h -= (1 - smooth(scd, 25, 65)) * 14 * fade
    return h

def terrain_color(x, y, h):
    """返回 (r,g,b)：田块 / 滩湿地 / 泥滩 / 岩石 / 雪线 / 城内台地"""
    rd = river_dist(x, y)
    noise = (math.sin(x * 0.026) * math.cos(y * 0.024) + 1) * 0.5
    r, g, b = mix(0.26, 0.31, noise), mix(0.36, 0.42, noise), mix(0.15, 0.20, noise)
    rr = math.hypot(x, y)
    if rr < 1050:  # 台地顶面：夯土
        r, g, b = 0.55, 0.51, 0.44
    elif rr < 1230:  # 环带一（62m 台阶）
        r, g, b = 0.53, 0.50, 0.41
    elif rr < 1400:  # 环带二（45m 台阶，泛绿）
        r, g, b = 0.47, 0.48, 0.35
    # 平原草地农田（130m 田块伪随机双色）
    f = math.sin(math.floor(x / 130) * 12.9898 + math.floor(y / 130) * 78.233) * 43758.5453
    fp = f - math.floor(f)
    if -8 < h < 48 and rd > 320:
        t = 0.55 * (1 - smooth(h, 32, 64))
        fr, fg, fb = (0.54, 0.58, 0.28) if fp > 0.5 else (0.43, 0.49, 0.24)
        r, g, b = mix(r, fr, t), mix(g, fg, t), mix(b, fb, t)
    # 滩湿地：河道两侧低洼带
    if h < 5 and rd < 368:
        t = (1 - smooth(rd, 240, 368)) * smooth(5 - h, 0, 20) * 0.8
        r, g, b = mix(r, 0.31, t), mix(g, 0.35, t), mix(b, 0.22, t)
    if h < -8:  # 泥滩
        t = smooth(-h, 8, 72)
        r, g, b = mix(r, 0.43, t), mix(g, 0.39, t), mix(b, 0.28, t)
    if h > 64:
        t = smooth(h, 64, 192)
        r, g, b = mix(r, 0.42, t), mix(g, 0.40, t), mix(b, 0.35, t)
    if h > 192:
        t = smooth(h, 192, 272)
        r, g, b = mix(r, 0.55, t), mix(g, 0.52, t), mix(b, 0.47, t)
    if h > 304:  # 雪线（仅南岭最高峰）
        t = smooth(h, 304, 368)
        r, g, b = mix(r, 0.87, t), mix(g, 0.90, t), mix(b, 0.92, t)
    return (r, g, b)

# ---------------------------------------------------------------- 场景初始化
bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene
scene.unit_settings.system = "METRIC"

def make_mat(name, color, rough=0.9, metal=0.0, alpha=1.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (*color, 1.0)
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Metallic"].default_value = metal
    if alpha < 1.0:
        bsdf.inputs["Alpha"].default_value = alpha
        m.blend_method = "BLEND"
    return m

mat_wall = make_mat("城墙", (0.55, 0.50, 0.42))
mat_wall_dark = make_mat("城墙暗", (0.44, 0.40, 0.34))
mat_gold = make_mat("金顶", (0.82, 0.62, 0.18), rough=0.35, metal=0.7)
mat_red = make_mat("宫墙红", (0.55, 0.22, 0.16))
mat_road = make_mat("道路", (0.60, 0.57, 0.50))
mat_tree = make_mat("密林", (0.13, 0.26, 0.09))
mat_water = make_mat("水面", (0.05, 0.18, 0.24), rough=0.06, alpha=0.9)
# 水面微波法线
wnt = mat_water.node_tree
wn = wnt.nodes.new("ShaderNodeTexNoise"); wn.location = (-400, 0)
wn.inputs["Scale"].default_value = 0.06
wn.inputs["Detail"].default_value = 6
wb = wnt.nodes.new("ShaderNodeBump"); wb.location = (-160, -160)
wb.inputs["Strength"].default_value = 0.18
wb.inputs["Distance"].default_value = 1.2
wnt.links.new(wn.outputs["Fac"], wb.inputs["Height"])
wnt.links.new(wb.outputs["Normal"], wnt.nodes.get("Principled BSDF").inputs["Normal"])
mat_label = make_mat("标注", (0.22, 0.14, 0.09))

# 地形材质：程序化 PBR —— 顶点色底色 × 噪声变化，按坡度/海拔/水距混岩体·湿地·雪线 + 法线凹凸
mat_terrain = bpy.data.materials.new("地形")
mat_terrain.use_nodes = True
nt = mat_terrain.node_tree
nodes, links = nt.nodes, nt.links
bsdf = nodes.get("Principled BSDF")
bsdf.inputs["Roughness"].default_value = 0.95

def nattr(name, loc):
    n = nodes.new("ShaderNodeAttribute"); n.location = loc; n.attribute_name = name
    return n

def nnoise(loc, scale, detail):
    n = nodes.new("ShaderNodeTexNoise"); n.location = loc
    n.inputs["Scale"].default_value = scale
    n.inputs["Detail"].default_value = detail
    return n

def nramp(src, loc, stops):
    n = nodes.new("ShaderNodeValToRGB"); n.location = loc
    cr = n.color_ramp
    while len(cr.elements) > 1:
        cr.elements.remove(cr.elements[-1])
    cr.elements[0].position = stops[0][0]
    cr.elements[0].color = (*stops[0][1], 1.0)
    for pos, colr in stops[1:]:
        e = cr.elements.new(pos)
        e.color = (*colr, 1.0)
    links.new(src, n.inputs["Fac"])
    return n

def nmix(loc, blend="MIX"):
    n = nodes.new("ShaderNodeMix"); n.location = loc
    n.data_type = "RGBA"; n.blend_type = blend
    return n

tc = nodes.new("ShaderNodeTexCoord"); tc.location = (-1500, 0)
n_fine = nnoise((-1250, 260), 0.085, 8)   # 细节噪声（~12m 肌理）
n_big = nnoise((-1250, -60), 0.010, 4)    # 大块 patch（~100m 斑块）
links.new(tc.outputs["Object"], n_fine.inputs["Vector"])
links.new(tc.outputs["Object"], n_big.inputs["Vector"])

a_col = nattr("col", (-1500, 420))
a_rk = nattr("rk", (-1500, -240))
a_sn = nattr("sn", (-1500, -440))
a_wt = nattr("wt", (-1500, -640))

# ① 底色 = 顶点色 × 大块明暗变化
ramp_big = nramp(n_big.outputs["Fac"], (-1020, 60), [(0.0, (0.80, 0.80, 0.78)), (1.0, (1.08, 1.07, 1.02))])
m1 = nmix((-800, 420), "MULTIPLY")
m1.inputs[0].default_value = 1.0
links.new(a_col.outputs["Color"], m1.inputs[6])
links.new(ramp_big.outputs["Color"], m1.inputs[7])
# ② 滩湿地压暗
m_wet = nmix((-560, 380))
links.new(a_wt.outputs["Fac"], m_wet.inputs[0])
links.new(m1.outputs[2], m_wet.inputs[6])
m_wet.inputs[7].default_value = (0.30, 0.28, 0.23, 1.0)
# ③ 岩体（坡度率 × 细节噪声打散）混合岩石色带
ramp_rock = nramp(n_fine.outputs["Color"], (-1020, -240),
                  [(0.0, (0.35, 0.32, 0.29)), (0.5, (0.45, 0.42, 0.37)), (1.0, (0.56, 0.53, 0.48))])
ramp_break = nramp(n_fine.outputs["Fac"], (-1020, -400), [(0.0, (0.55, 0.55, 0.55)), (1.0, (1.0, 1.0, 1.0))])
math_rk = nodes.new("ShaderNodeMath"); math_rk.location = (-700, -320)
math_rk.operation = "MULTIPLY"
links.new(a_rk.outputs["Fac"], math_rk.inputs[0])
links.new(ramp_break.outputs["Color"], math_rk.inputs[1])
m_rock = nmix((-320, 320))
links.new(math_rk.outputs[0], m_rock.inputs[0])
links.new(m_wet.outputs[2], m_rock.inputs[6])
links.new(ramp_rock.outputs["Color"], m_rock.inputs[7])
# ④ 雪线
m_snow = nmix((-100, 320))
links.new(a_sn.outputs["Fac"], m_snow.inputs[0])
links.new(m_rock.outputs[2], m_snow.inputs[6])
m_snow.inputs[7].default_value = (0.92, 0.94, 0.96, 1.0)
links.new(m_snow.outputs[2], bsdf.inputs["Base Color"])
# ⑤ 法线凹凸（细节肌理）
bump = nodes.new("ShaderNodeBump"); bump.location = (-320, 0)
bump.inputs["Strength"].default_value = 0.75
bump.inputs["Distance"].default_value = 3.0
links.new(n_fine.outputs["Fac"], bump.inputs["Height"])
links.new(bump.outputs["Normal"], bsdf.inputs["Normal"])

# ---------------------------------------------------------------- 1. 地形网格 + 顶点色（含细节起伏）
W = 6000
N = 560
print("[1/6] 建地形网格 %dx%d ..." % (N, N))

def detail_h(x, y, h):
    """细节起伏：只在城外自然地面叠加（河道/运河/南渠/城台地不叠），山地振幅更大"""
    plat = smooth(math.hypot(x, y), 1400, 1720)
    m = plat * smooth(river_dist(x, y), 90, 260)
    if CANAL_Y1 - 60 < y < CANAL_Y0:
        m = min(m, smooth(abs(x - CANAL_MX(y)), 60, 160))
    if y > 700:
        m = min(m, smooth(south_channel_dist(x, y), 60, 160))
    if m <= 0:
        return 0.0
    d = (math.sin(x * 0.041 + 1.3) * math.cos(y * 0.037 - 0.6)
         + 0.55 * math.sin(x * 0.087 + 2.1) * math.sin(y * 0.079 - 0.4)
         + 0.3 * math.sin(x * 0.151) * math.cos(y * 0.133 + 1.1))
    return d * (1.4 + smooth(h, 60, 220) * 4.6) * m

mesh = bpy.data.meshes.new("terrain")
bm = bmesh.new()
verts = []
zgrid = []
for iy in range(N + 1):
    row = []
    for ix in range(N + 1):
        x = -W / 2 + W * ix / N
        y = -W / 2 + W * iy / N
        h0 = terrain_h(x, y)
        h = h0 + detail_h(x, y, h0)
        row.append(h)
        verts.append(bm.verts.new((x, y, h)))
    zgrid.append(row)
bm.verts.ensure_lookup_table()
for iy in range(N):
    for ix in range(N):
        v0 = verts[iy * (N + 1) + ix]
        v1 = verts[iy * (N + 1) + ix + 1]
        v2 = verts[(iy + 1) * (N + 1) + ix]
        v3 = verts[(iy + 1) * (N + 1) + ix + 1]
        bm.faces.new((v0, v1, v3, v2))
bm.to_mesh(mesh)
bm.free()

col_attr = mesh.color_attributes.new(name="col", type="FLOAT_COLOR", domain="POINT")
rk_attr = mesh.attributes.new(name="rk", type="FLOAT", domain="POINT")  # 岩体率（坡度+海拔驱动）
sn_attr = mesh.attributes.new(name="sn", type="FLOAT", domain="POINT")  # 雪线率
wt_attr = mesh.attributes.new(name="wt", type="FLOAT", domain="POINT")  # 滩湿地率
cols, rks, sns, wts = [], [], [], []
cell = W / N
for iy in range(N + 1):
    for ix in range(N + 1):
        v = mesh.vertices[iy * (N + 1) + ix]
        x, y, h = v.co.x, v.co.y, v.co.z
        r, g, b = terrain_color(x, y, h)
        cols.extend((r, g, b, 1.0))
        ix0, ix1 = max(ix - 1, 0), min(ix + 1, N)
        iy0, iy1 = max(iy - 1, 0), min(iy + 1, N)
        dzdx = (zgrid[iy][ix1] - zgrid[iy][ix0]) / (cell * (ix1 - ix0))
        dzdy = (zgrid[iy1][ix] - zgrid[iy0][ix]) / (cell * (iy1 - iy0))
        slope = math.hypot(dzdx, dzdy)
        rks.append(clamp(smooth(slope, 0.30, 0.75) * 0.85 + smooth(h, 80, 220) * 0.30, 0.0, 1.0))
        sns.append(smooth(h, 304, 368))
        wts.append(clamp((1 - smooth(river_dist(x, y), 240, 368)) * smooth(4 - h, 0, 18), 0.0, 1.0))
col_attr.data.foreach_set("color", cols)
rk_attr.data.foreach_set("value", rks)
sn_attr.data.foreach_set("value", sns)
wt_attr.data.foreach_set("value", wts)
mesh.update()
mesh.validate()
mesh.polygons.foreach_set("use_smooth", [True] * len(mesh.polygons))  # 平滑着色，消除低多边形刻面
terrain_ob = bpy.data.objects.new("地形", mesh)
terrain_ob.data.materials.append(mat_terrain)
scene.collection.objects.link(terrain_ob)

# ---------------------------------------------------------------- 2. 水面条带
def river_ribbon(name, center_fn, t0, t1, half_w, steps=120, z=0.0, z_fn=None):
    verts_w = []
    faces = []
    for i in range(steps + 1):
        t = t0 + (t1 - t0) * i / steps
        cx, cy = center_fn(t)
        a = center_fn(max(t0, t - 20))
        b = center_fn(min(t1, t + 20))
        nx, ny = -(b[1] - a[1]), (b[0] - a[0])
        ln = math.hypot(nx, ny) or 1.0
        nx, ny = nx / ln, ny / ln
        zi = z_fn(cx, cy) if z_fn else z
        verts_w.append((cx - nx * half_w, cy - ny * half_w, zi))
        verts_w.append((cx + nx * half_w, cy + ny * half_w, zi))
        if i < steps:
            k = i * 2
            faces.append((k, k + 1, k + 3, k + 2))
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts_w, [], faces)
    ob = bpy.data.objects.new(name, me)
    scene.collection.objects.link(ob)
    ob.data.materials.append(mat_water)
    return ob

print("[2/6] 铺水面条带 ...")
river_ribbon("北江", lambda t: (t, NORTH_RIVER(t)), -3000, 3000, 190)
for i, (fx, join_y) in enumerate(TRIBS):
    river_ribbon("支流%d" % i, lambda t, fx=fx: (fx(t), t), join_y - 25, 2400, 85, 90, z=-0.5)
# 辫状支汊（北江北岸，两端渐变归并主河：分汊→汇合）
river_ribbon("北汊西", lambda t: (t, NORTH_RIVER(t) - braid_offset(t)), -2830, -700, 70, 80, z=-0.5)
river_ribbon("北汊东", lambda t: (t, NORTH_RIVER(t) - braid_offset(t)), 1070, 2830, 70, 80, z=-0.5)
# 运河（圣京北门 → 北江，水面随渠底逐级下降）
river_ribbon("运河", lambda t: (CANAL_MX(t), t), CANAL_Y1, CANAL_Y0, 34, 60,
             z_fn=lambda cx, cy: terrain_h(cx, cy) + 3.5)
# 出城南渠（圣京南门 → 向南渐没）
river_ribbon("南渠", lambda t: (0, t), 760, 1900, 30, 50,
             z_fn=lambda cx, cy: terrain_h(cx, cy) + 3.5)

# ---------------------------------------------------------------- 3. 城池体块
print("[3/6] 搭城池体块 ...")
def box(name, sx, sy, sz, x, y, z, mat):
    bpy.ops.mesh.primitive_cube_add(size=1, location=(x, y, z))
    ob = bpy.context.active_object
    ob.name = name
    ob.scale = (sx, sy, sz)
    bpy.ops.object.transform_apply(scale=True)
    ob.data.materials.append(mat)
    return ob

def wall_ring(half, thick, height, gate, mat, z):
    seg = (half - gate) / 2
    off = gate + seg
    for side in (-1, 1):
        for sx in (-1, 1):
            box("墙%d-NS" % half, seg * 2, thick, height, sx * off, side * half, z + height / 2, mat)
            box("墙%d-EW" % half, thick, seg * 2, height, side * half, sx * off, z + height / 2, mat)

Z = 80  # 台地顶面（三级环带 80/62/45）
wall_ring(736, 12, 24, 96, mat_wall, Z)   # 外城
wall_ring(384, 10, 16, 64, mat_wall_dark, Z)  # 内城
wall_ring(176, 8, 12, 40, mat_wall_dark, Z)   # 皇城
# 城门楼（外城四门）
for gx, gy in ((0, 736), (0, -736), (736, 0), (-736, 0)):
    horizontal = gy != 0
    box("门楼", 110 if horizontal else 20, 20 if horizontal else 110, 18, gx, gy, Z + 24 + 9, mat_red)
# 角楼
for sx in (-1, 1):
    for sy in (-1, 1):
        box("角楼", 24, 24, 30, sx * 736, sy * 736, Z + 12 + 15, mat_red)
# 御道（十字）+ 环路
box("御道NS", 104, 1472, 0.6, 0, 0, Z + 0.3, mat_road)
box("御道EW", 1472, 104, 0.6, 0, 0, Z + 0.3, mat_road)
bpy.ops.mesh.primitive_torus_add(align="WORLD", location=(0, 0, Z + 0.35),
                                 major_radius=544, minor_radius=52)
ring = bpy.context.active_object
ring.name = "环路"
ring.scale = (1, 1, 0.012)
bpy.ops.object.transform_apply(scale=True)
ring.data.materials.append(mat_road)
# 皇城台基 + 主殿（金顶）+ 两翼
box("皇城台基", 352, 352, 6, 0, 0, Z + 3, mat_road)
box("主殿", 96, 48, 28, 0, 0, Z + 6 + 14, mat_red)
bpy.ops.mesh.primitive_cone_add(vertices=4, radius1=84, radius2=0, depth=26,
                                location=(0, 0, Z + 6 + 28 + 13))
roof = bpy.context.active_object
roof.name = "主殿顶"
roof.rotation_euler = (0, 0, math.radians(45))
roof.data.materials.append(mat_gold)
box("东殿", 40, 64, 18, 110, 0, Z + 6 + 9, mat_red)
box("西殿", 40, 64, 18, -110, 0, Z + 6 + 9, mat_red)
# 东关哨塔（东山垭口）+ 南关/北关（沿云川，规格图东侧两处关隘）
box("东关哨塔", 24, 24, 36, 2400, -240, terrain_h(2400, -240) + 18, mat_wall)
box("南关哨塔", 24, 24, 34, 1920, 600, terrain_h(1920, 600) + 17, mat_wall)
box("北关哨塔", 24, 24, 34, 1698, -500, terrain_h(1698, -500) + 17, mat_wall)

# ---------------------------------------------------------------- 4. 南岭密林 + 平原疏林（合并单网格）
print("[4/6] 散布林木 ...")
bm_t = bmesh.new()
random.seed(7)
count = 0
guard = 0
while count < 1200 and guard < 20000:
    guard += 1
    x = random.uniform(-2900, 2900)
    y = random.uniform(-2900, 2900)
    d = max(abs(x), abs(y))
    if d < 800:  # 城内不种
        continue
    rd = river_dist(x, y)
    if rd < 170:
        continue
    # 避开运河与出城南渠
    if abs(x - CANAL_MX(y)) < 130 and CANAL_Y1 < y < CANAL_Y0:
        continue
    if abs(x) < 130 and 760 < y < 2400:
        continue
    in_forest = y > 1300  # 南岭密林带
    in_plains = y < 900 and rd > 420 and random.random() < 0.25
    if not (in_forest or in_plains):
        continue
    h = terrain_h(x, y)
    if h < -5 or h > 380:
        continue
    s = random.uniform(0.7, 1.6) * (1.3 if in_forest else 1.0)
    mtx = Matrix.Translation((x, y, h + 30 * s)) @ Matrix.Scale(s, 4)
    bmesh.ops.create_cone(bm_t, cap_ends=True, cap_tris=True, segments=6,
                          radius1=16, radius2=2.5, depth=58, matrix=mtx)
    count += 1
mesh_t = bpy.data.meshes.new("林木")
bm_t.to_mesh(mesh_t)
bm_t.free()
mesh_t.polygons.foreach_set("use_smooth", [True] * len(mesh_t.polygons))
tree_ob = bpy.data.objects.new("林木", mesh_t)
scene.collection.objects.link(tree_ob)
tree_ob.data.materials.append(mat_tree)
print("    林木 %d 棵" % count)

# ---------------------------------------------------------------- 5. 预留区虚线框 + 地名标注
print("[5/6] 加预留区与地名标注 ...")
mat_dash = make_mat("预留线", (0.72, 0.69, 0.62))

def dashed_frame(cx, cy, sx, sy):
    """矩形虚线框（每 130m 一段 75m 线段），单网格合并"""
    bm_f = bmesh.new()
    z0 = terrain_h(cx, cy) + 4
    corners = ((cx - sx, cy - sy, cx + sx, cy - sy), (cx + sx, cy - sy, cx + sx, cy + sy),
               (cx + sx, cy + sy, cx - sx, cy + sy), (cx - sx, cy + sy, cx - sx, cy - sy))
    for (ax, ay, bx, by) in corners:
        ln = math.hypot(bx - ax, by - ay)
        n = int(ln // 130) + 1
        for k in range(n):
            t0d = k * 130 / ln
            t1d = min((k * 130 + 75) / ln, 1.0)
            if t1d <= t0d:
                continue
            p0 = (ax + (bx - ax) * t0d, ay + (by - ay) * t0d)
            p1 = (ax + (bx - ax) * t1d, ay + (by - ay) * t1d)
            ang = math.atan2(p1[1] - p0[1], p1[0] - p0[0])
            seg_ln = math.hypot(p1[0] - p0[0], p1[1] - p0[1]) or 1
            mtx = (Matrix.Translation(((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2, z0))
                   @ Matrix.Rotation(ang, 4, "Z") @ Matrix.Diagonal((seg_ln, 8, 3, 1)))
            bmesh.ops.create_cube(bm_f, size=1, matrix=mtx)
    me_f = bpy.data.meshes.new("预留框")
    bm_f.to_mesh(me_f)
    bm_f.free()
    ob_f = bpy.data.objects.new("预留框", me_f)
    scene.collection.objects.link(ob_f)
    ob_f.data.materials.append(mat_dash)

RESERVES = [
    ("外部平原预留", -1900, 900, 450, 350),
    ("宫城核心预留", -1700, -450, 400, 300),
    ("东山关隘预留", 2350, -850, 400, 300),
    ("南岭山脉预留", 300, 2150, 600, 350),
]
for nm, cx, cy, sx, sy in RESERVES:
    dashed_frame(cx, cy, sx, sy)

def label(text, x, y, size=110):
    h = terrain_h(x, y)
    cur = bpy.data.curves.new(text, "FONT")
    cur.body = text
    cur.size = size
    cur.extrude = 5
    ob = bpy.data.objects.new(text, cur)
    ob.location = (x, y, h + 30)
    scene.collection.objects.link(ob)
    ob.data.materials.append(mat_label)

label("圣京", 0, 380)
label("北江", -1700, NORTH_RIVER(-1700) + 320, 130)
label("南岭", 1700, 2650, 150)
label("东山丘陵", 2650, -500, 120)
label("东关", 2280, -160, 90)
label("南关", 2010, 640, 80)
label("北关", 1790, -540, 80)
label("江北冲积平原", 1000, -1050, 100)
label("玉溪", -1500, 900, 90)
label("清溪", 1340, 1100, 90)
label("云川", 1950, 1000, 90)
label("江湾", 1150, NORTH_RIVER(1150) - 330, 90)
label("运河", 90, -1150, 80)
for nm, cx, cy, sx, sy in RESERVES:
    label(nm, cx - sx * 0.5, cy, 80)

# ---------------------------------------------------------------- 6. 灯光 / 相机 / 保存 / 渲染
print("[6/6] 灯光相机与渲染 ...")
sun = bpy.data.lights.new("日光", "SUN")
sun.energy = 4.0
sun.angle = math.radians(4)  # 更硬的阴影
sun_ob = bpy.data.objects.new("日光", sun)
scene.collection.objects.link(sun_ob)
sun_ob.rotation_euler = Vector((-0.5, -0.35, -0.85)).to_track_quat("-Z", "Y").to_euler()

# Nishita 大气天空（真实散射，不与日光重复出现太阳盘）
world = bpy.data.worlds.new("天光")
world.use_nodes = True
bg = world.node_tree.nodes.get("Background")
sky = world.node_tree.nodes.new("ShaderNodeTexSky")
try:
    sky.sky_type = "MULTIPLE_SCATTERING"  # Blender 5.x（旧版为 NISHITA）
except Exception:
    pass
sky.sun_elevation = math.radians(38)
sky.sun_rotation = math.radians(215)
sky.sun_disc = False
world.node_tree.links.new(sky.outputs["Color"], bg.inputs["Color"])
bg.inputs[1].default_value = 0.12  # 物理天空亮度很高，环境光只补暗部
scene.world = world
# 电影级色调映射
try:
    scene.view_settings.look = "AgX - Punchy"
except Exception:
    pass
try:
    scene.view_settings.exposure = -0.2
except Exception:
    pass

cam = bpy.data.cameras.new("主相机")
cam.lens = 45
cam.clip_start = 1.0
cam.clip_end = 30000
cam_ob = bpy.data.objects.new("主相机", cam)
scene.collection.objects.link(cam_ob)
cam_ob.location = (3800, -4100, 2500)
look = Vector((100, -300, 100)) - Vector(cam_ob.location)
cam_ob.rotation_euler = look.to_track_quat("-Z", "Y").to_euler()
scene.camera = cam_ob

bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
print("    blend 已保存:", OUT_BLEND)

# 导出同款彩色网格 PLY（Open3D 可读，与样例格式一致；仅地形网格）
try:
    bpy.ops.object.select_all(action="DESELECT")
    terrain_ob.select_set(True)
    bpy.context.view_layer.objects.active = terrain_ob
    bpy.ops.wm.ply_export(filepath="F:/blue-whale/work/shengjing-terrain/shengjing-terrain.ply",
                          export_selected_objects=True, export_colors="SRGB")
    print("    PLY 已导出: shengjing-terrain.ply")
except Exception as e:
    print("    PLY 导出失败(不影响建模): %s" % e)

# 导出非地形物件（城墙/城楼/水面/林木/标注）为 GLB： Principled 材质原生保留，
# 地形走 PLY 顶点色（程序化节点图无法进 glTF），网页端两份一起加载
try:
    bpy.ops.object.select_all(action="DESELECT")
    for ob in scene.objects:
        if ob.type in ("MESH", "FONT") and ob.name != "地形":
            ob.select_set(True)
    bpy.context.view_layer.objects.active = scene.objects.get("外城") or scene.objects[0]
    bpy.ops.export_scene.gltf(filepath="F:/blue-whale/work/shengjing-terrain/shengjing-structures.glb",
                              export_format="GLB", use_selection=True,
                              export_yup=True)
    print("    GLB 已导出: shengjing-structures.glb")
except Exception as e:
    print("    GLB 导出失败(不影响建模): %s" % e)

scene.render.resolution_x = 1440
scene.render.resolution_y = 1080
engines = [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
for eng, samples in (("BLENDER_EEVEE_NEXT", None), ("BLENDER_EEVEE", None), ("CYCLES", 64), ("BLENDER_WORKBENCH", None)):
    if eng in engines:
        scene.render.engine = eng
        if eng == "CYCLES":
            scene.cycles.samples = samples
            scene.cycles.use_denoising = True
        if eng == "BLENDER_WORKBENCH":
            scene.display.shading.color_type = "VERTEX"
        scene.render.filepath = OUT_PREVIEW
        try:
            bpy.ops.render.render(write_still=True)
            print("    预览渲染成功: %s (%s)" % (OUT_PREVIEW, eng))
            # 第二机位：正俯视（验证辫状支汊/关隘/预留区布局）
            cam_ob.location = (0, 0, 9200)
            cam_ob.rotation_euler = (Vector((0, 0, 0)) - Vector(cam_ob.location)).to_track_quat("-Z", "Y").to_euler()
            scene.render.filepath = OUT_PREVIEW.replace(".png", "-top.png")
            bpy.ops.render.render(write_still=True)
            print("    俯视渲染成功: %s" % OUT_PREVIEW.replace(".png", "-top.png"))
            break
        except Exception as e:
            print("    %s 渲染失败，降级: %s" % (eng, e))

tris = sum(len(p.vertices) - 2 for p in mesh.polygons)
print("完成：地形 %d 顶点 / %d 三角面；林木 %d 棵" % (len(mesh.vertices), tris, count))
