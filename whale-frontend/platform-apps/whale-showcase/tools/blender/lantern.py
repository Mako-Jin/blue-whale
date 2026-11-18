"""灯笼（挑杆 + 灯笼身 + 流苏）。

<p>街景机位里灯笼是最近的前景物，占屏面积比牌坊还大。程序化那版是"一根杆 +
一个亮圆柱"，出图就是个纸杯插在棍子上。这里把最容易露馅的三处做实：</p>
<ol>
  <li>灯笼体用<b>旋转剖面</b>收腰（上小下小中间鼓），八棱；</li>
  <li>加<b>上下箍</b>（深色木圈）和<b>吊绳</b>，灯体不直接粘在挑臂上；</li>
  <li>底部<b>流苏</b>，让剪影有收束。</li>
</ol>

运行：blender --background --factory-startup --python lantern.py
"""

import math
import os
import sys

import bpy
from mathutils import Vector

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import blib  # noqa: E402

blib.reset()

WOOD = blib.mat("lanternWood", blib.srgb(0x3B2C20), rough=0.88)
STONE = blib.mat("lanternStone", blib.srgb(0x6E675A), rough=0.93)
# 灯罩自发光：Blender 的 Emission Strength 会经 KHR_materials_emissive_strength
# 带到 glTF，three.js 侧再按时间段调 emissiveIntensity
GLOW = blib.mat("lanternGlow", blib.srgb(0xC2410F), rough=0.42, emit=blib.srgb(0xFF7A28))
CAP = blib.mat("lanternCap", blib.srgb(0x2A211A), rough=0.7)
TASSEL = blib.mat("lanternTassel", blib.srgb(0xA82E22), rough=0.9)

POST_H = 2.72
ARM = 0.62
HANG_Y = -0.44  # 挑臂末端（灯挂在街道一侧，即 -Y）
parts = []

# ---------------------------------------------------------------- 杆与基座
parts.append(blib.box("base", (0.34, 0.34, 0.22), (0, 0, 0.11), material=STONE, bevel=0.02))
parts.append(blib.cyl("post", 0.062, POST_H, (0, 0, POST_H / 2), seg=8, material=WOOD, r2=0.05))
# 挑臂：一根横方木 + 一个斜撑，灯笼才不像"贴在杆上"
parts.append(blib.box("arm", (0.09, ARM + 0.14, 0.09), (0, HANG_Y / 2 - 0.02, POST_H - 0.12), material=WOOD, bevel=0.01))
parts.append(
    blib.box(
        "brace",
        (0.06, 0.5, 0.06),
        (0, -0.16, POST_H - 0.34),
        rot=(0.72, 0, 0),
        material=WOOD,
        bevel=0.008,
    )
)
# 端头小挑头
parts.append(blib.box("armTip", (0.13, 0.13, 0.1), (0, HANG_Y - 0.06, POST_H - 0.12), material=WOOD, bevel=0.01))

# ---------------------------------------------------------------- 灯笼体
TOP_Z = POST_H - 0.26  # 吊点
BODY_H = 0.62
BODY_TOP = TOP_Z - 0.12
BODY_C = BODY_TOP - BODY_H / 2


def profile(r_max, h, waist=0.86):
    """旋转剖面：中间鼓、上下收。八棱灯笼的经典轮廓。"""
    pts = []
    n = 7
    for i in range(n + 1):
        t = i / n
        z = -h / 2 + h * t
        # 两端收、中间鼓的平滑曲线
        k = math.sin(math.pi * t) ** 0.55
        r = r_max * (waist + (1 - waist) * k)
        pts.append((r, z))
    return pts


R = 0.215
# LatheGeometry 在 three.js 侧；Blender 这里用 bmesh 旋转
import bmesh  # noqa: E402

me = bpy.data.meshes.new("body")
bm = bmesh.new()
prof = profile(R, BODY_H)
rings = []
for r, z in prof:
    ring = []
    for i in range(8):
        a = (i / 8) * math.tau
        ring.append(bm.verts.new((math.cos(a) * r, math.sin(a) * r, z)))
    rings.append(ring)
bm.verts.ensure_lookup_table()
for i in range(len(rings) - 1):
    for j in range(8):
        a, b_ = rings[i][j], rings[i][(j + 1) % 8]
        c, d = rings[i + 1][(j + 1) % 8], rings[i + 1][j]
        bm.faces.new((a, b_, c, d))
bm.faces.new(list(reversed(rings[0])))
bm.faces.new(rings[-1])
bm.to_mesh(me)
bm.free()
body = bpy.data.objects.new("body", me)
bpy.context.collection.objects.link(body)
body.location = (0, HANG_Y - 0.06, BODY_C)
body.data.materials.append(GLOW)
for p in body.data.polygons:
    p.use_smooth = False
parts.append(body)

# 上下箍 + 吊绳 + 流苏
parts.append(blib.cyl("capTop", R * 0.72, 0.10, (0, HANG_Y - 0.06, BODY_TOP + 0.03), seg=8, material=CAP))
parts.append(blib.cyl("capBot", R * 0.66, 0.09, (0, HANG_Y - 0.06, BODY_TOP - BODY_H - 0.02), seg=8, material=CAP))
parts.append(blib.cyl("rope", 0.014, 0.14, (0, HANG_Y - 0.06, BODY_TOP + 0.13), seg=5, material=WOOD))
parts.append(blib.cyl("hook", 0.012, 0.1, (0, HANG_Y - 0.03, TOP_Z + 0.02), seg=5, material=WOOD))
# 流苏：一小束下垂的细柱
for i in range(5):
    a = (i / 5) * math.tau
    parts.append(
        blib.cyl(
            f"tassel{i}",
            0.012,
            0.24,
            (math.cos(a) * 0.035, HANG_Y - 0.06 + math.sin(a) * 0.035, BODY_TOP - BODY_H - 0.17),
            seg=4,
            material=TASSEL,
        )
    )

blib.join(parts, "lantern")

bpy.context.view_layer.update()
zmin = min((ob.matrix_world @ v.co).z for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices)
for ob in bpy.data.objects:
    if ob.type == 'MESH':
        ob.location.z -= zmin

blib.setup_check_scene(bg=0.02)
blib.render_views("lantern", target=(0, -0.2, 1.6), radius=6.0, height=1.9, ortho_scale=3.4)
blib.export("lantern.glb")
