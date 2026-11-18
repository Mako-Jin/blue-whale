"""民居（江南/徽派）：白墙 + 青瓦 + 马头墙。

<p>马头墙是这类房子最值钱的一笔：一道阶梯状的封火山墙立在两端，屋顶从墙后露出，
轮廓立刻有了"错落"的感觉。程序化时代那版是"盒子 + 两块斜板"，九栋排开像一排集装箱。</p>

<p>朝向：正面（门）在 Blender 的 -Y，导出 glTF 后朝 +Z；在 three.js 里绕 Y 转 -90°
即可面向 -X（街道在西侧）。底面严格 z=0。</p>

运行：blender --background --factory-startup --python house.py -- small|big
"""

import math
import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import blib  # noqa: E402

blib.reset()

WALL = blib.mat("plaster", blib.srgb(0xB6AE9C), rough=0.92)
WALL_B = blib.mat("plasterBack", blib.srgb(0xA79E8C), rough=0.94)
TILE = blib.mat("tile", blib.srgb(0x2B2F33), rough=0.55)
WOOD = blib.mat("woodDark", blib.srgb(0x4A3327), rough=0.85)
STONE = blib.mat("stoneBase", blib.srgb(0x77705F), rough=0.93)
PAPER = blib.mat("paper", blib.srgb(0xE8C98A), rough=0.6, emit=blib.srgb(0xFFB964))

ARGS = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
BIG = bool(ARGS and ARGS[0] == 'big')

W = 7.4 if BIG else 6.2  # 面宽（沿 X）
D = 5.2 if BIG else 4.4  # 进深（沿 Y）
EAVE = 3.9 if BIG else 3.4
RIDGE = EAVE + 1.85
WALL_T = 0.34

parts = []

# ---------------------------------------------------------------- 台基与墙身
parts.append(blib.box("plinth", (W + 0.45, D + 0.45, 0.36), (0, 0, 0.18), material=STONE, bevel=0.02))
parts.append(blib.box("wall", (W, D, EAVE), (0, 0, 0.36 + EAVE / 2), material=WALL, bevel=0.02))

# 勒脚：墙脚一圈深色石带，白墙直接落在地面上会"飘"
parts.append(blib.box("skirt", (W + 0.08, D + 0.08, 0.5), (0, 0, 0.36 + 0.25), material=STONE, bevel=0.015))

# ---------------------------------------------------------------- 屋顶
# 正脊沿 X，前后两坡。屋顶比墙外挑 0.55，出檐才有阴影
for side in (1, -1):
    r = blib.roof(
        f"slope{side}",
        W + 0.30,
        D / 2 + 0.55,
        RIDGE,
        EAVE + 0.36,
        thickness=0.16,
        tile_pitch=0.30,
        sweep=0.34,
        per_tile=3,
        material=TILE,
    )
    if side < 0:
        bpy.ops.object.select_all(action='DESELECT')
        r.select_set(True)
        bpy.context.view_layer.objects.active = r
        bpy.ops.object.duplicate()
        r2 = bpy.context.object
        r2.rotation_euler = (0, 0, math.pi)
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=False)
        bpy.data.objects.remove(r, do_unlink=True)
        r = r2
    bpy.ops.object.select_all(action='DESELECT')
    r.select_set(True)
    bpy.context.view_layer.objects.active = r
    bpy.ops.object.transform_apply(location=True, rotation=False, scale=False)
    parts.append(r)

parts.append(blib.ridge("ridge", W + 0.22, RIDGE + 0.09, material=TILE))

# ---------------------------------------------------------------- 马头墙
RUN = D / 2 + 0.55
EAVE_TOP = EAVE + 0.36


def roof_z(y):
    """屋面在 |y| 处的高度，与 blib.roof 的剖面公式必须一致。"""
    t = min(abs(y) / RUN, 1.0)
    return RIDGE - (RIDGE - EAVE_TOP) * (t ** 1.45)


