# 小鲸宝 · 运行时资产

这里只放 3D 城运行时用的一份：

| 文件              | 说明                       |
| ----------------- | -------------------------- |
| `xiaojingbao.glb` | 宠物资产，贴图内嵌，2.9 MB |

**完整资产包在仓库根目录 `assets/小鲸宝/`**：原始扫描件、Blender 工程（`blend/`）、
OBJ/MTL（`model/`）、生成脚本（`scripts/`）、预览图（`preview/`）、交互展示页（`showcase/`），
以及规格与接入说明。改模型请改那边，再同步到这里：

```bash
cp assets/小鲸宝/model/xiaojingbao.glb \
   whale-frontend/platform-apps/whale-world/src/assets/models/xiaojingbao/
```

接入要点（详见 `assets/小鲸宝/README.md` 第五节）：

- 单网格 15,632 面、11 根骨骼、12 个动画 clip、2048² 单图集
- 原点在**脚底中心**，身高 1.0，正面朝 **−Y**（three.js 里 `rotation.y = Math.PI` 才朝 +Z）
- 循环 clip：`Idle_Float` / `Idle_Sleep` / `Swim_Slow` / `Swim_Fast` / `Action_Follow`；
  其余为一次性动作（`LoopOnce` + `clampWhenFinished`）
- 特效（水膜光晕、星光、铃铛发光、拖尾）由引擎侧实现，材质只有 BaseColor
