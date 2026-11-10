# 地图服务运行手册（whale-map）

> 适用版本：Wave 5.91 M0（2026-11-06）
> 服务坐标：**8100** / `/map/**` / 库 **`bluewhale_map`**
> 关联：[M0 计划](../plans/2026-11-06-wave5.91-map-service-m0.md)、[服务与端口清单](../service-catalog.md)

---

## 一、服务概览

| 项 | 值 |
|---|---|
| 进程 | `WhaleMapApplication`（Spring Boot，JDK 21） |
| 端口 | `SERVER_PORT`，默认 **8100** |
| 库 | `bluewhale_map`（Flyway 自动建表，5 张：打点 / 解析缓存 / 配额 / 围栏 / Flyway 历史） |
| 对外前缀 | `/map/**`（网关只转发不 StripPrefix） |
| 内网接口 | M0 无。后续如需内部批量打点，用 `/map/internal/**` 并加入网关 `internal-deny` |
| 依赖 | 腾讯位置服务 WebService API、高德开放平台 Web 服务 API（均出站 443） |
| 被动依赖 | 无数据库以外的中间件；不需要 Redis |

**它不负责什么**：底图与瓦片、路线导航 GUI、轨迹存储。围栏配置与判定由本服务提供（见第六节），但「打卡成功与否」的最终判定属于考勤业务，不在这里。

---

## 二、配置项

| 环境变量 | 默认 | 说明 |
|---|---|---|
| `WHALE_MAP_DEFAULT_PROVIDER` | `tencent` | 能力路由未指定时的供应商 |
| `WHALE_MAP_DISPLAY_COORD` | `GCJ02` | 对外坐标系。**改了要同时清缓存**（缓存键含坐标系，但前端会突然换口径） |
| `WHALE_MAP_STORAGE_COORD` | `WGS84` | 落库坐标系。**M0 起不要改**，已有数据不会跟着转 |
| `WHALE_MAP_ROUTING_GEOCODE` | `tencent` | 地址解析首选供应商 |
| `WHALE_MAP_ROUTING_REGEO` | `tencent` | 逆地理首选 |
| `WHALE_MAP_ROUTING_POI` | `tencent` | POI 首选 |
| `WHALE_MAP_ROUTING_ROUTE` | `amap` | 路径首选 |
| `WHALE_MAP_FALLBACK` | `amap` | 降级顺序（逗号分隔） |
| `TENCENT_MAP_KEY` | 空 | 腾讯 Key。空 = 该供应商视为未配置，自动跳过 |
| `TENCENT_MAP_BASE_URL` | `https://apis.map.qq.com` | 腾讯基址 |
| `WHALE_MAP_TENCENT_QUOTA` | `10000` | 腾讯日配额（0 = 不限） |
| `AMAP_MAP_KEY` | 空 | 高德 Key |
| `AMAP_MAP_BASE_URL` | `https://restapi.amap.com` | 高德基址 |
| `WHALE_MAP_AMAP_QUOTA` | `10000` | 高德日配额 |
| `WHALE_MAP_CONNECT_TIMEOUT_MS` | `2000` | 建连超时 |
| `WHALE_MAP_READ_TIMEOUT_MS` | `5000` | 读超时 |
| `WHALE_MAP_CACHE_ENABLED` | `true` | 解析缓存开关 |
| `WHALE_MAP_CACHE_TTL_HOURS` | `2160` | 缓存 TTL（90 天） |
| `WHALE_MAP_CACHE_MAX_TEXT_LENGTH` | `200` | 超过此长度的查询不缓存 |
| `WHALE_MAP_NEARBY_MAX_RADIUS` | `5000` | 附近查询半径上限（米） |
| `WHALE_MAP_NEARBY_MAX_ROWS` | `200` | 附近查询返回条数上限 |
| `WHALE_MAP_DISTANCE_MAX_BATCH` | `200` | 单次批量距离点数上限 |

### Key 放哪

- **生产**：环境变量注入（容器 secret / K8s Secret），或 Nacos 加密配置（`ENC(...)`）。**禁止**写进 `application.yml` 明文、禁止进 Git。
- **测试**：本地 `export TENCENT_MAP_KEY=...`，不要提交 `.env`。
- Key 只在服务端出现。前端若需要底图 Key，用另一把带 **Referer 白名单** 的前端 Key，两把 Key 不共用。

