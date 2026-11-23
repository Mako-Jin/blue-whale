# 工作空间待办总账（任务 / 阻塞 / Bug）

| 项 | 内容 |
|---|---|
| 日期 | 2026-11-22 |
| 定位 | **当前未收口事项的唯一汇总入口**。各服务主题计划的细节仍以原计划为准，本表只做横向汇总与状态追踪。 |
| 口径 | 只登记「现在仍存在」的事项；已闭环项进入 §六 已修正问题项或原计划，不在本表重复维护。 |
| 数据来源 | [roadmap.md](roadmap.md) Phase 15、[bluewhale-blueprint-2026Q4.md](bluewhale-blueprint-2026Q4.md)、[risk-register.md](../risk-register.md)、[2026-11-19-3d-capital-master-plan.md](2026-11-19-3d-capital-master-plan.md)、[pay-real-channel-readiness.md](../design/pay-real-channel-readiness.md)、[whale-file-extension-batch7.md](whale-file-extension-batch7.md)、[2026-11-06-five-services-backlog-stepwise.md](2026-11-06-five-services-backlog-stepwise.md)、[2026-11-19-three-admin-backlog.md](2026-11-19-three-admin-backlog.md)、[whale-gateway-backlog.md](whale-gateway-backlog.md)、[devlog/2026-11-21.md](../devlog/2026-11-21.md)、`git status`（2026-11-22） |
| 维护规则 | 每周一更新一次；处理完成即在本表划掉并回填对应主题计划；新增事项必须写来源链接与验收口径。 |

---

## 〇、总览

| 类别 | 数量 | 说明 |
|---|---|---|
| 进行中主线 | 2 | 3D 王城 S7、支付服务收尾 |
| 待做事项 | 26 项（按条目） | 3D 13、支付 6、文件 4、其他服务 8+、网关 2 |
| 阻塞 | 8 项 | 其中 P0 风险 2 项 |
| 已知 Bug / 缺陷 | 3 项 + 2 项限制 | 均非本轮引入 |
| 工作区未提交 | 8 改 + 12 新增 | 测试与覆盖率基线 |

---

## 一、任务

### 1.1 进行中主线

