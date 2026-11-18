"""民居（江南/徽派）：白墙 + 青瓦 + 马头墙；另有一款临街铺面。

<p>马头墙是这类房子最值钱的一笔：一道阶梯状的封火山墙立在两端，屋顶从墙后露出，
轮廓立刻有了"错落"的感觉。程序化时代那版是"盒子 + 两块斜板"，九栋排开像一排集装箱。</p>

<p>三款：</p>
<ul>
  <li><b>small / big</b>：院落住宅。正面只有门与两扇窗，街与墙是硬碰硬的——所以街面
      还需要院墙、门楼、台阶这些"过渡件"（在 props.ts 里程序化补）；</li>
  <li><b>shop</b>：临街铺面。一层前檐往里退 1.5m，由三根檐柱撑起一道<b>深檐廊</b>，
      檐廊下是排门板与柜台，二层是木格窗带。深檐下那块阴影是"街"这个空间最重要的
      一笔——没有它，再多的房子也是一排贴着街的白盒子。</li>
</ul>

<p>朝向：正面（门/柜台）在 Blender 的 -Y，导出 glTF 后朝 +Z；在 three.js 里绕 Y 转 -90°
即可面向 -X（街道在西侧）。底面严格 z=0。</p>

运行：blender --background --factory-startup --python house.py -- small|big|shop
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
WOOD_R = blib.mat("woodRed", blib.srgb(0x6B3A2A), rough=0.82)
STONE = blib.mat("stoneBase", blib.srgb(0x77705F), rough=0.93)
DEBUG = blib.mat("dbg", blib.srgb(0x00FF00), rough=0.5, emit=blib.srgb(0x00FF00))
PAPER = blib.mat("paper", blib.srgb(0xE8C98A), rough=0.6, emit=blib.srgb(0xFFB964))

ARGS = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
VARIANT = ARGS[0] if ARGS and ARGS[0] in ('small', 'big', 'shop') else 'small'
BIG = VARIANT == 'big'
SHOP = VARIANT == 'shop'

if SHOP:
    W, D, EAVE = 6.8, 6.0, 5.20
    RIDGE = EAVE + 1.90
elif BIG:
    W, D, EAVE = 7.4, 5.2, 3.9
    RIDGE = EAVE + 1.85
else:
    W, D, EAVE = 6.2, 4.4, 3.4
    RIDGE = EAVE + 1.85

WALL_T = 0.34
BASE_H = 0.36
EAVE_TOP = EAVE + BASE_H

# 铺面：檐廊进深。正墙退到 -D/2 + PORCH，前檐下空出这一条
PORCH = 1.55 if SHOP else 0.0
# 檐廊在进深之内，所以前坡<b>不需要</b>加长：按常规 0.55 出挑就盖住了。
# 早先按 D/2+1.62 算，檐口挑出去 1.86m 才到柱列——出图是"头重脚轻的凉棚"。
RUN_F = D / 2 + 0.55
RUN_B = D / 2 + 0.55

parts = []

# ---------------------------------------------------------------- 台基与墙身
parts.append(blib.box("plinth", (W + 0.45, D + 0.45, BASE_H), (0, 0, BASE_H / 2), material=STONE, bevel=0.02))

if SHOP:
    # 一层退后做檐廊，二层挑出到柱列——这是"挑楼"，也是铺面最要紧的体量关系。
    #
    # <p>两层一起退后（旧写法）会在檐口下留一条朝天的大缝：从街上看，屋檐以下、
    # 二层墙以上是一道通到天空的暗槽。真实的铺面是二层带着楼板<b>挑</b>在檐廊上面，
    # 由一排檐柱承托。</p>
    L1 = 2.86  # 一层净高
    SLAB = 0.16
    # 一层墙体要留出铺面敞口。实心墙 + 贴在墙上的"暗板"是假的：敞口后面永远是
    # 一道白墙，店里永远是块黑。这里按"左垛 / 右垛 / 门楣 / 后壁"四块拼出凹进去的铺面。
    OPEN_X0, OPEN_X1 = -2.30, 0.70
    OPEN_H = 2.42
    ow = OPEN_X1 - OPEN_X0
    ocx = (OPEN_X0 + OPEN_X1) / 2
    parts.append(blib.box("wallL", (W / 2 + OPEN_X0, D - PORCH, L1), ((OPEN_X0 - W / 2) / 2, PORCH / 2, BASE_H + L1 / 2), material=WALL, bevel=0.02))
    parts.append(blib.box("wallR", (W / 2 - OPEN_X1, D - PORCH, L1), ((OPEN_X1 + W / 2) / 2, PORCH / 2, BASE_H + L1 / 2), material=WALL, bevel=0.02))
    parts.append(blib.box("wallTop", (ow, D - PORCH, L1 - OPEN_H), (ocx, PORCH / 2, BASE_H + OPEN_H + (L1 - OPEN_H) / 2), material=WALL, bevel=0.02))
    # 后壁：铺面内腔深 1.1m，后壁上开一道横向的亮光（像是店里还有一层进深）
    parts.append(blib.box("shopBack", (ow, 0.18, OPEN_H), (ocx, PORCH - 1.1, BASE_H + OPEN_H / 2), material=WOOD, bevel=0.0))
    parts.append(blib.box("shopSideL", (0.12, 1.9, OPEN_H), (OPEN_X0 + 0.06, -0.5, BASE_H + OPEN_H / 2), material=WOOD, bevel=0.0))
    parts.append(blib.box("shopSideR", (0.12, 1.9, OPEN_H), (OPEN_X1 - 0.06, -0.5, BASE_H + OPEN_H / 2), material=WOOD, bevel=0.0))
    parts.append(
        blib.box(
            "wall2",
            (W, D - 0.12, EAVE - L1 - SLAB),
            (0, 0.06, BASE_H + L1 + SLAB + (EAVE - L1 - SLAB) / 2),
            material=WALL,
            bevel=0.02,
        )
    )
    # 楼板：从二层前墙一直挑到柱列，底面就是檐廊的天花
    parts.append(blib.box("floorSlab", (W, D - 0.06, SLAB), (0, 0.03, BASE_H + L1 + SLAB / 2), material=WOOD, bevel=0.01))
    # 两端山墙是联排的共用墙，整进深一通到顶
    for sx in (1, -1):
        parts.append(
            blib.box(
                f"gableWall{sx}",
                (WALL_T, D, EAVE),
                (sx * (W / 2 - WALL_T / 2), 0, BASE_H + EAVE / 2),
                material=WALL_B,
                bevel=0.02,
            )
        )
else:
    parts.append(blib.box("wall", (W, D, EAVE), (0, 0, BASE_H + EAVE / 2), material=WALL, bevel=0.02))

# 勒脚：墙脚一圈深色石带，白墙直接落在地面上会"飘"
parts.append(blib.box("skirt", (W + 0.08, D + 0.08, 0.5), (0, 0, BASE_H + 0.25), material=STONE, bevel=0.015))

# ---------------------------------------------------------------- 屋顶
for side in (1, -1):
    run = RUN_B if side > 0 else RUN_F
    r = blib.roof(
        f"slope{side}",
        W + 0.30,
        run,
        RIDGE,
        EAVE_TOP,
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
def roof_z(y):
    """屋面在 y 处的高度，与 blib.roof 的剖面公式必须一致（两坡 run 可以不同）。"""
    run = RUN_B if y > 0 else RUN_F
    t = min(abs(y) / run, 1.0)
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
        for sy in (1, -1):
            run = RUN_B if sy > 0 else RUN_F
            yin = run * zs[i]
            yout = run * zs[i + 1] + (0.12 if i == 2 else 0.0)
            yc = sy * (yin + yout) / 2
            top = roof_z(sy * yin) + 0.52
            h = top - BASE_H
            out.append(
                blib.box(
                    f"mt{sx}_{i}_{sy}",
                    (WALL_T, (yout - yin) + 0.02, h),
                    (sx * (W / 2 + 0.08), yc, BASE_H + h / 2),
                    material=WALL_B,
                    bevel=0.02,
                )
            )
            # 压顶瓦：墙头一条深色瓦檐。白墙没有这道黑边就没精神
            out.append(
                blib.box(
                    f"mtc{sx}_{i}_{sy}",
                    (WALL_T + 0.18, (yout - yin) + 0.22, 0.1),
                    (sx * (W / 2 + 0.08), yc, top + 0.05),
                    material=TILE,
                    bevel=0.015,
                )
            )
    return out


parts += ma_tou(1) + ma_tou(-1)


# ---------------------------------------------------------------- 门窗
def window(cx, cz, ww=1.0, wh=0.85, plane=None, frame=WOOD):
    """木格栅窗。窗棂是真的细条，不是贴图。"""
    out = []
    dy = -D / 2 if plane is None else plane
    out.append(blib.box(f"win_f{cx}{cz}", (ww + 0.16, 0.12, wh + 0.16), (cx, dy - 0.03, cz), material=frame, bevel=0.01))
    out.append(blib.box(f"win_p{cx}{cz}", (ww, 0.06, wh), (cx, dy - 0.09, cz), material=PAPER, bevel=0.005))
    for i in range(3):
        out.append(blib.box(f"win_v{cx}{cz}{i}", (0.045, 0.05, wh), (cx - ww / 2 + (i + 1) * ww / 4, dy - 0.13, cz), material=frame, bevel=0.0))
    out.append(blib.box(f"win_h{cx}{cz}", (ww, 0.05, 0.045), (cx, dy - 0.13, cz), material=frame, bevel=0.0))
    return out


if SHOP:
    FRONT = -D / 2 + PORCH  # 一层正墙所在平面（檐廊内墙面）
    COL_X = (-W / 2 + 0.55, 0.0, W / 2 - 0.55)
    L1 = 2.86                   # 一层净高
    SLAB_TOP = BASE_H + L1 + 0.16
    COL_Y = -D / 2 + 0.26

    # ---- 檐柱：柱顶顶到二层楼板底面，由它承托挑出的二层
    for i, cx in enumerate(COL_X):
        parts.append(blib.cyl(f"col{i}", 0.115, L1, (cx, COL_Y, BASE_H + L1 / 2), seg=10, material=WOOD_R))
        parts.append(blib.cyl(f"colBase{i}", 0.17, 0.12, (cx, COL_Y, BASE_H + 0.06), seg=10, material=STONE))
        parts.append(blib.box(f"colFoot{i}", (0.36, 0.36, 0.1), (cx, COL_Y, BASE_H + 0.05), material=STONE, bevel=0.01))

    # ---- 檐枋（柱头间的横梁，紧贴楼板底）+ 小额枋
    parts.append(blib.box("eaveBeam", (W - 0.3, 0.22, 0.30), (0, COL_Y, BASE_H + L1 - 0.15), material=WOOD_R, bevel=0.012))
    parts.append(blib.box("eaveTie", (W - 0.5, 0.13, 0.13), (0, COL_Y, BASE_H + L1 - 0.92), material=WOOD_R, bevel=0.008))

    # ---- 挂落：檐枋下的细木条。断面 0.05、间距 0.30——太稀就成了栏杆
    n_slat = 15
    for i in range(n_slat):
        sx = -W / 2 + 0.72 + i * (W - 1.44) / (n_slat - 1)
        parts.append(blib.box(f"gualuo{i}", (0.05, 0.06, 0.24), (sx, COL_Y + 0.06, BASE_H + L1 - 0.42), material=WOOD_R, bevel=0.0))
    parts.append(blib.box("gualuoRail", (W - 1.2, 0.07, 0.06), (0, COL_Y + 0.06, BASE_H + L1 - 0.55), material=WOOD_R, bevel=0.0))

    # ---- 排门板：四扇上着、中间留一处敞开的铺面（暗 + 暖光）
    for px in (-2.92, 1.22):
        parts.append(blib.box(f"doorPanel{px}", (0.74, 0.08, 2.42), (px, FRONT - 0.06, BASE_H + 1.21), material=WOOD, bevel=0.008))
        for b in range(3):
            parts.append(blib.box(f"doorSeam{px}{b}", (0.06, 0.05, 2.34), (px - 0.28 + b * 0.28, FRONT - 0.11, BASE_H + 1.21), material=WOOD_R, bevel=0.0))
    # 敞口内的铺面：深色内腔 + 一层暖光板（灯亮时是一间点着灯的店）
    # 光板要几乎铺满整个后壁：只做一小块时，柜台和货架一挡就全没了，
    # 出图是"一个黑窟窿"
    # 注意 +Y 是"往里"：后壁中心 0.45、前端面 0.36，光板必须放在 0.34（更小的 y 才更靠外），
    # 写成 PORCH-1.0=0.55 就跑到后壁背后去了——一块完全看不见的发光板。
    parts.append(blib.box("shopGlow", (ow - 0.12, 0.04, OPEN_H - 0.42), (ocx, PORCH - 1.21, BASE_H + (OPEN_H - 0.42) / 2 + 0.06), material=PAPER, bevel=0.0))

    # ---- 柜台：铺面前一张长条柜台，台面外挑一点压边
    # 柜台只占敞口的左半：横跨整个敞口时，店里那点暖光与货架会被全部挡住，
    # 出图就是"一道木栏杆"，看不出是铺面
    parts.append(blib.box("counter", (1.92, 0.62, 0.80), (-1.28, FRONT - 0.48, BASE_H + 0.40), material=WOOD, bevel=0.012))
    parts.append(blib.box("counterTop", (2.06, 0.74, 0.09), (-1.28, FRONT - 0.50, BASE_H + 0.84), material=WOOD_R, bevel=0.01))
    # 柜台后的货架（两三道横板，远看是"店里有东西"）
    # 货架只做靠边的一小段，别把光板挡满
    for b in range(2):
        parts.append(blib.box(f"shelf{b}", (1.1, 0.22, 0.05), (OPEN_X0 + 0.62, 0.16, BASE_H + 1.62 + b * 0.34), material=WOOD, bevel=0.0))

    # ---- 廊下吊灯：两盏挂在楼板底面。
    #
    # 檐廊被楼板和腰檐压着，白天也是一团黑——不加灯，铺面在画面上就是一个洞。
    # 吊灯用窗纸同款自发光材质（loader 会把它收进 glowMaterials，夜间跟着时段变亮）。
    for cx in (-2.05, 2.05):
        parts.append(blib.cyl(f"lampCord{cx}", 0.012, 0.34, (cx, -D / 2 + 0.95, BASE_H + L1 - 0.17), seg=5, material=WOOD))
        parts.append(blib.cyl(f"lampCap{cx}", 0.10, 0.05, (cx, -D / 2 + 0.95, BASE_H + L1 - 0.36), seg=8, material=WOOD_R))
        parts.append(blib.cyl(f"lampBody{cx}", 0.15, 0.26, (cx, -D / 2 + 0.95, BASE_H + L1 - 0.52), seg=10, material=PAPER, r2=0.12))

    # ---- 幌子：一根挑杆 + 一块布招，是铺面最省钱的"商业符号"
    px = W / 2 - 0.55
    parts.append(blib.cyl("flagPole", 0.035, 0.9, (px, -D / 2 - 0.28, BASE_H + L1 - 0.62), rot=(math.pi / 2, 0, 0), seg=6, material=WOOD_R))
    parts.append(blib.box("flagCloth", (0.42, 0.05, 0.82), (px, -D / 2 - 0.62, BASE_H + L1 - 1.18), material=WOOD_R, bevel=0.006))
    parts.append(blib.box("flagBar", (0.48, 0.06, 0.05), (px, -D / 2 - 0.62, BASE_H + L1 - 0.74), material=WOOD, bevel=0.0))

    # ---- 二层：木格窗带（三扇）+ 楼板外沿的一道腰檐
    for cx in (-2.05, 0.0, 2.05):
        parts += window(cx, SLAB_TOP + 0.62, ww=1.24, wh=0.86, plane=-D / 2 + 0.02)
    parts.append(blib.box("floorBand", (W + 0.06, 0.14, 0.2), (0, -D / 2 + 0.05, SLAB_TOP + 0.02), material=WOOD, bevel=0.01))
    # 腰檐：压住一层与二层的分界，也是檐廊的雨篷
    parts.append(blib.box("midEave", (W + 0.16, 0.66, 0.11), (0, -D / 2 - 0.26, SLAB_TOP + 0.20), rot=(0.30, 0, 0), material=TILE, bevel=0.012))

    # ---- 台阶：从街面蹬上台基（两级）
    parts.append(blib.box("step0", (W - 1.0, 0.44, 0.18), (0, -D / 2 - 0.22, 0.09), material=STONE, bevel=0.012))
    parts.append(blib.box("step1", (W - 1.6, 0.30, 0.18), (0, -D / 2 - 0.44, 0.27), material=STONE, bevel=0.012))
else:
    # 门在正面（-Y）。门洞做成"木框 + 深色门板"，凹进墙里 0.1
    door_w, door_h = 1.15, 2.15
    dy = -D / 2
    parts.append(blib.box("doorFrame", (door_w + 0.28, 0.16, door_h + 0.16), (0, dy - 0.02, BASE_H + (door_h + 0.16) / 2), material=STONE, bevel=0.015))
    parts.append(blib.box("doorLeaf", (door_w, 0.1, door_h), (0, dy - 0.10, BASE_H + door_h / 2), material=WOOD, bevel=0.01))
    # 门楣上方的小披檐
    parts.append(blib.box("doorCanopy", (door_w + 0.9, 0.62, 0.12), (0, dy - 0.28, BASE_H + door_h + 0.3), rot=(0.28, 0, 0), material=TILE, bevel=0.015))

    parts += window(-W / 2 + 1.35, BASE_H + EAVE * 0.62)
    parts += window(W / 2 - 1.35, BASE_H + EAVE * 0.62)
    # 山墙开一个小窗，避免大面积白墙是"死"的
    parts += window(0, BASE_H + EAVE * 0.78, ww=0.8, wh=0.7)

    # 门前踏跺：三级别太高，石阶是"住人"的证据
    parts.append(blib.box("step", (1.9, 0.34, 0.16), (0, dy - 0.26, 0.08), material=STONE, bevel=0.01))

NAME = "house_" + VARIANT
blib.join(parts, NAME)

bpy.context.view_layer.update()
zmin = min((ob.matrix_world @ v.co).z for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices)
for ob in bpy.data.objects:
    if ob.type == 'MESH':
        ob.location.z -= zmin

blib.setup_check_scene()
blib.render_views(NAME, target=(0, 0, 2.4), radius=14.0 if SHOP else 13.0, height=3.0, ortho_scale=11.0 if SHOP else 10.0)
blib.export(NAME + ".glb")
