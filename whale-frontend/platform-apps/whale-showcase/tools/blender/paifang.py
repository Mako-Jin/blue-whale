"""牌坊（四柱三间三楼）。

石基 + 朱红立柱 + 青瓦顶。整体沿 X 展开，厚度沿 Y，底面 z=0。
three.js 侧绕 Y 轴转 90°，让跨度落在 Z 向的街道上。

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
GOLD = blib.mat("gold", blib.srgb(0xD9B25E), rough=0.42, metal=0.15)
TILE = blib.mat("tile", blib.srgb(0x2E3236), rough=0.5)
PLASTER = blib.mat("plaster", blib.srgb(0xC9C0AE), rough=0.85)

# ---- 关键尺寸（一处改，全件跟着走）
OUTER_X = 3.90
INNER_X = 1.30
COL_W = 0.34
OUTER_H = 3.90
INNER_H = 6.30
FRAME_Y = 0.62  # 额枋厚度
parts = []

# ---------------------------------------------------------------- 立柱
for sx in (-1, 1):
    for x, h in ((OUTER_X, OUTER_H), (INNER_X, INNER_H)):
        cx = sx * x
        parts.append(blib.box(f"col_{cx}", (COL_W, COL_W, h), (cx, 0, h / 2), material=RED, bevel=0.02))
        # 夹杆石：石基比柱子外扩一圈，柱脚才不会"插进地里"
        parts.append(blib.box(f"base_{cx}", (0.78, 0.78, 0.52), (cx, 0, 0.26), material=STONE_D, bevel=0.03))
        parts.append(blib.box(f"base2_{cx}", (0.92, 0.92, 0.16), (cx, 0, 0.08), material=STONE_D, bevel=0.02))


# ---------------------------------------------------------------- 单间构件
def bay(name, cx, half_w, y_thick, beam_bottom, beam_top, plaque=False, roof_w=0.0, run=0.0):
    """
    一间：小额枋 → 垫板 → 大额枋 →（匾额）→ 斗拱 → 挑檐桁 →（屋顶另挂）。

    beam_bottom/beam_top 指"大额枋"的上下沿（古建里这根是承重主梁）。
    """
    w = half_w * 2
    out = []
    # 小额枋 + 由额垫板 + 大额枋
    out.append(blib.box(f"{name}_beamS", (w, y_thick * 0.86, 0.3), (cx, 0, beam_bottom - 0.62), material=RED))
    out.append(blib.box(f"{name}_panel", (w, 0.14, beam_bottom - 0.47 - (beam_bottom - 0.62)), (cx, 0, (beam_bottom - 0.47 + beam_bottom - 0.62) / 2), material=RED, bevel=0.01))
    out.append(blib.box(f"{name}_beamB", (w + 0.12, y_thick, beam_top - beam_bottom), (cx, 0, (beam_top + beam_bottom) / 2), material=RED, bevel=0.02))

    # 雀替：梁柱交角的小托木，缺了这一段会显得"梁是粘在柱子上的"
    for s in (-1, 1):
        # 贴梁底、齐柱侧的一对小托木。别转角度——转过之后正视图像插了两支箭头
        out.append(
            blib.box(
                f"{name}_que{s}",
                (0.30, y_thick * 0.62, 0.22),
                (cx + s * (half_w - 0.16), 0, beam_bottom - 0.11),
                material=RED,
                bevel=0.01,
            )
        )

    z = beam_top
    if plaque:
        # 匾额：石框 + 金地，居中偏内，两侧留白是刻意的
        out.append(blib.box(f"{name}_pqFrame", (2.30, 0.2, 1.10), (cx, 0, z + 0.55), material=STONE, bevel=0.02))
        out.append(blib.box(f"{name}_pqIn", (2.06, 0.26, 0.86), (cx, 0, z + 0.55), material=GOLD, bevel=0.01))
        z += 1.10

    # 斗拱带：一排小方块 + 挑出的桁
    n = max(3, int(w / 0.36))
    for i in range(n):
        bx = cx - w / 2 + (i + 0.5) * (w / n)
        out.append(blib.box(f"{name}_dg{i}", (0.2, 0.24, 0.3), (bx, 0, z + 0.15), material=RED, bevel=0.008))
        out.append(blib.box(f"{name}_dg2{i}", (0.3, 0.3, 0.12), (bx, 0, z + 0.34), material=RED, bevel=0.008))
    out.append(blib.box(f"{name}_purlin", (w + 0.5, 0.34, 0.16), (cx, 0, z + 0.46), material=RED, bevel=0.01))
    return out, z + 0.54


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
parts += roof_pair("mid", 0.0, 3.75, 0.95, 6.95, mid_top + 0.18)

# 次间（两侧）：低一头
for sx in (-1, 1):
    side = f"side{sx}"
    b, top = bay(side, sx * (INNER_X + OUTER_X) / 2, (OUTER_X - INNER_X) / 2, FRAME_Y * 0.9, 2.85, 3.30)
    parts += b
    parts += roof_pair(side, sx * (INNER_X + OUTER_X) / 2, 3.20, 0.82, 4.80, top + 0.14, tile_pitch=0.28)

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
