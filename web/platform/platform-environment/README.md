# platform-environment · 三维环境应用

整个系统的**环境应用**：与任何业务域无关，只提供一块 three.js 的三维画布，
以及"渲染生命周期该怎么管"的参考实现。

- 端口 `3400`，路由 `/environment`，构建前缀 `/environment/`
- 既是 qiankun 子应用（被基座加载时用基座容器），也能独立启动（用自建容器）

---

## 一、定位：为什么要有这个应用

1. **系统需要一个与业务无关的环境层**。地形、天空、光照这类"世界底座"不属于任何一个业务子应用，
   放在哪一边都会造成归属混乱，所以单独成应用。
2. **它是 three.js 在本项目里的落点**。三维依赖很重（构建产物 gzip 后约 236 kB），
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

| 文件          | 职责                                                                                       |
| ------------- | ------------------------------------------------------------------------------------------ |
| `types.ts`    | `SceneId` / `SceneMeta` / `SceneDefinition` / `SceneBuild` / `SceneControls` —— 类型契约   |
| `catalog.ts`  | 注册表与清单：`Record<SceneId, …>` 钉住完整性、`loadSceneCatalog()`、远端覆盖合并          |
| `common.ts`   | 各场景共用的构建工具：释放、材质、光照、雾、确定性伪随机、单位盒                           |
| `scenes/*.ts` | 五个场景实现：元数据 + 工厂，**不碰渲染器**                                                |
| `player/`     | 主人公：`motor.ts`（移动内核，**纯逻辑**）· `avatar.ts`（造型）· `controls.ts`（输入装配） |
| `env/`        | 环境内核（**纯函数**）：`solar.ts`（太阳高度角/方位角）· `palette.ts`（高度角→光/雾/天穹）· `sample.ts`（时间推进与归一化） |
| `atmosphere/` | 大气装配：天穹（Preetham 散射 + 内置云）、日月主光、雾与背景联动 —— **唯一把画面参数翻译成 three 的地方** |
| `minimap.ts`  | 小地图投影与几何（**纯函数**）：世界→地图、方向向量、视野扇形、朝向箭头                    |
| `stage.ts`    | 装配：场景图 + 渲染器 + 相机控制器 + 动画循环 + 尺寸监听 + **切场景 + 环境应用与 IBL 重烘** |
| `renderer.ts` | `WebGLRenderer` 装配：颜色空间、色调映射、阴影贴图类型、尺寸同步、上下文释放               |
| `viewport.ts` | 容器尺寸 → 绘制缓冲分辨率的纯函数（jsdom 可测）                                            |
| `options.ts`  | 场景开关类型与默认值（组件与舞台共用，避免类型归属地二义）                                 |

### 分层的动机

`WebGLRenderer` 构造时就要 WebGL 上下文，**在没有 GPU 的环境（jsdom、CI）里直接抛错**。
把"场景里有什么、动画怎么推进、资源怎么释放"放进不碰渲染器的 `scenes/` 与 `common.ts`，
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

---

## 四、多场景：注册表、切换与三条护栏

场景 = **实现**（`scenes/*.ts`，three.js 代码，后端给不了）+ **元数据**（`SceneMeta`，纯数据）。
"清单放前端还是后端"的三选一只对元数据有意义。当前用**前端注册表**，
依据与升级路径见 **ADR-0014**，这里只列落地时最容易踩的三条护栏：

1. **组件只认 `localSceneCatalog()`（首屏同步兜底）与 `loadSceneCatalog()`（最终来源）**，
   不直接引用注册表内部 —— 数据来源必须可替换，将来接后端字典时调用方一行不改。
2. **远端只能覆盖、不能发明**（`mergeCatalogOverrides`）：认不出的 id 记入 `unknownIds`
   交上层告警，绝不凭空造一个场景 —— 造出来只会在选中时白屏。
3. **`SceneId` 字面量 ↔ 实现文件一一对应**：注册表用 `Record<SceneId, SceneDefinition>` 承接，
   漏实现或拼错 id 都是编译期报错。新增场景的顺序：实现 → 加字面量 → 注册一行。

