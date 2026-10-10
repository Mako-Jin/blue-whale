# platform-environment · 三维环境应用

整个系统的**环境应用**：与任何业务域无关，只提供一块 three.js 的三维画布，
以及"渲染生命周期该怎么管"的参考实现。

- 端口 `3400`，路由 `/environment`，构建前缀 `/environment/`
- 既是 qiankun 子应用（被基座加载时用基座容器），也能独立启动（用自建容器）

---

## 一、定位：为什么要有这个应用

1. **系统需要一个与业务无关的环境层**。地形、天空、光照这类"世界底座"不属于任何一个业务子应用，
   放在哪一边都会造成归属混乱，所以单独成应用。
2. **它是 three.js 在本项目里的落点**。三维依赖很重（构建产物 gzip 后约 218 kB），
   独立成子应用意味着只有真的进入这个路由的用户才加载它，其他子应用不受拖累。
3. **它是渲染生命周期的样板**。WebGL 上下文是稀缺资源（浏览器通常只允许 ~16 个同时存在），
   微前端反复挂载/卸载的场景下，"释放干不干净"是硬问题。这里把答案写成了代码和测试。

---

## 二、两种运行方式，一条渲染路径

| 运行方式               | 容器来源                                                | 入口分支                                    |
| ---------------------- | ------------------------------------------------------- | ------------------------------------------- |
| 被基座加载             | 基座通过 `props.container` 传入                         | `renderWithQiankun` 的 `mount`              |
| 独立启动（`pnpm dev`） | 本应用自建（`index.html` 的 `#root`，缺失则现场建一个） | `qiankunWindow.__POWERED_BY_QIANKUN__` 为假 |

解析逻辑在 `src/mount.ts` 的 `resolveMountPoint()`，返回 `{ element, mode }` ——
**两条路共用同一个 `App`**，只是把 `mode` 传下去用于显示徽标（「被基座加载 · 基座容器」/
「独立运行 · 自建容器」）。分开写两套入口，迟早漂移。

两个刻意的设计：

- **不在容器本身上渲染**：被基座加载时 qiankun 卸载会清空容器 `innerHTML`，
  直接把根节点当容器用会跟基座的清理动作抢同一个节点。所以总是 `ensureChild()`
  在容器内找/建一层自己的 `#root`。
- **独立运行不是兜底代码**：改一行不用等基座起来，渲染问题在基座里排查要同时面对三层日志。

---

## 三、three.js 层（`src/scene/`）

| 文件          | 职责                                                                                           |
| ------------- | ---------------------------------------------------------------------------------------------- |
| `world.ts`    | 场景图：灯光、地面网格、几何体、动画。**不碰渲染器**                                           |
| `renderer.ts` | `WebGLRenderer` 装配：颜色空间、色调映射、阴影贴图类型、尺寸同步、上下文释放                   |
| `stage.ts`    | 装配：世界 + 渲染器 + 相机控制器 + 动画循环 + 尺寸监听，对外只有 `start()`、开关和 `dispose()` |
| `viewport.ts` | 容器尺寸 → 绘制缓冲分辨率的纯函数（jsdom 可测）                                                |
| `options.ts`  | 场景开关类型与默认值（组件与舞台共用，避免类型归属地二义）                                     |

### 分层的动机

`WebGLRenderer` 构造时就要 WebGL 上下文，**在没有 GPU 的环境（jsdom、CI）里直接抛错**。
把"场景里有什么、动画怎么推进、资源怎么释放"放进不碰渲染器的 `world.ts`，
这些逻辑全部可以跑单元测试；真正需要 GPU 的只有 `renderer.ts` 与 `stage.ts`，留给真实浏览器验证。

### 渲染生命周期：四条硬规则

1. **画布与上下文同生共死**（`stage.ts`）。画布由 `createStage` 创建、`dispose()` 时移除，
   **不接受外部传入**。原因是 WebGL 的一条硬约束：`forceContextLoss()` 会把这张 canvas 的
   上下文**永久**标记为 lost，此后 `getShaderPrecisionFormat()` 返回 `null`，
   在同一个 canvas 上再 new 一个 `WebGLRenderer` 会直接抛
   `Cannot read properties of null (reading 'precision')`。而"同一张画布被复用"在 React 下是常态 ——
   开发期 StrictMode 会**双跑 effect**（建 → 毁 → 建），组件用 JSX 持有固定画布必然踩到。
   把画布收进舞台，这种复用就不可能发生。
