# 工作区扫描报告（2026-11-11）

> 扫描范围：`whale-backend/`、`whale-frontend/`、`docs/`、`scripts/`、`.github/`、git 工作区与本地运行态。
> 口径：只登记**当前仓库仍存在**的缺口；本轮复核已闭环项单列，不再作为待办。
> 上一份：[2026-11-09 工作区扫描](2026-11-09-workspace-scan.md)。

## 一、需求项与扫描证据

| 项 | 结果 |
|---|---|
| HEAD / 同步 | `1b8a9a27`，`origin/main` 与本地 **0/0 同步** |
| 工作区 | **7 个已修改 + 5 个未跟踪**（均在途：登录失败锁定共享计数与前端提示） |
| 暂存区 | `stash@{0}`（2026-11-06 19:15 `pre-pull-local-edits-20261106`，5 文件）仍存在 |
| 文档门禁 | `check-doc-consistency.ps1` → **DOC-CONSISTENCY-PASS**（Boot 4.0.8 / Cloud 2025.1.2 / SCA 2025.1.0.0） |
| 前端权限映射 | `check-frontend-perm-mapping.py` → 菜单 266 / 契约键 33 / 后端码 906 / 前端引用 646，**FAIL 0，WARN 1** |
| 内网边界 | `check-internal-boundary.py` → 121 端点、24 前缀，**PASS，WARN 1** |
| qiankun 生命周期 | `check-qiankun-lifecycle.py` → 21 子应用 **FAIL 0 / WARN 0** |
| 号段门禁 | `check-permission-id-ranges.ps1` → **执行失败 exit 1**（脚本仍扫 `backend/`，目录已改名） |
| 主代码 TODO | 无未实现 TODO（命中项为 `ConfigEncryptionSettings` 占位口令、PM/OA 业务状态码 `TODO`、`whale-games-core` 冻结项） |
| 跳过测试 | 无 `@Disabled` / `@Ignore` / `test.skip` |
| 运行态 | 3306 / 6379 / 5672 / 8848 / 8080 / 8081 / 8090 **OPEN**；9004（OA）/ 9007（CRM）**CLOSED**；8 容器 healthy（auth/gateway/nacos/redis/rabbitmq/agent/twin/message） |

## 二、完成项（本轮复核已闭环）

- mall 分类页 `setTree(undefined)` 崩溃：代码已加 `Array.isArray` 守卫（提交 `168f2b1b`），devlog 6.24「未修待办」可销账。
- 知识助手菜单 404：`whale-platform` 已注册 `/agent` activeRule（Wave 6.18），旧 WARN 基线失效。
- 表单设计器真实拖拽复核（E6）：`pending-issues` V-02 已于 2026-10-28 闭环（`form-designer-drag.spec.ts`），任务书未勾选属陈旧。
- CRM 客户「负责人」筛选、OA 通知事务后异步、OA Checkstyle、audit_log 迁移缺口：均为 11-09 已修项，本轮无回归。

## 三、BUG 与风险

| 级别 | 问题 | 证据 | 建议 |
|---|---|---|---|
| **高** | 号段扫描门禁失效：`check-permission-id-ranges.ps1:22` 仍 `Join-Path $RepoRoot 'backend'`，目录已改 `whale-backend` → 实测 exit 1，等于该门禁从未真正执行 | 本机运行输出 | **已修（见 §八）**：路径订正后门禁可执行，并立即暴露 2 处真实撞号 |
| **中** | 目录重命名遗留 6 处旧 `backend/` / `frontend/` 引用，脚本空扫或直接失败 | `scripts/smoke/bw-auth-permission-cache-broadcast-smoke.ps1:46`、`scripts/security/check-perm-coverage.py:27`、`scripts/security/scan-id-ranges.py:31`、`scripts/dev/run-music.ps1:20`、`scripts/dev/whale-stack.ps1:69`、`scripts/dev/whale-stack534.ps1:69` | **已修（见 §八）**，另补 `whale-start-all.ps1` / `whale-hold.ps1` / `encrypt-secret.ps1` 三处更早的 `d:\BlueWhale\...` 硬编码 |
| **中** | 在途改动未提交：登录锁定异常/处理器（3 个 Java 未跟踪）+ `login-lockout.ts` 及其测试（2 个未跟踪），另有 7 个已修改文件 | `git status` | 先跑 auth 单测 + 前端 lint/tsc，再提交 |
| 低 | 权限映射 WARN：契约键 `components` 未被任何菜单种子命中（疑似死代码） | 门禁输出 | 确认后删除或在契约中标注保留 |
| 低 | 内网边界 WARN：`/alert/internal/prometheus` 仅靠自带凭据鉴权 | 门禁输出 | 维持现状并留档，或补共享令牌 |
| 低 | 泛型接口 `/auth/refresh` 仍为 HTTP 200 + 业务码 401（既有约定差异） | 11-09 扫描 | 先定口径再改 |