### 切换管线

`stage.loadScene(definition, options)` **只换场景图**：释放旧场景 → 构建新场景 →
相机搬到该场景的取景位。渲染器、画布、相机、控制器、PMREM 环境贴图**全部复用**，
不存在第二个 WebGL 上下文。**先释放旧场景、后构建新场景**（反过来峰值显存是两份之和）。

每个场景必须自带：相机位姿、拉近/拉远范围、IBL 环境光强度、默认渲染选项覆盖
（`SceneMeta`）—— 构图差着数量级，共用一套取景必然有场景取不对。
**切场景同时重置渲染选项**：网格辅助线对广场是参照、对丛林是干扰，
沿用上一个场景的开关只会让人以为"开关坏了"。

### 两个贯穿所有场景的约定

- **散布用固定种子的伪随机**（`common.ts` 的 `createPrng`），**禁用 `Math.random()`**：
  每次挂载画面都不同就既没法断言、也没法复现用户截图里的那一帧。
  测试里有一条"两次构建位姿指纹逐字一致"的断言钉住这条约定。
- **方盒构件共享一个单位 `BoxGeometry`**，尺寸靠 `mesh.scale` ——
  古建筑 127 次绘制调用只用了 6 份几何体。共享几何体被重复 `dispose()` 是安全的。

### 释放是否干净，可断言

`stage.diagnostics()` 暴露 three 维护的**当前在 GPU 上**的几何体/纹理计数。
`scripts/verify-scene-switching.mjs` 靠它断言：同一场景第二轮切回来时 GPU 几何体数
与第一轮一致（无累积）。看画面是看不出泄漏的 —— 泄漏要攒到上下文上限才突然白屏。

它还带一个 `frames`：**累计渲染帧数**（只数真正画出画面的帧，加载中的空转帧不算）。
这是给自动验证脚本用的**帧对齐等待原语** —— `setEnvironment()` 是同步改光和雾的，
但画面要等下一帧才重绘，脚本按墙钟 `sleep` 会稳定截到上一帧。实测软件渲染
（`--use-gl=swiftshader`）**单帧要 2.2 秒**，任何写死的毫秒数在那种环境下都不成立；
按帧等则从软件渲染到独显都成立，快机器上自然变快（见 `scripts/README.md`）。

### 已知坑（写进代码注释，也写进 `AGENTS.md`）

- `PCFSoftShadowMap` 在 three r186 **已被移除**，写上只会得到一条运行时警告并静默退回
  `PCFShadowMap` —— 看起来生效了，其实什么都没设。显式写 `PCFShadowMap`。
- PMREM 环境贴图与 `PMREMGenerator` 不在场景图里，`traverse` 释放不到，必须各自释放。
  注意 `PMREMGenerator` **不能**"用完即弃"：天穹变化时要靠它重烘（见 ADR-0017），
  所以它的释放搬到了 `stage.dispose()`。
- 相机远裁剪面给到 400：太空场景星点场半径接近 100，默认 200 会把它们**整片切掉**
  且不报错 —— 表现只是"星星突然少了一半"。

---

## 五、可操控场景：主人公、小地图与相机跟随

古建筑（第一阶段）是第一个**可操控**场景：殿前广场上有一个可以走的主人公。
契约与取舍见 **ADR-0015**，这里只列落地时最容易踩的四条：

1. **相机跟随必须"整体平移"**（`stage.ts` 的 `syncFocus`）。控制器只上报焦点，
   舞台把相机与轨道目标挪**同样的位移** —— 只改 `target` 不动相机，
   镜头会原地转头，人一走远就只剩个俯视小点。
2. **左键旋转与滚轮缩放仍归 OrbitControls**，控制器只借走键盘与右键；
   有控制器的场景 `enablePan` 被关掉（右键让给"设置行动终点"）。
   移动方向是**相机相对**的：按"上"永远朝画面深处走，相机转到哪边都成立。
3. **右键射线打地面平面（y=0），不打网格** —— 网格随开关存在，靠它取点
   会让右键跟着开关一起失效。画布还没布局（尺寸为 0）时直接返回 `null`，
   否则除出 `NaN` 会一路传进移动内核。
