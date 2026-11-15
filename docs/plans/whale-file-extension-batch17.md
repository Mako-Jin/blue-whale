# whale-file 能力扩展（批次 17：防盗链与限流）

## 一、范围与决策

下载/预览是文件服务最主要的被滥用入口：第三方站点可直接引用下载地址消耗带宽，脚本也可高频
拉取内容。本批次在 Web 层增加可选的内容访问防护：防盗链与按调用者限流。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 防盗链（Referer 主机） | ✅ | 同主机、精确主机、`*.suffix` 通配后缀 |
| 空 Referer 策略 | ✅ | 可配置放行或拒绝 |
| 调用者令牌桶限流 | ✅ | 按 `X-User-Id`，缺失时按客户端 IP |
| 拒绝响应 | ✅ | 403 / 429 + `Retry-After: 1` + JSON 错误体 |
| 上传/管理接口限流 | ❌ | 只覆盖下载与预览，避免影响业务写入 |
| 多实例共享限流 | ❌ | 进程内计数，多实例各自独立；见待做项 |
| 签名 URL / 时间戳防盗链 | ❌ | 已有预签名直连，网关层不在本轮范围 |
| 数据库变更 | ❌ | 无 |

**默认关闭**：`whale.file.access.hotlink.enabled` 与 `rate-limit.enabled` 默认均为 `false`，
升级后行为不变。已登录用户按用户维度限流；服务间无用户头时按 IP 限流。

## 二、设计

`FileAccessGuardFilter`（`OncePerRequestFilter`）只处理 URI 以 `/download` 或 `/preview`
结尾的请求：

1. 防盗链：读取 `Referer` 主机；同主机放行；命中 `allowed-hosts`（精确或 `*.suffix`）放行；
   无 Referer 时按 `allow-empty-referer`；其余 403。
2. 限流：以 `u:{userId}` 或 `ip:{remoteAddr}` 为键维护令牌桶，容量 `burst`、每秒补充
   `requests-per-second`；令牌不足返回 429 并带 `Retry-After`。
3. 桶数量超过 1 万时清理空闲超过 10 分钟的桶，避免内存无限增长。
4. 拒绝时直接写 JSON `Result`，短路后续过滤器与业务处理。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 过滤器 | 开关关闭时直接放行；非法 Referer 返回 403；通配后缀与同主机放行；空 Referer 按配置放行/拒绝；同用户连续请求超出桶容量返回 429 并带 `Retry-After`；非下载/预览路径不拦截 |
| 回归 | 既有下载/预览、权限与 Range 用例保持通过 |

## 四、完成情况

- `FileProperties` 新增 `whale.file.access.hotlink.*` 与 `whale.file.access.rate-limit.*`，
  默认关闭；
- 新增 `FileAccessGuardFilter`：只拦截 `/download` 与 `/preview`；Referer 支持同主机、精确主机
  与 `*.suffix` 通配，空 Referer 可配置；限流按用户或 IP 使用令牌桶，超限返回 429 与
  `Retry-After`；桶数量超限时清理空闲桶；
- 拒绝响应统一为 JSON `Result`，不进入业务链；
- 测试：开关关闭放行、非法来源 403、通配/同主机/空 Referer、超限 429、非内容路径跳过共 5 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
351 项单测零失败（批次 16 为 346，本轮新增 5 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 编辑 `FileProperties` 追加防护配置时误覆盖了 `Bucket#setDefault` 的方法体与结尾大括号，
  导致编译失败 → 恢复方法实现并补齐嵌套类边界。

## 七、待做项

- 多实例限流：接入 Redis 令牌桶或网关统一限流。
- 防盗链增强：签名 URL、有效期时间戳、CDN 回源校验。
- 限流维度扩展：租户级与接口级配额、上传限流。
- 监控指标：403/429 计数与热门调用者排行。
