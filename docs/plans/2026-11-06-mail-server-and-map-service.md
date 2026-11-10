# 方案：自建邮件系统与地图服务（2026-11-06）

> 状态：**第 2 节已被修订**（2026-11-06 20:10）
>
> **修订说明**：原方案把「邮箱」放在 `whale-message` 里做生产化，这个边界是错的。
> `whale-message` 是**消息管控**（SMS / 站内公告 / 邮件通道编排），不是邮箱。
> 邮箱已改为**独立服务 `whale-mail`**（:8099，库 `bluewhale_mail`），
> 详见 [Wave 5.89 邮箱服务 M0](2026-11-06-wave5.89-mail-service-m0.md)。
> 本文第 2 节保留作为背景与 MTA（Mailcow）侧的依据，**结论以新文档为准**；第 3 节地图部分不变。

> 状态：**待评审**
> 决策依据：用户 2026-11-06 明确「邮件要真正的邮箱系统」「地图要能兼容多供应商」
> 关联：[服务与端口清单](../service-catalog.md)、[网络边界](../design/network-boundary.md)、
> [统一消息决策](2026-10-28-crm-srm-and-message-service-decisions.md)

---

## 0. 本轮结论速览

| # | 问题 | 结论 |
|---|---|---|
| 1 | 邮件要不要新建服务 | **不新建 Java 服务**。新建**独立 DMZ 邮件主机**（Mailcow），平台侧仍由 `whale-message` 作为唯一发信出口 |
| 2 | 邮件能否直接对外发信 | **不能**。云厂商封禁出站 25，出站必须走 465/587 中继 |
| 3 | 邮件主机能否与业务同机 | **不能**。它必须公网暴露 25/465/587/993，违反 `network-boundary.md` 的「仅 443 暴露」红线，必须独立 DMZ 主机 |
| 4 | 地图要不要新建服务 | **要**。新建 `whale-map`（:8099，`/map/**`），Key 与坐标系收敛在服务端 |
| 5 | 地图能否兼容多供应商 | **能**。SPI + 能力路由 + 统一坐标模型；但供应商限定在腾讯/高德/百度/天地图白名单内 |
| 6 | 首个地图场景 | **地址解析 + 业务打点**（P0） |

---

## 1. 现状盘点

| 能力 | 现状 | 证据 |
|---|---|---|
| 邮件通道适配器 | 已有，`JavaMailSender` + mock 开关 | `whale-message/.../channel/impl/EmailChannelSender.java` |
| 邮件依赖 | 已引入 starter-mail | `whale-message/pom.xml` |
| `spring.mail.*` | **缺失**，从未配置 | `whale-message/src/main/resources/application.yml` |
| 邮件形态 | 纯文本，`MimeMessageHelper(..., false, ...)` 非 multipart，无 HTML/附件 | 同上 |
| 告警邮件 | 另一套独立实现 | `whale-ops/whale-alert/.../AlertNotifyServiceImpl.java` |
| 地图 | 无。`SceneMap2D` 为 CSS 绝对定位的园区节点图，无经纬度无底图 | `whale-scene/src/components/SceneMap2D.tsx` |
| 端口占用 | 8080–8098 已用，**8099 空闲** | `docs/service-catalog.md` |

---

## 2. 决策一：自建邮件系统

### 2.1 选型

| 方案 | 组成 | 内存 | 管理 | 结论 |
|---|---|---|---|---|
| **Mailcow** | Postfix + Dovecot + Rspamd + ClamAV + Webmail + 管理台 + REST API + ACME | 6–8 GB | Web UI + API | **采用** |
| docker-mailserver | 仅 MTA/MDA，需自配 Roundcube | 1 GB | 命令行 | 备选（极简场景） |
| Mailu | 含 Webmail/管理台 | 2–4 GB | Web UI | 备选 |

采用 Mailcow 的理由：管理台与 REST API 完整，账号同步可直接对接 `whale-auth`；内置 ACME 自动证书与 Rspamd 反垃圾，运维成本最低。

### 2.2 拓扑

- **入站**：公网 MTA → Postfix:25 → Rspamd 过滤 → Dovecot 存储 → Webmail/IMAP
- **出站**：`whale-message` → Postfix:587（认证提交）→ 中继服务商:465 → 外部收件人
- **出站 25 一律不直连**，既规避封禁，也保护 IP 信誉

### 2.3 前置条件（缺一项即进垃圾箱）