def ma_tou(sx):
    """
    两端阶梯状封火山墙（马头墙）。

    <p>关键是<b>高度必须按屋面反推</b>：早先写死成"檐口 + 0.55 + i*0.62"，最高一档
    才 5.55m，而正脊在 5.75m——整道墙埋在屋面里，只在瓦面上露出三个小方块，像屋顶
    上开了三个天窗。现在每一段的顶取"该段内侧边界处的屋面高度 + 0.5"，墙体永远
    高出屋面一截，阶梯轮廓才立得出来。</p>
    """
    out = []
    zs = (0.0, 0.40, 0.72, 1.0)  # 沿进深半侧的比例边界
    for i in range(3):
        yin = RUN * zs[i]
        yout = RUN * zs[i + 1] + (0.12 if i == 2 else 0.0)
        yc = (yin + yout) / 2
        top = roof_z(yin) + 0.52
        h = top - 0.36
        for sy in (1, -1):
            out.append(
                blib.box(
                    f"mt{sx}_{i}_{sy}",
                    (WALL_T, (yout - yin) + 0.02, h),
                    (sx * (W / 2 + 0.08), sy * yc, 0.36 + h / 2),
                    material=WALL_B,
                    bevel=0.02,
                )
            )
            # 压顶瓦：墙头一条深色瓦檐。白墙没有这道黑边就没精神
            out.append(
                blib.box(
                    f"mtc{sx}_{i}_{sy}",
                    (WALL_T + 0.18, (yout - yin) + 0.22, 0.1),
                    (sx * (W / 2 + 0.08), sy * yc, top + 0.05),
                    material=TILE,
                    bevel=0.015,
                )
            )
    return out


parts += ma_tou(1) + ma_tou(-1)

# ---------------------------------------------------------------- 门窗
# 门在正面（-Y）。门洞做成"木框 + 深色门板"，凹进墙里 0.1
door_w, door_h = 1.15, 2.15
dy = -D / 2
parts.append(blib.box("doorFrame", (door_w + 0.28, 0.16, door_h + 0.16), (0, dy - 0.02, 0.36 + (door_h + 0.16) / 2), material=STONE, bevel=0.015))
parts.append(blib.box("doorLeaf", (door_w, 0.1, door_h), (0, dy - 0.10, 0.36 + door_h / 2), material=WOOD, bevel=0.01))
# 门楣上方的小披檐
parts.append(blib.box("doorCanopy", (door_w + 0.9, 0.62, 0.12), (0, dy - 0.28, 0.36 + door_h + 0.3), rot=(0.28, 0, 0), material=TILE, bevel=0.015))

# 窗：木格栅。窗棂是真的细条，不是贴图
def window(cx, cz, ww=1.0, wh=0.85):
    out = []
    out.append(blib.box(f"win_f{cx}{cz}", (ww + 0.16, 0.12, wh + 0.16), (cx, dy - 0.03, cz), material=WOOD, bevel=0.01))
    out.append(blib.box(f"win_p{cx}{cz}", (ww, 0.06, wh), (cx, dy - 0.09, cz), material=PAPER, bevel=0.005))
    for i in range(3):
        out.append(blib.box(f"win_v{cx}{cz}{i}", (0.045, 0.05, wh), (cx - ww / 2 + (i + 1) * ww / 4, dy - 0.13, cz), material=WOOD, bevel=0.0))
    out.append(blib.box(f"win_h{cx}{cz}", (ww, 0.05, 0.045), (cx, dy - 0.13, cz), material=WOOD, bevel=0.0))
    return out


parts += window(-W / 2 + 1.35, 0.36 + EAVE * 0.62)
parts += window(W / 2 - 1.35, 0.36 + EAVE * 0.62)
# 楼上（山墙面）开一个小窗，避免大面积白墙是"死"的
parts += window(0, 0.36 + EAVE * 0.78, ww=0.8, wh=0.7)

blib.join(parts, "house_big" if BIG else "house_small")

bpy.context.view_layer.update()
zmin = min((ob.matrix_world @ v.co).z for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices)
for ob in bpy.data.objects:
    if ob.type == 'MESH':
        ob.location.z -= zmin

blib.setup_check_scene()
blib.render_views("house_" + ("big" if BIG else "small"), target=(0, 0, 2.4), radius=13.0, height=3.0, ortho_scale=10.0)
blib.export("house_big.glb" if BIG else "house_small.glb")
