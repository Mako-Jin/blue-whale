# S0 技术验证报告（3D 全量承载）

> 日期：2026-11-19
> 关联：[三维王城总纲 v2](2026-11-19-3d-capital-master-plan.md) §3 S0
> 结论：**三项已通过，两项待真机。方案可行，建议进入 S1。**
> 验证页：`whale-world/spike-css3d.html`、`whale-world/spike-antd.html`（dev 端口 8124，路径前缀 `/world/`）

---

## 0. 结论速览

| 任务 | 结论 | 关键数据 |
|---|---|---|
| T0.1 两层对齐 | ✅ **通过** | 层误差 **0.00 px**（探针点法），同相机同帧 |
| T0.2 中文输入法 | ⏸ 待真机 | 测试页已备好，需真实 IME 输入观察 |
| T0.3 深度遮挡 | ✅ **通过** | 柱子正确切掉面板左半，侧视角仍正确 |
| T0.4 性能 | ⏸ 待真机 | 无头 SwiftShader 只能跑 1fps，无参考价值 |
| T0.5 antd 页面复用 | ✅ **通过（带一个必须的全局配置）** | 复杂组件正常，弹层需 `getPopupContainer` |

**最重要的一条**：现有 React/antd 页面可以复用，不用重写。代价是**所有弹层组件必须统一配置 `getPopupContainer`**——而且是全局一处配置，不是逐页改。

---

## 1. T0.1 两层对齐

### 做法
WebGL 与 CSS3DRenderer 共用同一个 `PerspectiveCamera`，每帧各自 `render`。在面板世界坐标处画一个同尺寸绿色线框，正常情况下它应被面板完全盖住。

### 结果
- 层误差 `0.00 px`（连续 6 秒采样，平滑值）
- 绿色线框完全不可见（被精确覆盖）
- 拖拽旋转、滚轮缩放后仍为 0.00

### 踩到的坑（重要）
**用 `getBoundingClientRect()` 的中心量对齐是错的。** 透视变换后矩形变成梯形，轴对齐包围盒的中心会偏离真实中心，量出来约 4.8px 的"假误差"。

正确做法：在元素内放一个 `position:absolute; left:50%; top:50%` 的 **2px 无面积探针**，读它的 rect 中心——无面积元素没有透视变形，中心就是真实投影点。

> 这个测法要写进后续所有"屏风位置校验"的规范里。

---

## 2. T0.3 深度遮挡

### 做法
CSS3D 是 DOM 合成层，浏览器保证它永远在 canvas 之上。反向解决：再开一块**透明 canvas 压在 CSS3D 上层**，用 `camera.layers` 只渲染"应该挡住界面的物体"（柱子、墙、屋檐）。

层序：主场景 canvas → CSS3D 面板 → 遮挡 canvas → HUD。

### 结果
- 柱子正确切掉面板左半边（标题截断、第一张 KPI 卡被完全遮住）
- 侧视角（theta=0.85）遮挡关系仍然正确，随视角变化

### 踩到的三个坑
1. **`scene.background` 会污染遮挡层**：遮挡 pass 若不临时置 `null`，背景色会覆盖 `clearColor`，整块画布变不透明，下面的主场景和面板全被盖死（第一次截图就是全黑）。
2. **`camera.layers.set(1)` 会把光源也过滤掉**：three 的 `projectObject` 按 layer 过滤所有对象，包括灯。结果遮挡层里的柱子变成纯黑剪影。修法：给所有光源 `layers.enable(OCCLUDER_LAYER)`。
3. 遮挡层要关阴影（`shadowMap.enabled = false`），否则每帧重复计算。

### 代价
多一套 GPU 资源（同 scene 的几何/着色器在第二个 renderer 里再上传一份）。所以遮挡层只能放**真正会挡视线的物体**，不能把整座城塞进去。

---

## 3. T0.5 antd 页面挂入 CSS3D（最有价值的一项）

### 结果
- Table / Input / Button / Select 全部正常渲染，cssinjs 样式未错乱
- **弹层是唯一的雷**，而且是致命的

### 关键发现：弹层默认脱离 3D

| 配置 | theta=0（正视） | theta=0.5（侧视） |
|---|---|---|
| 默认（弹层挂 `document.body`） | 下拉"看起来"贴着下拉框 | **下拉与面板脱节**（面板斜了，下拉仍是屏幕坐标） |
| `getPopupContainer` 指向面板内元素 | 正常 | **下拉紧贴下拉框，跟随面板透视一起倾斜** |

原因：antd 默认把弹层渲染到 `document.body`，它拿不到面板祖先的 `matrix3d`，位置只按屏幕坐标算。相机一动，面板走了，弹层留在原地。

### 解法
```tsx
<ConfigProvider
  getPopupContainer={(trigger) => trigger.parentElement ?? document.body}
>
```
**一处配置覆盖全部弹层**（Select / DatePicker / Tooltip / Popover / Dropdown / Cascader 都走这条）。不需要改 31 个应用里的任何业务代码。

### 附带的两个细节
- 面板内承载弹层的父元素需要 `position: relative`，否则 antd 的 absolute 定位会以更外层为基准。
- 自动化截图时 rc-motion 会卡在 `appear-start`（class 里写死 `opacity:0`），只禁 `animation-duration` 不够，要额外覆盖 `opacity: 1`。真实运行时无此问题。

---

## 4. 待真机验证的两项

### T0.2 中文输入法
自动化无法模拟操作系统级候选框，需要真人操作：

1. 打开 `http://localhost:8124/world/spike-css3d.html`
2. 在面板的输入框中输入中文（如"蓝鲸城"）
3. 观察候选框是否贴在光标附近，还是偏移到别处
4. 分别在 **Chrome / Edge / Safari** 各试一次

**预判**：Chrome/Edge 大概率正常（合成层定位处理较好），Safari 风险高。若异常，按计划 §0.2 的方案一（编辑态把 input 提到屏幕层）兜底。

### T0.4 性能
无头 SwiftShader 跑不出有效数字（本次 1fps）。真机测法：

1. 打开 `?q=low` 与默认档各一次
2. 读左上角 FPS，记录静止 / 旋转 / 3 块面板同屏三种状态
3. 目标：1080p 中档 ≥30fps，低档 ≥45fps

---

## 5. 对主计划的影响

| 项 | 原估 | 实际结论 |
|---|---|---|
| T0.1 工期 | 0.5d | 已花 ~0.5d，无超支 |
| T0.3 工期 | 1.5d | 已花 ~1d（踩坑已记录，后续直接抄） |
| T0.5 工期 | 1d | 已花 ~0.5d |
| **新增必做项** | — | **S2 增加"全局 ConfigProvider 弹层容器"任务**（原计划漏了） |
| 架构调整 | — | 无。三层渲染方案成立 |

**建议**：直接进入 S1（场景骨架扩容）。S1 与 S2 不依赖 T0.2/T0.4 的结论，可并行推进；真机两项在 S1 期间找时间补测即可。

---

## 6. 复现方式

```bash
# 依赖（首次）
cd whale-frontend
CI=true pnpm --filter whale-world install --frozen-lockfile

# 启动
cd platform-apps/whale-world
pnpm dev          # → http://localhost:8124/world/spike-css3d.html
```

| 页面 | 用途 |
|---|---|
| `/world/spike-css3d.html?theta=0&phi=1.12&radius=14` | T0.1 对齐 + T0.3 遮挡 |
| `/world/spike-antd.html?theta=0.5` | T0.5 弹层脱节（反例） |
| `/world/spike-antd.html?theta=0.5&fix=1` | T0.5 弹层跟随（正解） |
