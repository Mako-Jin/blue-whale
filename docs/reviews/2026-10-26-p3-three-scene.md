# 检查报告：whale-common-three 场景封装落地

> 日期：2026-10-26 ｜ 包：`frontend/shared-packages/whale-common-three`（`@bluewhale/whale-common-three`）｜ 结论：**PASS（类型检查 / 构建 / Lint / Node 导入冒烟）**
> 关联：[todo-inventory 2026-10-23](../plans/archive/todo-inventory-2026-10-23.md) 第 14 项、优先级执行台账序号 26

---

## 一、问题与范围

`whale-common-three` 此前是空壳：`scene.ts` 仅有占位注释，`loader.ts` 把 `gltfLoader` / `textureLoader` 写成 `null as unknown as never`，任何调用都会在运行时报错。包没有消费方，属于「共享包先行、能力未实现」。

本轮只做**包内封装补齐**：场景生命周期（初始化 / resize / 渲染循环 / 资源释放）与模型/贴图加载器。不新建 3D 应用、不改 `whale-games-core`（其主循环 TODO 与本包无关，仍需独立排期）。

## 二、变更点

### 2.1 `scene.ts`

新增 `WhaleSceneManager`：

| 能力 | 说明 |
|---|---|
| `init()` | 创建 `Scene` / `PerspectiveCamera` / `WebGLRenderer`，挂载画布到容器，补环境光 + 平行光；缺省相机 fov 60 / near 0.1 / far 1000 / position (0,0,5)；重复调用幂等 |
| `getScene()` / `getCamera()` / `getRenderer()` | 未 `init()` 时抛出明确错误，避免下游拿到 `null` 后静默失败 |
| `resize()` | 按容器 `clientWidth/clientHeight` 更新纵横比与渲染尺寸（0 尺寸按 1 处理，避免除零）；`autoResize` 缺省开启，用 `ResizeObserver` 跟随容器，环境不支持时自动退化 |
| `render()` | 渲染单帧 |
| `onFrame(cb)` | 注册帧回调（`delta` / 累计 `elapsed`，秒），返回取消注册函数 |
| `start()` / `stop()` | `requestAnimationFrame` 渲染循环；单帧步长上限 0.1s，避免标签页切回跳帧；重复 start 幂等 |
| `dispose()` | 停循环、断开 `ResizeObserver`、递归释放几何体/材质/材质引用的贴图、`renderer.dispose()`、移除画布并置空引用 |

配置项 `SceneManagerOptions`：`container`（必填）、`backgroundColor`、`antialias`、`pixelRatio`（缺省 `min(devicePixelRatio, 2)`）、`camera`、`autoResize`。

### 2.2 `loader.ts`

| 导出 | 说明 |
|---|---|
| `getGltfLoader()` / `getTextureLoader()` | 懒创建模块级单例，模块导入期不触碰 DOM，兼容 SSR / Node 侧导入 |
| `loadGltf(url, onProgress?)` | Promise 化 `GLTFLoader.load`，返回 `GLTF` |
| `loadTexture(url, onProgress?)` | Promise 化 `TextureLoader.load`，返回 `Texture` |
| `disposeLoaders()` | 释放加载器单例引用（已加载资源不受影响，由调用方或场景管理器释放） |

删除原 `gltfLoader` / `textureLoader` 的 `never` 占位导出（无消费方，不留兼容垫片）。

## 三、验证

| 项 | 命令 | 结果 |
|---|---|---|
| 类型检查 | `npx tsc --noEmit -p tsconfig.json` | 通过（0 错误） |
| 构建 | `pnpm --filter @bluewhale/whale-common-three build` | ESM/CJS/DTS 全部成功（`index.mjs` 6.17 KB） |
| Lint | `pnpm --filter @bluewhale/whale-common-three lint` | 0 warning / 0 error（仅 Node 的 `MODULE_TYPELESS_PACKAGE_JSON` 提示） |
| Node 导入冒烟 | 导入 `dist/index.mjs` | 5 个导出均为函数；未 `init()` 调用 `getScene()` 按预期抛错；`onFrame` 返回取消函数；未初始化 `dispose()` 不抛错 |

未做浏览器 WebGL 实机渲染验证：包当前**无消费应用**（全仓 grep 无 `whale-common-three` 引用），无法在真实页面挂载画布；待首个 3D 页面（如数字孪生/游戏场景）接入时再做渲染态验收。

> **【2026-11-10 闭环】渲染态验收已完成**（Wave 6.03 孪生平台 M0，验收工具 `acceptance/{index.html,serve.mjs,verify.mjs}`）：
> 无头 Chromium（Playwright）加载 `dist/index.mjs`，空场景挂载旋转立方体，断言全绿——
> `webglVersion=WebGL 2.0 (OpenGL ES 3.0 Chromium)`、48 帧渲染循环递增、容器 resize 生效、
> `dispose()` 后画布摘除且帧循环停止、0 控制台错误。光照/材质（`MeshNormalMaterial`）经真机
> 渲染管线确认。遗留项「无消费方 → 无浏览器渲染态验证」就此关闭。

## 四、遗留

- `whale-games-core` 的 `requestAnimationFrame` 主循环与资源清理仍是 TODO，与本包独立，可复用 `WhaleSceneManager#onFrame/start/stop` 实现，但不属于本轮范围。
- ~~无消费方 → 无浏览器渲染态验证~~（已闭环，见第三节 2026-11-10 验收记录；首个消费方为 Wave 6.03 `whale-twin` 前端子应用 8122）。
- 未提供控制器（OrbitControls）、后处理（EffectComposer）、性能监控（Stats）等扩展；按需再增，避免提前封装。
