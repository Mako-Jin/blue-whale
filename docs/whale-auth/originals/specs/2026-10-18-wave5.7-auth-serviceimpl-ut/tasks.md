# Tasks：Wave 5.7 whale-auth 零覆盖 ServiceImpl 补测（AUTH-23 收口）

> 关联 spec：[spec.md](./spec.md) ｜ 关联需求：REQ-QA-002、REQ-CICD-001
> 执行顺序即下表顺序；每项完成后按 REQ-QA-002 留痕，未通过构建不得提交。

| # | 任务 | 依赖 | 优先级 | 验收 | 状态 |
|---|---|---|---|---|---|
| [x] S1 | 文档先行：spec/tasks/checklist 落地 | 无 | 高 | 文档一致可追溯 | ✅ 已完成 |
| [x] S2 | `SysRoleServiceImplTest` | S1 | 高 | 覆盖 page/getDetail/create/update/delete/assignPermissions/listXxx 的正常+异常+边界 | ✅ 已完成（21 用例） |
| [x] S3 | `SysPermissionServiceImplTest` | S1 | 高 | 覆盖 page/listAllCodes/getDetail/create/update/delete 的正常+异常 | ✅ 已完成（13 用例） |
| [x] S4 | `SysDeptServiceImplTest` | S1 | 高 | 覆盖 tree/getDetail/create/update/delete 的正常+异常 | ✅ 已完成（8 用例） |
| [x] S5 | `SysUserPermissionServiceImplTest` | S1 | 高 | 覆盖 listByUserId/replaceUserAttrs（空/非空） | ✅ 已完成（2 用例；非空 `saveBatch` 分支记残余项） |
| [x] S6 | 构建验证：`-pl :whale-auth -am test` | S2–S5 | 高 | 新增用例全绿，既有 55 用例不回归 | ✅ 已完成（99 run / 0 fail / 0 error） |
| [x] S7 | 覆盖率门禁：`-Pcoverage-gate verify` | S6 | 高 | `whale-auth` LINE ≥ 0.40、BRANCH ≥ 0.30 | ✅ 已完成（LINE 0.6755、BRANCH 0.6718，`All coverage checks have been met`） |
| [x] S8 | 规范门禁：`mvn checkstyle:check` | S6 | 高 | 全 reactor 0 违规 | ✅ 已完成（0 违规 / BUILD SUCCESS） |
| [x] S9 | 留痕收口：检查报告 `docs/reviews/2026-10-18-auth23-serviceimpl-unit-tests.md` + devlog §27 + §三 #53–#55 | S7、S8 | 中 | 四类信息齐备 | ✅ 已完成 |
| [x] S10 | 台账销账：`whale-auth-backlog.md` AUTH-23、`next-steps.md` T5.19 | S9 | 中 | 删除线 + ✅ | ✅ 已完成 |
| [x] S11 | 自动 commit + push（Gitee origin/main） | S10 | 中 | 工作区干净且 `main == origin/main` | ✅ 已完成 |

## 待做项（本次明确不做，登记备查）
- `saveBatch` 真实批量写路径的集成测试（依赖 Spring 容器/MyBatis 环境）
- 控制器层（Controller）单测
- 5 个 Mapper 的 XML/SQL 覆盖（依赖数据库环境）
- 将 `-Pcoverage-gate` 纳入 CI（Gitee Go）阻断链路（待覆盖率稳定后启用）
