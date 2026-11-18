"""whale-showcase 资产管线公共库。

约定（踩过就写进来）：
1. **单位 = 米**，Z 轴朝上，模型底面严格落在 z=0。放置时直接 `position.y = heightAt()`，
   不要靠"原点大概对"——那是模型悬空的头号成因。
2. 所有硬边都过一遍小倒角。直角盒子在 PBR 下高光是一条刀锋，一眼廉价。
3. glTF 导出时 Blender(X,Y,Z) → glTF(X,Z,-Y)：+X 还是 +X，+Z 变 +Y，+Y 变 -Z。
   所以"沿 X 展开"的构件在 three.js 里要绕 Y 轴转 90° 才落在 Z 向的街上。
4. 配置目录靠环境变量重定向到 F 盘（BLENDER_USER_CONFIG / BLENDER_USER_SCRIPTS），
   否则 Blender 会往 %APPDATA% 写几十 MB。

用法：由各单件脚本 `sys.path.append(dirname(__file__))` 后 `import blib`。
"""

import math
import os

import bpy
from mathutils import Euler

ASSET_DIR = r"F:\blue-whale\whale-frontend\platform-apps\whale-showcase\public\assets"
CHECK_DIR = r"F:\blue-whale\tmp\blender-check"


def reset():
    """清空场景。用 use_empty 而不是默认启动场景：默认场景带相机/灯/立方体，会混进 GLB。"""
    bpy.ops.wm.read_factory_settings(use_empty=True)


def srgb(hex_code):
    """
    #RRGGBB → 线性 RGB。

    Blender 的 Base Color 是**线性**值，直接填 0.56 会比设计稿亮一大截；
    而 glTF 导出又会把线性转回 sRGB 写进 baseColorFactor。所以取色必须统一走
    "按 hex（也就是肉眼看到的 sRGB）写，工具负责转换"，否则两边对不上。
    """
    r = ((hex_code >> 16) & 255) / 255.0
    g = ((hex_code >> 8) & 255) / 255.0
    b = (hex_code & 255) / 255.0

    def lin(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    return (lin(r), lin(g), lin(b))


def mat(name, color, rough=0.8, metal=0.0, emit=None):
    m = bpy.data.materials.get(name)
    if m:
        return m
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (color[0], color[1], color[2], 1.0)
    b.inputs["Roughness"].default_value = rough
    b.inputs["Metallic"].default_value = metal
    if emit is not None:
        b.inputs["Emission Color"].default_value = (emit[0], emit[1], emit[2], 1.0)
        b.inputs["Emission Strength"].default_value = 3.0
    return m


def _apply(ob, material, bevel, bevel_seg):
    if material:
        ob.data.materials.append(material)
    if bevel > 0:
        bpy.context.view_layer.objects.active = ob
        mod = ob.modifiers.new("bev", 'BEVEL')
        mod.width = bevel
        mod.segments = bevel_seg
        mod.limit_method = 'ANGLE'
        mod.angle_limit = math.radians(35)
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return ob


def box(name, size, loc, rot=(0, 0, 0), material=None, bevel=0.012, bevel_seg=2):
    """size 是全尺寸 (x, y, z)，loc 是中心点。"""
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc)
    ob = bpy.context.object
    ob.name = name
    ob.scale = size
    ob.rotation_euler = Euler(rot)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    return _apply(ob, material, bevel, bevel_seg)


def cyl(name, r, h, loc, rot=(0, 0, 0), seg=12, material=None, bevel=0.0, r2=None):
    # primitive_cylinder_add 的 r2 参数名是 radius；要锥形用 primitive_cone_add
    if r2 is None or abs(r2 - r) < 1e-6:
        bpy.ops.mesh.primitive_cylinder_add(radius=r, depth=h, vertices=seg, location=loc)
    else:
        bpy.ops.mesh.primitive_cone_add(radius1=r, radius2=r2, depth=h, vertices=seg, location=loc)
    ob = bpy.context.object
    ob.name = name
    ob.rotation_euler = Euler(rot)
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    return _apply(ob, material, bevel, 2)


def join(objs, name):
    """合并成单件，减少 GLB 里的 node 数（three.js 里 node 多会拖 drawcall）。"""
    if not objs:
        return None
    bpy.ops.object.select_all(action='DESELECT')
    for o in objs:
        o.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.object.join()
    ob = bpy.context.object
    ob.name = name
    return ob


# ---------------------------------------------------------------- 屋顶

