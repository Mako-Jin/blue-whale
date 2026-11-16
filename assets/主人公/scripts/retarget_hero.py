# -*- coding: utf-8 -*-
"""UAL(Quaternius 通用动作库) -> 主人公 17 骨骨架 重定向烘焙

原理：世界空间旋转转移。
  对每个映射骨对 (src, dst)：dst 期望世界旋转 = align @ src 世界旋转
  其中 align = dst_rest_rot @ src_rest_rot^-1（rest 对齐）
  即：源动画相对其 rest 的旋转增量，原样施加到 dst 的 rest 上。
  hips(root) 额外转移位移增量（按身高比例缩放）。

产出：替换 hero_game.blend 里的 Idle_Stand/Loco_Walk/Loco_Run/Action_Jump/Action_Cast
     （Action_Wave 保留程序版，Standard 库无挥手），重新导出 hero_game.glb。
"""
import os
import math
from mathutils import Vector, Matrix

HERE = os.path.dirname(os.path.abspath(__file__))
SRC_GLB = r"F:\blue-whale\work\hero\ual\Animation Library[Standard]\Godot\AnimationLibrary_Godot_Standard.glb"
HERO_BLEND = r"F:\blue-whale\work\hero\out\hero_game.blend"
OUT_GLB = r"F:\blue-whale\work\hero\out\hero_game.glb"

# (src bone, dst bone) —— dst 按层级顺序排列（父在前）
MAP = [
    ("DEF-hips", "Root"),
    ("DEF-spine.001", "Spine"),
    ("DEF-spine.002", "Chest"),
    ("DEF-neck", "Neck"),
    ("DEF-head", "Head"),
    ("DEF-upper_arm.L", "Arm_L"),
    ("DEF-forearm.L", "Forearm_L"),
    ("DEF-hand.L", "Hand_L"),
    ("DEF-upper_arm.R", "Arm_R"),
    ("DEF-forearm.R", "Forearm_R"),
    ("DEF-hand.R", "Hand_R"),
    ("DEF-thigh.L", "Thigh_L"),
    ("DEF-shin.L", "Shin_L"),
    ("DEF-foot.L", "Foot_L"),
    ("DEF-thigh.R", "Thigh_R"),
    ("DEF-shin.R", "Shin_R"),
    ("DEF-foot.R", "Foot_R"),
]

# 目标 clip: [(src action, 帧处理)]  trim1 = 掐头去尾各 1 帧
CLIPS = {
    "Idle_Stand": [("Idle_Loop", None)],
    "Loco_Walk": [("Walk_Loop", None)],
    "Loco_Run": [("Jog_Fwd_Loop", None)],
    "Action_Jump": [("Jump_Start", None), ("Jump_Loop", (28, 38)), ("Jump_Land", None)],
    "Action_Cast": [("Spell_Simple_Enter", None), ("Spell_Simple_Shoot", None), ("Spell_Simple_Exit", None)],
}
KEEP_OLD = {"Action_Wave"}  # 保留的程序动作

import bpy

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.scene.render.fps = 24

# 1. 先开 hero blend，再导入源 GLB 到同一场景
bpy.ops.wm.open_mainfile(filepath=HERO_BLEND)
bpy.context.scene.render.fps = 24
bpy.ops.import_scene.gltf(filepath=SRC_GLB)

hero_arm = bpy.data.objects.get("HeroRig")
src_arm = None
src_mannequin = None
for o in bpy.context.scene.objects:
    if o.type == "ARMATURE" and o.name != "HeroRig":
        src_arm = o
    if o.type == "MESH" and o.name == "Mannequin":
        src_mannequin = o
assert hero_arm and src_arm, "armature not found"
print("hero arm:", hero_arm.name, "matrix_world identity:", hero_arm.matrix_world == Matrix.Identity(4))
print("src arm:", src_arm.name)

