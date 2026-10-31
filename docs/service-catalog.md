# 服务与端口清单（Service Catalog）

> 用途：**全平台端口 ↔ 服务 ↔ 组件**的唯一权威清单。看端口即知对应哪个服务，看服务即知其分组、库、对外前缀与职责。
> 关联需求：REQ-ARCH-005（新增）、REQ-ARCH-004（命名规范，[naming-conventions.md](naming-conventions.md) §八）
> 最后更新：2026-10-31（**Wave 5.75 M5 安全与上线硬化**：越权矩阵统一不泄露存在性、上传白名单、版权来源必填与下架留痕、网关票据 / 点赞限流、SLO-6/7/8 + Grafana 音乐面板、50 并发流式首字节 P95 216–220ms、运维手册 `ops/music-service-ops.md`、CI 镜像矩阵接入，见 [M5 检查报告](reviews/2026-10-31-wave5.75-music-m5.md)）｜ 2026-10-31（**Wave 5.75 M4 互动**：评论发表/父子回复、审核队列与通过/隐藏、我的音乐、可选通知，见 [M4 检查报告](reviews/2026-10-31-wave5.75-music-m4.md)）｜ 2026-10-31（**Wave 5.75 M3 歌单与发现**：用户/官方歌单、首页聚合、热门/新歌榜、搜索、播放计数回刷，见 [M3 检查报告](reviews/2026-10-31-wave5.75-music-m3.md)）｜ 2026-10-31（**Wave 5.75 M2 播放与音频流**：播放票据（Redis / TTL 120s / 三要素绑定）、`/music/stream/**` Range 流式（200 / 206 / 416）、进度 / 最近播放 / 收藏幂等、全局播放器（播放条 / 队列 / 歌词），冒烟 `SMOKE-ALL-PASS`，见 [M2 检查报告](reviews/2026-10-31-wave5.75-music-m2.md)）｜ 2026-10-31（**Wave 5.75 M1 前端落地**：admin 曲库 / 歌手 / 专辑 / 标签页与 `whale-music` 子应用（公开曲库 / 上传 / 我的作品）已落盘并接入基座；权限号修正为 **281–303**，见 [M1 检查报告](reviews/2026-10-31-wave5.75-music-m1.md)）｜ 2026-10-31（**Wave 5.75 M0 骨架**：`whale-music` 8096 / `/music/**` / `bluewhale_music` 11 表 DDL、网关路由与权限/菜单种子（281–303 / 39–44）落地，见 [检查报告](reviews/2026-10-31-wave5.75-music-m0.md)）｜ 2026-10-31（**Wave 5.75 音乐平台规划**：新增 `whale-music` 8096 / 前端 8111 / `/music/**` / `bluewhale_music`，见 [实施计划](plans/wave5.75-music-platform.md)）｜ 2026-10-29（**Wave 5.74 M0–M5 交付**：`whale-doc` 8095 / 前端 8110 / `/doc/**` / `bluewhale_doc` 全量业务与运维能力落地，见 [实施计划](plans/2026-10-29-wiki-doc-service.md)）｜ 2026-10-29（**Wave 5.74 M1 骨架**：新增 `whale-doc` 8095、前端 `whale-doc` 8110、网关 `/doc/**`、库 `bluewhale_doc`、权限/角色/菜单迁移）｜ 2026-10-28（**Wave 5.69–5.70**：ERP 新增供应商主数据；`whale-message` M1 增模板管理/失败重试/站内信收件箱；**F-05 结案** `whale-config` 冻结且移除 `/config/**` 路由）｜ 维护：BlueWhale

---

## 〇、口径说明