---

## 三、启动与冒烟

```bash
# 1. 建库（容器首次启动会自动执行 infrastructure/docker/mysql/init/01-init-databases.sql）
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS bluewhale_map DEFAULT CHARSET utf8mb4;"

# 2. 权限（幂等）
mysql -uroot -p bluewhale_auth < whale-backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/migration-5.91-map-m0.sql

# 3. 启动
cd whale-backend/whale-services/whale-map && ../../../../mvnw spring-boot:run

# 4. 健康检查
curl -s localhost:8100/actuator/health

# 5. 供应商状态（经网关需带登录态；直连本服务调试可跳过网关）
curl -s localhost:8100/map/provider/health
```

### 三条冒烟命令

```bash
# 地址解析（首次 cached=false，第二次 true）
curl -s -X POST localhost:8100/map/geocode \
  -H 'Content-Type: application/json' \
  -d '{"address":"北京市东城区东长安街1号","city":"北京"}'

# 打点（只给地址）
curl -s -X PUT localhost:8100/map/location/CRM_CONTACT/1001 \
  -H 'Content-Type: application/json' \
  -d '{"address":"北京市东城区东长安街1号","remark":"总部"}'

# 附近 500 米
curl -s "localhost:8100/map/location/nearby?lng=116.404&lat=39.915&radiusMeters=500&limit=20"
```

> 直连 8100 意味着绕过网关，`X-Tenant-Id` 与 `X-User-Id` 都没有。租户上下文缺失时写库会 400（`TenantContext.requireId()` 失败关闭）。调试写接口请带 `-H 'X-Tenant-Id: 1' -H 'X-User-Id: 1'`。

---

## 四、切换供应商

改 `WHALE_MAP_ROUTING_*` 或 `WHALE_MAP_DEFAULT_PROVIDER` 后**重启生效**（配置为启动期绑定）。

切换步骤：

1. 确认目标供应商 `configured=true`：`GET /map/provider/health` 里 `configured` 字段。
2. 小流量验证：把 `WHALE_MAP_ROUTING_REGEO` 单独切过去，观察 1 小时。
3. 全量切：再切 `GEOCODE` / `POI`。

**注意缓存**：缓存按供应商分键，切换后旧供应商的缓存不会被新供应商命中，**首日配额消耗会上升**（每个地址都要重新解析一次）。这是设计使然——两家对同一地址的定位可能差一栋楼，混用会让「切了配置位置就变了」无法解释。

---

## 五、排查

### 5.1 全部接口 500，报「没有可用的地图供应商」

```bash
curl -s localhost:8100/map/provider/health | jq
```

- 全部 `configured=false` → Key 没注入。检查环境变量名拼写（`TENCENT_MAP_KEY` / `AMAP_MAP_KEY`）。
- 有 `configured=true` 但 `remaining=0` → 今日配额用尽。看下面 5.3。

### 5.2 地址解析结果偏 300–500 米

**大概率是坐标系口径错了**：

- 检查响应里的 `coordType` 是否为 `GCJ02`。如果下游把它当 WGS84 用，会整整偏一段。
- 检查是否有代码绕过 `CoordContext` 直接 new `GeoPoint` 并传错枚举。
- 验证方法：同一地址分别走腾讯、高德，两点距离应 < 50 米。差 300+ 米说明有一次转换被跳过。

### 5.3 配额异常

```sql
-- 今日各供应商用量
SELECT provider, usage_date, used_count, limit_count
FROM bluewhale_map.map_quota_usage
WHERE usage_date = CURDATE();

-- 缓存命中率（越低说明地址越分散或缓存被清过）
SELECT provider, query_type, COUNT(*) AS rows_cnt, SUM(hit_count) AS hits
FROM bluewhale_map.map_geocode_cache
WHERE expire_time > NOW()
GROUP BY provider, query_type;
```

临时提额：改 `WHALE_MAP_*_QUOTA` 并重启；或直接更新当日行的 `limit_count`（立即生效，无需重启）：

```sql
UPDATE bluewhale_map.map_quota_usage
SET limit_count = 50000
WHERE provider = 'tencent' AND usage_date = CURDATE();
```

### 5.4 附近查询查不到明明存在点

