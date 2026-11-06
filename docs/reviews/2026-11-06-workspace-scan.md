# 工作区扫描与状态清单（2026-11-06）

## 一、需求项

1. 拉取远端最新代码，确认本地与 `origin/main` 同步。
2. 扫描工作区（代码、文档、计划、评审、脚本），输出任务、待办、阻塞、BUG 清单。
3. 核对文档一致性门禁，确认文档与代码状态无失真。

## 二、完成项

- **代码更新**：本地 `c38d19c6` → `origin/main` `253ccb3e`，fast-forward 10 个提交；新增 LMS M0–M6（课程/培训/题库考试/证书学分/集成/看板）与 `whale-party` M-P0/M-P1（独立服务底座 + 党建台账）。
- **文档一致性**：`scripts/check-doc-consistency.ps1` = `DOC-CONSISTENCY-PASS`（已校验 README、architecture、service-catalog）。
- **代码标记扫描**：主代码无未实现 `TODO/FIXME/HACK`；命中项均为模板名（如 `OA_APPROVAL_TODO`）或既有文档用词。
- **环境探测**：MySQL 3306 可连；Redis 6379、Nacos 8848 未监听；9004/9008/9009 无业务服务在跑。

## 三、待做项

| 优先级 | 事项 | 依据 |
|---|---|---|
| P0 | 真实库执行各波迁移并起服冒烟（清单见「阻塞」） | 各波 devlog/评审「待做」 |
| P0 | OA M1A-6 移动端适配与 OA 真实用户试点（新域动工前排期约束） | roadmap 13.10 |
| P1 | `whale-party` M-P2 党建内容（先审后发、可见范围、阅读记录） | roadmap 13.7 |
| P1 | `whale-party` M-P3 工会（会员/慰问/福利/活动/提案） | roadmap 13.8 |
| P1 | `whale-party` M-P4 看板收口（含 M-P1 遗留：换届提醒、参会率） | roadmap 13.9、5.88 §8 |
| P1 | CRM 前端 8113（计划已成稿、代码未落地） | `.trae/documents/crm-frontend-8113-implementation.md` |
| P2 | OA M2–M6（考勤/报销/用车/用章/采购、协同、公文档案、文化人事、报表试点） | roadmap 11.4–11.8 |
| P2 | CRM M3 商务 / M4 售后 / M5 营销 / M6 分析 | roadmap 12.2–12.7 |
| P2 | LMS 收尾：试点复盘、课件上传接 `whale-file` | 5.87 计划 M1/M6 遗留 |
| P2 | PDM 遗留：CAD 插件本体、失败告警、ERP 只读字段、MES 撤回对账页 | PDM M5 评审 |
| P2 | MES 业务口径复盘（D1–D16 按推荐口径实现，未与现场确认） | MES 全景头部 |
| P3 | 8.1 文档治理 DOC-01–DOC-10、`whale-scene` 3D（需 F-09 解冻四条件） | roadmap 8.1/8.5 |

## 四、阻塞

1. **实机验证阻塞**：Redis/Nacos 未启动、业务服务未运行，所有「真实库 + 起服冒烟」无法执行；MySQL 可用。
2. **真实库迁移未执行**：PDM `V13` + `migration-5.80-pdm-part-merge.sql`（941–942/菜单 187）；OA `V5`/`V6` + Workflow `V5`/`V6` + Auth `migration-5.82-oa-m1b.sql`、`migration-5.82-oa-m1b-trip.sql`（1660–1668/370–371）+ Message `migration-5.86-oa-m1b-trip-reminder.sql`；LMS `V1`–`V9` + `migration-5.87-lms-m0.sql`、`migration-5.87-lms-m6.sql`（1900–1910/2000–2002、菜单 450/460）；Party `V1`–`V3` + `migration-5.88-party-m0.sql`、M-P1 迁移（1950–1958/470–474）。
3. **外部依赖阻塞**：OA 预算超限条件分支依赖 Workflow 条件表达式运行期求值；加班费月汇总推 ERP 依赖 M2 薪资契约；报销冲抵预支依赖 M2；PDM 库存可用/预占口径依赖 ERP 预占模型。
4. **范围性阻塞（已决策不做）**：CA/TSA、真实实名意愿、真实短信/邮件、合同供应商沙箱均按 mock 口径；prod compose 实跑缺 TLS 证书。

## 五、BUG 与风险

1. **工作区暂存含回归改动（高）**：拉取前本地未提交改动把 `ProcessCcServiceImpl` 抄送候选上限改回 10 并静默截断，等价于回滚 2026-11-06 已修复的「漏抄送」缺陷；已 `git stash`（`pre-pull-local-edits-20261106`），**不可直接 pop**，建议确认后丢弃。
2. **未跟踪的 CRM 前端计划文档**：`.trae/documents/crm-frontend-8113-implementation.md` 尚未纳入版本库，是否随代码提交需决策。
3. **OA 附件未校验文件归属（中）**：仅登记 `fileId`，存在跨用户引用风险；需文件服务补上传者/租户校验。
4. **OA 通知同步发送（中）**：消息服务抖动会拉长审批接口耗时；建议改为事务提交后异步投递。
5. **人工催办 1 小时频控覆盖待复核（低）**：M1A-4 验收口径是否完整覆盖未确认。
6. **`/auth/refresh` 返回 HTTP 200 + 业务码 401（低，遗留）**：属接口约定差异，需先确认口径再改。
7. **JaCoCo 零测试模块 skip 盲区（观察项）**：`whale-common-core` / `whale-common-domain` / `whale-auth-sdk` 无测试时跳过覆盖率检查。

## 六、已修正问题项

- 本轮无代码修复；扫描过程中确认 2026-11-06 已修复项仍在主线：抄送候选静默截断（`limit(10)` → 上限 100 + 显式报错）、文档状态对账、MES/PDM/OA 全景状态刷新。
