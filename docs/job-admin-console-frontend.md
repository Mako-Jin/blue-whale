# whale-job 管理端接入：拓扑图、批量治理、导入导出与告警标识

> 对应扩展路线图 C6。后端接口（`docs/job-dag-graph-and-stats.md`、
> `docs/job-batch-import-export.md`、`docs/job-alert-channels-and-recovery.md`）已完成并实测，
> 本文只讨论管理端 `whale-frontend/admin` 的接入方式，不改任何后端行为。

## 1. 接入范围

| 能力 | 页面位置 | 依赖接口 |
|---|---|---|
| DAG 依赖拓扑 | 定时任务页新增「依赖拓扑」页签 | `GET /job/graph`、`GET /job/graph/validate` |
| 批量启停 / 删除 | 任务列表页签工具栏 | `POST /job/info/batch-status`、`POST /job/info/batch-delete` |
| 按选中导出 / 导入 | 任务列表页签工具栏 | `GET /job/info/export/download`、`POST /job/info/import` |
| 告警通道与恢复标识 | 告警记录页签 | `GET /job/alert/page`（`channel` 参数 + `recovered` 字段） |

执行统计（`GET /job/log/stats`）不在本次接入范围，接口已可用，后续单独排期。

## 2. 选择：自绘 SVG 而不是引图库

管理端现有依赖只有 `antd` + `react-router`，仓库内没有 ECharts / AntV / ReactFlow，
lockfile 里也没有可复用的图库。为一张最大几十节点的依赖图引入 G6/ReactFlow
（体积 + 学习成本 + 对 React 19 的兼容验证）不划算，因此：

- 布局算法是纯函数（`dagLayout.ts`），按「最长路径分层 + 同层按 ID 顺序」计算坐标，
  环上节点用迭代松弛封顶，不会死循环（依赖图本身应当无环，环只是数据异常时的兜底展示）；
- 渲染是普通 SVG（`DagGraph.tsx`）：节点矩形 + 状态点 + 任务名，边为三次贝塞尔 + 箭头 marker；
- 缩放用容器 `overflow: auto` + SVG 尺寸缩放按钮，不做拖拽平移，避免手写交互状态；
- 环检测结果来自后端 `/job/graph/validate`，命中环节点标红，不在前端重复实现算法。

优点：零新增依赖、可单测、构建体积不变；缺点：节点数上百时布局会变宽，靠横向滚动兜底，
当前任务量级（分组内几十个）足够。

## 3. 批量选择与操作

- `ProTable` 增加可选 `rowSelection` 透传（antd `Table` 原生能力），
  选中集合由页面容器用 `selectedRowKeys` 受控，翻页/刷新后保留；
- 工具栏按钮按权限码渲染：
  | 按钮 | 权限码 | 说明 |
  |---|---|---|
  | 批量启用 / 批量停用 | `sys:job:edit` | 未选中时禁用 |
  | 批量删除 | `sys:job:delete` | 二次确认后调用 |
  | 导出选中 | `sys:job:list` | 未选中时禁用，直接下载附件 |
  | 导出全部 | `sys:job:list` | 不带 `jobIds`，可带当前分组 |
  | 导入 | `sys:job:add` | 打开导入弹窗 |
- 批量接口整体 200、逐条结果在体内：成功后统一提示
  `成功 N 条`，存在失败时用 `message.warning` 附前 3 条 `#id 原因`，避免长列表刷屏；
- 操作完成后清空选中集合并刷新当前页，避免对已删除任务重复操作。

## 4. 导出与导入

- 导出走 `downloadBlob`（`@bluewhale/http` 已实现：注入 Authorization、401 续期一次、
  从 `Content-Disposition` 解析文件名），再用临时 `<a download>` 保存，
  不把 JSON 渲染到页面，避免大文件卡顿；
- 导入弹窗：`Upload.Dragger` 选择 `.json` 文件后在本地解析并展示
  「任务 N 条 / 依赖 M 条 / 版本」预览，冲突策略 `SKIP`（默认）/ `OVERWRITE` 二选一；
- 提交后展示后端返回的四类计数与失败明细（`target` + `reason`），并提示是否需要刷新；
- 前端不做字段级校验，只做 JSON 可解析与 `jobs` 数组存在性检查，
  真正的校验链在服务端（cron、执行器 Bean、长度与范围），避免两套规则漂移。

## 5. 告警标识

- `channel` 是逗号拼接值（如 `DINGTALK,FEISHU`），列内拆成多个 Tag 展示；
- `recovered=true`（即 `level=INFO`）在级别列追加「已恢复」绿色 Tag，
  不再让值班同学按文案猜；
- 查询区新增级别与通道两个下拉：通道按单值筛选（后端模糊匹配），
  避免多通道记录因拼接值被精确匹配漏掉。

## 6. 测试与验证

- `dagLayout.test.ts`：分层顺序、边端点、孤立节点、环不导致死循环；
- `JobList.test.tsx`：按钮权限裁剪、批量选择后启用按钮可用、告警通道拆分与恢复标识、
  拓扑页签渲染节点；
- 构建验证：`pnpm --filter @bluewhale/whale-ui build`（ProTable 新属性需先出 d.ts）
  → `pnpm --filter whale-admin test` → `pnpm --filter whale-admin build`；
- 页面为纯前端渲染，不新增路由：拓扑作为定时任务页的第四页签，随 `/job` 菜单下发。

## 7. 兼容与回滚

- 未改动既有接口调用与列定义语义，只追加页签、工具栏按钮与可选查询条件；
- `ProTable` 的 `rowSelection` 是可选属性，其他页面行为不变；
- 后端不可用时：拓扑页签展示空状态与错误提示，不影响任务列表/日志/告警三个页签。
