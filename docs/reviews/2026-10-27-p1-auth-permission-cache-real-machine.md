# T4 / V-05 检查报告：权限缓存双实例广播 + Redis 断连恢复实机联调

- 日期：2026-10-27
- 范围：`whale-service-auth` 权限缓存失效链路
- 关联：P1 #3 [跨实例广播](2026-10-27-p1-auth-permission-cache-broadcast.md)、P1 #5 [失效残余](2026-10-27-p1-5-auth-permission-cache-residual.md)、P1 #7 [提交后失效与成员跨租户](2026-10-27-p1-7-auth-cache-after-commit-membership.md)
- 待办编号：T4 / V-05
- 复现脚本：[bw-auth-permission-cache-broadcast-smoke.ps1](../../scripts/smoke/bw-auth-permission-cache-broadcast-smoke.ps1)

## 1. 此前边界

P1 #3/#5/#7 均为单元测试验证，三份报告的「边界」一节都写着「Redis 双实例广播 / 断连恢复仍需联调环境」。
本轮在**本机真实 MySQL + Redis + 两个 auth 进程**上补齐取证，覆盖：

1. 双实例是否订阅同一 RTopic；
2. 实例 A 失效后实例 B 是否收到广播并删除共享 Redis Key；
3. Redis 停机期间删除失败是否进入待重对齐集合；
4. Redis 恢复后定时任务是否自动清零待处理；
5. Redis 重启后订阅是否自动恢复、反向广播是否仍可达。

## 2. 环境与启动参数

| 项 | 值 |
|---|---|
| JDK | 21 |
| MySQL | 8.0.26（`127.0.0.1:3306/bluewhale_auth`） |
| Redis | 5.0.14（`127.0.0.1:6379`，AOF，密码认证） |
| auth jar | `whale-service-auth-starter/target/whale-service-auth.jar`（`mvn -DskipTests package`） |
| 实例 A | `SERVER_PORT=8081`，PID `19580`，instance `e992df6e18af46998e71957e7b203300` |
| 实例 B | `SERVER_PORT=8082`，PID `12312`，instance `717f97b8f4dd4e7b99f6c45113bd9fb2` |
| 缓存 Key | `whale:1:cache:auth:user:perm:1` |
| 失效触发 | `POST /user/1/roles`（`ROLES_ASSIGNED`，事务提交后失效，覆盖 P1-7 主链路） |

启动时通过环境变量关闭无关依赖并缩短重对齐周期，便于取证：

```powershell
$env:MYSQL_PASSWORD='***'
$env:SPRING_DATA_REDIS_PASSWORD='***'
$env:WHALE_AUDIT_FORWARD_ENABLED='false'
$env:WHALE_AUDIT_MQ_PUBLISH_ENABLED='false'
$env:PERMISSION_CACHE_RECONCILE_INTERVAL_MS='3000'
$env:PERMISSION_CACHE_RECONCILE_INITIAL_DELAY_MS='1000'
$env:SPRING_CLOUD_NACOS_DISCOVERY_ENABLED='false'
$env:JWT_SECRET='<两实例共用>'

$env:SERVER_PORT='8081'; Start-Process java -ArgumentList '-jar', $jar ...
$env:SERVER_PORT='8082'; Start-Process java -ArgumentList '-jar', $jar ...
```

> 直连 auth 端口时，网关可信头需自行补齐：登录后的管理接口调用带 `X-Tenant-Id: 1`，否则
> `TenantContext.requireId()` 返回「缺少租户上下文，请通过网关访问」。这与生产网关注入一致，
> 不属于本次缺陷。

## 3. 实机取证

### 3.1 双实例订阅

```
A  c.b.a.i.c.AuthPermissionCacheInvalidator : 权限缓存跨实例失效广播已订阅:
   topic=auth:permission-cache:invalidate, instance=e992df6e18af46998e71957e7b203300
B  c.b.a.i.c.AuthPermissionCacheInvalidator : 权限缓存跨实例失效广播已订阅:
   topic=auth:permission-cache:invalidate, instance=717f97b8f4dd4e7b99f6c45113bd9fb2
```

两个进程 `Started WhaleAuthApplication` 分别在 8081 / 8082，Redisson 可用，未出现降级日志。

### 3.2 A 变更 → 共享删除 + B 收到广播

预置 `whale:1:cache:auth:user:perm:1` 后，在 A 调用 `POST /user/1/roles`：