| 项 | 说明 |
|---|---|
| 数据来源 | 实测核对：各服务 `src/main/resources/application.yml` 的 `server.port` / `spring.application.name` / `datasource.url`、`whale-gateway/application.yml` 路由、各前端 `vite.config.ts` 的 `server.port`、`infrastructure/docker/docker-compose*.yml`、本机 `Get-NetTCPConnection` 实监听核验 |
| 「本机状态」含义 | **已运行**＝本机实测监听/注册过；**可启动**＝模块已具备启动条件、尚未在本机启动；**规划**＝仅设计登记，无实现 |
| 端口分段 | 见 §七；服务端口一律用 `${SERVER_PORT:xxxx}` 或 `${server.port:xxxx}` 外置可覆盖 |
| 命名铁律 | `模块目录 == artifactId == spring.application.name == Nacos 服务名 == whale-<域>`（[naming-conventions.md](naming-conventions.md) §三） |

---

## 一、后端服务清单（24 个：Java 23 + Python 1）

| # | 服务名（Spring / Nacos） | 所属分组 | 模块目录 | 端口 | 对外一级前缀 | 网关路由 | 数据库（`bluewhale_<域>`） | 启动类 | 职责 | 本机状态 |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `whale-gateway` | whale-services | `whale-services/whale-gateway` | **8080** | ——（唯一入口） | —— | 无（仅 Redis 存放黑名单） | `WhaleGatewayApplication` | 统一入口：路由转发、JWT 鉴权、令牌黑名单校验、TraceId | **已运行**（已在 Nacos 注册） |
| 2 | `whale-service-auth` | whale-services | `whale-services/whale-service-auth`（starter 模块 `whale-service-auth-starter`） | **8081** | `/auth/**`（另自持 `/user`、`/dept`、`/role`、`/menu`、`/permission`） | ✅ `lb://whale-service-auth` | **`bluewhale_auth`**（13 表，已建） | `WhaleAuthApplication` | 认证与权限中心：登录/刷新/注销、RBAC+ABAC、用户/部门/角色/菜单/权限 | **已运行**（已在 Nacos 注册） |
| 3 | `whale-job` | whale-services | `whale-services/whale-job` | **8082** | `/job/**` | ✅ `lb://whale-job` | **`bluewhale_job`**（已建库） | `WhaleJobApplication` | 定时任务调度 | 可启动 |
| 4 | `whale-workflow` | whale-services | `whale-services/whale-workflow` | **8083** | `/workflow/**` | ✅ `lb://whale-workflow` | **`bluewhale_workflow`**（已建库） | `WhaleWorkflowApplication` | 工作流引擎（审批流） | 可启动 |
| 5 | `whale-form` | whale-services | `whale-services/whale-form` | **8084** | `/form/**` | ✅ `lb://whale-form` | **`bluewhale_form`**（已建库） | `WhaleFormApplication` | 动态表单 | 可启动 |
| 6 | `whale-file` | whale-services | `whale-services/whale-service-file`（启动模块 `whale-service-file-web`） | **8085** | `/file/**` | ✅ `lb://whale-file` | **`bluewhale_file`**（已建库） | `WhaleFileApplication` | 文件上传下载、附件管理；下载 / 预览 / 内网内容接口支持 HTTP Range（206 / 416 + `Content-Range` + `ETag`） | 可启动 |
| 7 | `whale-pay` | whale-services | `whale-services/whale-pay` | **8086** | `/pay/**` | ✅ `lb://whale-pay` | **`bluewhale_pay`**（4 表：`pay_order`/`pay_transaction`/`audit_log`/`api_log`） | `WhalePayApplication` | 支付与结算：支付单、mock 渠道支付/退款、渠道流水与对账 | 可启动（2026-10-28 本地起服验证通过） |
| 8 | `whale-erp` | whale-apps | `whale-apps/whale-erp` | **9001** | `/erp/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_erp`**（4 表：`erp_item`/`erp_supplier`/`audit_log`/`api_log`） | `WhaleErpApplication` | ERP 业务应用：物料主数据 + 供应商主数据（供应商准入/资质/绩效归 SRM，不属 CRM） | 可启动 |
| 9 | `whale-mes` | whale-apps | `whale-apps/whale-mes` | **9002** | `/mes/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_mes`**（2 表，已建） | `WhaleMesApplication` | MES 制造执行 | 可启动 |
| 10 | `whale-pdm` | whale-apps | `whale-apps/whale-pdm` | **9003** | `/pdm/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_pdm`**（2 表，已建） | `WhalePdmApplication` | PDM 产品数据管理 | 可启动 |
| 11 | `whale-oa` | whale-apps | `whale-apps/whale-oa` | **9004** | `/oa/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_oa`**（2 表，已建） | `WhaleOaApplication` | OA 协同办公 | 可启动 |
| 12 | `whale-audit` | whale-ops | `whale-ops/whale-audit` | **9101** | `/audit/**` | ✅ `lb://whale-audit` | **`bluewhale_audit`**（`audit_log`，已建） | `WhaleAuditApplication` | 审计日志落库与查询 | **已运行**（已在 Nacos 注册） |
| 13 | `whale-monitor` | whale-ops | `whale-ops/whale-monitor` | **9102** | `/monitor/**` | ✅ `lb://whale-monitor` | ——（只读聚合） | `WhaleMonitorApplication` | 服务实例只读聚合（Nacos 实例列表/健康）；指标采集与存储由外部 Prometheus 承担，不落库、不告警 | 可启动 |
| 14 | `whale-alert` | whale-ops | `whale-ops/whale-alert` | **9103** | `/alert/**` | ✅ `lb://whale-alert` | **`bluewhale_alert`**（已建库） | `WhaleAlertApplication` | 告警通知 | 可启动 |
| 15 | `whale-config` | whale-registry | `whale-registry/whale-config` | **9201** | `/config/**` | ✅ `lb://whale-config`（Wave 5.73 F-05 解冻恢复） | **`bluewhale_config`**（4 表：`config_item`/`config_item_history`/`audit_log`/`api_log`） | `WhaleConfigApplication` | 业务配置管理：配置项 CRUD（租户内键唯一）、版本历史、一键回滚（回滚产生新版本）；与 Nacos 互补——Nacos 管服务装配配置，本服务管业务应用配置 | **已运行**（Wave 5.73 解冻，本地起服验证通过） |
| 16 | `whale-agent` | whale-services | `whale-services/whale-agent` | **8090** | `/agent/**` | ✅ `lb://whale-agent` | **`bluewhale_agent`**（6 表，已建） | `WhaleAgentApplication` | 平台与项目知识 Agent（Lucene 检索 + 事实层 + 问答编排） | **已运行**（已在 Nacos 注册，Wave 5.47） |
| 17 | `whale-im` | whale-services | `whale-services/whale-im` | **8091** | `/im/**`、`/im/ws` | ✅ `lb://whale-im` / `lb:ws://whale-im` | **`bluewhale_im`**（5 表，**规划**） | `WhaleImApplication` | 自研 WebIM：会话/消息/已读/在线/机器人会话；WS ticket 鉴权 | **规划**（Wave 5.52） |
| 18 | `whale-archive` | whale-services | `whale-services/whale-archive` | **8093** | `/archive/**` | ✅ `lb://whale-archive` | **`bluewhale_archive`**（6 表，脚本已就绪） | `WhaleArchiveApplication` | 档案服务：门类/规则/条目/档号/快照/检索；只读拉取工作流已通过实例与表单附件 | **可启动**（Wave 5.53 M1 底座完成，未起服） |
| 19 | `whale-ai`（Python / FastAPI） | ——（`ai/` 独立树，**不进 Maven reactor**） | `ai/whale-ai` | **8092** | 无（**仅内网**，不注册网关、不注册 Nacos） | ❌ 不配（由 `whale-agent` 内网调用） | 无（记忆存 Redis `whale:ai:memory:*`） | `main:app`（uvicorn） | LangChain/LangGraph 编排、工具循环、确认态；**不持业务库凭据** | **骨架已落地**（Wave 5.52 T10；pytest 待 Python 3.11/3.12 环境） |
| 20 | `whale-pm` | whale-apps | `whale-apps/whale-pm` | **9005** | `/pm/**` | ✅ 已配路由（2026-10-25） | **`bluewhale_pm`**（4 表 DDL 就绪，**待建库**） | `WhalePmApplication` | 项目管理（对标 Jira/禅道）：项目空间/成员/状态流转（M1）、工作项（M2）、迭代看板（M3）、报表（M4）、站内通知 + 工作流审批（M5，默认关闭） | **可启动**（Wave 5.55 M1–M5 完成；未起服） |
| 21 | `whale-contract` | whale-apps | `whale-apps/whale-contract` | **9006** | `/contract/**` | ✅ `lb://whale-contract`（2026-10-26） | **`bluewhale_contract`**（4 表 + `api_log`） | `WhaleContractApplication` | 合同管理：起草/相对方/版本/审批/终止/归档（M1）；印章/PDF 盖章/数字签名/三方签章（M2–M3） | **可启动**（Wave 5.61 M1 完成） |
| 22 | `whale-message` | whale-services | `whale-services/whale-message` | **8094** | `/message/**` | ✅ `lb://whale-message`（2026-10-28） | **`bluewhale_message`**（5 表：`msg_template`/`msg_record`/`msg_delivery`/`audit_log`/`api_log`） | `WhaleMessageApplication` | 统一消息服务：模板渲染/渠道路由/幂等/投递记录（M0）；模板管理、失败重试、站内信收件箱（M1）；INAPP/EMAIL/SMS/IM 适配器，无供应商时默认 mock | **可启动**（Wave 5.68 M0 + 5.70 M1；2026-10-28 本地起服验证通过） |
| 23 | `whale-doc` | whale-services | `whale-services/whale-doc` | **8095** | `/doc/**` | ✅ `lb://whale-doc`（2026-10-29，M2 增 `/doc/ws`） | **`bluewhale_doc`**（16 业务表：M1 7 + M2 7 + M3 2，另 `audit_log`/`api_log`） | `WhaleDocApplication` | 在线文档 / Wiki：空间与成员、页面树、TipTap 富文本、自动保存与 revision 冲突、版本/评论/附件、搜索、页面 ACL、标签/反链/动态/最近访问、ngram 检索、协作/回收站、模板/导入导出/审批/通知/分享/归档、Agent 知识索引；M5 指标/清理/备份/运维 | **M1–M5 已交付**（Wave 5.74；239 项 smoke + 备份恢复演练通过） |
| 24 | `whale-music` | whale-services | `whale-services/whale-music` | **8096** | `/music/**` | ✅ `lb://whale-music`（2026-10-31，M0 增 `/music/stream/**` 限流与 `/music/internal/**` 屏蔽，M5 增票据 / 点赞限流，四条规则 8080 实机 429 已验证） | **`bluewhale_music`**（14 张业务表 + `audit_log` / `api_log`，已建库；M5 增 `mu_song.copyright_source`、`mu_song_appeal`、`idx_mu_history_played`） | `WhaleMusicApplication` | 音乐平台：歌手/专辑/歌曲/标签曲库、**播放票据与 Range 流式（M2 已落地）**、播放进度 / 收藏、歌单、首页/榜单（**热榜近 7 天滚动窗口**）/搜索、评论审核、**版权下架与申诉复核（M5-3）**；所有登录用户可上传（签署版权无争议协议 + 公开/自听可见性） | **可上线**（Wave 5.75 M0–M5 含收口轮；8096 起服，M2–M5 冒烟、申诉闭环、429 与备份演练均 PASS，SLO-6/7/8 已接入 Prometheus / Grafana） |