| 项 | 要求 | 阻塞性 |
|---|---|---|
| A 记录 | `mail.<domain>` → 邮件主机 IP | 必须 |
| MX | 根域或子域 → `mail.<domain>` 优先级 10 | 必须 |
| SPF | `v=spf1 mx include:<中继商 SPF> ~all` | 必须 |
| DKIM | Mailcow 生成后发布 `dkim._domainkey` | 必须 |
| DMARC | `v=DMARC1; p=quarantine; rua=mailto:dmarc@<domain>` | 必须 |
| PTR | IP 反解 = `mail.<domain>`，与 HELO 一致 | 必须（需云厂商配合） |
| 出站 25 | 申请解封，通常不批 → 走中继 | 已有绕行方案 |
| 入站 25 | 云安全组放行 + 部分区域需备案 | 必须 |

**注意**：若根域 MX 已指向企业邮箱，**不要直接改根域**，改用子域（如 `@mail.corp.<domain>`）承载员工邮箱，或整体迁移后再切换，避免现有邮件中断。

### 2.4 与平台集成

| 集成点 | 方式 |
|---|---|
| 账号开通 | `whale-auth` 建用户 → 调 Mailcow API `POST /api/v1/add/mailbox`，随机初始密码、首登强改 |
| 组织/别名 | 部门 → Mailcow alias / 共享邮箱 |
| 离职停用 | API 禁用邮箱，数据保留 N 天后归档 |
| 系统通知 | **独立发件域** `noreply@<domain>` 直走中继，**不进员工邮箱** |
| 邮件归档 | Mailcow BCC 到归档邮箱，满足留存要求 |
| Webmail 入口 | **独立域名，不接入 qiankun**（Cookie 域与 XSS 隔离） |

### 2.5 安全与运维

- DMZ 独立主机，仅开 25/80/443/465/587/993（关闭明文 143/110）
- 管理台 443 加 IP 白名单；fail2ban + Mailcow netfilter
- 出站限速：单账号每小时 N 封，防账号被盗群发
- 备份：`backup_and_restore.sh`（MySQL + vmail 卷），异地存一份
- 监控接现有 Prometheus：postfix_exporter / rspamd exporter，指标含队列长度、25 连通性、RBL 黑名单命中
- 合规：日志留存 ≥ 6 个月；邮件内容归档按行业要求

### 2.6 验收

1. 外域 Gmail/163 发信到 `user@<domain>`，Webmail 可读，SPF/DKIM/DMARC 全 pass
2. 内部发信到外域，`mail-tester.com` 评分 ≥ 9/10
3. `whale-message` 系统通知送达且不落入垃圾箱
4. 账号开通/停用与 `whale-auth` 状态一致
5. 队列积压、25 端口中断有告警

### 2.7 周期

- 技术实施 **5–7 人日**（部署 1 + 集成 2–3 + 监控备份 1 + 联调 1）
- **IP 预热 2–4 周**，不可压缩；期间控制日发信量逐步爬坡

---

## 3. 决策二：地图服务（多供应商兼容）

> **本节状态：M0 已落地（2026-11-06，Wave 5.91）**。实施口径以
> [Wave 5.91 地图服务 M0](2026-11-06-wave5.91-map-service-m0.md) 为准，本节保留作为背景与选型依据。
> 与下文的差异：① 端口 **8100**（原文 8099 已被 `whale-mail` 占用）；
> ② M0 只接腾讯 + 高德两家（同为 GCJ-02），天地图移入 M1、百度按需；
> ③ 模块结构未拆 6 个 Maven 模块，按 `whale-mail` 的做法单模块 + 包分层（provider 用 SPI 接口 + `@Component` 实现注册）；
> ④ `map_region`（围栏）不在 M0，理由见新文档 §4；⑤ 新增 `map_quota_usage`（跨实例配额）。

### 3.1 合规边界（硬约束）

- 供应商白名单：**腾讯地图、高德、百度、天地图**；Mapbox / Leaflet+OSM / Google 一律不可用
- 坐标系：内部统一 `GCJ-02`（展示）与 `WGS84`（存储），百度 BD-09 在适配器边界转换
- Key 仅存服务端（Nacos 或 `whale-config`），前端不得出现明文 Key
- 批量点位含个人信息，轨迹/打卡数据不得对外发布

### 3.2 模块结构

```
whale-backend/whale-services/whale-map/
├── whale-map-api/                 对外 DTO + 内部能力接口
├── whale-map-core/                领域模型、坐标转换、缓存、限流、审计
├── whale-map-provider-tencent/    腾讯位置服务适配
├── whale-map-provider-amap/       高德适配
├── whale-map-provider-tianditu/   天地图适配（离线/合规备选）
└── whale-map-starter/             启动模块（:8099）
```

