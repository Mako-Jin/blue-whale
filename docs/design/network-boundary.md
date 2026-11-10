# 网关边界与业务端口收口清单（S-06）

- 日期：2026-10-27（2026-11-09 增补 GW-06 通配收口与 dev 令牌口径；2026-11-11 增补 auth 白名单陷阱与静态门禁）
- 关联：待办 S-06「绕过网关直连业务端口」
- 结论：这是**部署网络边界**问题，代码侧无法可靠替代；本清单作为上线前必检项。

## 0. 应用层两道防线（已落地，2026-11-09 实测）

部署边界之外，应用层已有两道防线，上线前需按「验收」章节复测：

1. **网关第一道（GW-06）**：`InternalApiDenyGlobalFilter` 对 `whale.gateway.internal-deny.patterns`
   命中的外部请求直接 403。patterns 已收敛为单段通配（`<单段>/internal/**`，星号不跨段）+
   `/audit/log/ingest/**`，覆盖全部服务的 `<service>/internal/**`；新服务内网端点按该命名约定
   自动纳管，无需改配置。`discovery.locator` 服务名前缀形态由过滤器剥前缀兜底（V-01）。
   实测：经网关访问任意服务 internal 端点均 403，非 internal 形态不受影响。
2. **服务侧第二道（V-02）**：`InternalTokenFilter`（common starter 自动装配，最高优先级）
   对 `/**/internal/**` 与 `/audit/log/ingest/**` 校验 `X-Internal-Token` 常量时间比较；
   服务间出站经 `InternalTokenSupport` 自动携带同源令牌。令牌解析：`bluewhale.internal-token.token`
   优先，回退环境变量 `WHALE_INTERNAL_TOKEN`。**dev 未配置令牌时 fail-open（InternalTokenGuard
   告警），非 dev 拒绝启动**——本地联调由 `.toolchains/start-all-services.ps1` 统一注入
   `WHALE_INTERNAL_TOKEN`（联调占位值），生产必须替换为随机值并同样注入各服务实例。
3. **auth 的 Spring Security 白名单（第三处，易漏）**：`whale-service-auth` 是唯一启用
   Spring Security 的服务，内网端点必须同时在 `SecurityConfig.permitAll` 里显式列出
   （`/tenant/internal/**`、`/user/internal/**`、`/dept/internal/**`）。漏配时 Spring Security
   会在令牌过滤器之前返回 401 —— 端点「写了但永远打不通」，且调用方多数把 401 折算成空数据，
   **不报错也不告警**（2026-11-10 的 `/dept/internal/**` 就是这样静默了数月，影响 OA 的
   审批人/部门解析）。新增 auth 内网控制器必须同步白名单。

   另注意两条 `**` 语义不同：网关 `/*/internal/**` 的星号**不跨段**，`/**/internal/**`
   跨段。因此 `/twin/notify/internal/x` 这类多段命名网关拦不住，只剩令牌一层——
   内网端点一律按 `/<service>/internal/**` 命名。

## 1. 风险

业务服务（auth、contract、job、archive、alert、erp/mes/pdm/oa、workflow 等）默认直接监听 `8081+` / `9001+`。
若这些端口对办公网或公网可达，调用方可以绕过网关：

- 伪造 `X-User-Id` / `X-Tenant-Id` / `X-Roles` 等内部头；
- 跳过令牌撤销、租户状态/到期、限流、IP 白名单、审计入口校验；
- 直接访问 `/internal/**` 内部接口（部分接口仅靠内网假设保护）。

## 2. 必须满足的部署条件

1. **仅网关对公网暴露**：负载均衡/Nginx/云安全组只放行 `443`（及本机回环的 `8080`）。
2. **业务端口只监听内网或回环**：
   - 单机部署：`server.address=127.0.0.1`，或防火墙仅允许本机/容器网络；
   - 容器部署：不写 `ports:` 映射，仅通过 compose 内部网络访问（网关在同一网络用服务名转发）。
3. **禁止办公网直连业务端口**：确需调试时使用 SSH 隧道/跳板机，不长期开放安全组。
4. **内部接口再收敛**：`/internal/**` 仅允许网关与服务间调用来源；至少配置来源网段白名单。
5. **网关必须净化入站头**：客户端传入的 `X-User-Id`、`X-Tenant-Id`、`X-Roles` 等一律覆盖/剔除（P1-13 已实现）。

## 3. 推荐配置示例

### 3.1 Spring Boot 仅监听回环

```yaml
server:
  address: ${SERVER_ADDRESS:127.0.0.1}
```

容器内需要被网关访问时改为容器网段地址，不要使用 `0.0.0.0` 映射到宿主机公网。

### 3.2 Docker Compose

```yaml
services:
  whale-contract:
    # 不声明 ports，仅同网络内可达
    networks: [bluewhale]
```

### 3.3 主机防火墙（示例）

```bash
# 仅放行网关 8080/443，其余业务端口拒绝外部访问
iptables -A INPUT -p tcp --dport 8080 -s 10.0.0.0/8 -j ACCEPT
iptables -A INPUT -p tcp --dport 8080 -j DROP
iptables -A INPUT -p tcp --dport 8081:9100 -j DROP
```

云环境用安全组实现同样效果；Nginx 只反代网关，不配置业务服务 upstream。

## 4. 验收

- 从办公网 `curl http://<业务IP>:9006/contract/...` 必须超时/拒绝；
- 从办公网 `curl https://<域名>/api/...` 正常经网关鉴权；
- 直连业务端口伪造 `X-User-Id: 1` 不得返回成功；
- 变更后更新部署手册与本清单版本。

## 5. 自动化验收

在部署外部视角执行：

```powershell
pwsh -NoProfile -File scripts/smoke/bw-network-boundary-check.ps1 `
  -PublicHost app.example.com `
  -GatewayBaseUrl https://api.example.com/api
```

脚本会逐端口检查默认业务端口不可达，并走网关验证“无令牌 + 伪造身份头”被拒绝。
`-PublicHost` 禁止使用 `localhost`/回环地址；自签证书环境可临时加 `-SkipCertificateCheck`，
生产验收不要开启。

## 6. 静态门禁（2026-11-11 起为 CI 阻断项）

上面三处口径靠人眼读代码对不齐（三份配置分散在网关、starter、auth），因此加了静态核对：

```bash
python scripts/check-internal-boundary.py          # 全量核对
python scripts/check-internal-boundary.py --verbose # 逐条打印内网端点
```

检查项：网关收口覆盖、服务令牌模式覆盖、auth 白名单覆盖、内网控制器不得标
`@RequirePermission`（服务间调用没有用户权限码，标了必 403）、CI 里 `working-directory`
指向的目录是否存在。CI 作业名 `internal-boundary`（`.github/workflows/code-quality.yml`）。

当前基线：118 条内网端点 / 24 个前缀全部三层一致；`/alert/internal/prometheus` 为
WARN（Alertmanager 入站自带 webhook token，属设计的令牌豁免）。

静态门禁与本文第 5 节的部署侧取证互补：前者保证「配置口径对齐」，后者证明「网络上真的拦得住」，
上线前两者都要过。
