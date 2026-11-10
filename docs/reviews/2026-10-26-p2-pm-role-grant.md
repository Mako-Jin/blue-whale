# P2 PM 权限点 172–202 真实业务角色补授（2026-10-26）

> 来源：[2026-10-25 Wave 5.55 部署验证](2026-10-25-wave5.55-deployment-verification.md) 遗留第 2 条：「角色补授 172–202 未在真实业务角色上执行（admin 有 super_admin）」。

## 一、方案

平台租户（tenant_id=1）新建两个真实业务角色并固化为幂等迁移
[migration-5.57-pm-role-grant.sql](../../whale-backend/whale-services/whale-auth/src/main/resources/db/migration-5.57-pm-role-grant.sql)：

| 角色 | id | 授权 | 说明 |
|---|---|---|---|
| `pm_manager` 项目经理 | 17201 | 172–202 全量 31 项 + `sys:workflow:instance:start`(44) | 含项目/成员/状态/流转/迭代/看板管理与删除 |
| `pm_member` 项目成员 | 17202 | 16 项协作子集 + `sys:workflow:instance:start`(44) | 查询/工作项新增编辑流转排序/评论/附件/关联/报表/发起审批；无任何 delete 与项目级管理 |

- `sys_role_permission.id` 采用 `17201000+permissionId` / `17202000+permissionId` 确定性区间，INSERT IGNORE 幂等可重放。
- **配套授权（联调发现的必要修正）**：`sys:pm:item:approval`(202) 的履约依赖 whale-workflow 的 `sys:workflow:instance:start`(44)；缺 44 时 PM 侧放行后被 workflow 403，包装为 500「发起审批失败: 无权限」。两个 PM 角色均补授 44。
- acme 租户的 `tenant_admin` 已于 5.55 联调持全量 31 项，不在本次范围。
- 测试用户：`pm_mgr`(2114557567275360258)→pm_manager、`pm_mem`(2114557568281993218)→pm_member，经 `POST /user` + `POST /user/{id}/roles`（体为 `{"roleIds":[...]}`）创建绑定。

## 二、验证记录（全部经网关 8080，非 super_admin 令牌）

| # | 验证 | 结果 |
|---|---|---|
| 1 | 迁移执行 + 授权计数 | pm_manager=31+1(44)、pm_member=16+1(44) |
| 2 | pm_mgr `/auth/me` | sys:pm:* 共 31 项 |
| 3 | pm_mgr 建/删项目 | code=0 / code=0 |
| 4 | pm_mem `/auth/me` | sys:pm:* 共 16 项 |
| 5 | pm_mem `GET /pm/project/page` | code=0（有 172） |
| 6 | pm_mem `POST /pm/project` | **403 无权限**（无 173） |
| 7 | pm_mem `DELETE /pm/project/{id}` | **403 无权限**（无 175） |
| 8 | pm_mgr 加 pm_mem 为项目 MEMBER | code=0（字段为 `projectRole`，枚举 ADMIN/MEMBER/VIEWER） |
| 9 | pm_mem 建工作项 + 评论 | code=0 / code=0（有 188/193） |
| 10 | pm_mem `DELETE /pm/work-item/{id}` | **403 无权限**（无 190） |
| 11 | pm_mem 发起工作项审批 | code=0，instanceId=2114558914636689410（202+44 生效） |
| 12 | 清理 | 项目/工作项逻辑删除，孤儿工作项复核 0；e2e_item 角色绑定未被误伤 |

## 三、观察项（不阻塞，登记后续）

1. **项目/工作项守卫不对称**：非项目成员（即使有 175/190 权限码）删工作项被 `WorkItemGuard` 403，但删项目放行；且项目逻辑删除后其工作项无法经 API 删除（守卫拦截），本次两个测试孤儿项以 SQL 逻辑删除清理。建议后续：删项目时级联逻辑删除工作项，或守卫对平台超管放行。
2. 审批发起人对 workflow 的依赖仅有 `instance:start`；审批人侧的 `sys:workflow:task:approve` 等由审批人自身角色承担（admin/super_admin 天然覆盖），业务审批人角色化留待后续。
3. `POST /user/{id}/roles` 请求体为 `{"roleIds":[...]}`，裸数组会 500（GlobalExceptionHandler 包装）；本次误调用未造成数据写入（e2e_item 绑定复核一致）。
4. pm_mgr / pm_mem 两个测试用户保留在库中供后续 PM 联调复用（口令 123456，仅本机）。

## 四、结论

PM 权限点 172–202 已在真实业务角色（非 super_admin）落地并经网关级正/负向验证，5.55 遗留第 2 条闭环。
