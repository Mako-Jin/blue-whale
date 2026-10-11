# 0013 - 三维环境应用与 WebGL 渲染生命周期

- 状态：已采纳
- 日期：2026-10-10
- 关联：`0012-微前端接入与子应用改造`（挂载与全局状态的下发方式）、`0002-前端公共包与公共依赖治理`（依赖走 catalog）

---

## 一、背景

系统需要一个**与任何业务域无关的环境应用**：三维画布、渲染循环、相机控制。
同时它要满足两条既有约束：

- 作为 qiankun 子应用接入基座（ADR-0012 的接线方式），被加载时用**基座传入的容器**；
- 保留独立运行分支（ADR-0012 第 4 条），单独启动时用**自建容器**。

three.js 依赖重（生产构建 gzip 后约 218 kB），独立成子应用可以保证只有真正进入
`/environment` 路由的用户才加载它。真正需要提前想清楚的不是"怎么建 React 工程"
（照抄两个控制台即可），而是 **WebGL 的资源生命周期在微前端反复挂载/卸载下怎么管**。

---

## 二、决策

### 1. 独立成 `platform-environment`，不塞进任何业务子应用

环境层（地形、天空、光照这类"世界底座"）不属于任何一个业务子应用，放在哪一边都会造成归属混乱。
单独成应用后，业务子应用将来需要三维能力时走共享包，而不是互相引用（铁律 5）。

| 项                     | 值                                                                              |
| ---------------------- | ------------------------------------------------------------------------------- |
| 目录                   | `web/platform/platform-environment`                                             |
| 端口 / 路由 / 构建前缀 | `3400` / `/environment` / `/environment/`                                       |
| 依赖                   | `three`、`@types/three`（版本进 `pnpm-workspace.yaml` 的 `catalog:`，只写一处） |

### 2. 两种运行方式，一条渲染路径

`src/mount.ts` 的 `resolveMountPoint(container?)` 返回 `{ element, mode }`：
被基座加载时在基座容器内 `ensureChild()` 找/建一层自己的 `#root`；独立启动时用
`index.html` 的 `#root`（缺失则现场建一个）。两条路共用同一个 `App`，`mode` 只用于显示徽标。

**不在基座容器本身上渲染**：qiankun 卸载会清空容器 `innerHTML`，
直接把根节点当容器用会跟基座的清理动作抢同一个节点，卸载后残留什么全看时序。

### 3. 场景分层：让"没有 GPU 也能测"成为可能

`WebGLRenderer` 构造时就要 WebGL 上下文，在 jsdom/CI 里**直接抛错**。
所以 `src/scene/` 按这条线拆：

| 文件                         | 是否需要 GPU | 职责                                                                                 |
| ---------------------------- | ------------ | ------------------------------------------------------------------------------------ |
| `world.ts`                   | 否           | 场景图、灯光、动画（动画写成 `elapsed` 的显式函数，任意一帧可由 `elapsed` 唯一确定） |
| `viewport.ts` / `options.ts` | 否           | 尺寸换算纯函数、开关类型                                                             |
| `renderer.ts`                | 是           | 渲染器装配、上下文释放                                                               |
| `stage.ts`                   | 是           | 装配 + 动画循环 + 尺寸监听                                                           |

于是"场景里有什么、资源怎么释放"全部有单元测试；真正需要 GPU 的部分留给真实浏览器验证
（`scripts/verify-micro-frontend.mjs`）。

### 4. 画布与上下文同生共死（本 ADR 最关键的一条）

**画布由 `createStage(host)` 创建并持有，不接受外部传入**；`dispose()` 时随上下文一起移除。

原因是一条 WebGL 硬约束：`forceContextLoss()` 会把这张 canvas 的上下文**永久**标记为 lost，
此后 `getShaderPrecisionFormat()` 返回 `null`，在**同一个 canvas** 上再 new 一个
`WebGLRenderer` 会直接抛 `Cannot read properties of null (reading 'precision')`
（three 在 `WebGLCapabilities.getMaxPrecision` 里读这个值）。

而"同一张画布被复用"在 React 下是常态：开发期 **StrictMode 会双跑 effect**（建 → 毁 → 建），
组件用 JSX 持有固定画布必然踩到 —— 表现是"一进页面就显示无法创建 WebGL 上下文"，
且 jsdom 单元测试测不出来（jsdom 本来就没有 WebGL，走的又是降级分支）。
本项目首次跑起来即在真实浏览器里撞上，按上面的链路定位并修复。

**为什么必须 `forceContextLoss()`**：只 `dispose()` 的话，浏览器会保留上下文一段时间，
微前端反复挂载/卸载会攒到上限（通常 ~16 个），之后浏览器开始回收最旧的上下文 ——
表现是"切换几次之后画面变白，控制台一条错误都没有"。端到端脚本用
`gl.isContextLost()` 在卸载后断言释放成功。

### 5. GPU 资源必须显式释放，并且要"可断言"