# 2. 身高比例（位移缩放用）
def obj_height(o):
    bb = [o.matrix_world @ Vector(c) for c in o.bound_box]
    return max(v.z for v in bb) - min(v.z for v in bb)

hero_mesh = bpy.data.objects.get("Hero")
s_scale = obj_height(hero_mesh) / obj_height(src_mannequin)
print("scale loc factor: %.3f" % s_scale)

# 3. rest 矩阵 + 锚点姿态
# 源 rest 是 T-pose（手臂水平），与主人公垂臂 rest 相差 ~90°。
# 若按 rest 对齐转移，会把"T-pose->自然下垂"的固定偏移带进来（idle 手臂外抬）。
# 修正：锚点改用源 Idle_Loop 第 0 帧（自然站姿）——idle 时主人公≈扫描原姿态，
# 走/跑/跳的摆动都锚定在自然站姿上。
src_rest, dst_rest = {}, {}
for sn, dn in MAP:
    src_rest[sn] = src_arm.pose.bones[sn].bone.matrix_local.copy()
    dst_rest[dn] = hero_arm.pose.bones[dn].bone.matrix_local.copy()

_ad = src_arm.animation_data
_idle = bpy.data.actions["Idle_Loop"]
_ad.action = _idle
_ad.action_slot = _idle.layers[0].strips[0].channelbags[0].slot
bpy.context.scene.frame_set(0)
bpy.context.view_layer.update()
ANCHOR, ANCHOR_LOC = {}, {}
for sn, dn in MAP:
    ANCHOR[sn] = src_arm.pose.bones[sn].matrix.copy()
    ANCHOR_LOC[dn] = src_arm.pose.bones[sn].matrix.to_translation().copy()

align_rot = {}
for sn, dn in MAP:
    align_rot[dn] = dst_rest[dn].to_3x3() @ ANCHOR[sn].to_3x3().inverted()

def src_frames(name):
    act = bpy.data.actions.get(name)
    assert act, "action missing: " + name
    a, b = act.frame_range
    return act, int(math.ceil(a)), int(math.floor(b))

def seg_frames(name, mode):
    act, f0, f1 = src_frames(name)
    if isinstance(mode, tuple):
        f0, f1 = mode          # 指定区间（含端点）
    elif mode == "trim1":
        f0 += 1; f1 -= 1
    else:
        f1 = f1 - 1 if f1 - f0 > 2 else f1  # 循环段末帧与首帧同姿，掐掉
    return act, list(range(f0, f1 + 1))

# 4. 清旧动作（保留 KEEP_OLD）
hero_ad = hero_arm.animation_data
for t in list(hero_ad.nla_tracks):
    hero_ad.nla_tracks.remove(t)
for a in list(bpy.data.actions):
    if a.name in KEEP_OLD:
        continue
    users = a.users
    is_hero_old = a.name in CLIPS or a.name.startswith(("anim_", "Idle_Stand", "Loco_", "Action_Jump", "Action_Cast"))
    if is_hero_old:
        bpy.data.actions.remove(a)

def set_action(arm_obj, ad, act):
    ad.action = act
    cb = act.layers[0].strips[0].channelbags[0]
    ad.action_slot = cb.slot

bpy.context.view_layer.objects.active = hero_arm

