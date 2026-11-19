"""城墙：可重复铺设的 8m 标准段。

<p>S1 那版城墙是"每 4m 一个方块 + 顶面一个小方块"，出图是一排阶梯状的灰盒子——
因为盒子上既没有垛口节奏，也没有收分和基座，光一打就是一个平面。</p>

<p>这一版的关键是<b>做成可重复的段</b>而不是"一整道墙"：城墙要顺着地形起伏下降，
只有按段生成、每段单独取地面高度才能在坡地上不出悬空/埋没。段长 8m，运行时每段
沿 Z 缩放一点点把区间精确填满（8% 以内的缩放看不出垛口宽窄变化）。</p>

<p>坐标约定（与 blib 一致）：glTF 导出后 Blender +X → three.js +X，+Y → -Z。
所以段长沿 <b>Y</b> 铺，外墙朝 <b>+X</b>（城外），内墙朝 -X（街道侧）。</p>

<p><b>与 blib 的"底面 z=0"约定的偏离</b>：城墙必须埋进地里，模型 z=0 是<b>标称地面线</b>，
几何允许向下延伸到 z=-BURY。放置时 {@code position.y = heightAt()}，不是"底面贴地"。</p>

运行：blender --background --factory-startup --python wall.py
"""

import os
import sys

import bpy

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import blib  # noqa: E402

blib.reset()

# ---------------------------------------------------------------- 尺度

SEG = 8.0  # 段长（沿 Y）
YH = SEG / 2
H = 4.6  # 露出地面高度（马道面）
BURY = 3.2  # 埋深（坡地上墙根不会露出来）
W0 = 2.75  # 墙底厚（X）
W2 = 2.05  # 墙顶厚（收分到顶部）

# 墙身分三层退台：收分不是一条直斜面，而是"每层微微收一点、层间留一道台"。
# 这道台在光下就是一条水平阴影线，尺度感全靠它——纯斜面在远景里仍然是块板。
L0_TOP, L0_W = 1.55, 2.62
L1_TOP, L1_W = 3.15, 2.34

BATTLE_H = 1.18  # 垛墙高（自马道面算）
BATTLE_T = 0.46  # 垛墙厚
PARAPET_H = 0.74  # 内侧女墙高
PARAPET_T = 0.36

# ---------------------------------------------------------------- 材质

BRICK = blib.mat("wallBrick", blib.srgb(0x6B6659), rough=0.95)
BRICK_UP = blib.mat("wallBrickUp", blib.srgb(0x74705F), rough=0.94)
BASE = blib.mat("wallBase", blib.srgb(0x4C473D), rough=0.96)
DECK = blib.mat("wallDeck", blib.srgb(0x767061), rough=0.9)
CAP = blib.mat("wallCap", blib.srgb(0x35322B), rough=0.8)


def taper(name, w_bot, w_top, y_half, z_bot, z_top, material, bevel=0.02):
    """
    带收分的墙体（底面宽 w_bot、顶面 w_top 的六面体）。

    <p>不能用 box 再逐顶点缩放：那样倒角宽度也会被缩掉，顶边高光变细。这里直接
    按两组宽度建顶点，倒角宽度沿高度一致。</p>
    """
    hb, ht = w_bot / 2, w_top / 2
    verts = [
        (-hb, -y_half, z_bot), (hb, -y_half, z_bot), (ht, -y_half, z_top), (-ht, -y_half, z_top),
        (-hb, y_half, z_bot), (hb, y_half, z_bot), (ht, y_half, z_top), (-ht, y_half, z_top),
    ]
    faces = [
        (0, 1, 2, 3),  # -Y 端面
        (5, 4, 7, 6),  # +Y 端面
        (0, 3, 7, 4),  # -X 内墙面
        (1, 5, 6, 2),  # +X 外墙面
        (4, 5, 1, 0),  # 底面
        (3, 2, 6, 7),  # 顶面
    ]
    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.validate()
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)

    # from_pydata 不保证法线朝外，必须重算；否则整面墙背光时是全黑的
    bpy.ops.object.mode_set(mode='EDIT')
    bpy.ops.mesh.select_all(action='SELECT')
    bpy.ops.mesh.normals_make_consistent(inside=False)
    bpy.ops.object.mode_set(mode='OBJECT')

    return blib._apply(ob, material, bevel, 2)


parts = []

# ---------------------------------------------------------------- 墙身

parts.append(taper("body0", W0, L0_W, YH, -BURY, L0_TOP, BRICK))
parts.append(taper("body1", L0_W, L1_W, YH, L0_TOP, L1_TOP, BRICK))
parts.append(taper("body2", L1_W, W2, YH, L1_TOP, H, BRICK_UP))

# 基座：地面上下各露一点的条石基础。
# 用收分（下宽上窄）而不是等宽盒子——等宽盒子出图是一圈"围裙"，圈在墙上很扎眼；
# 收分之后它是从地面"长"出来的，与墙身的收分同向。
parts.append(taper("plinth", W0 + 0.52, W0 + 0.04, YH, -0.95, 0.42, BASE))

# ---------------------------------------------------------------- 马道

# 马道铺面比墙顶窄，两侧留给垛墙与女墙；浅色，与墙面拉开明度
deck_w = W2 - BATTLE_T - PARAPET_T - 0.06
deck_x = (BATTLE_T - PARAPET_T) / 2 * -1.0  # 让出垛墙（+X）之后铺面中心略偏 -X
parts.append(
    blib.box("deck", (deck_w, SEG, 0.1), (deck_x, 0, H + 0.03), material=DECK, bevel=0.02, bevel_seg=1)
)