几何体、材质、`WebGLRenderTarget`（PMREM 产物）都不会随对象被 GC 掉。`disposeSubtree()`
递归释放整棵子树并抽成导出函数 —— 让"释放得干不干净"可以被测试断言，而不是靠"看了一眼没报错"。
注意 `PMREMGenerator` 与 `RoomEnvironment` **不在场景图里**，`traverse` 释放不到，必须各自释放。

**同类的第三处是光源的阴影贴图**（`light.shadow.dispose()`）。它由光源持有，
既不在 `geometry`/`material` 上、也不随光源被 GC，`traverse` 同样遍历不到 ——
只放前两者的写法在切场景时每次留下两张纹理，而 `geometries` 计数照常回落，
**看画面、看几何体数都发现不了**（是靠逐场景遍历时纹理数单调上涨逮住的）。
这类"释放不全"的共同形态是：**资源挂在某个非 mesh 的对象上，而释放代码只认 mesh**。

### 6. 计时用 `Timer`，阴影用 `PCFShadowMap`（r186 的两处更替）

- `THREE.Clock` 自 r183 起弃用。`Timer` 的 `update()` 让 `getDelta()` / `getElapsed()`
  在同一帧内可反复读且值一致；`connect(document)` 借 Page Visibility API 兜住
  "切标签页再切回来"的巨大 delta（否则物体会瞬移一大截）。`dispose()` 时必须
  `timer.dispose()` 断开监听，否则每次挂载都在 document 上多留一个监听器。
- `THREE.PCFSoftShadowMap` 在 r186 **已被移除**：写上只得到一条运行时警告并静默退回
  `PCFShadowMap` —— 看起来生效了，其实什么都没设。这类"显式写出"的价值正在于此：
  默认值是会变的（PCFSoftShadowMap 自己就当过默认值）。

### 7. 全局状态订阅走桥的多路复用（顺带修掉的共享层 bug）

qiankun 的 `onGlobalStateChange` 为每个子应用只保存**一个**回调，第二次注册直接覆盖第一个
（源码原话：「每个子应用只有一个激活状态的全局监听，新监听覆盖旧监听」，且只打 `console.warn`）。
子应用里不止一个消费者（调试标记、业务状态、埋点上下文）时，谁后注册谁生效，先注册的**静默失效**。

这个 bug 在接入本应用时真实出现：应用内徽标更新了、右下角调试标记不变。修复落在
`common-tools/micro`：`bindHost` 只注册**一次**原生回调，新增
`onHostGlobalStateChange(listener, { fireImmediately? })` 分发给任意多个订阅者并返回取消订阅函数；
`unbindHost()` 清空订阅者并注销原生回调（子应用模块在多次挂载间是复用的，不清会重复回调）。
测试替身也改成**忠实建模** qiankun 的单槽位行为（覆盖即计数）—— 替身比真实实现宽容，
恰恰会放过这类 bug。子应用一律用新 API，不要再直接调 `props.onGlobalStateChange`。

### 8. 依赖与工程约定沿用既有决策

`three` / `@types/three` 版本只写在 `catalog:`（ADR-0002）；子应用改造（base 分环境、
`vite-plugin-qiankun`、`strictPort` + CORS、preamble 修复、保留独立运行分支）完全按
ADR-0012 第 3 / 5 / 10 条执行，本 ADR 不重复。

---

## 三、后果

- **正向**：三维能力有了独立归属；渲染生命周期有了可测试的参考实现；WebGL 上下文在
  微前端反复挂载/卸载下不再泄漏（有端到端断言兜底）；qiankun 全局状态的单槽位限制
  被桥吸收，后续子应用不会再踩。
- **代价**：three 的 ~218 kB（gzip）打进这个子应用的产物；`scene/` 层为了可测试性
  多了一层抽象（`world` 与 `renderer` 分离）。
- **已知限制**：场景内容目前是演示用的几何体，真正的环境（地形/天空/大气）、资源加载
  （GLTF/HDRI）、性能档位（像素比封顶、阴影分辨率分档）都未做；无头/无 GPU 环境走
  降级面板而不是白屏。
- **验证方式**：`node scripts/verify-micro-frontend.mjs`（真实 Chrome + CDP）覆盖
  挂载 → 画布存在 → 统计产生 → 卸载后 `isContextLost()` 为真 → 容器清空。
  实测截图见 `docs/assets/0013/`：

| 截图                   | 内容                                                                     |
| ---------------------- | ------------------------------------------------------------------------ |
| `env-in-portal.png`    | 被基座加载：容器徽标、桥接徽标、全局状态徽标齐全，60 fps / 28,474 三角面 |
| `env-standalone.png`   | 独立运行：自建容器，同一份渲染代码                                       |
| `env-wireframe.png`    | 线框模式开关生效（看清几何构成）                                         |
| `env-global-state.png` | 基座下发「全局主题」后应用徽标与右下角调试标记同步变化                   |

## 四、明确未做

- 地形 / 天空 / 大气等真实环境内容 —— 等有场景需求再做
- GLTF / HDRI 等外部资源与加载进度管理
- 性能档位与降级策略（像素比封顶、阴影分辨率分档）
- 物理引擎、多人协同 —— 本应用不承担