1. 确认点的 `geohash` 与查询半径匹配：`SELECT biz_type, biz_id, geohash, lng, lat FROM map_location WHERE biz_id='1001'`。
2. `geohash` 为空或长度异常 → 打点是用旧版本写的，重打一次即可。
3. 半径 5000 米时前缀长度为 4–5，候选集会很大，`CANDIDATE_LIMIT=1000` 可能截断。这是有意的保护：市中心一个点配大半径，本质是「按距离扫全城」，应该收敛到具体业务类型。

### 5.5 缓存表膨胀

```sql
-- 过期缓存（可安全物理删除）
DELETE FROM bluewhale_map.map_geocode_cache WHERE expire_time < NOW() LIMIT 1000;
```

建议每月执行一次，或在运维平台挂定时任务。**不要**清空整表：清空会把配额压力立刻转移到供应商。

---

## 六、围栏与打卡范围

围栏用于「打卡范围、销售区域、配送范围」。M1-2 起可用，权限 `sys:map:fence:manage` / `sys:map:fence:query`。

### 6.1 坐标口径（最容易出错的一处）

| 环节 | 坐标系 | 谁负责转 |
|---|---|---|
| 前端从 GCJ-02 底图画围栏 | GCJ-02 | — |
| 提交到 `/map/fence` | GCJ-02（声明 `coordType`） | 服务端转 WGS84 落库 |
| 表 `map_fence.center_lng/points` | **WGS84** | 与 `map_location` 同源 |
| 判定入参 `/map/fence/match` | GCJ-02（声明 `coordType`） | 服务端转 WGS84 再算 |
| 返回前端（`FenceVO.points`） | **GCJ-02** | 服务端转回，供底图叠加 |

**若围栏在底图上偏移 300–500 米**：不是画错了，是某一端漏了转换。检查 `map_fence.coord_type` 是否为 `WGS84`，以及调用方是否声明了 `coordType`。

### 6.2 为什么判定不走 MySQL 空间函数

`ST_Contains` 默认按「度」在笛卡尔平面算：经度 1 度在北京约 85 公里、在赤道约 111 公里，围栏在东西向会被拉伸；换成 SRID 4326 也得保证所有数据同源，而 SRID 写错不报错，只静默偏几百米。

本服务改为：**bbox 粗筛（SQL）→ 局部等距投影 → 射线法（应用层）**，顺带给「离边界还有多远」。围栏数量在租户内是几十到几百量级，这点计算量远小于一次网络往返。

### 6.3 bbox 跨 180 度经线

跨线围栏的顶点一半在 179.x、一半在 -179.x。若按 min/max 写成 179 → -179，SQL 的 `min_lng <= ? AND max_lng >= ?` 永远为假，围栏会被**静默跳过**（不报错、不命中）。所以写入时识别跨线并把 bbox 放成 `-180`/`180`——粗筛宽松可以接受，漏判不行。

### 6.4 打卡场景怎么调

```bash
# 我在哪些围栏里（只判该考勤组的围栏与通用围栏）
curl -s -X POST http://127.0.0.1:8100/map/fence/match \
  -H 'Content-Type: application/json' -H "X-Tenant-Id: 1" \
  -d '{"lng":116.404,"lat":39.915,"coordType":"GCJ02","bizType":"OA_ATTEND","bizId":"12"}'
```

返回按 `distanceMeters` 降序（越靠中心越前），只含 `inside=true` 的围栏。**空数组表示不在任何范围内，不是错误**——调用方不要把它当异常处理。

`distanceMeters` 是**有符号**的：`contains` 接口里为正表示在内且距边界多远，为负表示超出多少米。可以据此提示「还差 30 米」。

### 6.5 围栏删除

`POST /map/fence/{id}/remove` 是**逻辑删除**（与打点表相反）。原因：打卡记录、配送单会长期引用围栏，删掉后仍需指回一个能解释的名字；误删也要能恢复。同名的围栏允许并存，所以本表没有唯一键，逻辑删除不会引发撞键。

---

## 七、监控

Prometheus job：`whale-map`（`127.0.0.1:8100/actuator/prometheus`）。

建议告警：

| 指标 | 阈值 | 含义 |
|---|---|---|
| `http_server_requests_seconds_count{status="5xx"}` | 5 分钟 > 10 | 供应商大面积失败或本服务异常 |
| `http_server_requests_seconds_sum / _count{uri="/map/geocode"}` | > 2s | 供应商变慢（超时 5s，接近即需关注） |
| `map_quota_usage.used_count` 当日值 | > 80% 上限 | 配额将尽，提前评估（业务指标，可用 SQL 导出为自定义指标） |

