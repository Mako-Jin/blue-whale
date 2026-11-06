# 服务与端口清单（Service Catalog）

> 用途：**全平台端口 ↔ 服务 ↔ 组件**的唯一权威清单。看端口即知对应哪个服务，看服务即知其分组、库、对外前缀与职责。
> 关联需求：REQ-ARCH-005（新增）、REQ-ARCH-004（命名规范，[naming-conventions.md](naming-conventions.md) §八）
> 最后更新：2026-11-05（**Wave 5.80 PDM M5「集成收口与对账」落地**：`whale-pdm` 保持 9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106；新增 `V12__integration_sync.sql` 2 张表 `pdm_int_sync_log`/`pdm_int_reconcile`（共 **33 张**），唯一键 `uk_pdm_int_sync_key(tenant_id, channel, idempotency_key)`、`uk_pdm_int_reconcile(tenant_id, channel, biz_type, biz_no, version_no)`；对外新增 `/pdm/integration/cad/checkin`、`/pdm/integration/cad/ebom-import`、`/pdm/integration/part/{partId}/sync`、`/pdm/integration/sync/page`、`/pdm/integration/sync/{id}/retry`、`/pdm/integration/reconcile/**`（page/bom/route/oa/notify/repush/compensate）；ERP 侧新增内网 `POST /erp/internal/item/upsert`（按 `pdmPartNo` 建/改物料），网关 internal-deny 增 `/erp/internal/**`；权限 990–997、菜单 202–203；CSV/日志幂等键固定（`CAD:CHECKIN:`/`CAD:EBOM:`/`ERP:PART:`/`MES:ROUTE:`/`OA:COMPENSATE:`）；PDM 197 单测、ERP 466 单测全绿，前端 lint/build 通过；见 [M5 检查报告](reviews/2026-11-05-wave5.80-pdm-m5-integration.md)）｜ 2026-11-05（**Wave 5.80 PDM M2-2「变更申请 ECR」落地**：`whale-pdm` 保持 9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106；新增 `V7__change_request.sql` 1 张表 `pdm_chg_request`（变更原因/范围/影响对象 JSON/状态机/发起人/审批与关闭字段，唯一键 `uk_chg_request_no`）；对外新增 `/pdm/change/request/**` 9 个端点（分页/详情/新建/修改/删除/提交/审批/驳回/关闭）；权限 950–955、菜单 190、角色 21105 `pdm_change_manager`（`migration-5.80-pdm-change.sql`，幂等）；前端 8106 增变更管理页；单测 9 项；见 [M2 检查报告](reviews/2026-11-05-wave5.80-pdm-m2.md)）｜ 2026-11-05（**Wave 5.78 ERP M2 剩余 B 第七期「客户信用占用查询」落地**：不新增表；新增只读端点 `GET /erp/scm/sales-order/credit-usage?customerId=`，返回未结应收、在途预占、已占用与可用额度（额度 0 返回不限，超额返回负数与标记）；权限 541（`sys:erp:credit-usage:view`），四角色只读，无新菜单；前端 8102 销售订单新增弹窗与客户主数据「客户」页签均可查询；见 [M2 剩余 B 第七期检查报告](reviews/2026-11-05-wave5.78-erp-m2-credit-usage.md)）｜ 2026-11-05（**Wave 5.78 ERP M2 剩余 B 第六期「信用在途预占」落地**：无新表、无新端点、无新权限；销售订单审核校验改为「未结应收 + 在途预占 + 本单金额」，发货校验改为「未结应收 + 在途预占」；在途预占 = `APPROVED`/`PARTIALLY_SHIPPED` 订单未发货金额，发货转应收时同额释放，避免多单并发审核绕过额度；见 [M2 剩余 B 第六期检查报告](reviews/2026-11-05-wave5.78-erp-m2-credit-reservation.md)）｜ 2026-11-05（**Wave 5.78 ERP M2 剩余 B 第五期「采购比价 + 价格趋势」落地**：不新增业务表（仍 **68 张**）；在既有 `/erp/scm/price-list/**` 下新增 2 个查询端点——`/compare`（按物料 + 取价日期返回各供应商当前采购价、最近成交价、最低价标记与差额）与 `/trend`（按物料 + 统计月数 1–24 返回加权均价/最低/最高/成交笔数）；数据来源为 `erp_scm_price_list`（`PURCHASE` 且有效期内）与有效采购订单（`APPROVED/PARTIALLY_RECEIVED/RECEIVED/CLOSED`），纯查询计算、无新迁移；权限 539–540（`sys:erp:price-compare:compare` / `:trend`），复用角色 25112 `erp_scm`，无新菜单；前端 8102 价格表页新增「供应商比价」「价格趋势」两页签；见 [M2 剩余 B 第五期检查报告](reviews/2026-11-05-wave5.78-erp-m2-price-compare.md)）｜ 2026-11-05（**Wave 5.78 ERP M2 剩余 B 第四期「安全库存 + 缺料预警 + 呆滞分析」落地**：新增 `V12__scm_safety_stock.sql` **1 张**表（共 **68 张**）——`erp_scm_safety_stock`（仓库 + 物料、安全库存量、最高库存量、备注），唯一键 `uk_safety_stock(tenant_id, warehouse_id, item_id)`；对外新增 `/erp/scm/safety-stock/**` 6 个端点（分页/详情/新建/更新/删除/缺料预警与呆滞分析）；权限 533–538、菜单 103，复用角色 25112 `erp_scm`；缺料预警按安全库存与实际余额比较只返回缺口 > 0 项，呆滞分析按余额 > 0 且最近出库早于阈值（默认 90 天，从未出库恒计入）；批次追溯因需 ALTER 既有库存表，推迟单独迁移窗口；前端 8102 增安全库存页（配置/缺料预警/呆滞分析三页签）；见 [M2 剩余 B 第四期检查报告](reviews/2026-11-05-wave5.78-erp-m2-safety-stock.md)）｜ 2026-11-05（**Wave 5.78 ERP M2 剩余 B 第三期「价格表」落地**：新增 `V11__scm_price_list.sql` **1 张**表（共 **67 张**）——`erp_scm_price_list`（价格类型 `SALES/PURCHASE`、往来方、物料、单价、币种、有效期、启停），唯一键 `uk_price_list(tenant_id, price_type, partner_id, item_id)`；对外新增 `/erp/scm/price-list/**` 6 个端点（分页/详情/新建/更新/启停/取价）；权限 527–532、菜单 102，复用角色 25112 `erp_scm`；销售订单、采购订单与销售报价建单时行单价为空则按单据日期取价，取不到抛 `PRICE_LIST_NOT_FOUND`；前端 8102 增价格表页（销售价/采购价页签）；见 [M2 剩余 B 第三期检查报告](reviews/2026-11-05-wave5.78-erp-m2-price-list.md)）｜ 2026-11-05（**Wave 5.78 ERP M2 剩余 B 第二期「采购申请 + 销售报价」落地**：新增 `V10__scm_requisition_quotation.sql` **4 张**表（共 **66 张**）——采购申请头/行、销售报价头/行，唯一键 `uk_pa_doc_no`/`uk_pa_line`/`uk_sq_doc_no`/`uk_sq_line`；对外新增 `/erp/scm/purchase-requisition/**`、`/erp/scm/sales-quotation/**` 各 6 个端点（分页/详情/建单/审核或接受/转单/取消）；权限 515–526、菜单 100–101，复用角色 25112 `erp_scm`；采购申请 `DRAFT → APPROVED → CONVERTED`、销售报价 `DRAFT → ACCEPTED → CONVERTED`，转单复用既有订单服务生成草稿订单并回写 `convertedOrderNo`，重复转单拒绝；前端 8102 增采购申请、销售报价两页；见 [M2 剩余 B 第二期检查报告](reviews/2026-11-05-wave5.78-erp-m2-requisition-quotation.md)）｜ 2026-11-04（**Wave 5.78 ERP M2 剩余 B 第一期「采购退货 + 销售退货」落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V9__scm_returns.sql` **4 张**退货表（共 **62 张**）——采购退货头/行、销售退货头/行，唯一键 `uk_pr_doc_no`/`uk_sr_doc_no`/`uk_pr_line`/`uk_sr_line`；对外新增 `/erp/scm/purchase-return/**`、`/erp/scm/sales-return/**` 各 5 个端点（分页/详情/建单/过账/取消）；权限 507–514、菜单 98–99，复用角色 25112 `erp_scm`；采购退货按源订单单价红字出库并冲减相应供应商未核销应付，销售退货按当前移动加权均价红字入库并冲减相应客户未核销应收（不足整单拒绝 `RETURN_OFFSET_EXCEEDED`），过账写 `OUT`/`IN` 流水（`sourceType = PURCHASE_RETURN / SALES_RETURN`）并生成红字凭证（采购借应付/贷存货，销售借收入/贷应收，成本分录借存货/贷成本）；可退数量按源订单行累计校验（草稿计入、取消排除）；前端 8102 增采购退货、销售退货两页；见 [M2 剩余 B 第一期检查报告](reviews/2026-11-04-wave5.78-erp-m2-returns.md)）｜ 2026-11-04（**Wave 5.78 ERP M6「数据分析与报表中心」落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V8__bi.sql` **5 张**分析表（共 **58 张**）——驾驶舱快照、报表定义、报表计划、预警规则、预警事件，另建 **5 个只读事实视图**（销售/成本/库存/应收/质量）；对外新增 `/erp/bi/cockpit`（查询/刷新/失效）、`/erp/bi/cube`、`/erp/bi/report/**`（定义/计划/执行/CSV 导出/手动执行）、`/erp/bi/alert/**`（规则/事件/评估/确认/关闭）；权限 495–506、角色 25116 `erp_bi`、菜单 94–97；驾驶舱按 `(tenant_id, period_code, stat_date)` 唯一快照，刷新先置旧快照 `STALE` 再 upsert，利润 = 收入 − 成本；多维维度 `CUSTOMER/ITEM/WAREHOUSE` × 指标 `REVENUE/QUANTITY/ORDER_COUNT/COST`；预警 `STOCK_LOW`/`RECEIVABLE_OVERDUE`/`QUALITY_OPEN_NCR` 按唯一键去重，`BUDGET_EXCEEDED` 因预算模块未落地跳过并说明；前端 8102 增经营驾驶舱、多维分析、报表中心、预警中心四页；单测 363 项、行覆盖 49.3%、分支 46.0%；见 [M6 检查报告](reviews/2026-11-03-wave5.78-erp-m6.md)）｜ 2026-11-04（**Wave 5.78 ERP M5 第一期「质量与设备」落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V7__qc.sql` **11 张**质量与设备表（共 **53 张**）——检验单/检验项、不合格品单、质量成本、设备档案、保养计划、保养工单、OEE 记录、量具台账、校准计划、校准记录；对外新增 `/erp/qc/inspection/**`、`/erp/qc/ncr/**`、`/erp/qc/quality-cost/page`、`/erp/qc/equipment/**`、`/erp/qc/maintenance/**`、`/erp/qc/oee/**`、`/erp/qc/metrology/**`；权限 475–494、角色 25115 `erp_qc`、菜单 91–93；检验 `DRAFT→INSPECTING→PASSED/FAILED`，全项有结论才可判定，判定 `FAILED` 幂等生成 NCR 并回写 `ncr_id`，处置有金额自动派生质量成本（`SCRAP→SCRAP`、其余→`REWORK`）；OEE 以 `(tenant_id, equipment_no, record_date)` 唯一键 upsert，三率封顶 1；校准 `PASS` 才回写量具日期并顺延计划，过期量具创建检验单抛 `TOOL_EXPIRED(40930)`；前端 8102 增检验与不合格品、设备与保养、量具与校准三页（页签合并）；单测 339 项、行覆盖 51.2%、分支 48.5%；见 [M5 检查报告](reviews/2026-11-03-wave5.78-erp-m5.md)）｜ 2026-11-04（**Wave 5.78 ERP M4 第一阶段「轻量人力」落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V6__hr.sql` **9 张**人力表（共 **42 张**）——组织单元、员工档案、入转调离、考勤汇总、薪资批次、工资条、绩效周期、KPI、考核结果，并幂等写入科目 2241「其他应付款」；对外新增 `/erp/hr/org`、`/erp/hr/employee/**`、`/erp/hr/attendance/**`、`/erp/hr/payroll/**`、`/erp/hr/performance/**`；权限 456–474、角色 25114 `erp_hr`、菜单 87–90；薪资 `DRAFT→CALCULATED→APPROVED→PAID→CLOSED`，审批生成计提凭证（借 6602 / 贷 2211）、发放生成发放凭证（借 2211 / 贷 1002、2221、2241，`sourceType = PAYROLL_ACCRUAL / PAYROLL_PAYMENT` 幂等）；社保 10.5%、公积金 7%、月度 7 级累进个税 5000 起征点均配置化；前端 8102 增组织与员工、考勤汇总、薪资批次、绩效管理四页；单测 279 项、行覆盖 55.9%、分支 52.2%；见 [M4 检查报告](reviews/2026-11-03-wave5.78-erp-m4.md)）｜ 2026-11-04（**Wave 5.80 PDM 全景规划登记（零代码）**：`whale-pdm` 保持 9003 / `/pdm/**` / `bluewhale_pdm` / 前端 8106，不拆微服务；内部规划 Document/PartBom/Change/Process/Collab 五上下文 + Integration 适配层；7 大模块 28 子模块差距矩阵、V2–V7 迁移草案、权限 900–1099 / 菜单 180–219 / 角色 21103–21106 预留；首切片 M0 图文档中心 + M1A 零件库/EBOM 发布契约（解除 ERP MRP 的 BOM 快照阻塞）；详见 [Wave 5.80 计划](plans/2026-11-04-wave5.80-pdm-panorama.md) 与 [PDM 设计稿](design/pdm-module-panorama.md)）｜ 2026-11-04（**Wave 5.79 MES 全景规划登记（零代码）**：`whale-mes` 保持 9002 / `/mes/**` / `bluewhale_mes` / 前端 8105，不拆微服务；内部规划 masterdata + Dispatch/Execution/Trace/Quality/Equipment/Collect/Workforce 8 上下文；七大模块 35 子模块差距矩阵、65 张新增表草案（V2–V7）、权限 1100–1399 / 菜单 240–299 / 角色 20703–20707 预留；首切片 M0 车间主数据 + M1 调度执行；详见 [Wave 5.79 计划](plans/2026-11-04-wave5.79-mes-panorama.md) 与 [MES 设计稿](design/mes-module-panorama.md)）｜ 2026-11-04（**Wave 5.78 ERP M3 第一阶段「需求预测 + 主生产计划 + 生产订单」落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V5__manufacturing.sql` **6 张**生产计划表（共 **33 张**）——预测头/行、MPS 头/行、生产订单头、生产用料行，并幂等写入科目 5001「生产成本」；对外新增 `/erp/mfg/forecast/**`、`/erp/mfg/mps/**`、`/erp/mfg/production/**` 三组端点；权限 441–455、角色 25113 `erp_mfg`、菜单 84–86；领料扣减领料仓并累计材料成本、完工按「累计材料成本 ÷ 计划量」入库并生成领料/完工两张凭证（`sourceType = PRODUCTION_ISSUE / PRODUCTION_RECEIPT` 幂等）；前端 8102 增需求预测、主生产计划、生产订单三页；单测 198 项、行覆盖 57.6%、分支 51.8%；MRP/CRP 留待 M3 剩余，见 [M3 检查报告](reviews/2026-11-03-wave5.78-erp-m3.md)）｜ 2026-11-04（**Wave 5.78 ERP M2 剩余 A「库存调拨 + 库存盘点」落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V4__scm_stock_ops.sql` **4 张**供应链表（共 27 张）——调拨头/行、盘点头/行，并幂等写入科目 1901「待处理财产损溢」；对外新增 `/erp/scm/transfer/**`、`/erp/scm/stocktake/**` 两组端点；权限 433–436（调拨）/ 437–440（盘点）、菜单 82–83；盘点过账按净差异自动生成待处理财产损溢凭证（`sourceType = STOCKTAKE` 幂等）；前端 8102 增库存调拨、库存盘点两页；单测 152 项、行覆盖 57.1%、分支 51.7%；见 [M2 剩余 A 检查报告](reviews/2026-11-03-wave5.78-erp-m2-stock-ops.md)）｜ 2026-11-04（**Wave 5.78 ERP M2 供应链核心落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102；新增 `V3__scm.sql` **6 张**供应链表（共 23 张）——采购订单头/分录、销售订单头/分录、库存流水、库存余额，并为凭证/应收/应付补 `(tenant_id, source_type, source_id)` 唯一键实现自动记账幂等；对外新增 `/erp/scm/purchase-order/**`、`/erp/scm/sales-order/**`、`/erp/scm/stock/**` 三组端点；权限 420–424（采购）/ 426–430（销售）/ 431–432（库存，425 预留）、角色 25112 `erp_scm`、菜单 79–81；前端 8102 增采购订单、销售订单、库存台账三类页面；单测 127 项、行覆盖 56.7%、分支 51.6%；财务扩展（资产/成本/预算/资金）与退货/调拨/盘点留待 M2 剩余，见 [M2 检查报告](reviews/2026-11-03-wave5.78-erp-m2.md)）｜ 2026-11-04（**Wave 5.78 ERP M0 + M1 落地**：`whale-erp` 保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102 的模块化单体，新增 **13 张**主数据与财务表（共 17 张）——科目/会计期间/客户/仓库/计量单位/币种/税码 + 凭证与分录/应收应付/核销流水；对外新增 `/erp/master-data/**`、`/erp/finance/voucher/**`、`/erp/finance/**`、`/erp/finance/report/**` 四组端点；权限 355–367 / 375–387、角色 25110 `erp_finance` / 25111 `erp_masterdata`、菜单 63–66；前端 8102 增主数据、凭证、应收应付、报表四类页面；单测 84 项、行覆盖 55.6%、分支 56.9%。供应链/生产/人力/质量/分析仍按 M2–M6 规划，见 [M0+M1 检查报告](reviews/2026-11-03-wave5.78-erp-m0-m1.md)）｜ 2026-11-03（**Wave 5.78 ERP 全景规划登记（零代码）**：确认 `whale-erp` **不拆微服务**，保持 9001 / `/erp/**` / `bluewhale_erp` / 前端 8102 的模块化单体，内部按 MDM、Finance、SCM、Manufacturing、HR、Quality、Analytics 七上下文分区；全景 M0–M6 分期与差距矩阵见 [实施计划](plans/2026-11-03-wave5.78-erp-panorama.md) 与 [设计稿](design/erp-module-panorama.md)，需求登记 REQ-FUNC-036..042 / REQ-ARCH-018 / REQ-SEC-028）｜ 2026-11-03（**Wave 5.77 场景 M2 与 CRM 门禁骨架落地**：`whale-scene` 8097 / 前端 8112 / `/scene/**` / `bluewhale_scene` 的 M1/M2 2D 能力（CRUD、发布、导航、可见性）已编码并接入网关、权限/菜单与基座；`whale-crm` 9007 / `/crm/**` / `bluewhale_crm` 仅落地可启动门禁骨架（`/crm/meta`），不建业务 CRUD、不注册菜单、不建前端。3D 保持 M3 可选，见 [Wave 5.77 计划](plans/2026-11-03-wave5.77-scene-and-crm.md)）｜ 2026-11-03（**Wave 5.77 规划登记**：新增 `whale-scene` 8097 / 前端 8112 / `/scene/**` / `bluewhale_scene` 与 `whale-crm` 9007 / 前端 8113 / `/crm/**` / `bluewhale_crm`）｜ 2026-11-01（**Wave 5.77 视频平台 M0–M5**：后端 **8098** / 前端 **8114** / `/video/**` / `bluewhale_video`；短视频 / 视频 / 系列 / 电影 / 片单 / 榜单 / 运营 + 越权矩阵、上传安全、观看历史清理、SLO-9/10/11，单测 151/151，见 [M5 检查报告](reviews/2026-11-01-wave5.77-video-m5.md)）｜ 2026-10-31（**Wave 5.75 M5 安全与上线硬化**：越权矩阵统一不泄露存在性、上传白名单、版权来源必填与下架留痕、网关票据 / 点赞限流、SLO-6/7/8 + Grafana 音乐面板、50 并发流式首字节 P95 216–220ms、运维手册 `ops/music-service-ops.md`、CI 镜像矩阵接入，见 [M5 检查报告](reviews/2026-10-31-wave5.75-music-m5.md)）｜ 2026-10-31（**Wave 5.75 M4 互动**：评论发表/父子回复、审核队列与通过/隐藏、我的音乐、可选通知，见 [M4 检查报告](reviews/2026-10-31-wave5.75-music-m4.md)）｜ 2026-10-31（**Wave 5.75 M3 歌单与发现**：用户/官方歌单、首页聚合、热门/新歌榜、搜索、播放计数回刷，见 [M3 检查报告](reviews/2026-10-31-wave5.75-music-m3.md)）｜ 2026-10-31（**Wave 5.75 M2 播放与音频流**：播放票据（Redis / TTL 120s / 三要素绑定）、`/music/stream/**` Range 流式（200 / 206 / 416）、进度 / 最近播放 / 收藏幂等、全局播放器（播放条 / 队列 / 歌词），冒烟 `SMOKE-ALL-PASS`，见 [M2 检查报告](reviews/2026-10-31-wave5.75-music-m2.md)）｜ 2026-10-31（**Wave 5.75 M1 前端落地**：admin 曲库 / 歌手 / 专辑 / 标签页与 `whale-music` 子应用（公开曲库 / 上传 / 我的作品）已落盘并接入基座；权限号修正为 **281–303**，见 [M1 检查报告](reviews/2026-10-31-wave5.75-music-m1.md)）｜ 2026-10-31（**Wave 5.75 M0 骨架**：`whale-music` 8096 / `/music/**` / `bluewhale_music` 11 表 DDL、网关路由与权限/菜单种子（281–303 / 39–44）落地，见 [检查报告](reviews/2026-10-31-wave5.75-music-m0.md)）｜ 2026-10-31（**Wave 5.75 音乐平台规划**：新增 `whale-music` 8096 / 前端 8111 / `/music/**` / `bluewhale_music`，见 [实施计划](plans/wave5.75-music-platform.md)）｜ 2026-10-29（**Wave 5.74 M0–M5 交付**：`whale-doc` 8095 / 前端 8110 / `/doc/**` / `bluewhale_doc` 全量业务与运维能力落地，见 [实施计划](plans/2026-10-29-wiki-doc-service.md)）｜ 2026-10-29（**Wave 5.74 M1 骨架**：新增 `whale-doc` 8095、前端 `whale-doc` 8110、网关 `/doc/**`、库 `bluewhale_doc`、权限/角色/菜单迁移）｜ 2026-10-28（**Wave 5.69–5.70**：ERP 新增供应商主数据；`whale-message` M1 增模板管理/失败重试/站内信收件箱；**F-05 结案** `whale-config` 冻结且移除 `/config/**` 路由）｜ 维护：BlueWhale

---

## 〇、口径说明

| 项 | 说明 |
|---|---|
| 数据来源 | 实测核对：各服务 `src/main/resources/application.yml` 的 `server.port` / `spring.application.name` / `datasource.url`、`whale-gateway/application.yml` 路由、各前端 `vite.config.ts` 的 `server.port`、`infrastructure/docker/docker-compose*.yml`、本机 `Get-NetTCPConnection` 实监听核验 |
| 「本机状态」含义 | **已运行**＝本机实测监听/注册过；**可启动**＝模块已具备启动条件、尚未在本机启动；**规划**＝仅设计登记，无实现 |
| 端口分段 | 见 §七；服务端口一律用 `${SERVER_PORT:xxxx}` 或 `${server.port:xxxx}` 外置可覆盖 |
| 命名铁律 | `模块目录 == artifactId == spring.application.name == Nacos 服务名 == whale-<域>`（[naming-conventions.md](naming-conventions.md) §三） |

---

## 一、后端服务清单（27 个已落地：Java 26 + Python 1；无规划服务）

| # | 服务名（Spring / Nacos） | 所属分组 | 模块目录 | 端口 | 对外一级前缀 | 网关路由 | 数据库（`bluewhale_<域>`） | 启动类 | 职责 | 本机状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `whale-gateway` | whale-services | `whale-services/whale-gateway` | **8080** | ——（唯一入口） | —— | 无（仅 Redis 存放黑名单） | `WhaleGatewayApplication` | 统一入口：路由转发、JWT 鉴权、令牌黑名单校验、TraceId | **已运行**（已在 Nacos 注册） |
| 2 | `whale-service-auth` | whale-services | `whale-services/whale-service-auth`（starter 模块 `whale-service-auth-starter`） | **8081** | `/auth/**`（另自持 `/user`、`/dept`、`/role`、`/menu`、`/permission`） | ✅ `lb://whale-service-auth` | **`bluewhale_auth`**（13 表，已建） | `WhaleAuthApplication` | 认证与权限中心：登录/刷新/注销、RBAC+ABAC、用户/部门/角色/菜单/权限 | **已运行**（已在 Nacos 注册） |
| 3 | `whale-job` | whale-services | `whale-services/whale-job` | **8082** | `/job/**` | ✅ `lb://whale-job` | **`bluewhale_job`**（已建库） | `WhaleJobApplication` | 定时任务调度 | 可启动 |
| 4 | `whale-workflow` | whale-services | `whale-services/whale-workflow` | **8083** | `/workflow/**` | ✅ `lb://whale-workflow` | **`bluewhale_workflow`**（已建库） | `WhaleWorkflowApplication` | 工作流引擎（审批流） | 可启动 |
| 5 | `whale-form` | whale-services | `whale-services/whale-form` | **8084** | `/form/**` | ✅ `lb://whale-form` | **`bluewhale_form`**（已建库） | `WhaleFormApplication` | 动态表单 | 可启动 |
| 6 | `whale-file` | whale-services | `whale-services/whale-service-file`（启动模块 `whale-service-file-web`） | **8085** | `/file/**` | ✅ `lb://whale-file` | **`bluewhale_file`**（已建库） | `WhaleFileApplication` | 文件上传下载、附件管理；下载 / 预览 / 内网内容接口支持 HTTP Range（206 / 416 + `Content-Range` + `ETag`） | 可启动 |
| 7 | `whale-pay` | whale-services | `whale-services/whale-pay` | **8086** | `/pay/**` | ✅ `lb://whale-pay` | **`bluewhale_pay`**（4 表：`pay_order`/`pay_transaction`/`audit_log`/`api_log`） | `WhalePayApplication` | 支付与结算：支付单、mock 渠道支付/退款、渠道流水与对账 | 可启动（2026-10-28 本地起服验证通过） |
| 8 | `whale-erp` | whale-apps | `whale-apps/whale-erp` | **9001** | `/erp/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_erp`**（**68 表**：基线 4 = `erp_item`/`erp_supplier`/`audit_log`/`api_log`；V2 13 = `erp_account`/`erp_accounting_period`/`erp_customer`/`erp_warehouse`/`erp_uom`/`erp_currency`/`erp_tax_code`/`erp_fin_voucher`/`erp_fin_voucher_line`/`erp_fin_receivable`/`erp_fin_receivable_settlement`/`erp_fin_payable`/`erp_fin_payable_settlement`；V3 6 = `erp_scm_purchase_order`/`erp_scm_purchase_order_line`/`erp_scm_sales_order`/`erp_scm_sales_order_line`/`erp_scm_stock_movement`/`erp_scm_stock_balance` + 自动记账幂等键 `uk_voucher_source`/`uk_receivable_source`/`uk_payable_source`；V4 4 = `erp_scm_transfer`/`erp_scm_transfer_line`/`erp_scm_stocktake`/`erp_scm_stocktake_line` + 单据唯一键 `uk_transfer_doc_no`/`uk_stocktake_doc_no`；V5 6 = `erp_mfg_forecast`/`erp_mfg_forecast_line`/`erp_mfg_mps`/`erp_mfg_mps_line`/`erp_mfg_production_order`/`erp_mfg_production_material` + 唯一键 `uk_forecast_batch`/`uk_forecast_line`/`uk_mps_plan_no`/`uk_mps_line`/`uk_mo_order_no`/`uk_mo_material`；V6 9 = `erp_hr_org_unit`/`erp_hr_employee`/`erp_hr_employment_change`/`erp_hr_attendance_summary`/`erp_hr_payroll_run`/`erp_hr_payslip`/`erp_hr_performance_cycle`/`erp_hr_kpi`/`erp_hr_appraisal` + 唯一键 `uk_hr_org_code`/`uk_hr_employee_no`/`uk_hr_change_no`/`uk_hr_attendance`/`uk_payroll_period`/`uk_payroll_run_no`/`uk_payslip_employee`/`uk_hr_cycle_no`/`uk_hr_kpi_name`/`uk_hr_appraisal`；V7 11 = `erp_qc_inspection_order`/`erp_qc_inspection_item`/`erp_qc_ncr`/`erp_qc_quality_cost`/`erp_qc_equipment`/`erp_qc_maintenance_plan`/`erp_qc_maintenance_order`/`erp_qc_oee_record`/`erp_qc_measuring_tool`/`erp_qc_calibration_plan`/`erp_qc_calibration_record` + 唯一键 `uk_qc_order_no`/`uk_qc_item_line`/`uk_qc_ncr_no`/`uk_qc_cost_no`/`uk_qc_equipment_no`/`uk_qc_maint_plan_no`/`uk_qc_maint_order_no`/`uk_oee_equipment_date`/`uk_qc_tool_no`/`uk_qc_cal_plan_no`/`uk_qc_cal_record_no`；V8 5 = `erp_bi_cockpit_snapshot`/`erp_bi_report_def`/`erp_bi_report_schedule`/`erp_bi_alert_rule`/`erp_bi_alert_event` + 唯一键 `uk_bi_cockpit_period_date`/`uk_bi_report_no`/`uk_bi_schedule_no`/`uk_bi_alert_rule_no`/`uk_bi_alert_event_scope`，并建只读事实视图 `v_erp_bi_sales_fact`/`v_erp_bi_cost_fact`/`v_erp_bi_stock_fact`/`v_erp_bi_receivable_fact`/`v_erp_bi_quality_fact`；V9 4 = `erp_scm_purchase_return`/`erp_scm_purchase_return_line`/`erp_scm_sales_return`/`erp_scm_sales_return_line` + 唯一键 `uk_pr_doc_no`/`uk_pr_line`/`uk_sr_doc_no`/`uk_sr_line`；V10 4 = `erp_scm_purchase_requisition`/`erp_scm_purchase_requisition_line`/`erp_scm_sales_quotation`/`erp_scm_sales_quotation_line` + 唯一键 `uk_pa_doc_no`/`uk_pa_line`/`uk_sq_doc_no`/`uk_sq_line`；V11 1 = `erp_scm_price_list` + 唯一键 `uk_price_list`；V12 1 = `erp_scm_safety_stock` + 唯一键 `uk_safety_stock`；幂等种子科目 7001–7023 / 期间 7101–7112 / 客户 7201–7202 / 仓库 7301 / UOM 7401–7404 / 币种 7501–7502 / 税码 7601–7604，其中 7021 为 1901「待处理财产损溢」、7022 为 5001「生产成本」、7023 为 2241「其他应付款」） | `WhaleErpApplication` | ERP 业务应用：**M0 主数据 + M1 财务核心 + M2 供应链核心 + M2 剩余 A（调拨/盘点）+ M2 剩余 B 第一期（采购退货/销售退货）+ M2 剩余 B 第二期（采购申请/销售报价）+ M2 剩余 B 第三期（价格表）+ M2 剩余 B 第四期（安全库存/缺料预警/呆滞分析）+ M2 剩余 B 第五期（采购比价/价格趋势）+ M2 剩余 B 第六期（信用在途预占）+ M2 剩余 B 第七期（客户信用占用查询）+ M3 第一阶段（预测/MPS/生产订单）+ M4 第一阶段（轻量人力）+ M5 第一期（质量与设备）+ M6 数据分析与报表中心已落地（2026-11-04）**——科目/会计期间/客户/仓库/计量单位/币种/税码，凭证录入/记账/红冲、明细账、科目余额表、应收应付与核销、坏账、账龄、资产负债表/利润表；采购订单（建单/审核/收货/取消）、销售订单（建单/审核/发货/取消，信用额度校验含在途预占，提供信用占用查询）、库存余额与流水、移动加权平均计价与库存调整、库存调拨（双仓过账/取消）、库存盘点（快照账面/差异过账，盘盈盘亏自动记账）、采购退货（建单/过账/取消，按源订单单价红字出库并冲减供应商未核销应付）、销售退货（建单/过账/取消，按当前移动加权均价红字入库并冲减客户未核销应收）、采购申请（建单/审核/转采购订单草稿）、销售报价（建单/接受/转销售订单草稿）、价格表（客户售价/供应商采购价，订单与报价建单兜底取价）、安全库存（按仓库 + 物料维护安全量/最高量，缺料预警按实际余额比较，呆滞分析按余额 > 0 与最近出库早于阈值判定）、需求预测（建批次/发布/取消）、主生产计划（建单/下达/取消，下达回写已下达数量）、生产订单（下达/领料/完工入库/结案/取消，领料扣减领料仓并累计材料成本、完工按「累计材料成本 ÷ 计划量」入库目标仓）、组织单元与员工档案（建档/编辑/入转调离异动生效回写）、考勤汇总（员工 + 期间 upsert）、薪资批次（新建/计算/审批/发放/结账/取消，按在职员工 × 考勤生成工资条，社保 10.5% / 公积金 7% / 月度 7 级累进个税）、绩效周期与 KPI 考核（分数推导 A–D 等级）、质量检验（IQC/IPQC/FQC 建单/录入/判定，全项有结论才可判定，`FAILED` 幂等生成不合格品单并回写）、不合格品处置（返工/让步/报废/退货，`OPEN→DISPOSED→CLOSED`，有金额自动派生质量成本：报废→`SCRAP`、其余→`REWORK`）、设备档案与保养（设备新增/编辑、保养计划周期推算、保养工单完成回写下次保养日）、OEE 回传（同设备同日期 upsert，三率封顶 1）、量具与校准（量具台账、校准计划、校准记录，`PASS` 才回写日期并顺延计划，过期量具创建检验单抛 `TOOL_EXPIRED`）、经营驾驶舱（按期间 + 统计日快照，刷新先置旧快照 `STALE` 再 upsert，利润 = 收入 − 成本）、多维分析（客户/物料/仓库 × 收入/数量/订单数/成本）、报表中心（报表定义/计划/执行/CSV 导出，执行复用多维聚合）、预警中心（库存/超期应收/质量问题/超预算，规则/评估/去重/确认/关闭）、收货自动生成应付与凭证、发货自动生成应收与凭证、领料与完工自动生成生产成本凭证、薪资审批生成计提凭证（借 6602 / 贷 2211）、薪资发放生成发放凭证（借 2211 / 贷 1002、2221、2241）（`sourceType + sourceId` 幂等）；端点 `/erp/master-data/**`、`/erp/finance/voucher/**`、`/erp/finance/**`、`/erp/finance/report/**`、`/erp/scm/purchase-order/**`、`/erp/scm/sales-order/**`、`/erp/scm/stock/**`、`/erp/scm/transfer/**`、`/erp/scm/stocktake/**`、`/erp/scm/purchase-return/**`、`/erp/scm/sales-return/**`、`/erp/scm/purchase-requisition/**`、`/erp/scm/sales-quotation/**`、`/erp/scm/price-list/**`、`/erp/scm/safety-stock/**`、`/erp/mfg/forecast/**`、`/erp/mfg/mps/**`、`/erp/mfg/production/**`、`/erp/hr/org`、`/erp/hr/employee/**`、`/erp/hr/attendance/**`、`/erp/hr/payroll/**`、`/erp/hr/performance/**`、`/erp/qc/inspection/**`、`/erp/qc/ncr/**`、`/erp/qc/quality-cost/page`、`/erp/qc/equipment/**`、`/erp/qc/maintenance/**`、`/erp/qc/oee/**`、`/erp/qc/metrology/**`、`/erp/bi/cockpit`、`/erp/bi/cockpit/**`、`/erp/bi/cube`、`/erp/bi/report/**`、`/erp/bi/alert/**`；MRP/CRP 以及财务扩展（资产/成本/预算/资金）按 [Wave 5.78 ERP 全景计划](plans/2026-11-03-wave5.78-erp-panorama.md) **M3 剩余** 分阶段实施；保持模块化单体，供应商准入/绩效仍归 SRM | 可启动（M0+M1+M2+M3 第一阶段+M4 第一阶段+M5 第一期+M6 已编码，见 [M0+M1 检查报告](reviews/2026-11-03-wave5.78-erp-m0-m1.md)、[M2 检查报告](reviews/2026-11-03-wave5.78-erp-m2.md)、[M2 剩余 A 检查报告](reviews/2026-11-03-wave5.78-erp-m2-stock-ops.md)、[M2 剩余 B 第一期检查报告](reviews/2026-11-04-wave5.78-erp-m2-returns.md)、[M2 剩余 B 第二期检查报告](reviews/2026-11-05-wave5.78-erp-m2-requisition-quotation.md)、[M2 剩余 B 第三期检查报告](reviews/2026-11-05-wave5.78-erp-m2-price-list.md)、[M2 剩余 B 第四期检查报告](reviews/2026-11-05-wave5.78-erp-m2-safety-stock.md)、[M2 剩余 B 第五期检查报告](reviews/2026-11-05-wave5.78-erp-m2-price-compare.md)、[M2 剩余 B 第六期检查报告](reviews/2026-11-05-wave5.78-erp-m2-credit-reservation.md)、[M2 剩余 B 第七期检查报告](reviews/2026-11-05-wave5.78-erp-m2-credit-usage.md)、[M3 检查报告](reviews/2026-11-03-wave5.78-erp-m3.md)、[M4 检查报告](reviews/2026-11-03-wave5.78-erp-m4.md)、[M5 检查报告](reviews/2026-11-03-wave5.78-erp-m5.md) 与 [M6 检查报告](reviews/2026-11-03-wave5.78-erp-m6.md)；实机待环境） |
| 9 | `whale-mes` | whale-apps | `whale-apps/whale-mes` | **9002** | `/mes/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_mes`**（16 表：V1 `mes_work_order` + `api_log`，V2 12 张 `mes_md_*`/`mes_common_outbox`，V3 `mes_md_shift_calendar`，V9 `mes_process_route`） | `WhaleMesApplication` | MES 制造执行：M0 车间主数据（九类 CRUD/选项）+ 工单模型 + 统一发件箱；M3 增工艺路线接收（PDM 下发幂等/撤回）；M1–M5 见全景规划 | 可启动（M0 已交付，M3 接收端已落地，M1 待启动） |
| 10 | `whale-pdm` | whale-apps | `whale-apps/whale-pdm` | **9003** | `/pdm/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_pdm`**（33 张业务表；V1 存量 2 + V2 图文档 4 + V4 零件/BOM 5 + V6 BOM 导出 2 + V7 变更申请 2 + V8 变更单/任务 3 + V9 变更通知/追溯/投递/锁 5 + V10 工艺 5 + V11 协同 3 + V12 集成 2〔`pdm_int_sync_log`/`pdm_int_reconcile`〕） | `WhalePdmApplication` | PDM 产品数据管理：产品档案 CRUD + M0 图文档中心（目录/文档/版本/检出检入/发布回滚/下载/检索）+ M1 零件主档/分类/使用处/BOM 树/发布快照/对比/异步导出 + M2-2 变更申请 ECR（状态机/影响对象/人工审批）+ M2-3 变更单 ECO（影响评估/变更任务/执行计划/审批通过前不可执行）+ M2-4..M2-10 变更通知 ECN/追溯/OA 回调/生效任务/通知 + M3 工艺路线/工序/工装/SOP/MES 下发与内网 SOP 内容读取 + M4 项目交付物（仅归档已发布版本）/研发评审（草稿→关闭闭环）/在线预览任务（2D/3D 幂等与重试）+ M5 集成收口（CAD 检入/EBOM 导入、ERP 物料 upsert 同步、BOM 快照/MES 路线/OA 单据三通道对账、同步日志与重试、OA 中态补偿） | 可启动（M0–M5 已落地；M1B 去重与 ERP 只读、CAD 插件本体待做） |
| 11 | `whale-oa` | whale-apps | `whale-apps/whale-oa` | **9004** | `/oa/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_oa`**（15 张表；V1 存量 2 + V2 审批底座/文件/序列/发件箱 8 + V4 人事 5） | `WhaleOaApplication` | OA 协同办公：请假申请 + M0 审批底座（审批配置/实例/路由规则/路由快照/副作用）+ M1A 请假闭环（假期类型/年度额度/冻结扣减释放/额度流水/工作日历/半天跨天/重叠校验）+ 员工扩展档案 + 审批结果回写 | 可启动（M0 + M1A 人事切片已落地；审批中心/通知与对账待做） |
| 12 | `whale-audit` | whale-ops | `whale-ops/whale-audit` | **9101** | `/audit/**` | ✅ `lb://whale-audit` | **`bluewhale_audit`**（`audit_log`，已建） | `WhaleAuditApplication` | 审计日志落库与查询 | **已运行**（已在 Nacos 注册） |
| 13 | `whale-monitor` | whale-ops | `whale-ops/whale-monitor` | **9102** | `/monitor/**` | ✅ `lb://whale-monitor` | ——（只读聚合） | `WhaleMonitorApplication` | 服务实例只读聚合（Nacos 实例列表/健康）；指标采集与存储由外部 Prometheus 承担，不落库、不告警 | 可启动 |
| 14 | `whale-alert` | whale-ops | `whale-ops/whale-alert` | **9103** | `/alert/**` | ✅ `lb://whale-alert` | **`bluewhale_alert`**（已建库） | `WhaleAlertApplication` | 告警通知 | 可启动 |
| 15 | `whale-config` | whale-registry | `whale-registry/whale-config` | **9201** | `/config/**` | ✅ `lb://whale-config`（Wave 5.73 F-05 解冻恢复） | **`bluewhale_config`**（4 表：`config_item`/`config_item_history`/`audit_log`/`api_log`） | `WhaleConfigApplication` | 业务配置管理：配置项 CRUD（租户内键唯一）、版本历史、一键回滚（回滚产生新版本）；与 Nacos 互补——Nacos 管服务装配配置，本服务管业务应用配置 | **已运行**（Wave 5.73 解冻，本地起服验证通过） |
| 16 | `whale-agent` | whale-services | `whale-services/whale-agent` | **8090** | `/agent/**` | ✅ `lb://whale-agent` | **`bluewhale_agent`**（6 表，已建） | `WhaleAgentApplication` | 平台与项目知识 Agent（Lucene 检索 + 事实层 + 问答编排） | **已运行**（已在 Nacos 注册，Wave 5.47） |
| 17 | `whale-im` | whale-services | `whale-services/whale-im` | **8091** | `/im/**`、`/im/ws` | ✅ `lb://whale-im` / `lb:ws://whale-im` | **`bluewhale_im`**（5 表，**规划**） | `WhaleImApplication` | 自研 WebIM：会话/消息/已读/在线/机器人会话；WS ticket 鉴权 | **规划**（Wave 5.52） |
| 18 | `whale-archive` | whale-services | `whale-services/whale-archive` | **8093** | `/archive/**` | ✅ `lb://whale-archive` | **`bluewhale_archive`**（6 表，脚本已就绪） | `WhaleArchiveApplication` | 档案服务：门类/规则/条目/档号/快照/检索；只读拉取工作流已通过实例与表单附件；**文件下载与在线预览 / 播放**（两者均经 `whale-file` 内网流式代理、不整体缓冲，Range 200 / 206 / 416，下载支持断点续传） | **可启动**（Wave 5.53 M1 底座完成 + 2026-11-01 在线预览增量，未起服） |
| 19 | `whale-ai`（Python / FastAPI） | ——（`ai/` 独立树，**不进 Maven reactor**） | `ai/whale-ai` | **8092** | 无（**仅内网**，不注册网关、不注册 Nacos） | ❌ 不配（由 `whale-agent` 内网调用） | 无（记忆存 Redis `whale:ai:memory:*`） | `main:app`（uvicorn） | LangChain/LangGraph 编排、工具循环、确认态；**不持业务库凭据** | **骨架已落地**（Wave 5.52 T10；pytest 待 Python 3.11/3.12 环境） |
| 20 | `whale-pm` | whale-apps | `whale-apps/whale-pm` | **9005** | `/pm/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_pm`**（4 表 DDL 就绪，**待建库**） | `WhalePmApplication` | 项目管理（对标 Jira/禅道）：项目空间/成员/状态流转（M1）、工作项（M2）、迭代看板（M3）、报表（M4）、站内通知 + 工作流审批（M5，默认关闭） | **可启动**（Wave 5.55 M1–M5 完成；未起服） |
| 21 | `whale-contract` | whale-apps | `whale-apps/whale-contract` | **9006** | `/contract/**` | ✅ `lb://whale-contract`（2026-10-26） | **`bluewhale_contract`**（4 表 + `api_log`） | `WhaleContractApplication` | 合同管理：起草/相对方/版本/审批/终止/归档（M1）；印章/PDF 盖章/数字签名/三方签章（M2–M3） | **可启动**（Wave 5.61 M1 完成） |
| 22 | `whale-message` | whale-services | `whale-services/whale-message` | **8094** | `/message/**` | ✅ `lb://whale-message`（2026-10-28） | **`bluewhale_message`**（5 表：`msg_template`/`msg_record`/`msg_delivery`/`audit_log`/`api_log`） | `WhaleMessageApplication` | 统一消息服务：模板渲染/渠道路由/幂等/投递记录（M0）；模板管理、失败重试、站内信收件箱（M1）；INAPP/EMAIL/SMS/IM 适配器，无供应商时默认 mock | **可启动**（Wave 5.68 M0 + 5.70 M1；2026-10-28 本地起服验证通过） |
| 23 | `whale-doc` | whale-services | `whale-services/whale-doc` | **8095** | `/doc/**` | ✅ `lb://whale-doc`（2026-10-29，M2 增 `/doc/ws`） | **`bluewhale_doc`**（16 业务表：M1 7 + M2 7 + M3 2，另 `audit_log`/`api_log`） | `WhaleDocApplication` | 在线文档 / Wiki：空间与成员、页面树、TipTap 富文本、自动保存与 revision 冲突、版本/评论/附件、搜索、页面 ACL、标签/反链/动态/最近访问、ngram 检索、协作/回收站、模板/导入导出/审批/通知/分享/归档、Agent 知识索引；M5 指标/清理/备份/运维 | **M1–M5 已交付**（Wave 5.74；239 项 smoke + 备份恢复演练通过） |
| 24 | `whale-music` | whale-services | `whale-services/whale-music` | **8096** | `/music/**` | ✅ `lb://whale-music`（2026-10-31，M0 增 `/music/stream/**` 限流与 `/music/internal/**` 屏蔽，M5 增票据 / 点赞限流，四条规则 8080 实机 429 已验证） | **`bluewhale_music`**（14 张业务表 + `audit_log` / `api_log`，已建库；M5 增 `mu_song.copyright_source`、`mu_song_appeal`、`idx_mu_history_played`） | `WhaleMusicApplication` | 音乐平台：歌手/专辑/歌曲/标签曲库、**播放票据与 Range 流式（M2 已落地）**、播放进度 / 收藏、歌单、首页/榜单（**热榜近 7 天滚动窗口**）/搜索、评论审核、**版权下架与申诉复核（M5-3）**；所有登录用户可上传（签署版权无争议协议 + 公开/自听可见性） | **可上线**（Wave 5.75 M0–M5 含收口轮；8096 起服，M2–M5 冒烟、申诉闭环、429 与备份演练均 PASS，SLO-6/7/8 已接入 Prometheus / Grafana） |
| 25 | `whale-scene` | whale-services | `whale-services/whale-scene` | **8097** | `/scene/**` | ✅ `lb://whale-scene`（Wave 5.77；**不配 `StripPrefix`**，`/scene/internal/**` 由 internal-deny 403） | **`bluewhale_scene`**（5 张业务表：`sc_scene` / `sc_scene_node` / `sc_node_binding` / `sc_visibility` / `sc_layout_version` + `audit_log` / `api_log`） | `WhaleSceneApplication` | 城池场景导航：场景/区域/建筑/入口节点树、入口绑定、角色/用户/全体可见性、2D 优先导航与 3D 可选增强；发布校验（≤200 节点 / 深度 ≤5 / 无环 / ≥1 启用入口 / URL 白名单）与布局快照 checksum；不拥有业务数据 | **可启动**（Wave 5.77 M1/M2 2D 已落地；8097 + `/scene/**` + 前端 8112；单测 39 项、行覆盖 62.6%、分支 66.8%；实机待环境） |
| 26 | `whale-video` | whale-services | `whale-services/whale-video` | **8098** | `/video/**` | ✅ `lb://whale-video`（Wave 5.77 M0；**不配 `StripPrefix`**，内网 `/video/internal/**` 由 internal-deny 403，`/video/stream/**` 独立限流且**不进熔断名单**） | **`bluewhale_video`**（M0 Flyway `V1__baseline.sql` 14 张 `vd_*` 业务表 + `audit_log` / `api_log`；M3 +`vd_series`、M4 +`vd_collection`/`vd_collection_item`；M5 +`V4__watch_history_cleanup_index.sql` 索引 `idx_vd_history_watched`） | `WhaleVideoApplication` | 视频平台：短视频 / 视频 / 系列 / 电影三条内容线（共享 `vd_content` 注册与播放票据 / Range 流式 / 互动 / 审核 / 分类标签底座）；视频字节归 `whale-file`，大文件走按权限码上传策略（G1）；M5 安全收口：越权矩阵、上传 magic bytes / MIME / 时长校验、观看历史 180 天清理、SLO-9/10/11 与 Grafana 面板 | **M0–M5 已落地 / 可验收**（Wave 5.77；151 项单测、Checkstyle 0、ArchUnit 5/5；8098 / `/video/**` / 前端 8114；实机验收待环境） |
| 27 | `whale-crm`（**门禁骨架**） | whale-apps | `whale-apps/whale-crm` | **9007** | `/crm/**` | ✅ `lb://whale-crm`（门禁骨架；**不配 `StripPrefix`**，`/crm/internal/**` 由 internal-deny 403） | **`bluewhale_crm`**（4 张业务表 DDL：`crm_customer` / `crm_contact` / `crm_opportunity` / `crm_follow_up`，无业务种子） | `WhaleCrmApplication` | 客户关系：客户/联系人/商机/销售阶段/跟进/客户 360；供应商仍归 ERP/SRM，合同相对方只引用 `crmCustomerId` 快照 | **门禁骨架可启动**（仅 `GET /crm/meta` 返回门禁状态与里程碑；门禁通过前不建业务 CRUD / 菜单 / 前端，见 ADR-003 规划） |

> 「对外一级前缀」= 服务自持的前缀，网关**只转发、不剥离**（REQ-ARCH-003）。
> 网关 `discovery.locator.enabled=true` 已开启服务发现路由：`lb://whale-<域>` → `http://<服务实例>`。

---

## 二、网关路由映射（`whale-gateway`，现配 28 条，无规划路由）

| 路由 id | 断言前缀 | 目标 URI | 对应服务 | 状态 |
|---|---|---|---|---|
| `whale-service-auth` | `Path=/auth/**,/user/**,/dept/**,/role/**,/menu/**,/permission/**` | `lb://whale-service-auth` | whale-service-auth | ✅ 已配（Wave 5.2/AUTH-38 补齐管理前缀） |
| `whale-service-auth-tenant` | `Path=/tenant/**` | `lb://whale-service-auth` | whale-service-auth | ✅ 已配（Wave 5.54；内网 `/tenant/internal/**` 由 internal-deny 403） |
| `whale-job` | `Path=/job/**` | `lb://whale-job` | whale-job | ✅ 已配 |
| `whale-workflow` | `Path=/workflow/**` | `lb://whale-workflow` | whale-workflow | ✅ 已配 |
| `whale-form` | `Path=/form/**` | `lb://whale-form` | whale-form | ✅ 已配 |
| `whale-file` | `Path=/file/**` | `lb://whale-file` | whale-file | ✅ 已配 |
| `whale-pay` | `Path=/pay/**` | `lb://whale-pay` | whale-pay | ✅ 已配 |
| `whale-alert` | `Path=/alert/**` | `lb://whale-alert` | whale-alert | ✅ 已配（Wave 5.33） |
| `whale-monitor` | `Path=/monitor/**` | `lb://whale-monitor` | whale-monitor | ✅ 已配 |
| `whale-audit` | `Path=/audit/**` | `lb://whale-audit` | whale-audit | ✅ 已配 |
| `whale-agent` | `Path=/agent/**` | `lb://whale-agent` | whale-agent | ✅ 已配（Wave 5.47） |
| `whale-im` | `Path=/im/**`（**排除** `/im/ws`） | `lb://whale-im` | whale-im | 🟡 规划（Wave 5.52；**不配 `StripPrefix`**） |
| `whale-im-ws` | `Path=/im/ws` | `lb:ws://whale-im` | whale-im | 🟡 规划（Wave 5.52；保留 `Upgrade`/`Connection` 头，鉴权走 60s 一次性 ticket） |
| `whale-archive` | `Path=/archive/**` | `lb://whale-archive` | whale-archive | ✅ 已配（Wave 5.53 M1；**不配 `StripPrefix`**，内网 `/archive/internal/**` 由 internal-deny 403） |
| whale-erp | `Path=/erp/**` | `lb://whale-erp` | whale-erp | ✅ 已配（2026-10-25） |
| whale-mes | `Path=/mes/**` | `lb://whale-mes` | whale-mes | ✅ 已配（2026-10-25） |
| whale-pdm | `Path=/pdm/**` | `lb://whale-pdm` | whale-pdm | ✅ 已配（2026-10-25） |
| whale-oa | `Path=/oa/**` | `lb://whale-oa` | whale-oa | ✅ 已配（2026-10-25） |
| whale-pm | `Path=/pm/**` | `lb://whale-pm` | whale-pm | ✅ 已配（Wave 5.55 M1；**不配 `StripPrefix`**，内网 `/pm/internal/**` 由 internal-deny 403） |
| `whale-contract` | `Path=/contract/**` | `lb://whale-contract` | whale-contract | ✅ 已配（Wave 5.61 M1；**不配 `StripPrefix`**，内网 `/contract/internal/**` 由 internal-deny 403） |
| `whale-message` | `Path=/message/**` | `lb://whale-message` | whale-message | ✅ 已配（Wave 5.68 M0；**不配 `StripPrefix`**，内网 `/message/internal/**` 由 internal-deny 403） |
| `whale-doc` | `Path=/doc/**`（M2 排除 `/doc/ws`） | `lb://whale-doc` | whale-doc | ✅ 已配（Wave 5.74 M1；**不配 `StripPrefix`**，内网 `/doc/internal/**` 由 internal-deny 403） |
| `whale-doc-ws` | `Path=/doc/ws` | `lb:ws://whale-doc` | whale-doc | ✅ 已配（Wave 5.74 M2；保留 `Upgrade`/`Connection`，60s 一次性 ticket 鉴权） |
| `whale-music` | `Path=/music/**` | `lb://whale-music` | whale-music | ✅ 已配（Wave 5.75 M0；**不配 `StripPrefix`**，内网 `/music/internal/**` 由 internal-deny 403，M5 起四类路径限流：流式 120 / 评论 20 / 票据 60 / 点赞 30 每分钟） |
| `whale-video` | `Path=/video/**` | `lb://whale-video` | whale-video | ✅ 已配（Wave 5.77 M0；**不配 `StripPrefix`**，内网 `/video/internal/**` 由 internal-deny 403；M5 起六类路径限流：流式 120 / 票据 60 / 评论 20 / 点赞 30 / 收藏 30 / 上传 20 每分钟，超阈值 429 + `code=429`；**不加入熔断名单**） |
| `whale-scene` | `Path=/scene/**` | `lb://whale-scene` | whale-scene | ✅ 已配（Wave 5.77 M1/M2；**不配 `StripPrefix`**，内网 `/scene/internal/**` 由 internal-deny 403） |
| `whale-crm` | `Path=/crm/**` | `lb://whale-crm` | whale-crm | ✅ 已配（M0 底座 + M1 客户主数据已落地；**不配 `StripPrefix`**，内网 `/crm/internal/**` 由 internal-deny 403） |
| `whale-config` | `Path=/config/**` | `lb://whale-config` | whale-config | ✅ 已配（Wave 5.73 F-05 解冻：配置项 CRUD / 版本历史 / 回滚；**不配 `StripPrefix`**） |

**网关免鉴权白名单**：`/auth/login`、`/auth/refresh`、`/actuator/**`、`/v3/api-docs/**`、`/doc.html`、`/webjars/**`、`/favicon.ico`、`/audit/log/frontend/batch`、`/audit/log/behavior/batch`（后两条为前端/行为日志上报，Wave 5.19 起仅放行 batch 写入端点）、`/doc/ws`（WS 不携带 Authorization 头，改走 60s 一次性 ticket）。

---

## 三、前端应用清单（18 个已落地，另 1 个规划；开发态端口）

| # | 应用 | 目录 | 包名（NPM） | 角色 | 开发端口 | base 路径 | 备注 |
|---|---|---|---|---|---|---|---|
| 1 | whale-platform | `frontend/platform-apps/whale-platform` | ——（基座） | qiankun **基座**（登录页 + MainLayout 多标签） | **8000** | `/` | 承载所有子应用 |
| 2 | whale-admin | `frontend/admin` | `whale-admin` | 管理端（权限/用户/角色/菜单/部门/任务/文件） | **8001** | `/` | 端口已收进前端区段（C-01 已解） |
| 3 | whale-ops | `frontend/ops` | `whale-ops` | 运维端（审计/监控/告警） | **8104** | `/` | 端口已收进前端区段（C-01 已解） |
| 4 | whale-portal | `frontend/platform-apps/whale-portal` | `whale-portal` | qiankun **子应用**（门户） | **8101** | `/portal` | 微应用名 `whale-portal` |
| 5 | whale-erp | `frontend/platform-apps/whale-erp` | `whale-erp` | qiankun **子应用**（ERP） | **8102** | `/erp` | 微应用名 `whale-erp` |
| 6 | whale-oa | `frontend/platform-apps/whale-oa` | `whale-oa` | qiankun **子应用**（OA） | **8103** | `/oa` | 微应用名 `whale-oa` |
| 7 | whale-mes | `frontend/platform-apps/whale-mes` | `whale-mes` | qiankun **子应用**（MES） | **8105** | `/mes` | 微应用名 `whale-mes` |
| 8 | whale-pdm | `frontend/platform-apps/whale-pdm` | `whale-pdm` | qiankun **子应用**（PDM） | **8106** | `/pdm` | 微应用名 `whale-pdm` |
| 9 | whale-form-designer | `frontend/platform-apps/whale-form-designer` | `whale-form-designer` | 独立 SPA（拖拽式表单设计器） | **8002** | `/` | 直连网关 8080，非 qiankun 子应用 |
| 10 | whale-workflow-designer | `frontend/platform-apps/whale-workflow-designer` | `whale-workflow-designer` | 独立 SPA（工作流设计器 + 发起 + 待办） | **8003** | `/` | 直连网关 8080，非 qiankun 子应用 |
| 11 | whale-agent | `frontend/platform-apps/whale-agent` | `whale-agent` | 独立 SPA（知识问答工作台） | **8004** | `/` | 直连网关 8080，非 qiankun 子应用（Wave 5.47） |
| 12 | whale-im | `frontend/platform-apps/whale-im` | `whale-im` | qiankun **子应用**（即时通讯 + 蓝鲸助手） | **8107** | `/im` | 微应用名 `whale-im`（Wave 5.52 规划）；`/im` 同时为 API 前缀，沿用 `Accept: text/html` 旁路 |
| 13 | whale-pm | `frontend/platform-apps/whale-pm` | `whale-pm` | qiankun **子应用**（项目管理） | **8108** | `/pm` | 微应用名 `whale-pm`（Wave 5.55；M4 交付页面） |
| 14 | whale-contract | `frontend/platform-apps/whale-contract` | `whale-contract` | qiankun **子应用**（合同管理：列表 + 详情/版本/流程） | **8109** | `/contract` | 微应用名 `whale-contract`（Wave 5.61 M1） |
| 15 | whale-doc | `frontend/platform-apps/whale-doc` | `whale-doc` | qiankun **子应用**（在线文档/Wiki：空间、页面树、TipTap 协作编辑器、评论/版本/附件、回收站、模板、分享、AI 面板） | **8110** | `/doc` | 微应用名 `whale-doc`（Wave 5.74 M1–M5 已交付；lint/build 通过） |
| 16 | whale-music | `frontend/platform-apps/whale-music` | `whale-music` | qiankun **子应用**（音乐平台：首页/发现、搜索、曲库、上传 / 我的作品（**版权申诉入口**）、**全局播放器（播放条 / 队列 / 歌词抽屉）**、歌单、评论区、我的音乐） | **8111** | `/music` | 微应用名 `whale-music`（Wave 5.75 M1–M5；页面已接入基座，build + lint 通过） |
| 17 | whale-video | `frontend/platform-apps/whale-video` | `whale-video` | qiankun **子应用**（视频平台：首页/发现、短视频滑播、视频与系列、电影、我的视频、观看历史） | **8114** | `/video` | 微应用名 `whale-video`（Wave 5.77 M0–M5 已落地；页面接入基座，build + lint 通过） |
| 18 | whale-scene | `frontend/platform-apps/whale-scene` | `whale-scene` | qiankun **子应用**（城池场景导航：2D 地图 / 键盘列表 / 场景管理 / 入口绑定 / 可见性；3D 为 M3 可选增强） | **8112** | `/scene` | 微应用名 `whale-scene`（Wave 5.77 M1/M2 已落地；build + lint 通过） |
| 19 | whale-crm（**规划**） | `frontend/platform-apps/whale-crm`（**未创建**） | `whale-crm` | qiankun **子应用**（客户 / 联系人 / 商机 / 销售漏斗） | **8113** | `/crm` | 微应用名 `whale-crm`（后端 M0/M1 已落地；前端待后续增量，菜单 413–420 预留不登记） |

共享包（不占端口）：`frontend/shared-packages/whale-common-tools`、`whale-common-theme`、`whale-log-sdk`、`frontend/elemental/whale-ui`（NPM scope `@bluewhale/`）。
冻结共享包（无消费方，不删除代码、不升级依赖）：`frontend/shared-packages/whale-common-three`（F-09）、`frontend/games/whale-games-core`（F-07）。

---

## 四、第三方 / 基础设施组件清单（含端口）

| # | 组件 | 用途 | 端口 | 版本 | 部署方式 | 本机状态 |
|---|---|---|---|---|---|---|
| 1 | **MySQL** | 业务库 + Nacos 外置存储 | **3306**（TCP） | 8.0.45（本机 `D:\mysql8`）／镜像 8.4（compose） | 本机原生；compose `bw-mysql` | **已运行**。库：`bluewhale_auth`、`bluewhale_audit`、`bluewhale_nacos`、`bluewhale_agent`、**`bluewhale_im`（规划，Wave 5.52）**、**`bluewhale_message`（M0，脚本已就绪）**、**`bluewhale_doc`（Wave 5.74，已建）**、**`bluewhale_music`（Wave 5.75 M0–M5，已建库 14 表）**、**`bluewhale_video`（Wave 5.77 M0–M5，Flyway `V1`–`V4` 就绪，待建库）**、**`bluewhale_scene`（Wave 5.77 M1/M2，Flyway `V1__baseline.sql` 5 表 + 审计/API 日志，初始化脚本已登记）**、**`bluewhale_crm`（Wave 5.77 门禁骨架，4 表 DDL 无业务种子）**（另有外部遗留 `yc-sts-db-auth` / `yc-sts-db-flow`，登记不改名） |
| 2 | **Redis** | 令牌黑名单、i18n 缓存、分布式锁 | **6379**（TCP） | 5.0.14.1（本机 `tools/`）／镜像 7-alpine（compose） | 本机原生；compose `bw-redis` | **已运行** |
| 3 | **Nacos** | 服务注册发现 + 配置中心 | **8848**（HTTP OpenAPI + 主服务）<br>**8849**（控制台，仅 3.x 独立控制台）<br>**9848**（gRPC 客户端→服务端）<br>**9849**（gRPC 服务端→服务端，集群） | 3.2.4（本机 `tools/nacos`）／镜像 3.2.4（compose） | 本机原生（standalone + MySQL 外置存储）；compose `bw-nacos` | **已运行**（3.2.4 实机启动验证：8848/8849 监听、`started successfully ... with external storage`；已注册 `whale-gateway` / `whale-service-auth` / `whale-audit`） |
| 4 | **RabbitMQ** | 审计日志汇聚、异步消息（规划） | **5672**（AMQP）<br>**15672**（Management 控制台） | 4-management-alpine | compose `bw-rabbitmq` | ⚠ **未启动**（compose 服务已定义，本机未拉起） |
| 5 | **Nginx** | 反向代理 + 前端静态资源 + TLS | **80**（HTTP）<br>**443**（HTTPS） | 自建镜像（`infrastructure/docker/nginx`） | compose `bw-nginx` | ⚠ **未启动**（compose 服务已定义，本机未拉起） |
| 6 | **Prometheus** | 指标采集 | **9090**（默认，`PROMETHEUS_HOST_PORT` 可覆盖） | v2.54.1（compose `PROMETHEUS_IMAGE`） | compose `bw-prometheus`（挂载 `infrastructure/prometheus/prometheus-docker.yml` + `deploy/monitoring/prometheus-rules.yml`） | **已运行**（healthcheck healthy；抓取宿主机 gateway/auth/workflow/contract 均为 up） |
| 7 | **Grafana** | 监控看板 | **3000**（默认，`GRAFANA_HOST_PORT` 可覆盖） | 11.2.0（compose `GRAFANA_IMAGE`） | compose `bw-grafana`（provisioning 自动装配 Prometheus 数据源） | **已运行**（`/api/health` ok；数据源 `BlueWhale Prometheus` 已装配） |
| 8 | **Alertmanager** | 告警路由（webhook → `whale-alert`） | **9093**（默认，`ALERTMANAGER_HOST_PORT` 可覆盖） | v0.27.0（compose `ALERTMANAGER_IMAGE`） | compose `bw-alertmanager`（挂载 `infrastructure/prometheus/alertmanager-docker.yml`） | **已运行**（`amtool check-config` SUCCESS，`/-/ready` 200） |
| 9 | **Sentinel Dashboard** | 流控/熔断控制台（REQ-ARCH 限流方案） | **8858**（默认） | —— | **未引入**（`whale-gateway` 未接 Sentinel） | 规划 |

> Nacos 版本以本机实装 **3.2.4**（`tools/nacos` 解压体，JAR MANIFEST `Implementation-Version: 3.2.4`）为准，compose 镜像已统一 v3.2.4（T-16 口径变更，2026-10-30 实机启动验证通过）；健康检查使用 `/nacos/actuator/health`，控制台为独立端口 8849。

---

## 五、端口 → 服务 速查（升序）

| 端口 | 归属 | 类型 | 说明 |
|---|---|---|---|
| **80** | Nginx | 基础设施 | HTTP 入口（compose，未运行） |
| **443** | Nginx | 基础设施 | HTTPS 入口（compose，未运行） |
| **3000** | Grafana | 基础设施 | 监控看板（compose `bw-grafana`） |
| **3306** | MySQL | 基础设施 | 业务库 + `bluewhale_nacos` |
| **5672** | RabbitMQ | 基础设施 | AMQP（未运行） |
| **6379** | Redis | 基础设施 | 黑名单 / 缓存 / 锁 |
| **8000** | `whale-platform` | 前端（基座） | qiankun 基座 dev server |
| **8001** | `whale-admin` | 前端（管理端） | 独立 SPA |
| **8002** | `whale-form-designer` | 前端（独立 SPA） | 表单设计器 |
| **8003** | `whale-workflow-designer` | 前端（独立 SPA） | 工作流设计器 |
| **8004** | `whale-agent` | 前端（独立 SPA） | 知识问答工作台（Wave 5.47） |
| **8080** | `whale-gateway` | 后端 | **唯一 API 入口** |
| **8081** | `whale-service-auth` | 后端 | 认证与权限中心 |
| **8082** | `whale-job` | 后端 | 定时任务 |
| **8083** | `whale-workflow` | 后端 | 工作流 |
| **8084** | `whale-form` | 后端 | 动态表单 |
| **8085** | `whale-file` | 后端 | 文件服务 |
| **8086** | `whale-pay` | 后端 | 支付 |
| **8090** | `whale-agent` | 后端 | 知识 Agent |
| **8091** | `whale-im` | 后端 | WebIM（规划） |
| **8092** | `whale-ai` | 后端（Python） | LangChain 智能体（仅内网，规划） |
| **8093** | `whale-archive` | 后端 | 档案服务（Wave 5.53 M1，可启动） |
| **8094** | `whale-message` | 后端 | 统一消息服务（Wave 5.68 M0 + 5.70 M1，可启动） |
| **8095** | `whale-doc` | 后端 | 在线文档/Wiki（Wave 5.74 M1 骨架，可启动） |
| **8096** | `whale-music` | 后端 | 音乐平台（Wave 5.75 M0–M5 含收口轮已落地，可启动；播放、申诉与限流冒烟通过） |
| **8097** | `whale-scene` | 后端 | 城池场景导航（Wave 5.77 M1/M2 2D 已落地，可启动） |
| **8098** | `whale-video` | 后端 | 视频平台（Wave 5.77 M0–M5 已落地 / 可验收；短视频 / 视频 / 系列 / 电影 / 片单 / 榜单 / 运营 + 越权矩阵、上传安全、历史清理、SLO-9/10/11，实机待环境） |
| **8101** | `whale-portal` | 前端（子应用） | base `/portal` |
| **8102** | `whale-erp` | 前端（子应用） | base `/erp` |
| **8103** | `whale-oa` | 前端（子应用） | base `/oa` |
| **8104** | `whale-ops` | 前端（运维端） | 独立 SPA |
| **8105** | `whale-mes` | 前端（子应用） | base `/mes` |
| **8106** | `whale-pdm` | 前端（子应用） | base `/pdm` |
| **8107** | `whale-im` | 前端（子应用） | base `/im`（规划） |
| **8108** | `whale-pm` | 前端（子应用） | base `/pm`（Wave 5.55 M4） |
| **8109** | `whale-contract` | 前端（子应用） | base `/contract`（Wave 5.61 M1） |
| **8110** | `whale-doc` | 前端（子应用） | base `/doc`（Wave 5.74 M1 骨架，已接入基座菜单与 qiankun） |
| **8111** | `whale-music` | 前端（子应用） | base `/music`（Wave 5.75 M1–M5 页面、播放器与申诉入口已落地，已接入基座菜单与 qiankun） |
| **8112** | `whale-scene` | 前端（子应用） | base `/scene`（Wave 5.77 M1/M2 已落地，已接入基座菜单与 qiankun） |
| **8113** | `whale-crm` | 前端（子应用） | base `/crm`（后端 M0/M1 已落地，前端待后续增量） |
| **8114** | `whale-video` | 前端（子应用） | base `/video`（Wave 5.77 M0–M5 页面已落地，已接入基座菜单与 qiankun） |
| **8848** | Nacos | 基础设施 | HTTP OpenAPI + 主服务（contextPath `/nacos`） |
| **8849** | Nacos Console | 基础设施 | 控制台（仅 Nacos 3.x；本机 2.4.0 无独立控制台端口） |
| **8858** | Sentinel Dashboard | 基础设施 | 规划（未引入） |
| **9001** | `whale-erp` | 后端 | ERP |
| **9002** | `whale-mes` | 后端 | MES |
| **9003** | `whale-pdm` | 后端 | PDM |
| **9004** | `whale-oa` | 后端 | OA |
| **9005** | `whale-pm` | 后端 | 项目管理（Wave 5.55 M1） |
| **9006** | `whale-contract` | 后端 | 合同管理（Wave 5.61 M1） |
| **9007** | `whale-crm` | 后端 | 客户关系（M0 底座 + M1 客户主数据：客户 / 联系人 / 地址 / 画像 / 公海 / 跟进 / 360） |
| **9090** | Prometheus | 基础设施 | 指标采集（compose `bw-prometheus`） |
| **9093** | Alertmanager | 基础设施 | 告警路由（compose `bw-alertmanager`） |
| **9101** | `whale-audit` | 后端 | 审计日志 |
| **9102** | `whale-monitor` | 后端 | 服务实例只读聚合（不采集/不存储） |
| **9103** | `whale-alert` | 后端 | 告警 |
| **9201** | `whale-config` | 后端 | 业务配置管理（配置项/版本/回滚） |
| **9848** | Nacos | 基础设施 | gRPC 客户端→服务端 |
| **9849** | Nacos | 基础设施 | gRPC 服务端→服务端（集群） |
| **15672** | RabbitMQ | 基础设施 | Management 控制台（未运行） |

---

## 六、服务间调用契约

| 调用方 | 被调方 | 地址形式 | 说明 |
|---|---|---|---|
| 前端（任意） | 网关 | `http://<host>:8080/<一级前缀>/...` | 唯一对外入口，不再直连业务服务 |
| 网关 | 各后端服务 | `lb://whale-<域>` | Nacos 服务发现，服务名 = `spring.application.name` |
| 任意后端服务 | `whale-service-auth` | `lb://whale-service-auth` 或 SDK `whale-auth-sdk` | 包 `com.bluewhale.auth.sdk` |
| `whale-im` | `whale-service-auth` | `POST /user/internal/exists`（Nacos 直连，不经网关） | 群成员 / 单聊对方合法性校验（存在且启用）；网关已列入 `internal-deny`，外部访问 403 |
| 任意后端服务 | `whale-file` | `lb://whale-file` | —— |
| `whale-doc`（规划） | `whale-file` | `POST /file/internal/upload`、`GET /file/internal/{id}/content` | 附件/图片上传与代理读取；桶 `whale-doc`（Nacos 直连，不经网关） |
| `whale-doc`（规划） | `whale-message` | `POST /message/internal/send` | M3 评论提及/审批通知；显式传 `tenantId`，幂等键由文档服务生成 |
| `whale-doc`（规划） | `whale-workflow` | `/workflow/**` + 回调 | M3 发布审批；`businessKey=doc:{pageId}:v{revision}` |
| `whale-doc`（规划） | `whale-archive` | `POST /archive/internal/items` | M3 发布页快照入档；`sourceType=DOC_PAGE` |
| `whale-agent`（规划） | `whale-doc` | `GET /doc/internal/page/changes`、`GET /doc/internal/page/{id}` | M4 DOC 知识源增量采集；只读公开已发布页面 |
| `whale-im` | `whale-agent` | `lb://whale-agent`（`POST /agent/chat`，SSE） | 机器人问答；透传 `X-User-Id` / `X-Permission-Codes`（Wave 5.52 规划） |
| `whale-agent` | `whale-ai` | `http://<内网>:8092/v1/agent/*`（`WHALE_AI_BASE_URL`） | LangChain 编排；**不经网关、不注册 Nacos**（Wave 5.52 规划） |

---

## 七、端口分段规划（已落地口径）

| 区段 | 用途 | 现状 |
|---|---|---|
| **80 / 443** | Nginx 反代与 TLS | 规划（compose） |
| **3000 / 9090 / 9093** | 监控看板 / 指标 / 告警路由 | 已落地（compose `bw-grafana` / `bw-prometheus` / `bw-alertmanager`，端口可经 `*_HOST_PORT` 覆盖） |
| **3306 / 6379 / 5672 / 15672** | MySQL / Redis / RabbitMQ | 3306、6379 已运行；5672/15672 未运行 |
| **8000–8099** | **前端 dev server**：基座 `8000`、admin `8001`、form-designer `8002`、workflow-designer `8003`、agent `8004` | 已落地（C-01 已解） |
| **8080** | **网关（唯一后端入口）** | 已运行 |
| **8081–8099** | **核心服务** | auth 8081、job 8082、workflow 8083、form 8084、file 8085、pay 8086、**agent 8090、im 8091、ai 8092（Python）、archive 8093、message 8094、doc 8095（M1 骨架）、music 8096（M0–M5 已落地）、scene 8097（M1/M2 2D 已落地）、video 8098（M0–M5 已落地）**（8087 已随 `whale-i18n` 服务删除释放，Wave 5.50） |
| **8101–8199** | **qiankun 子应用 + 运维端** | portal 8101、erp 8102、oa 8103、ops 8104、mes 8105、pdm 8106、**im 8107、pm 8108、contract 8109、doc 8110、music 8111（M0–M5 已落地）、scene 8112（M1/M2 已落地）、crm 8113（后端 M0/M1 已落地，前端规划）、video 8114（M0–M5 已落地）** |
| **8848 / 8849 / 9848 / 9849** | **Nacos** | 已运行 |
| **9001–9099** | **业务应用** | erp 9001、mes 9002、pdm 9003、oa 9004、**pm 9005、contract 9006、crm 9007（M0/M1 已落地）** |
| **9101–9199** | **运维（ops）** | audit 9101、monitor 9102、alert 9103 |
| **9201–9299** | **注册与配置** | config 9201 |

---

## 八、环境变量覆盖（部署时按此覆盖，勿改源码）

| 变量 | 作用 | 默认 | 使用方 |
|---|---|---|---|
| `SERVER_PORT` | 服务端口 | 见 §一 | 全部后端服务（含 `whale-auth`，已外置为 `${SERVER_PORT:8081}`） |
| `NACOS_SERVER` / `NACOS_ADDR` | 注册中心地址 | `127.0.0.1:8848` | 全部服务（统一首选 `NACOS_SERVER`；`NACOS_ADDR` 仅作兼容回退） |
| `NACOS_NAMESPACE` | 命名空间 | `public` | 全部服务 |
| `PROMETHEUS_HOST_PORT` / `ALERTMANAGER_HOST_PORT` / `GRAFANA_HOST_PORT` | 可观测性端口 | `9090` / `9093` / `3000` | compose `bw-prometheus` / `bw-alertmanager` / `bw-grafana` |
| `PROMETHEUS_RETENTION` | 指标保留期 | `15d` | compose `bw-prometheus` |
| `GRAFANA_ADMIN_PASSWORD` | Grafana 管理员口令 | `admin`（仅供本机） | compose `bw-grafana` |
| `REDIS_HOST` / `REDIS_PORT` | Redis | `127.0.0.1` / `6379` | whale-gateway、whale-service-auth、**whale-im**（seq/ticket/presence） |
| `MYSQL_USER` / `MYSQL_PASSWORD` | 库账号 | 见各 yml | whale-service-auth、whale-audit、**whale-im**、**whale-message** |
| `WHALE_CONFIG_ENC_KEY` | 配置密文 `ENC(...)` 解密密钥（REQ-SEC-020）；至少 16 字符，生产必须由密钥管理系统注入 | 空（存在密文则启动失败） | 引入 `whale-config-crypto` 的服务（auth 已接入，其余按阶段铺开） |
| `WHALE_AUTH_PASSWORD_MAX_AGE_DAYS` / `WARN_DAYS` | 全局口令有效期 / 提前提醒天数（REQ-SEC-023） | `90` / `7` | whale-service-auth；租户可用 `PUT /tenant/password-policy` 覆盖（1–365 天） |
| `WHALE_AUTH_PASSWORD_HISTORY_SIZE` / `MIN_LENGTH` | 历史口令保留条数 / 最小长度（REQ-SEC-021） | `5` / `12` | whale-service-auth |
| `WHALE_AUTH_PASSWORD_FORCE_CHANGE` / `NOTICE_CRON` | 新建或重置后强制首登改密 / 到期提醒 cron | `true` / `0 0 2 * * ?` | whale-service-auth |
| `WHALE_FLYWAY_ENABLED` / `WHALE_FLYWAY_BASELINE_ON_MIGRATE` | Flyway 开关 / 存量库自动 baseline（REQ-SEC-022，**22 个服务库已启用**：auth + config/audit/alert/workflow/form/agent/doc/file + 12 个业务库 + video） | `true` / `true` | 各服务 `db/migration`（V1 基线起；auth V1–V3、workflow/contract 另有 V2 增量；`db/README.md` 说明脚本边界） |
| `WHALE_MESSAGE_MOCK` | 消息外部通道 mock 开关 | `true` | whale-message（生产接入真实渠道时置 `false`） |
| `WHALE_MESSAGE_EMAIL_ENABLED` / `WHALE_MESSAGE_EMAIL_FROM` | 邮件渠道 | `false` / 空 | whale-message |
| `WHALE_MESSAGE_SMS_ENABLED` / `WHALE_MESSAGE_SMS_URL` | 短信渠道（部署侧网关） | `false` / 空 | whale-message |
| `WHALE_MESSAGE_IM_ENABLED` / `WHALE_MESSAGE_IM_BASE_URL` | IM 渠道（内网 whale-im） | `false` / 空 | whale-message |
| `WHALE_AI_BASE_URL` | Python 智能体内网地址 | `http://127.0.0.1:8092` | whale-agent（Wave 5.52） |
| `WHALE_AGENT_QA_ENGINE` | 问答引擎开关 | `legacy` | whale-agent（`legacy`/`langchain`） |
| `WHALE_DOC_COLLAB_ENABLED` | 文档实时协作开关（M2） | `false` | whale-doc；关闭时回退 revision 乐观锁 |
| `WHALE_DOC_APPROVAL_ENABLED` | 文档发布审批开关（M3） | `false` | whale-doc；关闭时发布直接生效 |
| `WHALE_DOC_CONTENT_MAX_BYTES` | 单页正文 JSON 上限 | `2097152`（2MB） | whale-doc |
| `WHALE_DOC_ATTACHMENT_MAX_BYTES` | 单附件上限（透传文件服务） | `52428800`（50MB） | whale-doc |
| `WHALE_SCENE_3D_ENABLED` | 场景 3D 增强层开关（M3） | `false` | whale-scene（关闭时仅 2D 导航，前端自动降级） |
| `WHALE_SCENE_NAV_MAX_NODES` | 单场景节点上限 | `200` | whale-scene（超过拒绝发布） |
| `JWT_SECRET` | 认证签名密钥 | 开发占位（非 dev 拒绝启动） | whale-service-auth |
| `WHALE_GATEWAY_JWT_SECRET` | 网关校验密钥 | 开发占位 | whale-gateway |
| `SPRING_PROFILES_ACTIVE` | 环境 | `dev` | 全部服务 |

> ⚠ 注意：`NACOS_ADDR`（网关）与 `NACOS_SERVER`（其余服务）**两个变量名不一致**，见 §九 偏差 C-05。

---

## 九、待办与偏差

| 编号 | 问题 | 优先级 | 处置建议 |
|---|---|---|---|
| **C-01** | ~~前端 dev 端口与后端冲突：`frontend/admin` 8080 = 网关 8080、`frontend/ops` 8081 = `whale-auth` 8081~~ | **已解** | **✅ 已整改**：admin → **8001**、ops → **8104**，与后端端口区段无冲突 |
| **C-02** | ~~`whale-auth` 端口硬编码 `server.port: 8081`~~ | **已解（2026-10-26）** | **✅ 已整改**：改为 `${SERVER_PORT:8081}`，服务端口全部可外置覆盖 |
| **C-03** | ~~`whale-gateway` **缺 5 条路由**：`/erp/**`、`/mes/**`、`/pdm/**`、`/oa/**`、`/config/**`~~ | **已解（2026-10-25）** | **✅ 已整改**：5 条路由已写入 `whale-gateway/src/main/resources/application.yml`，统一 `lb://`（自持一级前缀，不含 `StripPrefix`）；基座同步挂载 `/mes/*`、`/pdm/*` 并补菜单/图标；路由冒烟需服务与 Nacos 就绪后执行 |
| **C-04** | ~~Nacos 版本/端口不一致：本机 3.2.4（控制台 8849）vs compose 镜像 2.3.2（控制台 8848）~~ | **已解（2026-10-26；2026-10-30 升级闭环）** | **✅ 已整改**：以本机实装 **2.4.0** 为准，compose `NACOS_IMAGE` 统一 `nacos/nacos-server:v2.4.0`，健康检查 `/nacos/actuator/health`；本机监听 8848/9848（无 8849 独立控制台）。**口径变更（2026-10-30，T-16 拍板）**：统一升级 Nacos **3.x（v3.2.4**，Wave 5.1f/5.1g 时期验证过的 3.x 版本）——compose `NACOS_IMAGE` 默认值、`.env.example`、check-env 提示已同步。**本机实机升级已完成（2026-10-30）**：`tools/nacos` 即为 3.2.4 解压体（MANIFEST `Implementation-Version: 3.2.4`、`Build-Jdk-Spec 17`、Spring Boot 3.5.14，含 `plugins/` 的 default-auth/oidc/datasource 插件），`scripts/dev/whale-stack*.ps1` 写死的 `D:\tools\installer\nacos` 已改为工作区 `tools/nacos`；standalone + 外置 MySQL（`bluewhale_nacos`）实测启动，8848 主服务与 8849 控制台监听、日志 `Nacos started successfully in stand alone mode with external storage`。仅剩 B-01：`docker compose config` 与容器内健康检查验证 |
| **C-05** | ~~注册中心环境变量名分裂：网关 `NACOS_ADDR` vs 其余 `NACOS_SERVER`~~ | **已解（2026-10-26）** | **✅ 已整改**：`whale-gateway`/`whale-auth` 改为 `${NACOS_SERVER:${NACOS_ADDR:127.0.0.1:8848}}`，统一首选 `NACOS_SERVER` 且保留 `NACOS_ADDR` 回退；`scripts/dev/whale-start-all.ps1` 同步传 `NACOS_SERVER` |
| **C-06** | ~~8 个业务服务库在规范 §八 已登记库名，但 `application.yml` **均未配数据源**、库亦未建~~ | **已解（2026-10-27）** | **✅ 已整改**：auth/audit/job/workflow/form/file/pay/alert 共 8 个服务均已配数据源并建库（原 `whale-i18n` 服务及 `bluewhale_i18n` 库已于 Wave 5.50 删除，国际化改由 SDK 在各服务库内自建 `sys_i18n_message`）；`erp/mes/pdm/oa` 四个业务应用也已分别接入 `bluewhale_erp` / `bluewhale_mes` / `bluewhale_pdm` / `bluewhale_oa` 独立数据源，compose 初始化脚本创建 4 库，各应用 `db/init.sql` 各建 2 张表。**C-06 全部闭环** |
| **C-07** | ~~Prometheus/Grafana/Sentinel 仅在文档中「规划」，compose 未定义、网关未接 Sentinel~~ | **已解（2026-10-26）** | **✅ Prometheus/Grafana/Alertmanager 已落地**：compose 新增 `bw-prometheus`(9090)/`bw-grafana`(3000)/`bw-alertmanager`(9093)，配置 `infrastructure/prometheus/prometheus-docker.yml`、`alertmanager-docker.yml`、Grafana provisioning 数据源，规则复用 `deploy/monitoring/prometheus-rules.yml`；实机 healthy，宿主机 gateway/auth/workflow/contract 抓取 up。**Sentinel 结案不引入**（见 [GW-01 评估](reviews/2026-10-21-wave5.35-sentinel-evaluation.md)）：限流由网关 `RateLimitGlobalFilter`（Redis 固定窗口，`whale.gateway.rate-limit.*`，429）承担，熔断由 `CircuitBreakerGlobalFilter`（resilience4j，`whale.gateway.circuit-breaker.*`，503）承担；重开条件为具备制品库/Dashboard 或出现热点参数、集群流控等现有方案不覆盖的诉求。**实机验证（2026-10-26）**：`/audit/log/behavior/batch` 特化规则 30/s，并发 60 次得 30 个 429 + 30 个 503；全局规则 200/s，并发 400 次得 18 个 429；`whale.gateway.rate-limit` 计数键正常落 Redis，无降级告警 |
| **C-08** | `whale-scene` M1/M2 已落地；`whale-crm` M0/M1 已落地 | **部分落地** | Wave 5.77 场景（2D 优先）与 Wave 5.83 CRM 首切片（M0 底座 + M1 客户主数据）均已交付；CRM M2–M6 见 [Wave 5.83 计划](plans/2026-11-04-wave5.83-crm-panorama.md) §12 |
| **C-09** | `whale-mes` M0 已交付车间主数据与工单模型，调度/执行/追溯/质量/设备/采集/绩效仍缺失 | M0 已落地，M1 起规划 | Wave 5.79；M0 车间主数据（权限 1100–1103 / 菜单 240–241 / 角色 20703–20707）已落地；M1 调度执行、M2 物料追溯、M3 质量、M4 设备采集、M5 人员绩效待启动；权限 1100–1399 / 菜单 240–299 预留；详见 [Wave 5.79 计划](plans/2026-11-04-wave5.79-mes-panorama.md)、[MES 设计稿](design/mes-module-panorama.md) 与 [M0 检查报告](reviews/2026-11-04-wave5.79-mes-m0.md) |
| **C-10** | `whale-pdm` 原仅产品档案占位 CRUD；图文档/BOM/变更/工艺/协同/集成 M0–M5 已落地，零件去重与 ERP 只读仍缺失 | **部分收口** | Wave 5.80：M0 图文档、M1A 零件库/EBOM 发布、M1B 多视图/对比、M2-1 版本值对象、M2-2 ECR、M2-3 ECO、M2-4 ECN、M2-5 OA 审批回调、M2-6 追溯、M2-7 到期任务、M2-8 通知、M3 工艺与 MES 下发、M4 交付物/评审/预览、M5 集成收口（CAD 检入/EBOM 导入、ERP 物料同步、BOM/MES/OA 对账与补偿）已完成；M1B 零件去重与 ERP 只读价格/周期/库存、CAD 插件本体、MES 撤回对账页待做；权限 900–1099 / 菜单 180–219 / 角色 21103–21107 分段使用；详见 [Wave 5.80 计划](plans/2026-11-04-wave5.80-pdm-panorama.md) 与 [PDM 设计稿](design/pdm-module-panorama.md) |
| **C-11** | `whale-crm` 已交付 M0 底座 + M1 客户主数据（客户 / 联系人 / 地址 / 画像 / 公海 / 跟进 / 360）；销售 / 商务 / 售后 / 营销 / 分析仍缺失 | **部分落地** | Wave 5.83；M0/M1 后端已落地（门禁默认开启），M2–M6 按计划推进；权限 1800–1899（已用 1800、1810–1830）/ 菜单 410–449（未登记）/ 角色 25128–25132 已登记；详见 [Wave 5.83 计划](plans/2026-11-04-wave5.83-crm-panorama.md) 与 [CRM 设计稿](design/crm-module-panorama.md) |
| **C-12** | `whale-oa` 已落地审批底座、审批中心（四类列表/详情/动作/催办/撤销/代理/监控）与请假闭环（类型/额度/日历/档案），考勤/报销/协同/公文/文化/人员异动/报表缺失 | **进行中** | Wave 5.82；M0 + M1A-1/2/3/5/6/7 已实现（2026-11-06），M1A-4 通知与对账待做，M1B–M6 按计划推进；权限 1600–1799 / 菜单 360–409 / 角色 25123–25127 预留；详见 [Wave 5.82 计划](plans/2026-11-04-wave5.82-oa-panorama.md) |

> 说明：C-04 口径现为 Nacos **3.2.4**（本机 `tools/nacos` 与 compose 一致）；历史记录中的 2.4.0/2.3.2 口径作废。

---

## 十、维护方式

1. **新增服务/组件**：先在本清单登记（端口从 §七 对应区段内分配），再落代码/配置；同时按 [naming-conventions.md](naming-conventions.md) §八 登记域词。
2. **改端口**：同步更新本清单 §一/§四/§五/§七 + `application.yml`/`vite.config.ts`，并跑一次冒烟。
3. **门禁**：代码检查清单 **E15** 已含「库/服务/包命名符合性」；服务端口与对外前缀的一致性由本清单兜底核对。