| 主线 | 当前进度 | 下一步 | 来源 |
|---|---|---|---|
| 3D 王城 | S0–S6 已完成，S7 外城进行中（T7.1 乐坊/music、T7.2 影戏院/video、T7.3 清议堂/forum、T7.4 邮驿/mail、T7.5 传信司/im、T7.6 舆图司/map、T7.7 观星台/twin 已交付） | T7.8 智囊院（agent + assistant） | [3D 总纲 §S7](2026-11-19-3d-capital-master-plan.md#L1204-L1216) |
| 支付服务 | 阶段 1–6 全部完成并通过单测与 Checkstyle | 6 项收尾（见 1.2.2） | [支付接入设计 §7](../design/pay-real-channel-readiness.md#L414-L424) |

### 1.2 待做事项

#### 1.2.1 3D 王城（10 项）

| 编号 | 任务 | 服务 | 状态 |
|---|---|---|---|
| ~~T7.7~~ | ~~观星台（3D 场景嵌入）~~ | twin | **已交付（2026-11-23）** |
| T7.8 | 智囊院（对话，贴脸模式） | agent + assistant | 待做 |
| T7.9 | 文库（归档目录） | archive | 待做 |
| T8.1 | 建筑改 InstancedMesh（绘制调用降 50%） | 前端 | 待做 |
| T8.2 | 城外子树进殿时卸载 | 前端 | 待做 |
| T8.3 | 纹理图集合并（显存降 30%） | 前端 | 待做 |
| T8.4 | LOD 远处建筑降模 | 前端 | 待做 |
| T8.5 | 首屏加载优化（≤3s 可操作） | 前端 | 待做 |
| T8.6 | 低档画质路径（≥45fps） | 前端 | 待做 |
| T8.7 | 全城走查与穿模修复 | 前端 | 待做 |
| T8.8 | 错误兜底与埋点 | 前端 | 待做 |

#### 1.2.2 支付服务（6 项）

| # | 待做项 | 验收口径 |
|---|---|---|
| 1 | 归档容量与保留期按合规调整，接入运维告警 | 归档结果不再是「仅落日志」，有可观测指标/告警 |
| 2 | 部署侧下发 `issuer-id`/`issuer-secret`、租户白名单、K8s NetworkPolicy | 跨租户越权 403；未带令牌 403；生产不再使用共享令牌 |
| 3 | 并发/幂等/故障注入集成测试 | MySQL 环境覆盖重复 pay、同 `refundNo` 并发、通知 DEAD、`applyRefund` 超额 |
| 4 | `queryStatus` 扩展为 `queryResult` | 自动收敛时可回填 `channel_trade_no` |
| 5 | `Money` 值对象 + 统计 SQL 按币种分组 | 多币种上线不出现 `SUM` 混算 |
| 6 | auth 补 `sys:pay:reconcile:handle` / `sys:pay:reconcile:run` 权限种子 | 新增权限码可下发并对 `tenant_admin` 授权 |

#### 1.2.3 whale-file 批次 7 收尾（4 项）

| # | 待做项 |
|---|---|
| 1 | 多维配额用量查询接口（个人/租户/桶）与租户管理员视图 |
| 2 | 按租户差异化配额（配额表 + 管理接口），替代全局默认值 |
| 3 | 配额告警（阈值 80%/95%）与用量趋势指标 |
| 4 | 路线图其余项：文件版本、存储迁移、CDN、领域事件、多段 Range、加密、策略热更新 |

#### 1.2.4 其他服务

| 服务 | 待做项 |
|---|---|
| 制造线 M1（最高优先级） | ① 销售订单→生产订单→排产报工→质检→入库→3D 看板全链路纯页面跑通；② 订单状态机每步留痕；③ 3D 看板数量 == 库内数量（对账脚本可验证）；④ 三组压测基线（权限判定 P95 / 订单列表 P95 / 并发报工）；⑤ ≥3 条链路冒烟进 CI |
| 三员分立 | ① 三员账号双人复核启用；② 应用 DB 账号按表收权（`GRANT SELECT, INSERT ON audit_log`）；③ 历史归档批次补签（不可追溯，只能哈希与链值校验）；④ `super_admin` 技术硬旁路无法在线拦截（仅有退役闸门） |
| CRM | 前端 3 项：跟进记录编辑/删除、公海释放免手填 ID、日期字段改 DatePicker；实机联调 |
| ERP | M3 剩余 MRP/CRP/预测增强/生产增强；M2 剩余批次追溯/财务扩展；ERP↔MES 工单下发与完工回传 |
| MES | 数据范围上下文过滤（依赖 auth `data-scope`）、归档物理清理（依赖 D14 分区/对象存储） |
| OA | M1A-6 移动端适配；M6 安全/性能加固与试点部门验证（运营事项） |
| PDM | 6 项外部依赖：CAD 插件本体、3D/Office 转换、ngram/OCR、下载票据、DEPT ACL、真实联调 |
| whale-video | 建库与迁移实跑、实机起服与 M1–M5 冒烟、429/授权到期任务/备份恢复实测、浏览器联调、海报封面、流式网关鉴权方案 |
| whale-music | 浏览器逐页点检 B-09、通知链路实机验证 B-10、`comment-pre-audit` 先审后发切换 |
| whale-gateway | ① 38 条静态路由迁入 Nacos（需运维协调发布）；② DB 覆盖层「停用 ≤60s 生效」真机取证 |

---

## 二、阻塞

| # | 阻塞 | 级别 | 受影响事项 | 解除条件 |
|---|---|---|---|---|
| B1 | 多数服务「实机待环境」，代码通过但未端到端验证（RSK-11） | **P0** | 上线前全部服务 | 分服务排期实机冒烟，按 [network-boundary.md](../design/network-boundary.md) 清单收口 |
| B2 | 国产数据库未实机验证（RSK-12） | **P0** | 私有化交付 | 至少 1 个国产库跑通 auth + 一个业务域 |
| B3 | 本机 Docker 未运行 | 高 | 支付并发/幂等集成测试（待做 3） | 启动 Docker 或用 Testcontainers 替代 |
| B4 | 本机 Nacos 未启动 | 中 | `GET /erp/overview` 真实 HTTP 断言 | 起 Nacos 后按 devlog 命令复核 |
| B5 | 支付部署侧身份与租户白名单未下发 | 高 | 支付内网鉴权「代码侧就绪、部署侧未落地」 | 下发 `issuer-id`/`issuer-secret` + 租户白名单 + NetworkPolicy |
| B6 | 蓝图 §十 五项待拍板 | 高 | M2 排期：目标形态、目标行业、移动端、试点客户、商城去留 | 产品/业务方决策 |
| B7 | 外部集成凭据缺失（RSK-13） | 中 | 地图、邮箱、支付、电子签、短信 | 统一预检作业单，无凭据时 mock/降级并显式标注 |
| B8 | 3D 性能与真实联机无数据（R2/R3/RSK-02） | 中 | 3D M5 准入门槛（全城 30 处可进、1080p ≥30fps） | 真机性能基线 + Nginx/真实网关下 WS 升级重测 |

> 风险登记册中处于「缓解中/开放」的 RSK-01/03/04/06/07/08/09/10/14/15/16/17/18/19 仍在持续跟踪，不单列为阻塞。

---

## 三、Bug / 已知缺陷

| # | 问题 | 影响 | 状态 | 来源 |
|---|---|---|---|---|
| D1 | `check-api-path-drift.py` FAIL 2 处：`POST /contract`（contract.ts）、`POST /scene`（scene.ts） | API 路径漂移门禁无法全绿 | 开放；来自版本化提交 `2022e115` / `ad5c5725`，HEAD 上既有 | [devlog 2026-11-22 S7 T7.1](../devlog/2026-11-21.md) |
| D2 | `world-city-smoke` 3 项断言失败：传送门 9→13、内城门外坐标随 S1 扩容失效 | 冒烟非全绿，易掩盖真实回归 | 开放；属旧断言未随布局更新 | devlog 2026-11-21 |
| D3 | 3D e2e 音频断言缺少负向验证（已在本轮补） | 曾出现「无条件拉起也 PASS」的假绿 | 已修正（T7.1 乐坊） | devlog 2026-11-22 |
| L1 | 表单设计器 E6、工作流设计器 C4 拖拽手势自动化不可复现 | 需人工复核，非功能缺陷 | 长期限制 | roadmap Wave 5.30/5.32 |
| L2 | 源码内无真实 TODO/FIXME | — | 已核实：`whale-backend` 匹配项均为 PM 状态码/测试常量 | 本轮 grep |

---

## 四、工作区未提交（2026-11-22 快照）

**已修改 8 个**：

- `scripts/ci/coverage-baseline.json`
- `whale-common-core`：`BizExceptionTest`、`LocaleSupportTest`
- `whale-monitor`：`HubOverviewAggregatorTest`
- `whale-ops-alert`：`AlertSilenceServiceImplTest`
- `whale-service-file`：`LocalChunkUploadStoreTest`、`StoragePluginRegistryTest`、`LocalStoragePluginTest`

**新增未跟踪 12 项**：

- `whale-monitor`：`config/`、`HubAggregateTest`、`HubHallStateTest`、`HubOverviewControllerTest`、`HubOverviewProviderTest`、`HubProbeTest`
- `whale-ops-audit`：`AuditOverviewStatQueryImplTest`
- `whale-scene`：`ErpManufacturingBoardClientTest`
- `whale-service-file`：`FileShareServiceImplQueryTest`、`FileLibraryStatQueryImplTest`、`StorageTypeBackfillRunnerTest`
- `whale-service-pay`：`PayOverviewStatQueryImplTest`

处理建议：先跑对应模块 `mvn test` + `checkstyle:check`，确认属于覆盖率基线校准后单独提交，不与文档混提。

---

## 五、需求项

1. 建立工作空间唯一的「任务 / 阻塞 / Bug」汇总入口，替代分散在各主题计划中的状态描述。
2. 每条事项必须可追溯到来源文档，并给出验收口径。
3. 阻塞项必须写明解除条件，Bug 项必须写明是否本轮引入。
4. 每周一更新，完成后回填原主题计划，避免双头维护。

## 六、完成项

| 项 | 状态 |
|---|---|
| 纵向状态盘点（3D / 支付 / 文件 / 五服务 / 三员 / 网关 / 风险） | 已完成（2026-11-22） |
| 阻塞与 P0 风险识别 | 已完成（B1–B8） |
| Bug / 已知缺陷核对 | 已完成（D1–D3、L1–L2） |
| 工作区未提交快照 | 已完成（8 改 + 12 新增） |
| 本汇总文档 | 已完成 |

## 七、待做项

即 §一 全部条目，按以下顺序推进：

1. **制造线 M1 一条链**（最高优先级，决定 M1 能否收口）。
2. **3D S7 外城 T7.8–T7.9**（按 T7.8 agent → T7.9 archive）。
3. **支付 6 项收尾**（其中部署侧与集成测试受 B3/B5 约束，可先做 4/5/6）。
4. **文件批次 7 收尾 4 项**。
5. **网关 2 项**、**各服务实机联调**（受 B1 约束）。
6. **3D S8 性能打磨**（受 B8 约束，需先有真机基线）。

## 八、已修正问题项

1. ~~支付阶段 1–6 遗留的三份状态推进逻辑重复~~ → 已由 `PaySettlementService` 收敛。
2. ~~支付 `PayChannelResult` 无法表达「受理中」~~ → 已增加 `PayChannelStatus`。
3. ~~网关熔断半开探测运行时未取证~~ → 已取证（2026-11-11，CLOSED→OPEN→HALF_OPEN→CLOSED 全链路）。
4. ~~网关镜像停在 11-07、缺 auth URL 版式白名单~~ → 重建镜像后恢复（部署时需注意同类漂移）。
5. ~~auth 台账全部 AUTH-xx 与风险项~~ → 2026-11-11 全量销账，转为历史台账。
6. ~~workflow backlog 全部 8 项~~ → 全部结案（加签/选路模拟/版本快照/BPMN-DI/画布能力/泳道/人工复核）。
7. ~~文件批次 1–7、表单扩展批次 1–5~~ → 已完成，剩余项见 §1.2.3。