4. **小地图不走 React 状态**：`requestAnimationFrame` 自绘 canvas，
   数据经 `stage.interaction()` 逐帧读取。地图**正北朝上**（指北针是静态 `N`），
   人物朝向画箭头、相机视野画扇形（半径随滚轮缩放变化）—— 两个独立信息。
   没有交互的场景（丛林 / 写字楼 / 太空 / 基线）返回 `null`，整块自动隐藏。

移动算法在 `player/motor.ts` 里是**纯内核**（不碰 three、不碰 DOM）：
相机相对位移、目标点行走、最短转向、边界夹取、沿穿透最浅的一边推出障碍。
碰撞与小地图用的矩形**同源**（`scenes/ancient-architecture.ts` 里声明一次）——
挡人的东西必须也是地图上画出来的东西。

---

## 六、大气与天气：一切从太阳高度角推导

日出日落、天空、云、雾与背景的联动不是"调"出来的，是**算**出来的。
契约与理由见 **ADR-0017**，这里只列落地时最容易踩的点。

### 环境参数是数据，大气是场景的可选能力

- `EnvironmentState`（时刻 / 日期 / 经纬度 / 时区 / 时间流逝倍速）与 `SceneOptions` 同级，
  但**不放进** `SceneOptions` —— 选项随场景重置，环境**跨场景延续**。
- 天穹与日月主光是 `SceneBuild.createAtmosphere?()`，与 `createControls?()` 同级。
  **不实现它的场景（室内、太空）天然退出**，没有一处 `if (sceneId === 'spacecraft')`。

### 高度角是唯一的自变量

`env/palette.ts` 只吃一个数：太阳高度角（度）。它一个量同时编码了"几点、什么季节、什么纬度"，
所以换到漠河或赤道，同一套曲线照样成立。太阳位置由 `env/solar.ts` 算出，
**对拍过公开天文数据**（北京夏至正午 73.2°/实测 73.5°，日出日落误差 ≤ 1 分钟），
锚点全在 `tests/env.test.ts` 里。

### 时间流逝由舞台推进，界面"读回"

"时间流逝"本质是播放，播放属于帧循环：

```
App 持有 environment 基准 → stage.setEnvironment()      ← 拨滑块时的"设定"
舞台每帧 advanceEnvironment(delta) → stage.environment()  ← 流逝时的"当前"
```

界面**每帧读回**（只在"分钟变化"时才落进 state），与小地图读 `interaction()` 同路。
让 React 以 60Hz `setState` 驱动时间，等于拿重渲染当节拍器，而且会让"当前时刻"出现两个真相源。
**默认暂停**（`flow = 0`）：演示要可复现。

### 动态 IBL：纯洁的天穹 + 双重节流 + 每帧补检查

黄昏时建筑被染成金色靠的是**环境贴图跟着天穹走**（不是换灯的颜色）：

- 场景提供 `captureSource()` —— 一个**只有天穹**的可渲染对象。把整棵场景图烘进去，
  建筑会把自己的颜色当环境光反射回来（"建筑照亮自己"），画面会莫名其妙发亮。
- 舞台按"高度角变化 ≥ 1.2° **且** 距上次 ≥ 320ms"重烘。单靠阈值挡不住拖滑块，
  单靠时间又会漏掉最后一次变化。
- **每帧补一次检查**（代价只是一次比较）。否则节流窗口会把最后一次变化挡在门外，
  而那次之后再没有新事件 —— 天光就永久停在旧值上。

### 气候域：`SceneMeta.climate`

`'outdoor' | 'indoor' | 'space'`。它回答"室外的天气该不该落到这个场景上"。
必填的理由和 `camera` 一样：漏填就等于让这个问题没有答案。
一期只有古建筑是 `outdoor` 且实现了大气。

### 配套的硬约束：默认取景的俯角必须小于垂直半视角

**加了天空却看不见，等于没做。** 垂直半视角是 25°，俯角一旦超过它，
地平线就落到画面之外 —— 天空、日出、云全都看不见。古建筑的默认机位为此
从 `[0, 9, 24]`（俯角 35°）调到 `[0, 5, 24]`（俯角 18°）。

