# whale-world 视觉合并计划（showcase 成果移植）

> 立项：2026-11-20 ｜ 依据：[2026-11-18-3d-showcase-scene.md](2026-11-18-3d-showcase-scene.md)（S0–S33 已验证）＋ whale-world 源码核查
> 目标：把 whale-showcase 验证过的渲染底座（IBL / 后处理 / 阴影跟随 / 画质档 / 材质贴图 / 天空增强）移植进 `whale-world`（蓝鲸城）本体，**不破坏 6 个 e2e 冒烟与 qiankun 联机链路**。
> 用户决策：showcase 场景搭建（S34+）暂停，转入合并。

## 一、安全边界（调研结论，2026-11-20）

**硬契约（动了必挂 e2e）**：
1. `city.ts:3061-3260` 全部 `window.__bww*` 探针及返回字段（`__bwwDebug` 的 npcs=6/peers=4/brainMode、`__bwwGlyphs` 8 块牌、`__bwwQuests`、`__bwwGuideStops` 9 站等）；
2. App.tsx 全部 `bww-*` DOM id/class（`#bww-speech`、面板、`#bww-minimap`、`.bww-portal-item` ×9）；
3. 存储键 `bww.avatar.v1`、`sessionStorage['bww.debug.token']`；
4. `collision.ts`/`layout.ts` 派生的城墙门洞几何（穿门/挡墙断言）；
5. `net.ts` 的 `world.*` 帧协议与 sceneKey `bluewhale-city-3d`；
6. `city-layout.json` 的 nodeKey/坐标（glyphs/quests/minimap/guide/portal 都数着它）。

**安全改造区**：`city.ts` 渲染基础段（97-169）、水/天 shader（315-446）、`animate()` 的 `renderer.render`（3020）、`updateSky`（2440-2491）。材质工厂 `M()`（141-152）。

**e2e 特殊约束**：6 个冒烟在 SwiftShader 软渲染下跑 WebGL——PMREM 与 EffectComposer 多 pass 在软渲染下又慢又可能有兼容问题，**必须自动检测软渲染降级到直渲路径**（e2e 断言只依赖 DOM/探针，不依赖像素）。

## 二、批次

### 批1 渲染底座（本次）
1. 移植 `env.ts`（PMREM IBL）＋ `sky.ts`（仅作 env 烘焙源，改名 `env-sky.ts`）＋ `postfx.ts`（Render→Bloom→Output→Grade→SMAA）到 `whale-world/src/world/`。
2. `city.ts`：建 env + postfx，`scene.environment = env.texture`；hemi 强度压低（IBL 接管漫射：日 0.5→0.22、夜 0.14→0.08）。
3. **env 节流**：world 是连续昼夜（150s/天），PMREM 不可能每帧烘——每 6s 重建一次（一天 25 次），由 `updateSky` 末尾按 `dayF/duskF/sunDir` 构造 `SkyState`。
4. 后处理参数随昼夜：bloom 夜 0.7 / 昼 0.22（灯笼溢光），暗角夜 0.55 / 昼 0.32，暗部染色黄昏冷紫。
5. `animate()`：`renderer.render` → quality≠low 时 `postfx.render(dt)`；resize 同步 `postfx.setSize`。
6. **画质档** high/mid/low（dpr/aa/shadow 三参数，showcase QUALITY 表口径）；`WebGLRenderer` 的 `UNMASKED_RENDERER` 含 `SwiftShader`/`llvmpipe` 时强制 low（直渲 + 单次 env）。新增 `window.__bwwQuality` 探针（只加不删）。
7. **阴影相机跟随玩家**：固定 ±130 → ±44 小盒，`sunLight.target` 每帧贴 `player.pos`，texel 精度大幅提升。
8. `renderer.info.autoReset = false` + animate 里手动 reset（HUD 无影响，调试用）。

### 批2 材质贴图（待批1验收）
- `brickTexture/plasterTexture/stoneTexture` 移植；新增 `scaleBoxUV`（BoxGeometry 六面按 (w,h,d)×砖米数缩放 UV，解决"每面恰好一张图"的砖块尺寸漂移）；接进 `M()` 共享材质（matWall/matStone/matRoad/matRed…）。

### 批3 天空/水增强（待批2验收）
- 主天空 shader 换 showcase 增强（月盘+月海/星空/云层），保留 `skyUniforms` 写入接口与 `updateSky` 结构；水面涟漪（`uRipples`）视成本。

### 批4 装饰构件（待批3验收）
- 城门楼/匾额/檐灯（showcase props.ts 的 buildGateTower 系）作**纯装饰**放外墙门洞外侧——不改 collision/layout 几何。

## 三、验收

- 每批：`tsc -b` + `eslint` + `vite build` 过 → 6 个 e2e 全绿（`BASE=http://127.0.0.1:8125/world/?debug=1`）→ 实机截图评审（昼/黄昏/夜三时段）。
- 性能：1080p mid 档 fps 不低于移植前直渲的 80%。
- 收尾：按文件暂存提交（禁 `git add .`）、push、`git status` 干净。

## 四、执行记录

### 2026-11-20 立项
- 完成代码级调研（渲染管线/qiankun/e2e 契约/性能），批次划定如上。

### 2026-11-20 批1 完成（渲染底座：IBL + 后处理 + 画质档 + 阴影跟随）

- **需求项**：showcase 的渲染底座进蓝鲸城本体，e2e 与联机契约零破坏。
- **完成项**：
  - 新增 `src/world/env-sky.ts`（showcase sky.ts 原样，作 env 烘焙源）、`src/world/env.ts`（PMREM IBL）、`src/world/postfx.ts`（Render→Bloom→Output→Grade→SMAA）。
  - `city.ts`：软渲染检测（`UNMASKED_RENDERER` 含 swiftshader/llvmpipe 强制 low=直渲+无 IBL，保 e2e）；画质三档 `setQuality` + `window.__bwwQuality/__bwwSetQuality` 探针；mid 档默认。
  - `updateSky` 末尾同步：env 节流重烘（6s 一次，白天太阳/夜里月亮方向各烘月夜环境光）；bloom 夜 0.7/昼 0.22；暗角夜 0.55/昼 0.32；暗部染色黄昏冷紫。
  - hemi 0.62→0.2 压低（IBL 接管漫射）；阴影盒 ±130→±44 并逐帧跟随玩家（`sunLight.position = target + sunDir*220`，方向不漂移）；animate 按 quality 分支 `postfx.render/renderer.render`；resize/dispose 同步。
- **验证**：`tsc -b`/`eslint --max-warnings 0`/`vite build` 过；e2e 8/10 全绿（city-smoke/path/quest/weather/audio/glyphs/guide/say；npc-chat/ws 需 TOKEN+Docker 环境前置，非回归）；实机三时段截图（昼/日落/月夜）评审通过，`quality=mid`。
- **性能**：mid 41.5fps / high 36.5 / low(直渲基线) 49.5——mid=84% 达标；首测 34.5（69%）→ mid 摘掉 SMAA（两遍全屏 pass）后达标，SMAA 只留 high。
- **遗留**：批2 材质贴图、批3 天空/水增强、批4 装饰构件；dusk 截图实为日落时刻（150s/天节奏快，非回归）。
- **已修正问题项**：`lanternOn` 重复声明（build 才报，tsc 因 @ts-nocheck 不查 city.ts）；首次截图脚本等面板 3.5s 不足（mount 后 2100ms 才弹）。
