# P1-3（用户口径）审计日志与脱敏 规格

> 日期：2026-10-22 ｜ 状态：**已批准并完成（实机验收通过）**
> 来源：用户指令「继续执行 P1-3 审计日志与脱敏」；用户已明确选择「审计日志与脱敏（推荐）」而非整改清单里同名编号的「可观测性最小闭环」。
> 整改清单原 P1-3（Prometheus 可观测性）保持原状，另记为 P1-3b，不与本项混做。

## 一、现状盘点（2026-10-22 实测结论）

| 能力 | 现状 | 结论 |
|---|---|---|
| 审计切面 | `AuditLogAspect` 采集 trace/user/module/operation/params/result/ip/cost，事务 `afterCommit` + 独立线程池落库 | 可用，不重写 |
| 查询审计 | `AuditLogController`（page/detail/trace/export）与 `LogIngestController`（四通道 page）均已有 `@RequirePermission` + `@AuditLog(QUERY/EXPORT)` | **已完备，不改代码** |
| 请求体/响应体脱敏 | `SensitiveDataMasker.maskJson` 覆盖字段名规则、嵌套、深度 8、截断 4096、`@Sensitive` 兜底 | 可用 |
| **查询串脱敏** | `ApiLogFilter` L183 原样落 `request.getQueryString()`；`OutboundLogInterceptor#buildTargetUri` 原样拼 `?query` | **缺口 1：`?token=`/`?password=` 明文入库** |
| **跨库可见性** | 各服务 `@AuditLog` 写各自库；whale-audit 只查 `bluewhale_audit.audit_log`，看不到 `bluewhale_auth.audit_log` | **缺口 2：审计管理员无法统一检索** |
| 审计摄入端点 | 仅有 `POST /audit/log/frontend/batch`、`/behavior/batch`，无 `audit_log` 摄入 | 需新增最小摄入端点 |
| 汇聚通道 | AUTH-10 计划用 RabbitMQ，本机无 MQ | 本轮用 **HTTP 转发** 做最小闭环 |

## 二、目标与范围

### 目标
1. 查询串与请求体/响应体采用同一套字段规则脱敏，杜绝凭据明文落 `api_log.query_string` / `outbound_log.target_uri`。
2. 审计记录可汇聚到 `bluewhale_audit.audit_log`，审计管理员一处检索跨服务事件。
3. 汇聚失败不丢审计：转发失败自动回退本地落库。
4. 不引入新中间件（无 RabbitMQ/Kafka），不改变 `audit_log` append-only 语义。

### 范围内
- starter：`SensitiveDataMasker` 查询串脱敏、secret 键扩充、`AuditLogForwarder`、`AuditLogAspect` 转发优先 + 本地兜底。
- whale-audit：`POST /audit/log/ingest` + `AuditLogService.ingest`。
- whale-auth：开启转发（唯一试点服务）。
- whale-gateway：内网屏蔽 `/audit/log/ingest/**`。
- 单测、构建、实机验收、文档同步。

### 范围外（明确不做）
- 不给其余 9 个业务服务开启转发（本轮只验证机制，避免多点同时变更）。
- 不实现批量聚合/重试队列/离线补投；单条转发 + 本地兜底即可。
- 不改动既有审计切面字段、表结构、四通道日志表结构。
- 不做整改清单原 P1-3b 的 Prometheus 接入。

## 三、设计决策（决策完整，可直接实施）

### D1 查询串脱敏

**文件**：`whale-backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/audit/support/SensitiveDataMasker.java`

新增公开方法：

```java
public String maskQueryString(String queryString)
```

行为：
- `null` / 空白 → 返回 `null`。
- 按 `&` 拆分（保留原顺序与空段），每段按**第一个** `=` 拆 key/value。
- key 经 `URLDecoder.decode(key, UTF_8)` 后按 `normalizeKey` 归一化；解码失败用原始 key（不抛异常）。
- 命中规则的值替换：
  - `SECRET_KEYS` / `extraSecretKeys` → `******`
  - `TOKEN_KEYS` → `***`
  - `MOBILE_KEYS` / `ID_CARD_KEYS` / `BANK_CARD_KEYS` / `EMAIL_KEYS` → 复用现有 `maskMobile` / `maskIdCard` / `maskBankCard` / `maskEmail`（value 先 URL 解码；解码失败用原值）。
- 未命中规则的段**原样保留**；无 `=` 的段原样保留。
- 最终按 `MAX_PARAM_LENGTH=4096` 截断；任何异常只降级为原文截断，不抛出。

`SECRET_KEYS` 扩充为：