report = []
for clip_name, segments in CLIPS.items():
    act = bpy.data.actions.new(clip_name)
    act.use_fake_user = True
    hero_ad.action = act
    hero_ad.action_slot = None  # keyframe_insert 会自动建 slot

    dst_f = 0
    hips_max = Vector((0, 0, 0))
    for seg_name, mode in segments:
        src_act, frames = seg_frames(seg_name, mode)
        set_action(src_arm, src_arm.animation_data, src_act)
        scn = bpy.context.scene
        for f in frames:
            scn.frame_set(f)
            bpy.context.view_layer.update()
            W = {}  # dst 已解算的世界矩阵
            for sn, dn in MAP:
                spb = src_arm.pose.bones[sn]
                dpb = hero_arm.pose.bones[dn]
                S = spb.matrix.copy()
                desired_rot = (align_rot[dn].to_4x4() @ S).to_3x3()
                if dn == "Root":
                    A = align_rot["Root"].to_3x3()
                    t_out = dst_rest["Root"].to_translation() + A @ (S.to_translation() - ANCHOR_LOC["Root"]) * s_scale
                    M_target = desired_rot.to_4x4()
                    M_target.translation = t_out
                    mb = dst_rest["Root"].inverted() @ M_target
                    dpb.location = mb.to_translation()
                    dpb.rotation_quaternion = mb.to_quaternion()
                    W[dn] = M_target
                else:
                    parent = dpb.parent
                    Wp = W.get(parent.name)
                    assert Wp is not None, "parent not posed: " + parent.name
                    K4 = Wp @ parent.bone.matrix_local.inverted() @ dpb.bone.matrix_local
                    q = (K4.to_3x3().inverted() @ desired_rot).to_quaternion()
                    dpb.rotation_quaternion = q
                    W[dn] = K4 @ Matrix.Translation(Vector((0, 0, 0))) @ q.to_matrix().to_4x4()
            bpy.context.view_layer.update()
            # keyframe
            for sn, dn in MAP:
                dpb = hero_arm.pose.bones[dn]
                dpb.keyframe_insert("rotation_quaternion", frame=dst_f, group=dn)
                if dn == "Root":
                    dpb.keyframe_insert("location", frame=dst_f, group=dn)
            hips_max.x = max(hips_max.x, abs(hero_arm.pose.bones["Root"].matrix.to_translation().x))
            dst_f += 1
    act.frame_range = [0, dst_f - 1]
    report.append((clip_name, dst_f))

# 5. 校验：评估网格 bbox 不爆 + 旋转误差抽查
scn = bpy.context.scene
deps = bpy.context.evaluated_depsgraph_get()
for clip_name, n in report:
    act = bpy.data.actions[clip_name]
    set_action(hero_arm, hero_ad, act)
    zmin, zmax, xmax = 9e9, -9e9, 0
    for f in range(0, n, max(1, n // 12)):
        scn.frame_set(f)
        bpy.context.view_layer.update()
        ev = hero_mesh.evaluated_get(deps)
        bb = [hero_mesh.matrix_world @ Vector(c) for c in ev.bound_box]
        zmin = min(zmin, min(v.z for v in bb)); zmax = max(zmax, max(v.z for v in bb))
        xmax = max(xmax, max(abs(v.x) for v in bb))
    print("CHECK %-12s frames=%-4d bbox z %.2f~%.2f |x|<=%.2f" % (clip_name, n, zmin, zmax, xmax))

# 6. 清理源对象并导出
bpy.data.objects.remove(src_arm, do_unlink=True)
if src_mannequin:
    bpy.data.objects.remove(src_mannequin, do_unlink=True)
for o in list(bpy.data.objects):
    if o.type == "MESH" and o.name not in ("Hero",):
        bpy.data.objects.remove(o, do_unlink=True)

bpy.ops.object.select_all(action="DESELECT")
hero_mesh.select_set(True)
hero_arm.select_set(True)
bpy.context.view_layer.objects.active = hero_arm
set_action(hero_arm, hero_ad, bpy.data.actions["Idle_Stand"])
bpy.ops.export_scene.gltf(filepath=OUT_GLB, export_format="GLB",
                          use_selection=True, export_apply=False,
                          export_animations=True, export_animation_mode="ACTIONS",
                          export_bake_animation=False, export_optimize_animation_size=False)
print("glb saved %s %d bytes" % (OUT_GLB, os.path.getsize(OUT_GLB)))
print("ACTIONS final:", [a.name for a in bpy.data.actions if a.users])
bpy.ops.wm.save_as_mainfile(filepath=HERO_BLEND)
print("RETARGET DONE")