# ---------------------------------------------------------------- 外侧垛口

bx = W2 / 2 - BATTLE_T / 2  # 垛墙中心 X
# 下半截连续矮墙：垛口不是"墙上的洞"，而是矮墙上方缺一块——缺口底就是马道
parts.append(
    blib.box("battleLow", (BATTLE_T, SEG, 0.34), (bx, 0, H + 0.17), material=BRICK, bevel=0.018, bevel_seg=1)
)

# 4 个垛 / 4 个口，逐个 1.0m 均分（8m ÷ 8）；口宽 1.0、垛宽 1.0
for i in range(4):
    yc = -YH + 1.0 + i * 2.0
    parts.append(
        blib.box(f"battle{i}", (BATTLE_T, 1.0, BATTLE_H), (bx, yc, H + BATTLE_H / 2), material=BRICK, bevel=0.02)
    )
    # 垛顶压顶石：比垛略宽，一圈出挑。垛口顶没有这道边，垛子就像没戴帽
    parts.append(
        blib.box(f"battleCap{i}", (BATTLE_T + 0.1, 1.1, 0.09), (bx, yc, H + BATTLE_H + 0.045), material=CAP, bevel=0.015, bevel_seg=1)
    )

# ---------------------------------------------------------------- 内侧女墙

px = -W2 / 2 + PARAPET_T / 2
parts.append(
    blib.box("parapet", (PARAPET_T, SEG, PARAPET_H), (px, 0, H + PARAPET_H / 2), material=BRICK, bevel=0.018, bevel_seg=1)
)
parts.append(
    blib.box("parapetCap", (PARAPET_T + 0.1, SEG, 0.08), (px, 0, H + PARAPET_H + 0.04), material=CAP, bevel=0.014, bevel_seg=1)
)

# ---------------------------------------------------------------- 吐水嘴

# 外墙每段一个石制排水槽：从墙身<b>斜向下</b>挑出去，末端要压在墙面里。
# 早先做成一个正的方盒，出图是个悬在墙上的"插座"——正盒子在斜墙上永远接不上。
# 绕 Y 轴斜 20° 之后，槽底顺着墙面向下走，看上去是从墙体里伸出来的。
#
# 起点按 body1 层在此高度的实际半宽反推（墙面是收分的，写死 x 就会浮空）。
SPOUT_Z = H - 1.35
_layer_t = (SPOUT_Z - L0_TOP) / (L1_TOP - L0_TOP)
_face_x = (L0_W + (L1_W - L0_W) * _layer_t) / 2
parts.append(
    blib.box(
        "spout",
        (0.56, 0.4, 0.17),
        (_face_x - 0.06, -YH + 2.9, SPOUT_Z),
        rot=(0, -0.35, 0),
        material=BASE,
        bevel=0.018,
        bevel_seg=1,
    )
)

def box_uv(ob, names, scale=4.0):
    """
    按米重算盒式投影 UV（与 `house.py::box_uv` 同一套做法）。

    <p>城墙原本是纯色件，8m 一段在 40m 外就是一块 8×4.6m 的灰板。加砖纹必须
    先把 UV 落到"米"上：`from_pydata` 建的六面体本来没有 UV 层，blib.box 的
    立方体 UV 又是每面各占 0..1（等于把贴图拉伸到一个 8m 见方的面上用）。</p>

    <p>`scale=4.0` 与 `textures.ts::BRICK_METERS` 严格一致，而且 <b>8 / 4 = 2</b>
    是整数：一个标准段正好铺两次，段与段之间砖缝自然接上。若用 3 或 5，
    每 8m 边界就会露出半块砖的错位。</p>
    """
    me = ob.data
    # join 之后会同时存在两份 UV 层：立方体自带的 `UVMap`，以及给无 UV 的
    # taper 自动补的一份。glTF 导出取的是 active_render 那一层，写错层等于
    # 没写——S11 给白墙贴图时就因为"写了但导出的不是它"白改了一轮。
    # 这里直接清空重建，只留一份，避免任何歧义。
    while me.uv_layers:
        me.uv_layers.remove(me.uv_layers[0])
    uvl = me.uv_layers.new(name="UVMap")
    me.uv_layers.active = uvl
    uvl.active_render = True
    target = {i for i, m in enumerate(me.materials) if m and m.name in names}
    if not target:
        return
    for poly in me.polygons:
        if poly.material_index not in target:
            continue
        n = poly.normal
        an = (abs(n.x), abs(n.y), abs(n.z))
        axis = an.index(max(an))
        for li in poly.loop_indices:
            co = me.vertices[me.loops[li].vertex_index].co
            if axis == 0:
                u, v = co.y, co.z
            elif axis == 1:
                u, v = co.x, co.z
            else:
                u, v = co.x, co.y
            uvl.data[li].uv = (u / scale, v / scale)


ob = blib.join(parts, "wall_seg")
box_uv(ob, ("wallBrick", "wallBrickUp", "wallBase"), scale=4.0)

blib.setup_check_scene()
blib.render_views("wall_seg", target=(0, 0, 2.2), radius=11.0, height=3.0, ortho_scale=9.0)
blib.export("wall_seg.glb")