2. **`dispose()` 必须调 `forceContextLoss()`**。只 `dispose()` 的上下文浏览器会保留一段时间，
   攒够上限后开始回收最旧的 —— 表现是"切换几次之后画面变白，控制台一条错误都没有"。
   端到端验证脚本用 `gl.isContextLost()` 断言这一点。
3. **GPU 资源不会随 GC 释放**。几何体、材质、渲染目标必须显式 `dispose()`；
   `disposeSubtree()` 递归释放整棵子树，并且**可被测试断言**。
4. **计时用 `Timer` 不用 `Clock`**（`Clock` 自 r183 起弃用）。`Timer` 的 `update()` 让
   `getDelta()`/`getElapsed()` 在同一帧内可反复读且值一致，`connect(document)` 借
   Page Visibility API 兜住"切标签页再切回来"的巨大 delta。

### 已知坑（写进代码注释，也写进 `AGENTS.md`）

- `PCFSoftShadowMap` 在 three r186 **已被移除**，写上只会得到一条运行时警告并静默退回
  `PCFShadowMap` —— 看起来生效了，其实什么都没设。显式写 `PCFShadowMap`。
- PMREM 环境贴图（`RoomEnvironment`）与 `PMREMGenerator` 不在场景图里，
  `traverse` 释放不到，必须各自释放。

---

## 四、全局状态订阅：必须走桥，不要直接调 props

订阅基座状态用 `common-tools/micro` 的 `onHostGlobalStateChange()`，
**不要直接调 `props.onGlobalStateChange`**：qiankun 每个子应用只保留**一个**回调，
第二次注册会覆盖第一个（只打一条 `console.warn`），先注册的那个**静默失效**。
这个 bug 的实际表现是"应用内的徽标变了，右下角的调试标记不变" —— 桥已经做了多路复用，
应用侧直接用即可。

---

## 五、目录结构

```
platform-environment/
├── index.html                 独立运行的挂载点（#root）
├── vite.config.ts             base 分环境 · vite-plugin-qiankun · strictPort + CORS
├── src/
│   ├── main.tsx               入口：renderWithQiankun 生命周期 + 独立运行分支
│   ├── mount.ts               挂载点解析（基座容器 / 自建容器）
│   ├── App.tsx                页面装配：徽标、全局状态订阅、降级面板
│   ├── format.ts              统计数字的格式化（千分位、分辨率）
│   ├── scene/                 three.js 层（见上表）
│   ├── components/
│   │   ├── SceneView.tsx      画布组件：只管舞台生命周期，不渲染 <canvas>
│   │   └── ControlPanel.tsx   HUD 面板：开关与帧率/绘制调用/三角面统计
│   └── styles/                Less 令牌与全局样式
└── tests/                     31 例：mount / viewport / world / format / App 降级
```

---

## 六、命令

```bash
pnpm --filter platform-environment dev    # 独立启动 :3400
pnpm --filter platform-environment build  # 生产构建（产物 base = /environment/）
node scripts/verify-micro-frontend.mjs    # 端到端验证（需基座 :3000 与各子应用在跑）
```

调试入口：基座 `http://127.0.0.1:3000/environment`，或独立 `http://localhost:3400`。

---

## 七、尚未实现（别误以为已有）

- **场景内容由空到有**：当前是演示用的几何体，真正的"环境"（地形/天空/大气）等需求来了再做
- **资源加载**：没有 GLTF/HDRI 等外部资源与加载进度管理
- **性能档位**：像素比封顶、阴影分辨率分档等降级策略未做
- **多人/物理**：与实时协同、物理引擎均无关，本应用不承担这些

## 相关

- `docs/decisions/0012-微前端接入与子应用改造.md` —— 微前端整体决策
- `web/platform/platform-portal/README.md` —— 基座如何挂载子应用、新增子应用的步骤
- `web/packages/common-tools/README.md` —— `micro` 桥接与全局状态多路复用