> 「对外一级前缀」= 服务自持的前缀，网关**只转发、不剥离**（REQ-ARCH-003）。
> 网关 `discovery.locator.enabled=true` 已开启服务发现路由：`lb://whale-<域>` → `http://<服务实例>`。

---

## 二、网关路由映射（`whale-gateway`，现配 24 条）

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

**网关免鉴权白名单**：`/auth/login`、`/auth/refresh`、`/actuator/**`、`/v3/api-docs/**`、`/doc.html`、`/webjars/**`、`/favicon.ico`、`/audit/log/frontend/batch`、`/audit/log/behavior/batch`（后两条为前端/行为日志上报，Wave 5.19 起仅放行 batch 写入端点）、`/doc/ws`（WS 不携带 Authorization 头，改走 60s 一次性 ticket）。

---

## 三、前端应用清单（16 个，开发态端口；含 1 个规划）

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

共享包（不占端口）：`frontend/shared-packages/whale-common-tools`、`whale-common-theme`、`whale-log-sdk`、`frontend/elemental/whale-ui`（NPM scope `@bluewhale/`）。
冻结共享包（无消费方，不删除代码、不升级依赖）：`frontend/shared-packages/whale-common-three`（F-09）、`frontend/games/whale-games-core`（F-07）。

---

## 四、第三方 / 基础设施组件清单（含端口）