**新增任何场景时都要检查这一条。**

### 稳态开销

静止时每帧只多一次比较（`maybeBakeEnvironment`）；天穹单体 1 个绘制调用，
云包含在天穹着色器里，日月共用主光。拨时刻时最多每 320ms 一次 PMREM 卷积 ——
那是这条链上最贵的操作，比渲染一帧贵得多。

---

## 七、全局状态订阅：必须走桥，不要直接调 props

订阅基座状态用 `common-tools/micro` 的 `onHostGlobalStateChange()`，
**不要直接调 `props.onGlobalStateChange`**：qiankun 每个子应用只保留**一个**回调，
第二次注册会覆盖第一个（只打一条 `console.warn`），先注册的那个**静默失效**。
这个 bug 的实际表现是"应用内的徽标变了，右下角的调试标记不变" —— 桥已经做了多路复用，
应用侧直接用即可。

---

## 八、目录结构

```
platform-environment/
├── index.html                 独立运行的挂载点（#root）
├── vite.config.ts             base 分环境 · vite-plugin-qiankun · strictPort + CORS
├── src/
│   ├── main.tsx               入口：renderWithQiankun 生命周期 + 独立运行分支
│   ├── mount.ts               挂载点解析（基座容器 / 自建容器）
│   ├── App.tsx                页面装配：场景选择器、小地图、徽标、全局状态订阅、降级面板
│   ├── format.ts              统计数字的格式化（千分位、分辨率）
│   ├── scene/                 three.js 层（见上表）
│   │   ├── scenes/            ancient-architecture · primeval-jungle ·
│   │   │                      modern-office · spacecraft · baseline-lab
│   │   ├── player/            motor.ts（移动内核）· avatar.ts（造型）· controls.ts（输入）
│   │   ├── env/               solar.ts · palette.ts · sample.ts（环境内核，**纯函数**）
│   │   ├── atmosphere/        rig.ts（天穹 / 日月 / 云 / 雾与背景联动）
│   │   └── minimap.ts         小地图投影与几何（纯函数）
│   ├── components/
│   │   ├── SceneView.tsx      画布组件：只管舞台生命周期，不渲染 <canvas>
│   │   ├── SceneSwitcher.tsx  卡片式场景选择器（横排，含氛围色块与图标）
│   │   ├── Minimap.tsx        左上角小地图：自绘 canvas + 指北针 + 操作图例
│   │   └── ControlPanel.tsx   HUD 面板：渲染开关 · 时刻/日期滑块 · 时间流逝 · 太阳高度读数
│   └── styles/                Less 令牌与全局样式
├── design/                    场景设计源（Blender 源文件与生成脚本，非运行时资源）
│   └── city-wall/             古城墙直段单元：尺寸表 + 拼接契约 + renders/
└── tests/                     181 例：motor / controls / minimap / catalog / scenes 契约 /
                               环境内核（含日出日落对拍天文值）/ 选择器 / mount / viewport /
                               format / App 降级
```

---

## 九、命令

```bash
pnpm --filter platform-environment dev    # 独立启动 :3400
pnpm --filter platform-environment build  # 生产构建（产物 base = /environment/）
node scripts/verify-player-controls.mjs   # 主人公操控端到端验证（需 :3400 在跑；可传截图目录）
node scripts/verify-atmosphere.mjs        # 大气端到端验证（需 :3400 在跑；可传截图目录）
node scripts/verify-scene-switching.mjs   # 场景切换端到端验证（需 :3400 在跑；可传截图目录）
node scripts/verify-micro-frontend.mjs    # 微前端全链路验证（需基座 :3000 与各子应用在跑）
```

调试入口：基座 `http://127.0.0.1:3000/environment`，或独立 `http://localhost:3400`。

### 新增一个场景

1. 新建 `src/scene/scenes/<id>.ts`：导出 `SceneDefinition`（`meta` + `create()`）。
   散布必须用 `createPrng(seed)`，方盒构件用 `addBox`（共享单位盒）。
