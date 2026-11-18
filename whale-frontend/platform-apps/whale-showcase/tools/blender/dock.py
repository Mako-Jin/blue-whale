"""渡口木码头与乌篷船。

<p>`--` 之后的参数决定出哪一件：`dock` / `boat`。</p>

<p>坐标约定：模型 <b>z=0 就是水面</b>（不是底面贴地）——这两件都浮在水上，
平台在 z=+0.95、船体吃水到 z=-0.45。放置时 {@code position.y = WATER_LEVEL}。</p>

<p>码头沿 X 长 8.6、沿 Z 宽 5.6（Blender 里沿 Y 是 5.6，导出后 +Y → -Z）。
船头朝 +X。</p>

运行：blender --background --factory-startup --python dock.py -- dock|boat
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import blib  # noqa: E402

blib.reset()

ARGS = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
WHAT = ARGS[0] if ARGS else 'dock'

DECK_WOOD = blib.mat("dockPlank", blib.srgb(0x7A5A40), rough=0.86)
DARK_WOOD = blib.mat("dockBeam", blib.srgb(0x4E3828), rough=0.9)
WET_WOOD = blib.mat("dockPile", blib.srgb(0x503E2E), rough=0.93)
ROPE = blib.mat("dockRope", blib.srgb(0xBFA980), rough=0.95)
HULL = blib.mat("boatHull", blib.srgb(0x76573F), rough=0.8)
CANOPY = blib.mat("boatCanopy", blib.srgb(0x413A30), rough=0.86)


# ---------------------------------------------------------------- 码头


def build_dock():
    parts = []
    LEN, WID = 8.6, 5.6
    DECK = 0.95

    # 板条沿 Y 横向铺：人从岸（+X）走向船（-X），板条垂直于走行方向才是木栈道的做法。
    # 板缝必须留（0.045），否则整片平台是一块木板，光照下没有一条线，看不出是拼的。
    pitch, gap = 0.30, 0.045
    n = int(LEN / pitch)
    for i in range(n):
        x = -LEN / 2 + pitch * (i + 0.5)
        # 每块板在 Z 向的宽度递减/抖动一点，纯均分太像地板砖
        jitter = 0.0 if i % 3 else 0.02
        # 每 5 块有一块略高/略低 6mm：木板是手铺的，完全等高在光下是一条假平线
        sink = (0.006 if i % 5 == 0 else 0.0) - (0.004 if i % 7 == 0 else 0.0)
        parts.append(
            blib.box(
                f"plank{i}",
                (pitch - gap, WID - jitter, 0.11),
                (x, 0, DECK - 0.055 - sink),
                material=DECK_WOOD,
                bevel=0.012,
                bevel_seg=1,
            )
        )

    # 龙骨：板下的横向承重梁，比板窄，从侧面才看得见
    for x in (-LEN / 2 + 0.6, -1.2, 1.2, LEN / 2 - 0.6):
        parts.append(blib.box(f"joist{x}", (0.26, WID + 0.42, 0.24), (x, 0, DECK - 0.23), material=DARK_WOOD, bevel=0.02, bevel_seg=1))

    # 边梁：外沿一圈挡木，把平台"围"起来
    for sy in (-1, 1):
        parts.append(
            blib.box(f"rim{sy}", (LEN, 0.16, 0.30), (0, sy * (WID / 2 + 0.08), DECK - 0.02), material=DARK_WOOD, bevel=0.018, bevel_seg=1)
        )

    # 桩：要穿透水面往河床里扎。露出水面的部分被水浸润，颜色更深（WET_WOOD）
    #
    # <p><b>四个角桩必须穿过板面、露出桩头</b>。桩全埋在板下时（哪怕长度对了），
    # 只要机位高于平台面 5°，视线就整个被边梁挡住——出图就是"一块木板浮在水上"。
    # 真实木码头也是这么做的：角桩当系缆桩用。中间两根不露头，留出走道。</p>
    for x in (-LEN / 2 + 0.7, 0, LEN / 2 - 0.7):
        for y in (-WID / 2 + 0.25, WID / 2 - 0.25):
            corner = x != 0
            if corner:
                # 从水下 3.3m 一直顶到平台面以上 0.52
                parts.append(blib.cyl(f"pile{x}{y}", 0.18, 4.9, (x, y, -0.85), seg=10, material=WET_WOOD))
                parts.append(blib.cyl(f"pileHead{x}{y}", 0.21, 0.1, (x, y, DECK + 0.52), seg=10, material=DARK_WOOD))
                for ring in range(2):
                    parts.append(blib.cyl(f"pileRope{x}{y}{ring}", 0.225, 0.055, (x, y, DECK + 0.16 + ring * 0.13), seg=10, material=ROPE))
            else:
                parts.append(blib.cyl(f"pile{x}{y}", 0.17, 4.3, (x, y, -1.5), seg=10, material=WET_WOOD))
                parts.append(blib.box(f"pileCap{x}{y}", (0.34, 0.34, 0.1), (x, y, DECK - 0.34), material=DARK_WOOD, bevel=0.015, bevel_seg=1))

    # 斜撑：从桩的水下段斜顶到平台底面的承重梁。
    # 必须<b>两端都接上实物</b>（桩 y=±2.55 / 梁 y=±1.6 下方），否则它就是一个
    # 悬在桩边的斜方块——角度再漂亮也没用。
    for x in (-LEN / 2 + 0.7, LEN / 2 - 0.7):
        for sy in (-1, 1):
            parts.append(
                blib.box(
                    f"brace{x}{sy}",
                    (0.14, 0.14, 2.1),
                    (x, sy * 2.075, -0.29),
                    rot=(sy * 0.48, 0, 0),
                    material=DARK_WOOD,
                    bevel=0.015,
                    bevel_seg=1,
                )
            )



    # 水侧爬梯：从平台东沿下到水里（船家上下用）。
    # 码头是西端搭岸、东端伸进深水，所以梯子必须在 +X 端；挂西端就成了"靠岸的梯子"。
    LX = LEN / 2 + 0.14
    for y in (-0.55, 0.55):
        parts.append(blib.box(f"ladderS{y}", (0.1, 0.1, 2.4), (LX, y, -0.25), material=DARK_WOOD, bevel=0.012, bevel_seg=1))
    for i in range(5):
        parts.append(blib.box(f"rung{i}", (0.1, 1.18, 0.07), (LX, 0, -1.25 + i * 0.52), material=DARK_WOOD, bevel=0.01, bevel_seg=1))

    return parts


# ---------------------------------------------------------------- 乌篷船


def build_boat():
    """
    船壳用参数化网格生成，不用"圆柱拉长"。

    <p>旧版是 LatheGeometry 绕 Y 拉 2.8 倍得到的桶，出图是一只拖鞋：船底是圆的、
    船头船尾没有上翘、舷缘是一条直线。这里每个 x 位置单独给宽度、船底高度、舷顶
    高度，两端自然收窄上翘。</p>

    <p>船头朝 +X。吃水线 z=0。</p>
    """
    L, HW = 5.4, 0.82
    NX, NJ = 18, 10

    def width(u):
        return HW * max(0.0, 1.0 - 0.985 * u * u) ** 0.55

    def keel_z(u):
        return -0.44 + 0.40 * (u ** 4)

    def top_z(u):
        return 0.30 + 0.66 * max(0.0, (abs(u) - 0.5) / 0.5) ** 2.3

    # 截面点数：两侧各 2 个"舷缘外翻"点 + 中间 NJ-1 个弧点
    COLS = NJ + 3

    def section(u):
        """
        单帧横剖面点列（左舷 → 船底 → 右舷）。

        <p>舷缘那两个点是关键：早先舷边用一串小方盒拼，出图是<b>一排锯齿</b>——
        方盒是直的、舷线是弯的，每段都歪一点，拼起来就是狗牙。直接把外翻做进
        网格，连续曲面天然贴合，还省掉 36 个物件。</p>
        """
        w, zb, zt = width(u), keel_z(u), top_z(u)
        pts = [
            (w * 1.045, zt + 0.075),  # 舷缘外沿（翻起）
            (w * 0.92, zt + 0.015),  # 舷缘内沿
        ]
        for j in range(1, NJ):
            th = math.pi * j / NJ
            # sin^1.6 让船底由圆变平——真船的横剖面是"平底 + 圆弧转舷"
            pts.append((w * math.cos(th), zt - (zt - zb) * math.sin(th) ** 1.6))
        pts.append((-w * 0.92, zt + 0.015))
        pts.append((-w * 1.045, zt + 0.075))
        return pts

    verts, faces = [], []
    for i in range(NX + 1):
        u = -1.0 + 2.0 * i / NX
        x = u * L / 2
        for (py, pz) in section(u):
            verts.append((x, py, pz))

    for i in range(NX):
        for j in range(COLS - 1):
            a = i * COLS + j
            faces.append((a, a + 1, a + COLS + 1, a + COLS))

    me = bpy.data.meshes.new("hull")
    me.from_pydata(verts, [], faces)
    me.validate()
    me.update()
    hull = bpy.data.objects.new("hull", me)
    bpy.context.collection.objects.link(hull)
    bpy.context.view_layer.objects.active = hull
    hull.select_set(True)
    # 单层壳从岸上看是空心的（背面剔除），必须给厚度
    solid = hull.modifiers.new("sol", 'SOLIDIFY')
    solid.thickness = 0.07
    solid.offset = 1.0
    bpy.ops.object.modifier_apply(modifier=solid.name)
    hull.data.materials.append(HULL)

    parts = [hull]

    # 两段半圆篷，中间留一段敞口（人能站起来撑篙）。篷底挂在舷缘上，不能悬空
    for cx, span, r in ((-1.15, 1.75, 0.56), (1.05, 1.45, 0.52)):
        verts2, faces2 = [], []
        NA, NB = 10, 12
        for i in range(NA + 1):
            x = cx - span / 2 + span * i / NA
            for j in range(NB + 1):
                th = math.pi * j / NB
                verts2.append((x, r * math.cos(th), 0.32 + r * math.sin(th) * 0.92))
        for i in range(NA):
            for j in range(NB):
                a = i * (NB + 1) + j
                faces2.append((a, a + 1, a + NB + 2, a + NB + 1))
        me2 = bpy.data.meshes.new(f"canopy{cx}")
        me2.from_pydata(verts2, [], faces2)
        me2.validate()
        me2.update()
        ob = bpy.data.objects.new(f"canopy{cx}", me2)
        bpy.context.collection.objects.link(ob)
        bpy.context.view_layer.objects.active = ob
        ob.select_set(True)
        s2 = ob.modifiers.new("sol", 'SOLIDIFY')
        s2.thickness = 0.05
        s2.offset = 1.0
        bpy.ops.object.modifier_apply(modifier=s2.name)
        ob.data.materials.append(CANOPY)
        parts.append(ob)

    # 座板与舱板
    parts.append(blib.box("thwart", (0.5, 1.5, 0.07), (0.35, 0, 0.12), material=DARK_WOOD, bevel=0.012, bevel_seg=1))
    parts.append(blib.box("deckBoard", (1.5, 0.9, 0.05), (-2.0, 0, -0.26), material=DARK_WOOD, bevel=0.01, bevel_seg=1))

    # 橹：从船尾斜插入水，一根细杆 + 橹板
    parts.append(
        blib.cyl("oar", 0.045, 2.6, (-3.05, 0.42, 0.18), rot=(0, 1.02, 0), seg=8, material=DARK_WOOD)
    )
    parts.append(
        blib.box("oarBlade", (0.72, 0.045, 0.22), (-4.42, 0.42, -0.66), rot=(0, -0.55, 0), material=DARK_WOOD, bevel=0.012, bevel_seg=1)
    )

    # 竹篙横搁在篷顶（从船尾斜搭向船头），船家的日常姿态
    parts.append(
        blib.cyl("pole", 0.035, 4.6, (0.0, 0.0, 0.88), rot=(0, 1.52, 0), seg=8, material=ROPE)
    )

    return parts


parts = build_dock() if WHAT == 'dock' else build_boat()
blib.join(parts, "dock" if WHAT == 'dock' else "boat")

if WHAT == 'dock':
    blib.setup_check_scene()
    blib.render_views("dock", target=(0, 0, 0.6), radius=13.0, height=4.0, ortho_scale=11.0)
    blib.export("dock.glb")
else:
    blib.setup_check_scene()
    blib.render_views("boat", target=(0, 0, 0.3), radius=9.0, height=3.0, ortho_scale=7.0)
    blib.export("boat.glb")
