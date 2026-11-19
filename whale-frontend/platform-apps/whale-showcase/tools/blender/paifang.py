"""牌坊（四柱三间三楼）。

石基 + 朱红立柱 + 青瓦顶。整体沿 X 展开，厚度沿 Y，底面 z=0。
three.js 侧不旋转：跨度落在 X 向，正好横跨沿 Z 的街道。

运行：blender --background --factory-startup --python paifang.py
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import blib  # noqa: E402

blib.reset()

STONE = blib.mat("stone", blib.srgb(0xB8B0A0), rough=0.9)
STONE_D = blib.mat("stoneDark", blib.srgb(0x7C7568), rough=0.92)
RED = blib.mat("lacquer", blib.srgb(0x8E2F22), rough=0.55)
# 拱眼壁比柱子再暗两档：它在斗拱背后，本来就吃不到光，同色会让整片糊成一团
RED_D = blib.mat("lacquerDark", blib.srgb(0x5C1D15), rough=0.72)
GOLD = blib.mat("gold", blib.srgb(0xD9B25E), rough=0.42, metal=0.15)
TILE = blib.mat("tile", blib.srgb(0x343A40), rough=0.78)

# ---- 关键尺寸（一处改，全件跟着走）
#
# 开间必须"明间宽、次间窄"。旧值 INNER=1.30 / OUTER=3.90 时两间都是 2.60m——
# 三等分出一座牌坊，出图更像一个门洞加两条腿。四柱三间的常规比例是
# 明间 : 次间 ≈ 1.4 : 1，这里取 3.10 : 2.20。
OUTER_X = 3.75
INNER_X = 1.55
COL_W = 0.40
OUTER_H = 3.90
INNER_H = 6.30
FRAME_Y = 0.62  # 额枋厚度

# 斗拱带总高（坐斗 + 两跳 + 挑檐桁）。喂给屋顶当作檐口标高
DG_H = 0.66

parts = []

# ---------------------------------------------------------------- 立柱
for sx in (-1, 1):
    for x, h in ((OUTER_X, OUTER_H), (INNER_X, INNER_H)):
        cx = sx * x
        parts.append(blib.box(f"col_{cx}", (COL_W, COL_W, h), (cx, 0, h / 2), material=RED, bevel=0.02))
        # 夹杆石：石基比柱子外扩一圈，柱脚才不会"插进地里"
        parts.append(blib.box(f"base_{cx}", (0.78, 0.78, 0.52), (cx, 0, 0.26), material=STONE_D, bevel=0.03))
        parts.append(blib.box(f"base2_{cx}", (0.92, 0.92, 0.16), (cx, 0, 0.08), material=STONE_D, bevel=0.02))
        # 抱鼓石：贴在夹杆石外侧的鼓形石。没有它，柱子像插在方砖上的木棍
        for sy in (-1, 1):
            parts.append(
                blib.cyl(
                    f"drum_{cx}_{sy}",
                    0.22,
                    0.15,
                    (cx, sy * 0.45, 0.28),
                    rot=(math.pi / 2, 0, 0),
                    seg=12,
                    material=STONE_D,
                )
            )


# ---------------------------------------------------------------- 斗拱
def dougong(name, cx, w, z0):
    """
    一攒斗拱带，坐在额枋顶面，托住挑檐桁。

    <p>旧写法是"一排 0.2×0.24×0.3 的小方块 + 一块 0.3×0.3×0.12 的帽"，方块之间
    留 0.16 的空隙，背后<b>直接是天空</b>——逆光下一圈等距竖条，就是一道栅栏。
    问题不在方块本身，在三点：</p>

    <ol>
      <li>没有拱眼壁。真实斗拱背后是垫板（拱眼壁），从正面看不到天；</li>
      <li>没有"横拱"这道通长构件。真斗拱的视觉主体是<b>连续的水平拱</b>，
          方块（斗）只是它上面的节点，不能等宽等高地排一溜；</li>
      <li>没有出跳。斗拱是<b>垂直墙面伸出去</b>的悬挑构件，只在墙面内堆方块，
          从任何角度看都是一排贴片。</li>
    </ol>

    <p>这里按"坐斗 → 一跳华拱（出跳）→ 横拱（通长）→ 升 / 二跳 → 挑檐桁"分层做，
    华拱贯通前后两面（牌坊两个方向都要看）。</p>
    """
    out = []
    # 攒距 0.44、坐斗 0.28 → 净距 0.16。这是"像不像斗拱"的关键手感：
    # 0.62 攒距时净距 0.38，逆光剪影是一排垛口（实测街景机位一眼看出）；
    # 真实平身科的净距只有一个斗口（十几厘米），远看是一条致密的托木带。
    n = max(3, int(round(w / 0.44)))
    step = w / n

    # 拱眼壁：堵住攒与攒之间的空隙。高度必须一直顶到挑檐桁底面——
    # 只做到 0.46 时，0.46~0.51 那 5cm 会从正面漏出一排亮缝，逆光看着像"牙齿缝里有光"
    out.append(blib.box(f"{name}_spandrel", (w, 0.26, DG_H - 0.15), (cx, 0, z0 + (DG_H - 0.15) / 2), material=RED_D))

    # 横拱：通长一道，两端顶到柱。这是"成组"的关键——它把离散的斗串起来
    out.append(blib.box(f"{name}_gg", (w + 0.04, 0.46, 0.12), (cx, 0, z0 + 0.36), material=RED, bevel=0.006, bevel_seg=1))

    for i in range(n):
        bx = cx - w / 2 + (i + 0.5) * step
        # 坐斗：坐在额枋上，比横拱略窄，形成"下小上大"的层次
        out.append(blib.box(f"{name}_dou{i}", (0.28, 0.34, 0.14), (bx, 0, z0 + 0.07), material=RED, bevel=0.006, bevel_seg=1))
        # 一跳华拱：垂直墙面出跳，贯通前后（0.94 比额枋 0.62 宽，两侧各露 0.16）
        out.append(blib.box(f"{name}_hg1{i}", (0.18, 0.94, 0.10), (bx, 0, z0 + 0.19), material=RED, bevel=0.005, bevel_seg=1))
        # 升：横拱两端的小斗，只在这一层"冒头"
        for s in (-1, 1):
            out.append(
                blib.box(f"{name}_sh{i}{s}", (0.13, 0.26, 0.08), (bx + s * step * 0.36, 0, z0 + 0.455), material=RED, bevel=0.005, bevel_seg=1)
            )
        # 二跳华拱：更高、伸得更远，端头承挑檐桁
        out.append(blib.box(f"{name}_hg2{i}", (0.17, 1.12, 0.09), (bx, 0, z0 + 0.455), material=RED, bevel=0.005, bevel_seg=1))

    # 挑檐桁：压在斗拱上的一道圆桁，出檐从这里开始
    out.append(blib.box(f"{name}_purlin", (w + 0.42, 0.52, 0.15), (cx, 0, z0 + 0.585), material=RED, bevel=0.01, bevel_seg=1))
    return out


# ---------------------------------------------------------------- 单间构件
def bay(name, cx, half_w, y_thick, beam_bottom, beam_top, plaque=False):
    """
    一间：小额枋 → 垫板 → 大额枋 →（匾额）→ 斗拱。

    beam_bottom/beam_top 指"大额枋"的上下沿（古建里这根是承重主梁）。
    """
    w = half_w * 2
    out = []
    # 小额枋 + 由额垫板 + 大额枋
    out.append(blib.box(f"{name}_beamS", (w, y_thick * 0.86, 0.3), (cx, 0, beam_bottom - 0.62), material=RED))
    out.append(blib.box(f"{name}_panel", (w, 0.14, beam_bottom - 0.47 - (beam_bottom - 0.62)), (cx, 0, (beam_bottom - 0.47 + beam_bottom - 0.62) / 2), material=RED_D, bevel=0.01))
    out.append(blib.box(f"{name}_beamB", (w + 0.12, y_thick, beam_top - beam_bottom), (cx, 0, (beam_top + beam_bottom) / 2), material=RED, bevel=0.02))

    # 雀替：梁柱交角的小托木，缺了这一段会显得"梁是粘在柱子上的"
    for s in (-1, 1):
        out.append(
            blib.box(
                f"{name}_que{s}",
                (0.34, y_thick * 0.62, 0.24),
                (cx + s * (half_w - 0.17), 0, beam_bottom - 0.12),
                material=RED,
                bevel=0.01,
            )
        )

    z = beam_top
    if plaque:
        # 匾额：石框 + 金地。宽度跟着开间走，别写死——明间加宽后旧值两侧会空出 0.3
        pw = min(2.62, w - 0.20)
        out.append(blib.box(f"{name}_pqFrame", (pw, 0.20, 1.12), (cx, 0, z + 0.56), material=STONE_D, bevel=0.02))
        out.append(blib.box(f"{name}_pqIn", (pw - 0.24, 0.27, 0.88), (cx, 0, z + 0.56), material=GOLD, bevel=0.01))
        z += 1.12

    out += dougong(name, cx, w, z)
    return out, z + DG_H


def roof_pair(name, cx, width, run, ridge_z, eave_z, tile_pitch=0.30):
    """前后两坡 + 正脊。屋顶比梁架外挑，出檐才不局促。"""
    out = []
    for side in (1, -1):
        r = blib.roof(
            f"{name}_slope{side}",
            width,
            run,
            ridge_z,
            eave_z,
            thickness=0.15,
            tile_pitch=tile_pitch,
            sweep=0.5,
            material=TILE,
        )
        if side < 0:
            # 复制 + 绕 Z 转 180°：屋顶的起翘是沿 X 的偶函数，转过去正好对称
            bpy.ops.object.select_all(action='DESELECT')
            r.select_set(True)
            bpy.context.view_layer.objects.active = r
            bpy.ops.object.duplicate()
            r2 = bpy.context.object
            r2.rotation_euler = (0, 0, math.pi)
            bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
            bpy.data.objects.remove(r, do_unlink=True)
            r = r2
            r.name = f"{name}_slope{side}"
        r.location.x = cx
        bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
        out.append(r)
    out.append(blib.ridge(f"{name}_ridge", width * 0.98, ridge_z + 0.10, material=TILE))
    for o in out:
        if o.name.endswith("_ridge"):
            o.location.x = cx
            bpy.ops.object.select_all(action='DESELECT')
            o.select_set(True)
            bpy.context.view_layer.objects.active = o
            bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
    return out


# ---------------------------------------------------------------- 三间
# 明间（中间）：最高，带匾额，屋顶最大
mid, mid_top = bay("mid", 0.0, INNER_X, FRAME_Y, 3.75, 4.35, plaque=True)
parts += mid
# 出檐(run)不能大：给到 1.7 时屋顶把下面的斗拱和匾额整片吞掉，正视图像三把倒扣的伞
parts += roof_pair("mid", 0.0, 4.05, 0.95, 7.06, mid_top + 0.16)

# 次间（两侧）：低一头
for sx in (-1, 1):
    side = f"side{sx}"
    b, top = bay(side, sx * (INNER_X + OUTER_X) / 2, (OUTER_X - INNER_X) / 2, FRAME_Y * 0.9, 2.85, 3.30)
    parts += b
    # 屋顶中心要往外挪：按开间中心 2.65 摆，内沿到 1.15，正好探进明间、从正面把
    # 匾额左（右）下角切掉一块。挪到 2.85、宽 2.75 后内沿 1.475，藏在内柱后面。
    parts += roof_pair(side, sx * 2.85, 2.75, 0.80, 4.86, top + 0.14, tile_pitch=0.28)

blib.join(parts, "paifang")

# 落地校验：把最底面压到 z=0，避免"建模时随手放在 0.1"导致整件悬空
bpy.context.view_layer.update()
zmin = min((ob.matrix_world @ v.co).z for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices)
print("ZMIN:", round(zmin, 4))
for ob in bpy.data.objects:
    if ob.type == 'MESH':
        ob.location.z -= zmin

blib.setup_check_scene()
blib.render_views("paifang", target=(0, 0, 3.6), radius=11.0, height=4.2, ortho_scale=10.5)
blib.export("paifang.glb")
