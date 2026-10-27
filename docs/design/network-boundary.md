# 网关边界与业务端口收口清单（S-06）

- 日期：2026-10-27
- 关联：待办 S-06「绕过网关直连业务端口」
- 结论：这是**部署网络边界**问题，代码侧无法可靠替代；本清单作为上线前必检项。

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
