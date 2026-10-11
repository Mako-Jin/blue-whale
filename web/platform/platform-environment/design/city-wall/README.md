# design/city-wall · 古城墙直段单元（设计源）

`platform-environment` 场景的**设计源文件**：给将来的城墙场景提供经过验证的
尺寸、比例与构件拆分。**不是运行时资源** —— 本应用按约定不加载外部模型
（见本应用 README 第八节「资源加载」），场景仍用 TypeScript 程序化拼装。

```
design/city-wall/
├── README.md              本文件：尺寸表 + 拼接契约 + 复现方式
├── city-wall-unit.blend   Blender 源文件（Blender 5.2）
├── renders/               评审用渲染图
└── scripts/               可复跑的生成脚本
    ├── bridge.py          连接运行中 Blender 的 socket 桥
    └── build_wall.py      参数化建模 + 材质 + 布光 + 渲染（单文件、幂等）
```

## 一、拼接契约（TILING CONTRACT）

单元作为模块沿 X 轴阵列拼成整墙（阵列步长 = `L` = 10.0）。**这是硬约束**，
新增或修改构件时必须满足三条，否则接缝处会出现凸起、重叠面（z-fighting）
或断续的垛口节奏：

1. **所有构件的 X 范围严格等于 `[-L/2, +L/2]`**。出沿只允许留在 Y 方向
   （条石基础勒脚、马道挑檐），X 向一律齐平。
2. **垛口在单元两端各留半个**（宽 `GAP/2` = 0.30）。阵列后跨缝的
   半口 + 半口自动接成一个完整垛口，节奏连续。
3. `renders/tiling-check.png` 是拼接验证图（3 个单元），改动后必须重渲染核对。

## 二、尺寸表（单位 m）

| 部件 | 尺寸 | 说明 |
| ---- | ---- | ---- |
| 单元长度 `L` | 10.0 | 阵列步长 |
| 条石基础 | 高 1.00（埋深 0.60 + 露明 0.40），宽 6.80 | 两级出沿，Y 向 0.40 / 0.20 |
| 墙身（青砖） | 底宽 6.00 → 顶宽 4.60，高 8.00 | 两侧收分 0.70/侧 |
| 马道 | 净宽 3.65，铺砖厚 0.06 | Y 向挑檐 0.03 |
| 外侧垛口墙 | 高 1.80，厚 0.60 | 下段 0.80 + 垛身 1.00 |
| 垛口节奏 | 模数 2.00 = 垛 1.40 + 口 0.60 | 每单元 5 垛 + 两端各半口 |
| 射孔 | 0.14 × 0.38 | 距垛底 0.40，距垛顶 0.22 |
| 内侧宇墙 | 高 1.00，厚 0.35 | 实心，无垛口 |

关键标高：基础顶 0.40 · 墙顶 8.40 · 马道面 8.46 · 垛口底 9.26 ·
垛口墙顶 10.26 · 宇墙顶 9.46。

## 三、复现方式

1. 打开 Blender（≥ 3.0，当前在 5.2 验证），在 3D 视图 `N` 面板的
   **BlenderMCP** 标签里点 **Start MCP Server**（监听 `127.0.0.1:9876`）。
2. 运行：

   ```bash
   cd web/platform/platform-environment/design/city-wall/scripts
   python bridge.py build_wall.py
   ```

   脚本幂等：重复运行会先删掉同名 collection 再重建。改 `build_wall.py`
   顶部的参数块即可调整尺寸，渲染图与 `.blend` 会一并更新。

> 顺带一提：`bridge.py` 复刻了 MCP-for-Blender 的 socket 协议
> （明文 JSON、无分隔符、`execute_code`），在 MCP 连接器尚未授权时也能直连。

## 四、后续

把这套尺寸落成 three.js 场景时（`src/scene/scenes/city-wall.ts`），按本应用
README 第八节的四步走，并同步 `decisions/0014` 与本 README。届时在
`docs/assets/<ADR号>/` 放 1–2 张定稿截图即可，`design/` 只保留源文件。