def roof(
    name,
    width,
    run,
    ridge_z,
    eave_z,
    thickness=0.14,
    tile_pitch=0.30,
    tile_amp=0.028,
    sweep=0.42,
    sag=0.0,
    per_tile=4,
    material=None,
):
    """
    一片瓦屋顶（单坡）。以正脊为原点，向 +Y 方向下坡。

    <p>三个参数决定"像不像中国古建"：</p>
    <ul>
      <li><b>sag/凹曲</b>：剖面 z 用 t^1.45 而不是线性，屋面凹下去（举折）；</li>
      <li><b>tile_pitch × cos</b>：沿宽度方向的余弦波纹就是瓦垄，直接在网格里做，
          比后期贴图省事且不怕 UV 拉伸；</li>
      <li><b>sweep</b>：檐口两端要翘起来（起翘），否则是从中间劈开的西式坡顶。</li>
    </ul>
    """
    # 每片瓦默认 4 个采样点：3 个就够画出正弦起伏，6 个是浪费（瓦垄是整件最费的三角面来源）。
    # 远景构件（民居）用 3，省下的三角面留给镜头前景。
    nx = max(18, int(width / tile_pitch * per_tile))
    ny = 8
    verts = []
    for i in range(nx + 1):
        x = -width / 2 + width * i / nx
        u = abs(x) / (width / 2)
        for j in range(ny + 1):
            t = j / ny
            y = run * t
            # 举折：坡度由陡变缓
            z = ridge_z - (ridge_z - eave_z) * (t ** 1.45)
            # 起翘：越靠檐口、越靠两端，抬得越高
            z += sweep * (u ** 3.2) * (t ** 2.2)
            # 正脊中点微沉，两端略高（生硬的水平脊梁最假）
            z -= sag * (1.0 - u) * 0.0  # 预留给屋面挠度
            # 瓦垄
            z += tile_amp * (0.5 + 0.5 * math.cos(2 * math.pi * x / tile_pitch))
            verts.append((x, y, z))

    faces = []
    for i in range(nx):
        for j in range(ny):
            a = i * (ny + 1) + j
            b = a + 1
            c = a + (ny + 1) + 1
            d = a + (ny + 1)
            faces.append((a, b, c, d))

    me = bpy.data.meshes.new(name)
    me.from_pydata(verts, [], faces)
    me.validate()
    me.update()
    ob = bpy.data.objects.new(name, me)
    bpy.context.collection.objects.link(ob)
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    # 平面网格没有厚度，出图是一张纸；实体化后檐口才有"瓦片层厚"
    solid = ob.modifiers.new("sol", 'SOLIDIFY')
    solid.thickness = thickness
    solid.offset = -1.0
    bpy.ops.object.modifier_apply(modifier=solid.name)
    ob.data.materials.append(material)
    return ob


def ridge(name, width, z, y=0.0, h=0.34, w=0.2, curl=0.16, material=None):
    """正脊 + 两端起翘（鸱吻的简化）。"""
    parts = [box(f"{name}_bar", (width, w, h), (0, y, z), material=material, bevel=0.02)]
    for s in (-1, 1):
        parts.append(
            box(
                f"{name}_curl{s}",
                (0.26, w * 1.1, h * 1.5),
                (s * (width / 2 - 0.04), y, z + curl * 0.6),
                rot=(0, s * -0.5, 0),
                material=material,
                bevel=0.02,
            )
        )
    return join(parts, name)


# ---------------------------------------------------------------- 检查图

def setup_check_scene(bg=0.06):
    """给检查图配一套三点光 + 中性灰背景，避免"看不清是不是黑的"。"""
    scene = bpy.context.scene
    scene.render.resolution_x = 640
    scene.render.resolution_y = 640
    scene.render.film_transparent = False
    world = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs[0].default_value = (bg, bg, bg * 1.1, 1)
    world.node_tree.nodes["Background"].inputs[1].default_value = 1.0

    for k, (rot, energy) in enumerate(
        [((0.9, 0.1, 0.7), 5.0), ((1.1, -0.2, -2.2), 2.0), ((0.2, 0.0, 1.6), 1.2)]
    ):
        ld = bpy.data.lights.new(f"L{k}", type='SUN')
        lo = bpy.data.objects.new(f"L{k}", ld)
        bpy.context.collection.objects.link(lo)
        lo.rotation_euler = Euler(rot)
        ld.energy = energy


def render_views(tag, target=(0, 0, 0), radius=9.0, height=3.6, ortho_scale=10.0):
    """三视图检查：斜 3/4、正视、俯视。落 F:\\blue-whale\\tmp\\blender-check。"""
    os.makedirs(CHECK_DIR, exist_ok=True)
    scene = bpy.context.scene
    for eng in ('BLENDER_EEVEE_NEXT', 'BLENDER_EEVEE', 'BLENDER_WORKBENCH'):
        try:
            scene.render.engine = eng
            break
        except Exception:
            continue
    scene.render.image_settings.file_format = 'PNG'

    cam_data = bpy.data.cameras.new("chk")
    cam = bpy.data.objects.new("chk", cam_data)
    bpy.context.collection.objects.link(cam)
    scene.camera = cam

    views = {
        "iso": ((radius * 0.62, -radius * 0.72, height), True),
        "front": ((0.0, -radius, height * 0.55), True),
        "top": ((0.001, -0.01, radius * 0.9), True),
    }
    from mathutils import Vector
    for name, (pos, ortho) in views.items():
        cam.location = pos
        d = Vector(target) - Vector(pos)
        cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
        cam_data.type = 'ORTHO' if ortho else 'PERSP'
        cam_data.ortho_scale = ortho_scale
        scene.render.filepath = os.path.join(CHECK_DIR, f"{tag}_{name}.png")
        bpy.ops.render.render(write_still=True)


def export(name):
    os.makedirs(ASSET_DIR, exist_ok=True)
    path = os.path.join(ASSET_DIR, name)
    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format='GLB',
        use_selection=False,
        export_apply=True,
        export_yup=True,
    )
    tris = 0
    for ob in bpy.data.objects:
        if ob.type == 'MESH':
            ob.data.calc_loop_triangles()
            tris += len(ob.data.loop_triangles)
    size = os.path.getsize(path)
    print(f"ASSET {name} tris={tris} size={size / 1024:.1f}KB")
    return path
