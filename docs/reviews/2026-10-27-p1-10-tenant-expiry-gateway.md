# P1 #10 检查报告：租户到期网关即时生效与续期缓存同步

- 日期：2026-10-27
- 范围：`whale-service-auth` 租户生命周期链路、`whale-gateway` JWT 鉴权链路、`whale-security` 缓存键约定
- 关联：Wave 5.54 Phase 2 租户状态缓存、P1 #8/#9 存量令牌撤销、REQ-SEC-013

## 1. 问题

### R1：租户到期后网关仍放行存量令牌

Wave 5.54 Phase 2 只把租户「启用/停用」写入缓存（`whale:{tenantId}:cache:auth:tenant:status`），网关 `TenantStatusChecker#isDisabled` 也只读状态键。`Tenant#isAvailableAt` 中的到期判定只在登录、刷新令牌等走数据库的链路生效。

结果：租户到期后，其用户已签发的 accessToken 在过期前仍可经网关访问全部业务接口，直到用户重新登录才被拒绝。

### R2：续期未同步租户状态缓存

`TenantAppService#renew` 只更新数据库，不调用 `TenantStatusCache#sync`。即使补上到期校验，续期后缓存仍是旧状态；若缓存中被写入过到期时间，续期后仍会按旧到期时间拒绝该租户全部请求，直到应用重启预热才恢复。

## 2. 改动

| 文件 | 说明 |
|---|---|
| `whale-security/.../token/TenantStatusKeys.java` | 新增到期时间键 `whale:{tenantId}:cache:auth:tenant:expire`（Unix 秒，缺失表示长期有效），并在类注释中登记键约定 |
| `auth-application/.../tenant/TenantStatusCache.java` | `sync` 在写状态键后同步到期键：`expireTime` 有值写入 `toEpochSecond()`，为空则删除键（长期有效），保持与状态键一致的 Redis 异常降级（仅告警） |
| `auth-application/.../tenant/TenantAppService.java` | `renew` 保存后调用 `tenantStatusCache.sync(saved)`，避免续期后仍按旧到期时间判定 |
| `gateway/.../security/TenantStatusChecker.java` | `isDisabled` 重写为 `isUnavailable`：`multiGet` 一次读取状态键与到期键，任一满足「停用」或 `expireAt <= now` 即判定不可用；到期值缺失/非法按未到期放行，Redis 异常降级放行并告警 |
| `gateway/.../filter/JwtAuthGlobalFilter.java` | 调用 `isUnavailable`，日志改为「租户已停用或已到期」，校验顺序保持：签名/类型 → 黑名单 → 租户可用性 → 成员撤销 → 用户级撤销 → 注入身份头 |

## 3. 行为矩阵

| 场景 | 状态键 | 到期键 | 网关结果 |
|---|---|---|---|
| 租户启用且未到期 | `1` | 未来秒数 | 放行 |
| 租户启用但已到期 | `1` | `<= now` | 401 |
| 租户停用 | `0` | 任意 | 401 |
| 租户长期有效（`expireTime=null`） | `1` | 缺失 | 放行 |
| 租户停用且长期有效 | `0` | 缺失 | 401 |
| 到期前续期 | `1` | 新到期秒数 | 放行（旧令牌无需重登） |
| 到期后续期 | `1` | 新到期秒数 | 放行；续期前被拒的令牌可直接恢复使用 |
| 缓存缺失 | 缺失 | 缺失 | 放行（登录/刷新已由数据库校验兜底） |
| Redis 不可用 | — | — | 放行 + `SECURITY_DEGRADATION` 告警 |
| 到期键值非法 | `1` | 非数字 | 放行 + WARN（视为长期有效） |

到期改为「写入到期秒数 + 请求时比较」，不依赖定时任务即可在到期瞬间生效；状态键取值仍为 `"0"/"1"`，对既有缓存与前端/运维脚本保持兼容。

## 4. 验证

```powershell
mvn -B -pl backend/whale-services/whale-service-auth/whale-service-auth-starter -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
mvn -B -pl backend/whale-services/whale-gateway -am ^
  "-Dtest=!Sm4CryptoTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

- auth starter `400` 用例，`0` 失败、`0` 错误，`7` 跳过（Live IT）；Checkstyle `0` 违规；
- gateway `104` 用例，`0` 失败、`0` 错误；Checkstyle `0` 违规，BUILD SUCCESS；
- 新增/更新用例：
  - `TenantStatusCheckerTest` 8：停用、已到期、未到期、长期有效（到期键缺失）、双键同时缺失、到期值非法、Redis 降级放行且触发安全降级告警、非法租户 ID 不访问 Redis；
  - `TenantStatusCacheTest` 5：状态与到期同时写入（Unix 秒）、停用 + `expireTime=null` 删除到期键、Redis 抛异常不外抛、模板缺失跳过、租户为空/无 ID 跳过；
  - `TenantAppServiceTest` 新增 `shouldSyncStatusCacheWhenRenewed`、`shouldSyncStatusCacheWhenStatusChanged`；
  - `JwtAuthGlobalFilterTest` 按 `isUnavailable` 调整「租户不可用（停用/到期）→ 401 且不转发下游」。
- 日志中的 `RuntimeException: connection refused` 为降级用例主动抛出的模拟异常，对应用例 `Failures: 0`；
- `Sm4CryptoTest` 为本机 JDK/JCE 既有失败，已隔离，与本次无关。

## 5. 边界

- **续期与缓存写入的时序**：`renew` 在事务内保存后立即写缓存，若事务随后回滚，缓存会短暂领先于数据库（最长到一个访问周期）。当前租户管理为单库本地事务、回滚概率低；后续如需严格一致可改为 `AFTER_COMMIT` 事件同步；
- **网关缓存依赖 Redis 可用性**：Redis 不可用时按可用放行，与登出黑名单、成员/用户撤销的取舍一致，并输出安全降级告警；
- **时区**：到期秒数按 auth 服务所在 JVM 默认时区换算（`expireTime` 为 `LocalDateTime`），auth 与网关需部署在同一时区约定下；跨时区部署需改为显式 UTC 存储；
- **精确到秒**：到期判定为 `expireAt <= nowSeconds`，即到期时刻所在秒内已判定过期，粒度与 JWT `iat` 一致；
- **绕过网关直连业务服务**：到期校验落在网关；直连业务端口属于部署安全边界（生产仅暴露网关），auth 侧登录/刷新仍按数据库校验；
- 未引入定时清理任务：过期租户的缓存键保留至下次启停/续期/重启预热更新，键本身无 TTL，但判定值随时间自然失效。