2. 在 `src/scene/types.ts` 的 `SceneId` 联合里加 id 字面量。
3. 在 `src/scene/catalog.ts` 的 `REGISTRY` 里注册一行。
4. 跑 `pnpm test` —— `scenes.test.ts` 会自动把通用契约（纯函数动画、确定性布局、
   释放干净、线框开关）套到新场景上，不用手写第二遍。

### 新增一个可操控场景

在"新增一个场景"的四步之上再加两步：

5. 声明可行走范围（`halfExtent`）与不可通行矩形 —— **一份**，同时喂给移动内核与小地图。
6. 实现 `createControls(context)`，把主人公加进场景图并返回控制器。
   舞台会接管它的生命周期、相机跟随与右键（自动关掉轨道平移）。
   `tests/scenes.test.ts` 里有一条"只有实现 `createControls` 的场景才算可操控"的断言。

### 给一个场景挂上大气

7. 标 `climate`：`'outdoor'` / `'indoor'` / `'space'`。**室内与太空不要实现 `createAtmosphere`**
   —— `scenes.test.ts` 有一条断言钉住这一点。
8. 实现 `createAtmosphere()`：把场景自己建的 `DirectionalLight` 与 `AmbientLight`
   交给 `createAtmosphereRig({ scene, keyLight, ambient, baseBackground, fog, sky })`，
   并把 rig 的 `dispose()` 挂进场景的 `dispose()`（天穹有一份**不在场景图里**，`traverse` 清不掉）。
9. **检查默认取景的俯角小于 25°**（垂直半视角），否则天地平线落在画面外，天穹白做。
10. 验收跑 `node scripts/verify-atmosphere.mjs`：它会拨到四个时刻、断言天空的颜色与亮度关系，
    并反复拨 24 次看显存有没有回不去。

---

## 十、尚未实现（别误以为已有）

- **场景深化**：五个场景都是"可辨识的简化版"（全基础几何体），瓦垄、彩画、
  植被多样性、舱内细节等逐场景深化时再做
- **大气只挂在古建筑上**：丛林与写字楼标了 `climate: 'outdoor'`，但**尚未**接 `createAtmosphere`，
  它们现在仍是"一盏写死的灯"
- **天气**：雨、雪、霜与风向量都没做。云的漂移速度是常量，不是"风速算出来的"，
  也不影响雨雪倾角或树摆幅度 —— 天气状态机与降水粒子属于下一阶段（ADR-0017 的"未做"清单）
- **星空与月亮圆盘**：夜里只有"更暗的天穹 + 反向的主光"，没有星点、没有月面
- **主人公的寻路与动画**：终点直线过不去就直接放弃，不绕障碍；人物没有走路摆动
- **小地图交互**：只读，不能拖动 / 缩放 / 点图定位
- **移动端**：虚拟摇杆与触屏没做，当前只支持键鼠
- **远端清单**：`loadSceneCatalog()` 尚无后端来源，接通时同步契约与缓存策略（ADR-0014）
- **资源加载**：没有 GLTF/HDRI 等外部资源与加载进度管理 —— 当前全部程序化生成
- **性能档位**：像素比封顶、阴影分辨率分档等降级策略未做
- **多人/物理**：与实时协同、物理引擎均无关，本应用不承担这些

## 相关

- `docs/decisions/0017-大气与天气的现实映射.md` —— 环境内核分层、高度角作唯一自变量、动态 IBL 与气候域（本轮决策）
- `docs/decisions/0015-场景交互与主人公操控.md` —— 可操控场景的契约、相机跟随与小地图
- `docs/decisions/0014-多场景架构与切换管线.md` —— 清单落地方式与切换管线
- `docs/decisions/0013-三维环境应用与WebGL渲染生命周期.md` —— 画布与上下文同生共死、GPU 资源释放
- `docs/decisions/0012-微前端接入与子应用改造.md` —— 微前端整体决策
- `web/platform/platform-portal/README.md` —— 基座如何挂载子应用、新增子应用的步骤
- `web/packages/common-tools/README.md` —— `micro` 桥接与全局状态多路复用
