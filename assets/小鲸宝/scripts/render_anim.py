# -*- coding: utf-8 -*-
"""打开已生成的 blend，按不同 Action 渲关键帧，拼成预览图"""
import bpy, os, math, numpy as np
from mathutils import Vector

# 工作目录：默认脚本所在目录；可用 XJB_DIR 指向含 out/ 的工作目录
HERE = os.environ.get("XJB_DIR") or os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")

bpy.ops.wm.open_mainfile(filepath=os.path.join(OUT, "xiaojingbao.blend"))
scn = bpy.context.scene
cam = scn.camera
arm = bpy.data.objects["WhaleRig"]

# 相机：偏正面一点，能看清全身
cam.location = (1.02, -3.05, 0.30)
cam.rotation_euler = (Vector((0, 0.00, -0.02)) - Vector(cam.location)).to_track_quat("-Z", "Y").to_euler()
cam.data.type = "PERSP"
cam.data.lens = 70
scn.render.resolution_x = 640
scn.render.resolution_y = 640
scn.render.image_settings.file_format = "PNG"
scn.view_settings.exposure = -0.50

shots = [
    ("Idle_Float", [1, 25, 49, 73]),
    ("Swim_Fast", [1, 7, 13, 19]),
    ("Action_Happy", [7, 15, 23, 30]),
    ("Action_Snuggle", [1, 10, 22, 34]),
]

paths = []
for act_name, frames in shots:
    act = bpy.data.actions.get(act_name)
    if not act:
        print("MISSING ACTION", act_name)
        continue
    if not arm.animation_data:
        arm.animation_data_create()
    arm.animation_data.action = act
    for f in frames:
        scn.frame_set(f)
        p = os.path.join(OUT, "anim_%s_%03d.png" % (act_name, f))
        scn.render.filepath = p
        bpy.ops.render.render(write_still=True)
        paths.append(p)
        print("saved", p)

# 拼 4x4
imgs = []
for p in paths:
    im = bpy.data.images.load(p)
    w, h = im.size
    px = np.empty(w * h * 4, dtype=np.float32)
    im.pixels.foreach_get(px)
    imgs.append(px.reshape(h, w, 4))
    bpy.data.images.remove(im)

cols, gap = 4, 8
rows = (len(imgs) + cols - 1) // cols
w, h = imgs[0].shape[1], imgs[0].shape[0]
W = cols * w + (cols + 1) * gap
H = rows * h + (rows + 1) * gap
sheet = np.ones((H, W, 4), dtype=np.float32)
sheet[:, :, :3] = 0.9
for i, arr in enumerate(imgs):
    r, c = divmod(i, cols)
    rr = rows - 1 - r
    y0 = gap + rr * (h + gap)
    x0 = gap + c * (w + gap)
    sheet[y0:y0 + h, x0:x0 + w, :] = arr
out = bpy.data.images.new("sheet", width=W, height=H, alpha=True)
out.pixels.foreach_set(sheet.reshape(-1))
out.filepath_raw = os.path.join(OUT, "sheet_anim.png")
out.file_format = "PNG"
out.save()
print("sheet saved")
