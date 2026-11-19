"""树（三种变体）：圆冠阔叶、高瘦阔叶、垂柳。

<p>三版迭代出来的结论，按踩坑顺序：</p>
<ol>
  <li>「多团二十面体球拼树冠」出图是一簇明显的球——球与球之间硬相交，轮廓上能数出几个圆。
      改为 <b>metaball 融合</b>：元素之间自动长出过渡曲面，转成网格后是一整块有机形状。</li>
  <li>但 22 个大球融合出来仍然是一颗光滑的蛋/花椰菜。宋画的树冠之所以"有树气"，
      是因为<b>叶团尺度</b>：真实阔叶树的叶团直径 0.4~0.8m，一颗 5m 冠就是几十个小团。
      大球 = 低模感。现在用 100+ 个小元素铺成壳层，内部留空。</li>
  <li>树干直接插进冠心、没有分枝，是"棒棒糖"的第二个成因。加了 5 根锥形分枝，
      让树冠有"被撑起来"的结构；分枝末端还各挂两团叶，冠缘因此不规整。</li>
  <li>树叶不能只有一个绿。没有贴图的情况下用 <b>顶点色</b>：按两级噪声在两个绿之间
      混合，glTF 导出为 COLOR_0，three.js 的 GLTFLoader 会自动开 vertexColors。</li>
</ol>

<p>运行前必须 `reset()`：`--factory-startup` 给的是<b>默认场景</b>（带立方体、相机、
灯），不清理的话那个立方体会被一起导出（第一版真踩了，检查图正中间一个白方块）。</p>

运行：blender --background --factory-startup --python tree.py [-- round tall willow]
"""

import math
import os
import random
import sys

import bpy
from mathutils import Vector
from mathutils import noise as bnoise

sys.path.append(os.path.dirname(os.path.abspath(__file__)))
import blib  # noqa: E402


def clamp01(x):
    return max(0.0, min(1.0, x))


