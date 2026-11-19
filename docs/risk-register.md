# 风险登记册

> 定位：**全局风险唯一台账**。散落在各波次报告里的风险在此收敛；波次报告仍可保留细节，但状态以本表为准。
> 维护规则：新增风险给 `RSK-NN` 编号；每次波次复盘更新「状态」；关闭需写结论，不删除行。

## 1. 评级口径

| 维度 | 取值 |
|---|---|
| 概率 | 高 / 中 / 低 |
| 影响 | 高（阻塞上线或数据错误）/ 中（返工或体验下降）/ 低（可控成本） |
| 等级 | **P0** = 高概率×高影响，或已发生且有扩散面；**P1** = 影响高但已有缓解；**P2** = 可控或需持续观察 |

状态：`开放` / `缓解中` / `已接受` / `已关闭`。

## 2. 产品与范围

| ID | 风险 | 概率 | 影响 | 等级 | 状态 | 缓解 / 触发条件 | 关联 |
|---|---|---|---|---|---|---|---|
| RSK-01 | 场景、CRM、行业应用并行开工导致范围膨胀、交付延期 | 中 | 高 | P1 | 缓解中 | 一次只推进一个主域；场景先行、CRM 走需求门禁；3D 不进 MVP | [wave5.77 §10](../../docs/plans/2026-11-03-wave5.77-scene-and-crm.md) |
| RSK-05 | CRM 需求未确认即编码 → 域模型漂移、返工 | 低 | 高 | P2 | 已关闭 | 2026-11-04 门禁通过；边界写入 [ADR-003](design/adr/ADR-003-crm-boundary-and-demand-gate.md)，新增能力先核对边界 | [crm-service.md](design/crm-service.md) §1 |
| RSK-06 | 客户主数据在 CRM 与 ERP 重复，数据不一致 | 中 | 高 | P1 | 缓解中 | CRM 唯一客户主数据；合同只存 `crmCustomerId` 快照；禁止跨库共享表 | ADR-003 §2.2 |
| RSK-17 | 前端微应用数量增长（28 个子应用）导致构建时长与首屏资源膨胀 | 中 | 中 | P2 | 开放 | 共享包复用、按需注册 qiankun 子应用；新增应用须评估体积 | [frontend.md](frontend.md) |

## 3. 场景与 3D

| ID | 风险 | 概率 | 影响 | 等级 | 状态 | 缓解 / 触发条件 | 关联 |
|---|---|---|---|---|---|---|---|
| RSK-02 | 3D 性能不达标（首屏卡顿、低配不可用） | 中 | 高 | P1 | 已接受（3D 未启动） | 2D 优先；预算卡口；FPS<30 降级；资源上限 | [ADR-002](design/adr/ADR-002-scene-2d-first-3d-optional.md) |
| RSK-03 | 场景可见性配置错误导致越权入口 | 中 | 高 | P1 | 缓解中 | DENY 优先；解析时二次过滤；越权矩阵测试 | [scene-service.md](design/scene-service.md) §4.3 |
| RSK-04 | 入口 URL 被用于开放重定向或内网探测 | 中 | 高 | P1 | 缓解中 | 协议/前缀白名单、禁止内网地址、发布校验硬卡口 | scene-service.md §4.4 |
| RSK-09 | 3D 资源版权/许可证缺失 | 中 | 中 | P2 | 开放 | 素材来源与许可证登记；未授权不上线（F-09 解冻条件之一） | [frozen-optional-modules.md](design/frozen-optional-modules.md) |
| RSK-14 | 冻结模块被无门禁解冻，重新引入未定稿依赖 | 低 | 高 | P2 | 缓解中 | F-09 四条件全部满足才解冻；解冻须新开波次并留设计稿 | frozen-optional-modules.md |

## 4. 工程与文档

| ID | 风险 | 概率 | 影响 | 等级 | 状态 | 缓解 / 触发条件 | 关联 |
|---|---|---|---|---|---|---|---|
| RSK-07 | 服务数量增长使 CI 构建与镜像矩阵成本上升 | 高 | 中 | P1 | 缓解中 | 复用 Dockerfile 模板与镜像矩阵；变更镜像检测只构建受影响服务 | [devops.md](devops.md) |
| RSK-08 | 文档只写不校验，状态再次漂移 | 高 | 中 | P1 | 缓解中 | `scripts/check-doc-consistency.ps1` 门禁（版本/迁移覆盖/骨架声明/端口库名） | 本目录脚本 |
| RSK-10 | 权限/菜单编号段冲突导致授权错误 | 中 | 高 | P1 | 缓解中 | 预留段登记；迁移脚本幂等；新增前查号段 | [naming-conventions.md](naming-conventions.md) |
| RSK-18 | 各服务单测/覆盖率不均衡，回归信心不足 | 中 | 中 | P2 | 开放 | 新波次要求定向单测 + 覆盖率门槛；优先补核心链路 | [roadmap.md](plans/roadmap.md) |

## 5. 部署与运维

| ID | 风险 | 概率 | 影响 | 等级 | 状态 | 缓解 / 触发条件 | 关联 |
|---|---|---|---|---|---|---|---|
| RSK-11 | 多数服务处于「实机待环境」，代码通过但未端到端验证 | 高 | 高 | **P0** | 缓解中 | 分服务排期实机冒烟；合并前沿用定向单测 + 静态契约核对；上线前以 [network-boundary.md](design/network-boundary.md) 清单收口 | [roadmap.md](plans/roadmap.md) |
| RSK-12 | 国产数据库（达梦/Kingbase/openGauss/Oracle）未实机验证，SQL 方言差异暴露风险 | 高 | 高 | **P0** | 开放 | 驱动版本已锁定；方言差异设计见 [multi-database.md](design/multi-database.md)；上线前至少完成目标库冒烟 | multi-database.md |
| RSK-13 | 外部集成凭据缺失（地图 Key、邮箱、支付、电子签、短信）导致能力不可用 | 高 | 中 | P1 | 缓解中 | 统一预检作业单；无凭据时走 mock/降级并显式标注 | [ops/external-integration-onboarding.md](ops/external-integration-onboarding.md) |
| RSK-15 | 备份恢复只在部分服务演练，全量恢复不可承诺 | 中 | 高 | P1 | 缓解中 | 按服务补演练剧本；RPO/RTO 写入运维手册 | [devops.md](devops.md) §备份与恢复 |
| RSK-16 | 生产未挂载 TLS 证书即 `up`，或直连业务端口暴露 | 低 | 高 | P1 | 缓解中 | 生产覆盖层缺证书直接失败；端口仅回环；上线必检脚本 | [network-boundary.md](design/network-boundary.md) |
| RSK-19 | 中间件默认口令/弱口令进入非开发环境 | 低 | 高 | P1 | 缓解中 | compose 无弱口令回退（Grafana 未配口令直接失败）；密钥统一注入 | [devops.md](devops.md) §密钥与配置 |

## 6. 复盘记录

| 日期 | 动作 |
|---|---|
| 2026-11-19 | 建立本册；种子来自 wave5.77 §10 + 部署/安全/集成缺口；RSK-05 关闭（门禁通过） |