```text
password, oldpassword, newpassword, confirmpassword,
secret, secretkey, clientsecret, privatekey, apikey, appsecret, accesskey
```

**接入点**
- `whale-backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/log/filter/ApiLogFilter.java` L183：
  `record.setQueryString(sensitiveDataMasker.maskQueryString(request.getQueryString()));`
- `whale-backend/whale-common/whale-spring-boot-starter/src/main/java/com/bluewhale/common/log/interceptor/OutboundLogInterceptor.java`：
  `buildTargetUri(URI)` 由 `static` 改为实例方法，查询串经 `sensitiveDataMasker.maskQueryString(uri.getQuery())`；`null` 时只返回 path。

### D2 审计 HTTP 转发（失败回退本地）

**新增** `.../common/audit/properties/AuditLogForwardProperties.java`

```text
prefix = whale.audit.forward
enabled: boolean = false
url: String = "http://127.0.0.1:9101"   // whale-audit 基址，不含路径
timeoutMillis: int = 800                // 连接与读取超时
```

**新增** `.../common/audit/support/AuditLogForwarder.java`

- 构造：`(AuditLogForwardProperties, ObjectMapper)`，用 `RestClient` + JDK `HttpClient`（连接/读取超时 = `timeoutMillis`）。
- `public boolean forward(AuditLogRecord record)`：
  - `POST {url}/audit/log/ingest`，`Content-Type: application/json`，body 为单元素数组 `[record]`。
  - HTTP 2xx → `true`；4xx/5xx、连接失败、超时、序列化失败 → `log.warn` + `false`，**绝不抛出**。
- 序列化使用 Spring 管理的 Jackson 3 `ObjectMapper`（含 JavaTime 模块），保证 `LocalDateTime` 可被 whale-audit 反序列化。

**修改** `.../common/audit/aop/AuditLogAspect.java`

- 构造器新增第 5 个参数 `AuditLogForwarder forwarder`（允许 `null`）。
- `persist(AuditLogRecord)` 线程池内逻辑改为：

```java
if (forwarder != null && forwarder.forward(record)) {
    return;
}
try {
    auditLogMapper.insert(record);
} catch (Exception e) {
    log.warn(...);
}
```

> 语义：转发成功即视为已汇聚；转发失败回退本地库，**任何情况下不丢记录**。

**修改** `.../common/audit/config/AuditLogAutoConfiguration.java`

- 类上新增 `@EnableConfigurationProperties(AuditLogForwardProperties.class)`。
- 新增 Bean：

```java
@Bean
@ConditionalOnMissingBean
@ConditionalOnProperty(prefix = "whale.audit.forward", name = "enabled", havingValue = "true")
public AuditLogForwarder auditLogForwarder(AuditLogForwardProperties properties, ObjectMapper objectMapper)
```

- `auditLogAspect(...)` 新增 `ObjectProvider<AuditLogForwarder> forwarderProvider`，把 `getIfAvailable()` 传入切面。

**同步测试构造器**
- `.../src/test/java/com/bluewhale/common/audit/aop/AuditLogAspectTest.java`：4 参构造改为 5 参，末位传 `null`。
- `whale-backend/whale-ops/whale-audit/src/test/java/com/bluewhale/audit/live/LiveAuditLogIT.java`：同上。

### D3 whale-audit 摄入端点

**修改** `whale-backend/whale-ops/whale-audit/src/main/java/com/bluewhale/audit/service/AuditLogService.java`
- 新增 `int ingest(List<AuditLogRecord> records);`

**修改** `.../service/impl/AuditLogServiceImpl.java`
- 实现 `ingest`：
  - `null` / 空 → 返回 0。
  - `size > 50` → `throw new BizException(ErrorCode.PARAM_ERROR)`（防刷量）。
  - 每条强制 `record.setId(null)`（由本库雪花重新生成，避免跨库主键冲突）。
  - 逐条 `auditLogMapper.insert`，返回成功条数；方法加 `@Transactional(rollbackFor = Exception.class)`。
- 更新类注释「只读」表述为「查询/导出 + 内网摄入」。

**修改** `.../controller/AuditLogController.java`
- 新增：

```java
@PostMapping("/ingest")
public Result<Integer> ingest(@RequestBody List<AuditLogRecord> records)
```

- 不加 `@RequirePermission`（调用方是服务，无用户权限头）；靠「网关内网屏蔽 + 9101 仅内网可达」保护。
- 不加 `@AuditLog`，避免摄入动作产生新的审计记录形成放大。

### D4 防回环与网关屏蔽

