"""树（三种变体）：圆形阔叶、高瘦阔叶、垂柳。

<p>两个版本迭代出来的结论：</p>
<ol>
  <li>第一版用"多团二十面体球拼树冠"，出图是一簇明显的球——因为球与球之间是
     硬相交，轮廓上能数出几个圆。改为 <b>metaball 融合</b>：元素之间自动长出
      过渡曲面，转成网格后是一整块有机形状，再按噪声推顶点破面。</li>
  <li>树叶不能只有一个绿。没有贴图的情况下用 <b>顶点色</b>：按噪声在两个绿之间
      混合，glTF 导出为 COLOR_0，three.js 的 GLTFLoader 会自动开 vertexColors。</li>
</ol>

<p>运行前必须 `reset()`：`--factory-startup` 给的是<b>默认场景</b>（带立方体、相机、
灯），不清理的话那个立方体会被一起导出（第一版真踩了，检查图正中间一个白方块）。</p>

运行：blender --background --factory-startup --python tree.py
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
    bsdf.inputs["Roughness"].default_value = 0.87
    return m


def paint_foliage(ob, dark, light, freq=1.6, seed=0.0):
    """按噪声在两个绿之间混色，模拟受光/背光的叶团。"""
    me = ob.data
    attr = me.color_attributes.get("Col")
    if attr is None:
        attr = me.color_attributes.new(name="Col", type='FLOAT_COLOR', domain='POINT')
    for i, v in enumerate(me.vertices):
        n = bnoise.noise(v.co * freq + Vector((seed, seed * 1.3, seed * 0.7)))
        # 偏暗：整棵树的平均反照率要压住，否则正午直射下糊成一片亮绿
        t = max(0.0, min(1.0, (0.38 + n * 0.85) * 0.8))
        # 顶部偏亮：树冠受光主要在阳面，纯随机噪声会显得脏
        t = max(0.0, min(1.0, t + (v.co.z / max(ob.dimensions.z, 0.01)) * 0.35))
        c = tuple(dark[k] + (light[k] - dark[k]) * t for k in range(3))
        attr.data[i].color = (c[0], c[1], c[2], 1.0)


def canopy(name, centers, radii, material, dark, light, seed=0.0, target_tris=1400, res=0.16):
    """metaball 融成一整块 → 转网格 → 抽面 → 噪声推顶点 → 顶点色。"""
    mb = bpy.data.metaballs.new(name)
    mb.resolution = res
    mb.render_resolution = res
    ob = bpy.data.objects.new(name, mb)
    bpy.context.collection.objects.link(ob)
    for c, r in zip(centers, radii):
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
        d = bnoise.noise(v.co * 4.2 + Vector((seed, seed * 0.7, seed * 1.3))) * 0.20
        d += bnoise.noise(v.co * 9.3 + Vector((seed * 2.1, 0, seed))) * 0.075
        v.co += v.normal * d

    tris = sum(len(p.vertices) - 2 for p in ob.data.polygons)
    if tris > target_tris:
        mod = ob.modifiers.new("dec", 'DECIMATE')
        mod.ratio = target_tris / tris
        bpy.ops.object.modifier_apply(modifier=mod.name)

    for p in me.polygons:
        p.use_smooth = True
    me.materials.append(material)
    paint_foliage(ob, dark, light, seed=seed)
    return ob


DARK = blib.srgb(0x2F451F)
LIGHT = blib.srgb(0x6B883A)
BARK_C = blib.srgb(0x4A3B2E)


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
        flare = 1.0 + 0.5 * math.exp(-zrel / max(height * 0.13, 0.05))
        v.co.x *= flare
        v.co.y *= flare
    return ob


def build_round(seed=11):
    random.seed(seed)
    height, R = 6.6, 2.5
    parts = [trunk("trunk", height * 0.62, R * 0.14, R * 0.085, bend=0.05)]
    cy = height * 0.74
    # 元素多而小：融合后表面会自然出现一串鼓包（花椰菜），
    # 少数几个大球融合只会得到一颗光滑的蛋
    centers = [(0, 0, cy)]
    radii = [R * 0.62]
    n = 22
    for i in range(n):
        a = random.uniform(0, math.tau)
        el = random.uniform(-0.55, 1.0)
        rr = R * random.uniform(0.55, 0.95) * math.cos(el * 1.1)
        centers.append((math.cos(a) * rr, math.sin(a) * rr * 0.95, cy + el * R * 0.72))
        radii.append(R * random.uniform(0.34, 0.52))
    parts.append(canopy("canopy", centers, radii, leaf_material(), DARK, LIGHT, seed, target_tris=1900))
    return parts


def build_tall(seed=23):
    random.seed(seed)
    height, R = 8.4, 1.75
    parts = [trunk("trunk", height * 0.5, R * 0.17, R * 0.1, bend=0.03)]
    centers, radii = [], []
    for i in range(5):
        z = height * (0.46 + i * 0.088)
        r = R * (1.0 - i * 0.1)
        off = (random.uniform(-0.2, 0.2) * R, random.uniform(-0.2, 0.2) * R, 0)
        centers.append((off[0], off[1], z))
        radii.append(r)
    parts.append(canopy("canopy", centers, radii, leaf_material(), DARK, LIGHT, seed, target_tris=1200))
    return parts


def strand_material():
    """柳条单独一支材质：曲线转网格之后没有 "Col" 属性，套顶点色材质会渲染成纯黑。"""
    m = bpy.data.materials.get("strand")
    if m:
        return m
    m = blib.mat("strand", blib.srgb(0x5C7A36), rough=0.9)
    return m


def strand(i, az, top_r, z0, drop):
    a = math.radians(az)
    cu = bpy.data.curves.new(f"st{i}", 'CURVE')
    cu.dimensions = '3D'
    cu.bevel_depth = 0.032
    cu.bevel_resolution = 1
    cu.resolution_u = 3
    sp = cu.splines.new('BEZIER')
    sp.bezier_points.add(2)
    x0, y0 = math.cos(a) * top_r, math.sin(a) * top_r
    # z0 是树冠底的**世界**高度：柳条从冠里垂出来，不是从地面长出来
    pts = [(x0, y0, z0), (x0 * 1.24, y0 * 1.24, z0 - drop * 0.42), (x0 * 1.05, y0 * 1.05, z0 - drop)]
    for bp, co in zip(sp.bezier_points, pts):
        bp.co = Vector(co)
        bp.handle_left_type = bp.handle_right_type = 'AUTO'
    ob = bpy.data.objects.new(f"st{i}", cu)
    bpy.context.collection.objects.link(ob)
    ob.data.materials.append(strand_material())
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.convert(target='MESH')
    return bpy.context.object


def build_willow(seed=37):
    random.seed(seed)
    height, R = 5.6, 2.35
    parts = [trunk("trunk", height * 0.52, R * 0.15, R * 0.09, bend=0.12)]
    # 冠要是个"馒头"不是一个"盘子"：分三层收窄上升，单层平铺融合出来是扁蘑菇
    centers, radii = [], []
    rings = ((6, 0.46, 0.54, 0.58), (4, 0.24, 0.70, 0.44), (1, 0.0, 0.80, 0.38))
    for cnt, rr, zz, rad in rings:
        for i in range(cnt):
            a = (i / cnt) * math.tau + rr * 3.0
            centers.append((math.cos(a) * R * rr, math.sin(a) * R * rr, height * zz))
            radii.append(R * rad)
    parts.append(canopy("canopy", centers, radii, leaf_material(), DARK, LIGHT, seed, target_tris=1300))
    for i in range(18):
        az = i * 20 + random.uniform(-7, 7)
        parts.append(strand(i, az, R * random.uniform(0.42, 0.78), height * 0.6, height * random.uniform(0.34, 0.52)))
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