## 四、任务（待做项）

### 4.1 在途收口（优先）

1. ~~登录失败计数与锁定：补前端 lint/tsc、auth 单测；回填 spec/devlog；提交 7 改 + 5 新增。~~ ✅ 见 §七。
2. ~~文档订正 4 处~~ ✅ 见 §八：
   - `docs/specs/2026-11-11-wave6.22-auth-reauth/spec.md` 状态行改为「已实施」，「未做：未接登录锁定计数」标为已闭环；
   - `docs/design/audit-log.md:30` 改为已由 Wave 6.22 落地；
   - `docs/plans/task-breakdown.md:495` E6 已勾选（2026-10-28 闭环）；
   - `docs/devlog/2026-11-11.md` 6.24 的 mall 分类页待办标为已修（`168f2b1b`）。
3. 复核并清理 `stash@{0}`（2026-11-06 的 `ProcessCcServiceImpl` 及其测试改动），确认无价值后再删，勿直接 drop。

### 4.2 功能遗留

| 来源 | 事项 |
|---|---|
| Wave 6.22 | 基座与其余子应用未接 `onReauthRequired`（仅 admin 有危险操作入口） |
| Wave 6.25 | 其余服务 `WHALE_INTERNAL_TOKEN` 未注入；租户级场景偏好未接；前端 3D 开关未联动 |
| Wave 6.20 | `WHALE_AGENT_LLM_API_KEY` 未配置 → 回答为检索摘要（`degraded=true`） |
| 2026-11-10 遗留 | 告警无用户偏好中心；选人无滚动加载/部门懒加载；8G 内存上限下扩展 profile 一次只能起一组 |
| CRM 前端 | 跟进记录仅新增（无编辑/删除）；公海/营销/行动计划仍手填客户 ID；日期字段未改 DatePicker |
| roadmap | 8.1 文档治理 DOC-01–10；8.5 scene M3 3D；11.3 出差预算超限条件分支；13.10 OA M1A-6 移动端与试点 |
| 五服务总账 | MES 数据范围过滤（依赖 auth `data-scope`）、归档物理清理（依赖 D14）；ERP M3 剩余（计划订单转单/联产品副产品/MES 回写）、M2 批次追溯与财务扩展（D5）；PDM 六项外部依赖；LMS / 党政工会试点复盘；邮箱 MTA |
| OA | M6 安全/性能加固与试点部门验证（运营事项） |

## 五、阻塞

1. **决策阻塞**：D5（ERP 批次/财务扩展）、D2（CRM→ERP 客户主数据映射）、Workflow 条件表达式运行期求值、auth `data-scope`、D14（分区/对象存储）。
2. **外部依赖**：V-09 prod compose 缺 Let's Encrypt 证书；Mailcow 需公网 25/465/587 与 PTR；PDM 六项（CAD 插件、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调）；agent LLM Key；`whale-ai`（8092）不在本仓库；iot 依赖 emqx；map 依赖腾讯/高德 Key。
3. **环境**：OA（9004）/ CRM（9007）当前未起，相关实机链路需先起服。

## 六、建议执行顺序

1. 修 7 处旧目录路径脚本（含号段门禁），重跑门禁恢复覆盖。
2. 收尾在途登录锁定改动并提交（含 5 个未跟踪文件）。
3. 订正 4 处文档失真 + 复核 stash。
4. 再开功能：CRM 三项交互 → OA 移动端/试点 → ERP M3 / MES（需先解决策）。

