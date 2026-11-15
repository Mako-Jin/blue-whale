# whale-file 能力扩展（批次 9：CDN 预签名地址）

## 一、范围与决策

批次 1~8 已完成存储路由与回收站、重命名/移动、指标与健康检查、孤儿对象对账、分享链接、
批量删除与打包下载、上传内容魔数校验、租户/桶级配额、文件生命周期领域事件。本批次落地路线图
阶段 3 的「CDN 地址」。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 预签名 URL 走 CDN | ✅ | 配置 `base-url` 后把 `scheme://host[:port]` 替换为 CDN 域名 |
| 路径与签名保留 | ✅ | 只替换 authority，path/query 原样保留（S3/OSS 预签名不签 host） |
| 开关与回退 | ✅ | `base-url` 为空即完全保持原行为 |
| 服务端流式下载走 CDN | ❌ | 流式代理依赖服务端鉴权与 Range，不经 CDN；如需要应改为直连预签名 |
| 本地插件 | ❌ | 本地存储不支持预签名，配置 CDN 也不生效（仍抛参数错误） |

**边界**：CDN 必须回源到对象存储 endpoint，且不能改写查询串（签名在 query 中）。
若 CDN 供应商会重写/丢弃 query 或要求 URL 鉴权，需改用其他方案，本批次不覆盖。

## 二、设计

### 2.1 配置

`FileProperties.Cdn`（前缀 `whale.file.cdn`）：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `base-url` | 空 | CDN 加速地址（含 scheme），空白表示不重写；建议不带路径与结尾斜杠 |

环境变量：`WHALE_FILE_CDN_BASE_URL`。

### 2.2 重写规则

`CdnUrlRewriter#rewrite(url, baseUrl)`：

1. `url` 或 `baseUrl` 为空白 → 原样返回；
2. `url` 不含 `://`（相对地址/异常值）→ 原样返回；
3. 没有路径部分 → 返回去掉结尾斜杠的 `baseUrl`；
4. 否则返回 `trimTrailingSlash(baseUrl) + 从第一个 "/" 起的 path+query`。

只处理字符串拼接，不解析/重建查询串，避免破坏签名参数的编码。

### 2.3 接入

`FileInfoServiceImpl#presignedUrl` 在拿到存储插件生成的 URL 后统一经过重写；
本地插件在重写前已因「不支持预签名」抛错，行为不变。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 重写工具 | 替换主机保留 path+query；空白 base/url 原样；相对地址原样；base 结尾斜杠；无路径 URL |
| 应用 | 配置 CDN 后预签名结果被重写；未配置时与插件返回值一致（既有用例） |

## 四、完成情况

- 配置：`FileProperties.Cdn`（`base-url`，默认空）与 `application.yml` 的
  `whale.file.cdn.base-url` / `WHALE_FILE_CDN_BASE_URL`。
- 工具：新增 `CdnUrlRewriter`（`service/support`），按字符串替换 `scheme://host[:port]`，
  路径与查询串原样保留；空 URL、空 CDN 地址、相对地址、结尾斜杠均做了安全处理。
- 接入：`FileInfoServiceImpl#presignedUrl` 在存储插件返回后统一重写；未配置 CDN 时与改造前完全一致。
- 测试：`CdnUrlRewriterTest` 5 项；应用新增 1 项（配置 CDN 后返回 CDN 域名且保留签名查询串）。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
290 项单测零失败（批次 8 为 284，本轮新增 6 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 本轮无返工。
- 设计期规避的隐患（非上线后发现）：
  - 用 URI 解析并重建 URL 会重新编码查询串，可能破坏预签名签名值 → 改为纯字符串前缀替换；
  - `base-url` 结尾带斜杠会拼出双斜杠，部分 CDN 会返回 404 → 统一去掉结尾斜杠；
  - 相对地址或异常值无法安全判断主机边界 → 一律原样返回，不做猜测性改写。

## 七、待做项

- CDN 回源鉴权（URL 鉴权/回源鉴权头）与私有桶策略。
- 分享链接、打包下载是否走 CDN 的评估。
- 路线图其余项：文件版本（需单独拆批：数据模型、列表过滤、回滚、清理与配额口径）、
  存储迁移工具、多段 Range、存储加密、策略热更新、防盗链/限流。