| # | 组件 | 用途 | 端口 | 版本 | 部署方式 | 本机状态 |
|---|---|---|---|---|---|---|
| 1 | **MySQL** | 业务库 + Nacos 外置存储 | **3306**（TCP） | 8.0.45（本机 `D:\mysql8`）／镜像 8.4（compose） | 本机原生；compose `bw-mysql` | **已运行**。库：`bluewhale_auth`、`bluewhale_audit`、`bluewhale_nacos`、`bluewhale_agent`、**`bluewhale_im`（规划，Wave 5.52）**、**`bluewhale_message`（M0，脚本已就绪）**、**`bluewhale_doc`（Wave 5.74，已建）**、**`bluewhale_music`（Wave 5.75 M0–M5，已建库 14 表）**（另有外部遗留 `yc-sts-db-auth` / `yc-sts-db-flow`，登记不改名） |
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
| **8848** | Nacos | 基础设施 | HTTP OpenAPI + 主服务（contextPath `/nacos`） |
| **8849** | Nacos Console | 基础设施 | 控制台（仅 Nacos 3.x；本机 2.4.0 无独立控制台端口） |
| **8858** | Sentinel Dashboard | 基础设施 | 规划（未引入） |
| **9001** | `whale-erp` | 后端 | ERP |
| **9002** | `whale-mes` | 后端 | MES |
| **9003** | `whale-pdm` | 后端 | PDM |
| **9004** | `whale-oa` | 后端 | OA |
| **9005** | `whale-pm` | 后端 | 项目管理（Wave 5.55 M1） |
| **9006** | `whale-contract` | 后端 | 合同管理（Wave 5.61 M1） |
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
| **8081–8099** | **核心服务** | auth 8081、job 8082、workflow 8083、form 8084、file 8085、pay 8086、**agent 8090、im 8091、ai 8092（Python）、archive 8093、message 8094、doc 8095（M1 骨架）、music 8096（M1 后端已落地）**（8087 已随 `whale-i18n` 服务删除释放，Wave 5.50） |
| **8101–8199** | **qiankun 子应用 + 运维端** | portal 8101、erp 8102、oa 8103、ops 8104、mes 8105、pdm 8106、**im 8107、pm 8108、contract 8109、doc 8110、music 8111（M1 页面已落地）** |
| **8848 / 8849 / 9848 / 9849** | **Nacos** | 已运行 |
| **9001–9099** | **业务应用** | erp 9001、mes 9002、pdm 9003、oa 9004、**pm 9005、contract 9006** |
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

> 说明：C-04 口径现为 Nacos **3.2.4**（本机 `tools/nacos` 与 compose 一致）；历史记录中的 2.4.0/2.3.2 口径作废。

---

## 十、维护方式

1. **新增服务/组件**：先在本清单登记（端口从 §七 对应区段内分配），再落代码/配置；同时按 [naming-conventions.md](naming-conventions.md) §八 登记域词。
2. **改端口**：同步更新本清单 §一/§四/§五/§七 + `application.yml`/`vite.config.ts`，并跑一次冒烟。
3. **门禁**：代码检查清单 **E15** 已含「库/服务/包命名符合性」；服务端口与对外前缀的一致性由本清单兜底核对。