## 七、收口更新（21:35）

- 第三节「在途改动未提交」**已闭环**：登录锁定前端倒计时（Wave 6.28）连同后端剩余秒数下发一起提交，
  `git status` 干净、`origin/main..HEAD` = 0。
- 第一节的「工作区 7 修改 + 5 未跟踪」是 21:05 扫描时点的快照，保留以便追溯。
- 4.1 第 1 项（补 lint/tsc/单测 + 提交）完成；4.1 第 2 项（文档订正 4 处）→ 已于 §八 完成；4.2 其余项仍待做。
- 第三节「高」项（号段门禁 `check-permission-id-ranges.ps1` 扫旧 `backend/` 路径）**仍未修**，优先级最高：
  该门禁目前等于从未执行过。（→ 已于 §八 闭环）

## 八、收口更新（21:23，本轮执行）

### 需求项

1. 恢复号段门禁的真实覆盖（含脚本路径订正）。
2. 收口 4 处文档失真。
3. 复核 `stash@{0}` 价值。

### 完成项

| 内容 | 证据 |
|---|---|
| 10 个脚本的旧目录路径订正：`check-permission-id-ranges.ps1`、`bw-auth-permission-cache-broadcast-smoke.ps1`、`check-perm-coverage.py`、`scan-id-ranges.py`、`run-music.ps1`、`whale-stack.ps1`、`whale-stack534.ps1`，另补 `whale-start-all.ps1` / `whale-hold.ps1`（`d:\BlueWhale\...`）/ `encrypt-secret.ps1` | `git diff` |
| 号段门禁恢复执行：`PERMISSION-ID-CHECK-PASS`（381 份 SQL / 权限 843 条 / 菜单 246 条 / 基线已知 10 处） | 本机运行 |
| 静态号段扫描：`ERROR 0`，WARN 31（号段未声明类，历史基线） | `scan-id-ranges.py` |
| 文档订正 4 处（6.22 状态与锁定项、audit-log 范围外项、E6 勾选、6.24 待办） | `git diff docs/` |
| `check-doc-consistency.ps1` → `DOC-CONSISTENCY-PASS` | 本机运行 |

### 已修正问题项（门禁恢复后暴露的真实缺陷）

| 问题 | 根因 | 修法 |
|---|---|---|
| `sys:assistant:define` / `sys:assistant:chat` **从未入库**，助手管理/问答接口除超管外一律 403、菜单不出现 | `migration-6.01-assistant.sql` 用 2300/2301，而 2026-11-09 01:09 的 CRM 修复已把 `sys:crm:analytics:*` 挪到 2300–2306；幂等条件 `id=? OR perm_code=?` 让后执行的助手两条 INSERT 整条跳过。更隐蔽的是其授权段按 `p.id IN (2300,2301)` 联查，若在 CRM 之后执行会把 **CRM 权限**授给 tenant_admin | 手工脚本改用 **2330–2331** 且判重键只看 `perm_code`、授权按 `perm_code` 联查、菜单改 MD5 派生 + `(tenant_id,path)` 判重；新增 Flyway `V8__assistant_permission_menu.sql` 为存量库补齐并复制到业务租户 |
| Flyway 新建库里「租户配置」菜单出现两行 | `V6__tenant_config_menu.sql` 固定 `id=37` 写 `/system/tenant-config`，与 doc 的 `/doc/spaces` 撞号；空库中先由 V6 写 37、V7 又按路径写一条 | 新增 `V9__tenant_config_menu_dedupe.sql` 删除 id=37 的重复行；`scripts/permission-id-baseline.txt` 登记 `id:sys_menu:37` 并注明 V6 受 `validate-on-migrate` 保护不得改写 |

### 待做项

1. `stash@{0}` 复核结论：**内容全部过时**，建议丢弃（未执行，等确认）——
   - `ProcessCcServiceImpl` 把现行「上限 100 + 超限显式报错」回退成旧的 `.limit(10)` 静默截断；
   - 其单测删除的正是现行两个用例（12 人双口径不丢人 / 超 100 报错）；
   - 三处文档改动为旧状态文本与 BOM 空白，HEAD 已更新。
2. 决策阻塞与功能遗留同 §四/§五，本轮未动。