| 断言 | 结果 |
|---|---|
| `assignRoles code=0` | 成功 |
| Redis `EXISTS` 前 → 后 | `1` → `0` |
| B 广播日志 | `收到跨实例权限缓存失效广播: source=e992df6e18af46998e71957e7b203300, keys=1` |
| A 自身回声 | 未处理（`source` 与本实例相同直接忽略） |

说明：共享 Redis 删除是主路径；广播在 B 侧被真实接收并再次执行删除（Key 已不存在，`DEL` 幂等）。

### 3.3 Redis 停机 → 登记待重对齐

预置 Key 后 `Stop-Process -Name redis-server -Force`，确认 6379 无监听，再在 A 调用同一接口：

| 断言 | 结果 |
|---|---|
| `assignRoles code=0` | 接口不受缓存故障影响（权限判定降级回源） |
| A 日志 | `Redis 批量删除失败（待调用方重试）: size=1` → `权限缓存删除失败，登记待重对齐: keys=1` |
| 定时任务（3s 周期） | `权限缓存重对齐失败，保留待处理: 本次=1, 剩余=1`（Redis 仍不可用） |

说明：失败 Key 以**完整 Key**登记，异步重试不依赖 `TenantContext`；Redis 未恢复时不会误清空待处理。

### 3.4 Redis 恢复 → 重对齐清零

使用原 `redis.windows.conf` 重启 Redis，`PING` 返回 `PONG` 后：

| 断言 | 结果 |
|---|---|
| A 日志 | `权限缓存断连重对齐完成: 清除待失效用户=1, 剩余=0`（15:21:32） |
| Redis `EXISTS` | `0` |
| 重试次数 | 恢复后首个调度周期即完成，无需等待 TTL 或下一次权限变更 |

说明：重对齐任务按 `reconcile-interval-ms` 周期执行；生产默认 30s，最坏延迟一个周期。

### 3.5 Redis 重启后订阅恢复 → 反向广播

在 B 登录并调用 `POST /user/1/roles`：

| 断言 | 结果 |
|---|---|
| `assignRoles code=0` | 成功 |
| Redis `EXISTS` 后 | `0` |
| A 广播日志 | `收到跨实例权限缓存失效广播: source=717f97b8f4dd4e7b99f6c45113bd9fb2, keys=1`（15:22:09） |

说明：Redisson 在 Redis 重启后自动重连并恢复订阅，反向广播仍可达；未观察到需要重启 auth 进程。

## 4. 结论

- V-05 的五个取证点全部通过，P1 #3/#5/#7 的「多实例/断连未实机」边界在本机闭环；
- 失效器「共享删除 → 广播兜底 → 断连待处理 → 恢复重对齐」链路行为与设计一致；
- 复现脚本已落库，参数化端口/路径/凭据，默认使用 `18081/18082` 避免与日常实例冲突。

## 5. 边界（仍未覆盖）

- **网络分区形态**：本轮为进程级停机，未模拟「实例 ↔ Redis 单向不通」「广播通道阻塞但 Redis 可写」；
- **超过两实例**：广播为 `RTopic` 扇出，理论无差异，但未做 3+ 实例取证；
- **Redis 集群/Sentinel 故障切换**：未验证主从切换期间的待处理集合与重订阅；
- **长时间断连**：`pending-limit`（默认 10000）丢弃告警仍为已知取舍；待处理集合已支持本地文件落盘，进程重启会读回并重放；
- **其他触发路径**：P1 #5 的角色 `STATUS_CHANGED`、权限点跨租户失效复用同一失效器，仍为单元测试级验证；
- **网关链路**：本轮直连 auth 端口，未走 `whale-gateway`（网关只做身份与租户校验，不参与缓存失效）。

## 6. 第二轮补强（2026-10-27）

- `PermissionCacheProperties` 新增 `pending-persistence-enabled`（默认 true）与 `pending-file`
  （默认 `${java.io.tmpdir}/bluewhale-auth-permission-pending.keys`）；
- `AuthPermissionCacheInvalidator`：`subscribe()` 时读回文件中的待重对齐 Key（仅接受 `whale:` 前缀、受 `pending-limit` 限制），
  `markPending` 后原子落盘，`flushPending` 成功后清空文件；
- 容器部署建议把 `PERMISSION_CACHE_PENDING_FILE` 指向持久卷；多实例共享同一文件时重复 Key 自动去重；
- 用例：`AuthPermissionCacheInvalidatorTest` 10/10 通过，新增「落盘 → 重启读回 → flush 清空」。`docker compose`/多实例共享卷的实机重启未复测，仍属部署验证边界。