与 `whale-service-auth` 的 starter 结构保持一致。

### 3.3 统一模型

```java
public interface MapProvider {
    String name();
    Set<Capability> capabilities();
    GeoPoint geocode(AddressQuery query);
    Address regeo(GeoPoint point);
    List<RouteResult> route(RouteQuery query);
    List<Poi> searchPoi(PoiQuery query);
}
```

`GeoPoint(lng, lat, CoordType)` 三要素强制显式声明坐标系。**跨供应商结果必须先统一坐标系再计算距离**，否则数值错误（腾讯/高德为 GCJ-02，百度 BD-09，天地图约 WGS84）。

### 3.4 能力路由与降级

```yaml
whale:
  map:
    default-provider: tencent
    capability-routing:
      GEOCODE: tencent
      ROUTE: amap
    fallback: [amap, tianditu]
    providers:
      tencent: { key: ${TENCENT_MAP_KEY:}, quota-per-day: 10000 }
      amap:    { key: ${AMAP_KEY:},        quota-per-day: 10000 }
```

按能力选供应商（例如逆地理用高德、路径规划用腾讯），主供应商超配额或超时自动降级。降级结果需带 `provider` 字段回传，便于排查与计量。

### 3.5 接口（M0）

```
POST /map/geocode                      地址 → 坐标（带缓存）
POST /map/regeo                        坐标 → 地址
POST /map/distance                     批量距离
POST /map/route                        驾车/步行/骑行
GET  /map/poi/search                   POI 检索
PUT  /map/location/{bizType}/{bizId}   业务对象打点
GET  /map/location/nearby              geohash 前缀 + 半径
GET  /map/provider/health              供应商可用性与配额余量
```

### 3.6 数据表（`bluewhale_map`）

| 表 | 关键字段 |
|---|---|
| `map_location` | `tenant_id, biz_type, biz_id, lng, lat, geohash, address, adcode, coord_type, provider`；唯一键 `(tenant_id, biz_type, biz_id)` |
| `map_geocode_cache` | 地址哈希 → 坐标/标准地址/供应商，带 TTL |
| `map_region` | 行政区与围栏，MySQL 8 `ST_Contains` + POLYGON，无需 PostGIS |

### 3.7 前端

- 新建 `whale-frontend/shared-packages/whale-map`
- 定义 `MapAdapter`（底图/点/线/面/信息窗/视野），实现腾讯 GL JS 与高德 JS API 两套
- SDK 用动态 `<script>` 加载，不进 npm 依赖；容器必须固定高度
- 统一导出 `<WhaleMap>` / `<MarkerLayer>` / `<GeoFencePicker>`，qiankun 子应用共享

### 3.8 平台接入清单

`service-catalog.md` 加行 → `whale-gateway/application.yml` 加 `lb://whale-map`（不 StripPrefix）→ `docker/mysql/init` 建库 → CI 矩阵加模块。

---

## 4. 场景优先级（推荐）

| 优先级 | 场景 | 理由 |
|---|---|---|
| **P0** | 地址解析 + 业务打点 | CRM 联系人、ERP 供应商、OA 出差已有地址字段；只消耗 geocode/regeo，配额便宜；一次落库后续全部复用 |
| P1 | 围栏与区域校验 | 打卡范围、销售区域划分，复用 `map_location` |
| P2 | 路径规划与里程 | 出差里程、拜访路线，单价最高，最后做 |

P0 能同时验证「坐标系转换 + 缓存命中 + 多供应商切换」三件事，是最合适的探路场景。

---

## 5. 排期

| 周 | 邮件 | 地图 |
|---|---|---|
| W1 | 域名/PTR/端口前置、Mailcow 部署 | `whale-map` 骨架 + 腾讯适配器 + geocode/regeo + 缓存 |
| W2 | 账号同步、系统通知分流、监控备份 | 打点接口 + CRM/ERP 接入 |
| W3 | IP 预热开始 | 前端 `whale-map` 包 + 第二个供应商适配器 |
| W4+ | 预热爬坡、归档与等保项 | 围栏（P1） |

---

## 6. 待确认

1. **邮件域名**：根域 MX 是否已被企业邮箱占用？员工邮箱用根域还是子域？
2. **DMZ 主机**：是否已有可独立公网暴露的机器（4C8G / 100G SSD 起）？
3. **中继账号**：用腾讯企业邮还是阿里云 DirectMail（后者退信回调更完整）？
4. **前端 Key 策略**：地图前端走「后端代理签名」还是「前端 Key + Referer 白名单」？
