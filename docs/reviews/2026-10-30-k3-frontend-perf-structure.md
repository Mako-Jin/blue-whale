# K-3 前端性能与结构检查报告（2026-10-30）

> 对应任务包：[工作空间待办盘点](2026-10-30-workspace-backlog-inventory.md) K-3（T-09 / T-10 / T-11 / T-12）。
> 关联 devlog：[2026-10-30.md](../devlog/2026-10-30.md) §K-3。
> 提交：`e1d722ff`（T-09）、`eb47d42e`（T-10）、`90074488`（T-11）、`5cd9c851`（T-12）。
> 验证环境：Node v24.14.1 / pnpm 10.33.0；本机中间件与 Docker 未启动（B-01/B-02），全部验证为构建期/静态期证据。

## 一、结论速览

| 任务 | 改动 | 验证 | 结论 |
|---|---|---|---|
| T-09 巨型组件拆分 | 6 主文件 + 27 个新文件，纯结构重构 | 各包 build+lint、全仓 lint、whale-ui 17/17 单测、交叉复验 | 完成，行为零变更 |
| T-10 qiankun 首屏 | prefetch all→false + 基座 vendor 拆分 | build 前后产物对比 + lint | 完成；external 全方案记待做 |
| T-11 env 文档 | 4 份 .env.example + frontend.md 登记节 | grep 全量 VITE_* 核对 | 完成，6/6 独立 SPA 覆盖 |
| T-12 shamefully-hoist | .npmrc 删 1 行 | install + 23/23 build + 全仓 lint + vitest | 结论「可关闭」，已落地 |

## 二、T-09 巨型组件拆分

### 改动面

执行方式：3 个并行子代理（按包分组避免冲突），主会话统一验收。

| 组件 | 所属包 | 拆前 | 拆后 | 拆出文件 |
|---|---|---:|---:|---|
| DocEditor | whale-doc | 1073 | 397 | editorShared.ts(48) / EditorToolbar.tsx(90) / EditorHeader.tsx(232) / useDocCollab.ts(383) / useDocPageActions.ts(277) / EditorOverlays.tsx(114) |
| InfoPanel | whale-doc | 788 | 80 | OverviewTabs.tsx(262) / VersionTab.tsx(182) / AttachmentTab.tsx(138) / CommentTab.tsx(179) |
| FormDataList | whale-form-designer | 848 | 325 | filterHelpers.ts(116) / useDynamicFilters.ts(228) / FilterPanel.tsx(286) / DetailDrawer.tsx(120) |
| FormDesigner | whale-form-designer | 672 | 346 | useFieldHistory.ts(128) / useDesignerData.ts(189) / designerUtils.ts(83) / Toolbar.tsx(112) / PreviewDrawer.tsx(35) |
| FormRenderer | whale-ui（共享包） | 741 | 120 | types.ts(98) / values.ts(95) / fileRuntime.ts(60) / AttachmentControl.tsx(148) / SubtableControl.tsx(190) / controls.tsx(75) |
| JobList | whale-admin | 636 | 332 | constants.ts(45) / columns.tsx(208) / JobEditorModal.tsx(119) |

### 检查项

1. **行为零变更**：拆分为逐段原样搬运（含注释、文案、id 属性、样式、eslint-disable）；已知偏离均在报告中登记——① hooks 下沉后 exhaustive-deps 补依赖数组（均为稳定 ref/setter，回调重建时机不变）；② `buildJobColumns` 由内联数组改工厂函数（因操作列依赖注入，调用语义逐字一致）；③ FormDataList 下拉 onChange 内联逻辑原样移入 `selectField`/`selectOp`。
2. **公开 API 稳定**：whale-ui `src/index.ts` 未手改，FormRenderer re-export 面经 `dist/index.d.ts` 核对完整保留；linkage.test.ts 未动且 17/17 通过。
3. **无越界改动**：`git status` 仅 6 个主文件 M + 新增文件，package.json / router / 其他包零触碰。
4. **规模达标**：主文件 80–397 行（全部 <400），新文件全部 <600 行。
5. **交叉复验**：3 个代理并行期间 whale-ui dist 被重建，验收时对 whale-doc、whale-form-designer 重新执行 build，确认与最新 dist 兼容。

### 风险与残留

- 无实机 E2E（B-01/B-02）：行为保障来自「逐段搬运 + tsc 严格编译 + eslint --max-warnings 0 + whale-ui 单测」，Playwright 实跑列入待做。
- `useDocCollab`（383 行）为最大新文件，属协作编排内聚逻辑，继续拆分会破坏可读性，不再细分。

## 三、T-10 qiankun 首屏与公共依赖

### 改动

1. `whale-platform/src/qiankun/index.ts`：`prefetch: 'all'` → `prefetch: false`。
   - 依据：基座默认首页为自研工作台 Dashboard（routes.tsx index 路由），不渲染任何子应用；`'all'` 会在 `start()` 后立即并发拉取全部 9 个子应用 entry（HTML+JS），首屏纯浪费。改为首次激活按需加载。
2. `whale-platform/vite.config.ts`：新增 `manualChunks`——`vendor-react`（react / react-dom / react-router / @remix-run / scheduler）与 `vendor-antd`（antd / @ant-design / @rc-component / rc-* / dayjs）。

