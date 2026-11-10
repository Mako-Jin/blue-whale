# Wave 6.09：场景行级告警角标（列表页一眼看到哪在报警）

> 状态：**已交付（2026-11-10）** · 上游：Wave 6.08（无头消费 + 跨场景总览 + 全局角标）
> 动机：6.08 只解决了「平台有没有告警」，要看**哪个场景**在报警仍需开抽屉逐条读场景名；
> 本波把未恢复计数按场景聚合进分页结果，列表每行直接显示角标，且点角标只看该场景。

## 一、设计取舍

| 方案 | 评价 |
|---|---|
| 前端按行分别请求 `/twin/scenes/{id}/alerts/unresolved` | N+1（一页 20 行 = 20 请求），放弃 |
| 分页接口逐场景 COUNT | 一页 20 次 COUNT，仍偏高 |
| **一次 `IN` 查询 + 本地分组（采用）** | 1 次查询、3 列数据，未恢复行规模受「绑定 × 状态键」约束 |

- 取数只选 `scene_id / level / ack` 三列，命中既有索引 `idx_scene_resolved (scene_id, resolved)`；
- 分组在 JVM 内完成，产出「未恢复 / 其中严重 / 其中未确认」三个数；
- 无告警的场景**显式写 0**，前端据此区分「没有告警」与「没统计到」；
- 统计失败只记 WARN 并保持字段为空——场景列表不能因告警统计抖动而不可用。

## 二、后端改动

- `dto/SceneAlertCountVO`：场景维度计数载体（unresolved / critical / unacked）
- `dto/SceneVO` 扩展 `unresolvedAlerts` / `criticalAlerts` / `unackedAlerts`
  （仅分页列表装配，详情接口不填）
- `TwinAlertService`：
  - `countUnresolvedByScenes(Collection<Long>)`：空集合不查库；一次 IN 查询后本地分组
  - `fillAlertCounts(List<SceneVO>)`：原地写三个字段；`sceneId` 解析失败跳过；
    统计异常降级（WARN + 留空）
- `TwinSceneController.page`：装配 `fillAlertCounts`（控制器只调服务，符合 ArchUnit 分层）

## 三、前端改动

- `api/twin.ts`：`SceneVO` 补三个可选计数字段
- `ScenePage.tsx`：
  - 新增「告警」列：`0` 显示 `—`；有未恢复时显示橙色计数，含严重则转红并附「· 严重 N」；
    悬停给出「未恢复 / 严重 / 未确认」，点击打开抽屉并**仅看该场景**
  - 抽屉新增「仅看：场景名」可关闭标签；场景限定下若该场景告警未全部进入本次加载
    （超出 200 条上限），显式提示还差多少条，不假装完整
  - 30s 轮询改为：静默统计 + 静默重取当前页（不置 loading，不闪表格）；
    页面不可见时跳过，避免后台标签页空转
  - 抽屉内确认告警后顺带静默刷新列表，行级角标随之回落

## 四、验收

- 单测（`TwinAlertServiceTest` +4）：分场景分组正确 / 空集合不查库 /
  无告警场景写 0 / 统计异常降级留空；whale-twin **114/114 全绿**
- 前端 `tsc -b && vite build` 与 `eslint --max-warnings 0` 均通过

## 五、后续可选项

- 角标点击后抽屉里再按实体分组（当前是平铺列表）；
- 严重告警的浏览器通知（Notification API，需用户授权，注意与 SSE 实时帧去重）；
- 若单页场景数继续增大，可把 `IN` 查询改为按页 `LIMIT` 的聚合子查询。