- `.../common/log/properties/ApiLogProperties.java` 默认排除路径追加 `/audit/log/ingest/**`（whale-audit 不把摄入请求写入自己的 `api_log`）。
- `whale-backend/whale-services/whale-gateway/src/main/resources/application.yml` 的 `whale.gateway.internal-deny.patterns` 追加 `- /audit/log/ingest/**`，外部经网关访问返回 403。

### D5 whale-auth 开启转发

`whale-backend/whale-services/whale-auth/src/main/resources/application.yml` 在 `whale` 下新增：

```yaml
  audit:
    forward:
      enabled: ${WHALE_AUDIT_FORWARD_ENABLED:true}
      url: ${WHALE_AUDIT_FORWARD_URL:http://127.0.0.1:9101}
      timeout-millis: ${WHALE_AUDIT_FORWARD_TIMEOUT_MILLIS:800}
```

- 仅 auth 试点；whale-audit 自身不开启（防止自转发）。
- 转发失败（如 9101 未启动）时按 D2 回退本地 `bluewhale_auth.audit_log`。

## 四、测试与验收

### 单元测试
1. `SensitiveDataMaskerTest` 新增：
   - `?token=jwt&password=P%40ss&page=1` → `token=***&password=******&page=1`。
   - 明文值 `jwt`、`P@ss` 不出现在结果中。
   - `clientSecret` / `apiKey` 等新增键命中整体掩码。
   - `null` / 空白返回 `null`；超长按 4096 截断。
2. `ApiLogFilterTest` 新增：请求带 `?token=abc&password=xyz&page=1` 时，落库 `queryString` 为掩码值且不含明文。
3. `OutboundLogInterceptorTest` 新增：目标 URL 带敏感查询串时，`targetUri` 已掩码。
4. `AuditLogAspectTest` 新增：注入 stub forwarder 返回 `false` 时仍本地 `insert`；返回 `true` 时不本地 `insert`。

### 构建
```powershell
$env:JAVA_HOME='D:\tools\installer\jdk\java21'
mvn -o -B -pl whale-common/whale-spring-boot-starter,whale-services/whale-auth,whale-ops/whale-audit,whale-services/whale-gateway -am package -DskipTests
```
要求 BUILD SUCCESS（含 Checkstyle）。

### 实机验收（本机中间件已就绪）
1. 确认 `bluewhale_audit` 库存在；缺失时执行 `whale-backend/whale-ops/whale-audit/src/main/resources/db/init.sql`。
2. 启动 whale-audit（9101）、重启 whale-auth（8081），网关保持 8080。
3. `POST http://127.0.0.1:8080/auth/login`（admin/123456）→ 200。
4. `SELECT id,module,operation,biz_status FROM bluewhale_audit.audit_log WHERE module='whale-auth' ORDER BY create_time DESC LIMIT 5` → 命中新记录。
5. 带敏感查询串调用 auth 查询接口 → `bluewhale_auth.api_log.query_string` 含 `token=***` / `password=******`，不含明文。
6. **反向验证（失败回退）**：停止 whale-audit → 再登录一次 → `bluewhale_auth.audit_log` 新增 LOGIN 记录，auth 日志出现转发失败告警。
7. 网关反向验证：`POST http://127.0.0.1:8080/audit/log/ingest` → 403。

### 文档
- `docs/devlog/2026-10-22.md` 追加「十二、平台评审 P1-3（用户口径）：审计日志与脱敏」。
- `docs/plans/2026-10-22-platform-remediation.md` 新增本项小节并标记完成；原 P1-3 改注为 P1-3b 待办。
- `docs/plans/roadmap.md` Phase 5 追加勾选条目。
- 完成后提交并推送 `origin`（Gitee），再推 `ci`（GitHub）。

## 五、风险与控制

| 风险 | 控制 |
|---|---|
| 转发同步调用拉高业务耗时 | 在线程池内执行，业务线程零阻塞；超时 800ms；失败即回退 |
| whale-audit 未启动导致告警刷屏 | 仅审计事件触发，非每请求；可调 `WHALE_AUDIT_FORWARD_ENABLED=false` 关闭 |
| 摄入端点被外部调用 | 网关 `internal-deny` 403；9101 仅内网监听约定 |
| 主键冲突 | 摄入端强制 `id=null` 由目标库重新生成 |
| 查询串解码异常 | 解码失败降级原文，绝不抛出 |

## 六、需用户批准

1. 仅 **whale-auth** 试点开启审计转发，默认地址 `http://127.0.0.1:9101`。
2. 新增 `POST /audit/log/ingest`，不加用户权限校验，依赖网关内网屏蔽。
3. `SECRET_KEYS` 扩充 7 个键（secret/secretkey/clientsecret/privatekey/apikey/appsecret/accesskey）。