def leaf_material():
    """带顶点色的叶材质。Color Attribute → Base Color 是 glTF 导出 COLOR_0 的前提。"""
    m = bpy.data.materials.get("leafVCol")
    if m:
        return m
    m = bpy.data.materials.new("leafVCol")
    m.use_nodes = True
    nt = m.node_tree
    bsdf = nt.nodes["Principled BSDF"]
    attr = nt.nodes.new("ShaderNodeVertexColor")
    attr.layer_name = "Col"
    nt.links.new(attr.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.88
    return m


def paint_foliage(ob, dark, light, seed=0.0):
    """
    按两级噪声在两个绿之间混色。

    <p>一级（2.6/m）画"这半边冠背光"的整块明暗，二级（7.8/m）画<b>团与团之间</b>的
    明暗差——二级才是"是一堆叶团"而不是"一个绿球"的关键。最后叠一条自下而上的
    受光梯度：树冠顶部接天光、底部被自己遮住，这条梯度比噪声更决定体积感。</p>
    """
    me = ob.data
    attr = me.color_attributes.get("Col")
    if attr is None:
        attr = me.color_attributes.new(name="Col", type='FLOAT_COLOR', domain='POINT')

    zs = [v.co.z for v in me.vertices] or [0.0]
    z0, z1 = min(zs), max(zs)
    dz = max(z1 - z0, 0.01)

    o1 = Vector((seed * 1.7, seed * 0.9, seed * 2.3))
    o2 = Vector((seed * 3.1, seed * 1.3, seed * 0.6))
    for i, v in enumerate(me.vertices):
        n1 = bnoise.noise(v.co * 2.6 + o1)
        n2 = bnoise.noise(v.co * 7.8 + o2)
        # 偏暗：整棵树的平均反照率要压住，否则正午直射下糊成一片亮绿
        t = 0.30 + n1 * 0.46 + n2 * 0.42
        t = clamp01(t + ((v.co.z - z0) / dz - 0.45) * 0.42)
        c = tuple(dark[k] + (light[k] - dark[k]) * t for k in range(3))
        attr.data[i].color = (c[0], c[1], c[2], 1.0)


def canopy(
    name,
    elements,
    material,
    dark,
    light,
    seed=0.0,
    target_tris=2800,
    res=0.085,
    flat=True,
):
    """
    metaball 融成一整块 → 转网格 → 噪声推顶点 → 抽面 → 顶点色。

    <p><b>flat=True</b> 是这一版的第二个关键决定。3000 面铺在约 50m² 的冠面上，
    每个三角面约 12cm——正好是"一簇叶子"的尺度。平面着色让每簇自己朝一个方向，
    冠面因此在光照下碎成几十个明暗不同的小块；平滑着色则是把 3000 个面糊成一个
    连续曲面，细节全被法线插值抹掉（前三版就是这个病）。</p>
    """
    mb = bpy.data.metaballs.new(name)
    mb.resolution = res
    mb.render_resolution = res
    ob = bpy.data.objects.new(name, mb)
    bpy.context.collection.objects.link(ob)
    for c, r in elements:
        e = mb.elements.new()
        e.co = Vector(c)
        e.radius = r
        e.stiffness = 2.0
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.convert(target='MESH')
    ob = bpy.context.object
    ob.name = name

    # 位移必须在抽面**之前**做：先抽面再推顶点，大三角形会被推成尖锐的折角，
    # 看着像破布；先推后抽，抽面只减少细节密度，轮廓仍然是圆润的叶团
    me = ob.data
    for v in me.vertices:
        d = bnoise.noise(v.co * 3.1 + Vector((seed, seed * 0.7, seed * 1.3))) * 0.13
        d += bnoise.noise(v.co * 8.4 + Vector((seed * 2.1, 0, seed))) * 0.10
        v.co += v.normal * d

    tris = sum(len(p.vertices) - 2 for p in ob.data.polygons)
    if tris > target_tris:
        mod = ob.modifiers.new("dec", 'DECIMATE')
        mod.ratio = target_tris / tris
        bpy.ops.object.modifier_apply(modifier=mod.name)

    for p in me.polygons:
        p.use_smooth = not flat
    me.materials.append(material)
    paint_foliage(ob, dark, light, seed=seed)
    return ob


DARK = blib.srgb(0x354C22)
LIGHT = blib.srgb(0x7C9C44)
WILLOW_DARK = blib.srgb(0x415A26)
WILLOW_LIGHT = blib.srgb(0x8DA349)
BARK_C = blib.srgb(0x574736)


def fib_dirs(n):
    """
    斐波那契球面分布。

    <p>随机撒点在球面上必然结块（有几处挤在一起、有几处空着），出图是"一串疙瘩"。
    这个分布是确定性的均匀覆盖，再加位置哈希做径向抖动，就既均匀又不规则。</p>
    """
    out = []
    for i in range(n):
        y = 1.0 - 2.0 * (i + 0.5) / n
        rr = math.sqrt(max(0.0, 1.0 - y * y))
        phi = i * 2.399963229728653
        out.append((math.cos(phi) * rr, y, math.sin(phi) * rr))
    return out


def hash01(*k):
    """确定性抖动。用这个而不是 random：同一颗树重建两次必须一模一样。"""
    s = 0.0
    for i, val in enumerate(k):
        s += math.sin(val * (12.9898 + i * 4.7) + i * 3.1) * 43758.5453
    return s - math.floor(s)


def crown_shell(n, ax, ay, az, center, r0, r1, seed=0.0, inner=0.62):
    """沿一个椭球壳撒 n 个小叶团：半径方向 0.62~1.0 之间，叶团半径 r0~r1。"""
    els = []
    for i, (dx, dy, dz) in enumerate(fib_dirs(n)):
        k = inner + (1.0 - inner) * hash01(i * 1.7, seed)
        jr = hash01(i * 0.9, seed + 5.5)
        c = (
            center[0] + dx * ax * k,
            center[1] + dy * ay * k,
            center[2] + dz * az * k,
        )
        els.append((c, r0 + (r1 - r0) * jr))
    return els


def trunk(name, height, r_bottom, r_top, bend=0.0):
    ob = blib.cyl(name, r_bottom, height, (0, 0, height / 2), seg=10, material=blib.mat("bark", BARK_C, rough=0.95), r2=r_top)
    # 注意：这里拿到的是**局部坐标**，圆柱的局部 z 是 -h/2..+h/2。
    # 直接拿 v.co.z 当"离地高度"用，底面会得到 exp(+3.8)≈45 的放大系数，
    # 树干瞬间变成一根撑满画面的圆锥（柳树第一版就是这样，检查图整个糊住）。
    hz = height / 2
    for v in ob.data.vertices:
        zrel = max(v.co.z + hz, 0.0)  # 0 = 根部
        t = zrel / height
        if bend:
            v.co.x += bend * (t ** 2) * height
        # 根部张开：等截面圆柱插进地里像根电线杆，指数衰减的外扩只影响底部一段
        flare = 1.0 + 0.26 * math.exp(-zrel / max(height * 0.10, 0.05))
        v.co.x *= flare
        v.co.y *= flare
    return ob


def limb(name, p0, p1, r0, r1, seg=7):
    """
    一根锥形枝：从 p0 长到 p1。

    <p>primitive_cone 的轴是 +Z，所以先把 +Z 旋到目标方向，再放到两点的中点。
    `rotation_difference` 在方向与 -Z 完全相反时会退化成 180° 任意轴，这里的分枝
    都是向上的，不会踩到。</p>
    """
    a, b = Vector(p0), Vector(p1)
    d = b - a
    rot = Vector((0.0, 0.0, 1.0)).rotation_difference(d.normalized()).to_euler()
    return blib.cyl(
        name,
        r0,
        d.length,
        tuple((a + b) / 2),
        rot=(rot.x, rot.y, rot.z),
        seg=seg,
        material=blib.mat("bark", BARK_C, rough=0.95),
        r2=r1,
    )


def build_round(seed=11):
    random.seed(seed)
    height, R = 6.8, 2.40
    cy = height * 0.70
    parts = [trunk("trunk", height * 0.62, 0.26, 0.13, bend=0.05)]

    # 分枝：从树干 45%~64% 高度斜向外上，末端指向冠的下半部。
    # 没有这几根，冠就是"浮在棍子上的球"。
    tips = []
    for i in range(5):
        a = i * math.tau / 5 + 0.5
        z0 = height * (0.45 + 0.04 * i)
        rr = R * random.uniform(0.42, 0.58)
        p0 = (0.02 * math.cos(a), 0.02 * math.sin(a), z0)
        p1 = (math.cos(a) * rr, math.sin(a) * rr, z0 + height * random.uniform(0.16, 0.22))
        parts.append(limb(f"limb{i}", p0, p1, 0.105, 0.045))
        tips.append(p1)

    # 冠形：横向 1.0R、纵向只有 0.60R —— 真实的行道树是"伞"不是"球"。
    # 再按水平半径把外圈压低一点：正球形的冠在剪影上就是一颗圆，一眼假。
    els = crown_shell(104, R * 1.0, R * 0.94, R * 0.60, (0, 0, cy), 0.34, 0.60, seed, inner=0.55)
    # 外圈压低成"伞"，并在约四成的方位上再多垂半米：冠底不能是一条水平线，
    # 真实阔叶树的下缘永远是几个垂下来的舌——那才是"树"而不是"蘑菇"。
    for idx, (c, r) in enumerate(els):
        hr = math.hypot(c[0] / max(R * 1.0, 1e-6), c[1] / max(R * 0.94, 1e-6))
        tongue = 0.58 if hash01(idx * 2.7, seed + 9.0) > 0.60 else 0.0
        els[idx] = ((c[0], c[1], c[2] - 0.42 * hr * hr - tongue * hr), r)
    # 冠心补几团：壳层是空的，从下往上看会透视到天空
    for i in range(7):
        k = (i + 0.5) / 7
        els.append(((0.0, 0.0, cy - R * 0.24 + k * R * 0.46), R * 0.58))
    # 枝端各挂两团：让冠缘有几处"被枝撑出来的尖"，轮廓才不规整
    for i, p in enumerate(tips):
        for j in range(2):
            t = 0.55 + j * 0.35
            els.append(((p[0] * t, p[1] * t, p[2] + 0.30 * j + 0.2), R * (0.34 - j * 0.06)))

    parts.append(canopy("canopy", els, leaf_material(), DARK, LIGHT, seed, target_tris=4200))
    return parts


def build_tall(seed=23):
    """
    高瘦阔叶（杨/楠一类）：冠是竖长的纺锤。

    <p>第一版按 5 层"轮生枝"撒团，层距 0.86m、团半径只有 0.5m——相邻层不接触，
    出图是一根串了五串糖葫芦的竿子。<b>树的冠必须是一个连续体</b>：层与层之间
    要么重叠，要么干脆不要分层。现在按螺旋线一路撒上去，半径按纺锤剖面（下 0.35、
    中 1.0、上 0.25）收放，团半径 0.45~0.60，必然连成一片。</p>
    """
    random.seed(seed)
    height, R = 8.4, 1.55
    parts = [trunk("trunk", height * 0.62, 0.22, 0.10, bend=0.025)]

    # 中央领导枝：一路捅到冠顶，两侧 4 根短枝。纺锤形的冠是靠这根主轴撑起来的
    parts.append(limb("lead", (0, 0, height * 0.40), (0.06, -0.04, height * 0.94), 0.10, 0.02))
    for i in range(4):
        a = i * math.tau / 4 + 1.1
        z0 = height * (0.46 + 0.05 * i)
        p0 = (0.02 * math.cos(a), 0.02 * math.sin(a), z0)
        p1 = (math.cos(a) * R * 0.62, math.sin(a) * R * 0.62, z0 + height * 0.14)
        parts.append(limb(f"limb{i}", p0, p1, 0.07, 0.028))

    n = 64
    els = []
    for i in range(n):
        t = (i + 0.5) / n  # 0 = 冠底，1 = 冠顶
        z = height * (0.44 + 0.50 * t)
        # 纺锤剖面：0.35 → 1.0 → 0.22
        prof = math.sin(math.pi * (0.18 + 0.82 * t)) * (1.0 - 0.55 * t) + 0.18
        a = i * 2.399963229728653
        rad = R * prof * (0.55 + 0.45 * hash01(i * 1.9, seed))
        els.append(
            (
                (math.cos(a) * rad, math.sin(a) * rad, z),
                R * (0.34 + 0.20 * hash01(i * 3.3, seed + 2.0)),
            )
        )

    parts.append(canopy("canopy", els, leaf_material(), DARK, LIGHT, seed, target_tris=2400, res=0.075))
    return parts


def build_willow(seed=37):
    """
    垂柳：冠是"馒头"，四面挂下垂帘。

    <p>两版迭代：</p>
    <ol>
      <li>18 根贝塞尔曲线 + bevel 做垂枝，出图是 <b>18 根铁丝</b>：太粗（3.2cm）、
          太稀、从冠底一圈等距垂下，像水母。</li>
      <li>改成"每条 3 团"去堆叶幕，团距 0.55m 而团半径只有 0.5m——相邻团不接触，
          出图是一串<b>悬空的珠子</b>，比铁丝更糟。</li>
    </ol>
    <p>结论：柳条是<b>连续</b>的。每条帘用 5 团、团距 0.28m、半径 0.60~0.50，
    重叠够了才会融成一条垂舌；28 条围一圈，垂舌之间的缝就是柳树的竖向纹理。</p>
    """
    random.seed(seed)
    height, R = 6.2, 2.35
    cy = height * 0.64
    parts = [trunk("trunk", height * 0.60, 0.30, 0.14, bend=0.10)]

    # 冠本体：扁馒头（横向 1.0R、纵向 0.58R）
    els = crown_shell(64, R * 0.92, R * 0.92, R * 0.58, (0, 0, cy), 0.38, 0.56, seed, inner=0.50)
    for i in range(6):
        k = (i + 0.5) / 6
        els.append(((0.0, 0.0, cy - R * 0.20 + k * R * 0.40), R * 0.58))

    # 垂帘：28 条 × 5 团，紧挨着往下挂
    for i in range(28):
        a = i * math.tau / 28 + 0.2
        rr = R * (0.84 + 0.16 * hash01(i * 2.3, seed))
        drop = height * (0.26 + 0.24 * hash01(i * 5.1, seed + 3.0))
        for j in range(5):
            t = j / 4.0
            rad = rr * (1.0 - 0.12 * t)
            z = cy - 0.10 - drop * t
            els.append(
                (
                    (math.cos(a) * rad, math.sin(a) * rad, z),
                    R * (0.26 - 0.075 * t) * (1.0 + 0.10 * hash01(i, j, seed)),
                )
            )

    parts.append(
        canopy(
            "canopy",
            els,
            leaf_material(),
            WILLOW_DARK,
            WILLOW_LIGHT,
            seed,
            target_tris=3400,
            res=0.07,
        )
    )
    return parts


def finish(parts, name, radius, height, ortho):
    blib.join(parts, name)
    bpy.context.view_layer.update()
    zmin = min((ob.matrix_world @ v.co).z for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices)
    for ob in bpy.data.objects:
        if ob.type == 'MESH':
            ob.location.z -= zmin
    blib.setup_check_scene()
    blib.render_views(name, target=(0, 0, height * 0.5), radius=radius, height=height * 0.55, ortho_scale=ortho)
    blib.export(f"{name}.glb")


ONLY = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []

for fn, nm, rad, hh, osc in (
    (build_round, "tree_round", 15.0, 7.2, 9.5),
    (build_tall, "tree_tall", 16.0, 8.8, 11.0),
    (build_willow, "tree_willow", 13.0, 6.2, 8.5),
):
    if ONLY and not any(k in nm for k in ONLY):
        continue
    blib.reset()  # 每个资产都要从空场景开始，否则上一个的残留会一起导出
    finish(fn(), nm, rad, hh, osc)