日志关键词：

- `配额已用尽，跳过` → 该供应商当天到顶，看是否有异常流量。
- `未完成调用，尝试下一个` → 降级触发，看 `provider` 与错误码。
- `写缓存失败` → 不影响主流程，但缓存命中率会下降，查 DB 连接。

---

## 八、合规与安全

1. **供应商白名单**：腾讯、高德、百度、天地图。Mapbox / Leaflet+OSM / Google 一律不可用（数据出境与测绘资质问题）。
2. **Key 不进前端、不进日志、不进响应**。`ProviderHttp` 已剥掉异常信息里的 URL（URL 带 Key）。
3. **点位数据**：`map_location` 可能含客户地址、供应商地址，属商业敏感。导出需审批；批量导出走报表，不直接开 SQL 权限。
4. **轨迹与打卡**（M1）不得对外发布；如需对外展示，只能出聚合结果（如「某区域覆盖率」），不出原始点。
5. **行政区划码 `adcode`** 是跨系统对齐组织归属的稳定键，比省市名可靠（重名、改名都常见）。

---

## 九、前端接入（`@bluewhale/whale-map`）

### 9.1 两把 Key，不要混用

| Key | 谁用 | 配置在哪 | 白名单 |
|---|---|---|---|
| **服务端 Key** | `whale-map` 调 WebService（地址解析、POI、路径） | `TENCENT_MAP_KEY` / `AMAP_MAP_KEY`（环境变量） | 通常按 IP 或账号额度 |
| **底图 Key** | 浏览器加载 JS SDK 画地图 | 前端 `VITE_TENCENT_MAP_KEY` | **必须配 Referer 白名单** |

混用会出现「本地能跑、线上 403」：服务端 Key 没有 Referer 白名单，浏览器直接调用会被拒；
反过来，底图 Key 从服务端调 WebService 属于越权。**线上一定要把 Referer 白名单配到域名级**。

### 9.2 坐标口径（前端侧最容易错）

| 数据来源 | 坐标系 | 处理 |
|---|---|---|
| `navigator.geolocation` | **WGS84** | 用 `wgs84ToGcj02()` 转再打点 |
| 底图点击 / 拖动 | GCJ-02 | 直接提交，`coordType: 'GCJ02'` |
| `/map/**` 响应 | 响应里带 `coordType` | **不要当常量假设**，按字段走 |
| 百度 SDK | BD-09 | 提交前 `convertPoint(p, 'GCJ02')` |

`distanceMeters()` 会自动统一坐标系，所以**不能**用它度量「加偏前后差多少」——
两个点会被算成同一个物理位置，距离恒为 0。要测偏移量，得把加偏后的数值当成同一坐标系比较。

### 9.3 常见故障

| 现象 | 先查什么 |
|---|---|
| 地图区域全白，控制台无错 | 容器高度为 0（`height` 没给或父级 `display:none` 时初始化）。SDK 初始化时读容器尺寸，0 高度下画布是 0×0 |
| 白屏 + `TMap is not defined` | 底图 Key 无效或 Referer 不在白名单，脚本加载了但 SDK 没挂载 |
| 点位整体偏 300–500 米 | 某一环漏了坐标系转换，**不是数据错**。见 9.2 |
| 地图疯狂闪烁、点位闪回 | `engineKey` 变了导致重建（内联工厂函数当依赖的经典事故，共享包已规避） |
| 围栏画不上 / 面积是 0 | 顶点不足 3 个，或顶点顺序自交（`validateFenceDraft` 会给出提示） |
| 搜索结果与关键词对不上 | 并发搜索的「后发先至」。共享包已用请求序号丢弃过期响应；自研实现要注意 |
| 切换底图供应商后位置全变 | 新底图的坐标系不是 GCJ-02（如天地图是 WGS84），`MapEngine` 实现里要声明 |

### 9.4 没有 Key 的环境怎么开发

用 `createFakeEngine()`：它不渲染、只记录「被要求画什么」，围栏的
「点选 → 校验 → 提交流程」可以完整跑通。CI 与离线演示都靠它。

```ts
import { createFakeEngine } from '@bluewhale/whale-map';
const factory = async () => createFakeEngine();
```
