# Checklist：Wave 5.8 whale-auth 权限判定缓存场景接入（AUTH-20）

> 关联 spec：[spec.md](./spec.md) ｜ 关联 tasks：[tasks.md](./tasks.md)
> 逐项核验，通过则勾选；未通过需在 tasks.md 新增修复任务后重新核验。

## 一、文档先行

- [x] 1.1 spec.md / tasks.md / checklist.md 已落地，含读路径、写路径、配置、降级链、残余项
- [x] 1.2 缓存 Key 约定与 REQ-ARCH-004（`whale:<域>:<用途>:<标识>`）一致：`whale:cache:auth:user:perm:{userId}`

## 二、代码

- [x] 2.1 `PermissionCacheProperties` 支持 `whale.auth.permission-cache.enabled`（默认 true）与 `.ttl`（默认 5m），并已按既有 `@ConfigurationProperties` 风格注册
- [x] 2.2 `PermissionCache` 读路径：命中直接返回、未命中回源并写缓存、空集合写空串、`enabled=false` 直查，Redis 异常由模板降级不阻断
- [x] 2.3 `PermissionCache` **未注入** `SysRoleService`（无循环依赖），且**未复制**权限码查询逻辑（loader 由调用方传入）
- [x] 2.4 `PermissionCache` 写路径：`evictUser` / `evictUsers` / `evictByRole`（按角色反查用户）/ `evictByPermission`（权限→角色→用户）正确，空集合不产生 `IN ()` 非法 SQL
- [x] 2.5 事务提交后失效：同步激活时走 `afterCommit` 回调，未激活时立即执行
- [x] 2.6 `PermissionInterceptor` 读路径改为经 `PermissionCache`（loader 传 `roleService::listPermissionCodesByUserId`），超管直放行、authorities 命中、拒绝 403 语义不变
- [x] 2.7 失效点全覆盖：角色 `assignPermissions`/`delete`、用户 `assignRoles`/`delete`/`changeStatus`、权限点 `update`/`delete` 共 7 处
- [x] 2.8 未失效项（`SysRoleServiceImpl#update`）已在 spec/tasks 中说明理由并登记
- [x] 2.9 全部生产代码含中文 Javadoc（类型 + 方法，E16 门禁），无魔法值（TTL/缓存名等提为常量或配置）

## 三、测试与门禁

- [x] 3.1 `PermissionCacheTest` 覆盖读路径 5 类分支与写路径 5 个方法、`afterCommit` 两分支
- [x] 3.2 `PermissionInterceptorTest` 改造后用例全绿，放行/拒绝结论不变
- [x] 3.3 3 个 `ServiceImplTest` 构造参数同步更新，并断言新增失效调用（`verify`）
- [x] 3.4 `mvn -f backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify`：全绿 + LINE ≥ 0.40、BRANCH ≥ 0.30
- [x] 3.5 `mvn -f backend/pom.xml checkstyle:check`：全 reactor 0 违规

## 四、留痕与销账

- [x] 4.1 检查报告 `docs/reviews/2026-10-18-wave5.8-auth-permission-cache.md` 逐项对应 [code-review-checklist.md](../../checklists/code-review-checklist.md) 编号，含未通过项与修复记录
- [x] 4.2 `docs/devlog/2026-10-18.md` 追加本波次条目，四节（需求项 / 完成项 / 待做项 / 已修正问题项）齐备
- [x] 4.3 台账销账：`whale-auth-backlog.md` AUTH-20 与 `next-steps.md` 对应条目为「删除线 + ✅」
- [ ] 4.4 变更已 commit 并 push 至 Gitee `origin/main`，工作区干净且 `main == origin/main`

## 五、残余项登记

- [x] 5.1 击穿防护（同 Key 并发一次回源）→ AUTH-19 已登记
- [x] 5.2 多实例缓存失效广播 / Canal 演进、`SysRoleServiceImpl#update` 失效条件、端到端实测已在 tasks.md「待做项」登记

## 六、核验结论

- 核验人：BlueWhale ｜ 日期：2026-10-18 ｜ 结论：**通过**
- 依据：`mvn -f backend/pom.xml -pl :whale-auth -am -Pcoverage-gate clean verify` → **120 run / 0 fail / 0 error / 0 skipped**、`All coverage checks have been met`；`mvn -f backend/pom.xml checkstyle:check` → **28 模块 0 违规**；JaCoCo 行 **0.7184**（551/767）/ 分支 **0.7238**（207/286），`PermissionCache` 行 42/42、分支 24/24（均 100%）。
- **复测提示**：覆盖率复测**须带 `clean`**（`mvn ... clean verify`），否则 JaCoCo `append=true` 会累积历史 `jacoco.exec`，导致覆盖率虚高。
- 4.4（commit + push）待提交后补勾。