### 产物对比（whale-platform build）

| chunk | 改造前 | 改造后 |
|---|---:|---:|
| 业务主 chunk | 1330.89 kB / gzip 426.22 | **186.06 kB / gzip 63.48** |
| vendor-react | —（混在主 chunk） | 285.85 kB / gzip 90.45 |
| vendor-antd | —（混在主 chunk） | 927.16 kB / gzip 292.27 |
| MainLayout | 52.07 kB | 6.40 kB |

收益：首屏不再预取 9 个子应用 entry；vendor hash 稳定后业务迭代仅需重新下载 ~64 kB（gzip）业务 chunk。

### 检查项

- 正则分组顺序：先匹配 antd 系（含 @ant-design/icons 内部 react 引用）再匹配 react 系，避免误分组；pnpm 路径（`.pnpm/<pkg>@<ver>/node_modules/<pkg>/`）末段可命中。
- vite dev 不受 manualChunks 影响（仅 rollup build 生效）；`prefetch: false` 不改变子应用加载方式，仅去掉预取时机。
- lint 通过（--max-warnings 0）。

### 评估结论（react/antd 全量 external）

qiankun 经典方案（子应用 external + 基座/CDN 注入共享依赖）在 vite ESM 体系下需 importmap 或运行时脚本注入 + 全部子应用改造 + 实机回归；当前无实机验证环境（B-01/B-02），**本轮不落地**，共享 chunk 策略作为过渡方案已生效并测量。列入 devlog 待做。

### 风险与残留

- `vendor-antd` 927 kB 仍超 500 kB 警戒线（antd 全量引用固有），后续评估按需加载；其余独立 SPA 单 chunk（whale-admin 1517 kB、whale-oa 1229 kB）可复制基座 manualChunks 模式。
- 实机首屏请求数/耗时对比待 B-02 环境恢复后补测。

## 四、T-11 env 文档覆盖

### 改动

- 新增 4 份 `.env.example`（admin / whale-agent / whale-form-designer / whale-workflow-designer），与既有 ops、whale-platform 格式一致。
- `docs/frontend.md` 新增「环境变量登记」节：全量变量表 + 未配置行为 + 子应用零 `VITE_*` 说明。

### 检查项

1. **全量核对**：grep `import.meta.env.VITE_*` 全仓 34 处，全部归属 6 个独立 SPA——`VITE_API_BASE_URL`（6 app 通用）、`VITE_PROMETHEUS_URL`/`VITE_GRAFANA_URL`（仅 ops）、`VITE_SUBAPP_ENTRY_<NAME>`×9 与 `VITE_PLATFORM_TOKEN`（仅 whale-platform）；新增 4 份后 **6/6 独立 SPA 全覆盖**。
2. **语义准确**：`VITE_API_BASE_URL` 未配置 = 同源相对路径（client.ts 注释与 vite proxy 配置交叉验证），已在 example 中写明「仅跨域直连网关时才需配置」。
3. **顺手更正**：frontend.md 两处过时陈述（`initGlobalState` 已移除；T-02 已闭环，原标注「待决策 B-08①」）。

## 五、T-12 shamefully-hoist 评估

### 改动

`whale-frontend/.npmrc` 删除 `shamefully-hoist=true`（`strict-peer-dependencies=false`、`auto-install-peers=true` 保持不变）。

### 验证序列（关闭后全链路）

1. `pnpm install`：2.8s 完成，lockfile 无变化，仅按新规则重排 node_modules 链接。
2. 全仓 `pnpm run build`（turbo）：**23/23 任务成功**（1m36s）。
3. 全仓 `pnpm run lint`：exit 0。
4. `pnpm --filter @bluewhale/whale-ui test`：vitest 17/17 通过。

### 结论

workspace 无幽灵依赖（所有 import 均声明在各包 package.json），**可安全关闭**。收益：依赖解析严格化，未声明依赖在安装/构建期立即暴露，避免「本地能跑 CI 挂」。风险：后续新增依赖若漏声明会立即失败（属期望行为）。

## 六、验证汇总

| 验证 | 命令 | 结果 |
|---|---|---|
| 各拆分包 build | `pnpm --filter whale-doc/whale-form-designer/whale-admin/@bluewhale/whale-ui build` | 全部成功 |
| 交叉复验 build | whale-ui dist 重建后 whale-doc、whale-form-designer 重跑 build | 全部成功 |
| 单测 | `pnpm --filter @bluewhale/whale-ui test` | 17/17 通过 |
| 全仓 lint | `pnpm run lint` | exit 0（两轮：T-09 后、T-12 后） |
| 全仓 build（T-12 裁决） | `pnpm run build` | 23/23 成功 |
| 产物测量 | whale-platform build 前后对比 | 见 §三表格 |

未覆盖：实机 E2E / 首屏实测（B-01/B-02 阻塞），已列入 devlog 待做。

## 七、提交清单

| commit | 内容 |
|---|---|
| `e1d722ff` | T-09 六组件拆分（34 文件，+5314/−4383） |
| `eb47d42e` | T-10 prefetch 按需 + vendor 拆分 |
| `90074488` | T-11 四份 .env.example + frontend.md 登记节 |
| `5cd9c851` | T-12 关闭 shamefully-hoist |
