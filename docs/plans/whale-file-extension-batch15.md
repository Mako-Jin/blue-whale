# whale-file 能力扩展（批次 15：多段 Range 下载）

## 一、范围与决策

下载/预览已支持单段字节区间，多段 Range 按 RFC 9110 被整体忽略、回落 200 全量。本批次补齐
多段 Range：返回 `206` + `multipart/byteranges`，让播放器/断点续传客户端可以一次请求多个片段。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 多段 Range 解析 | ✅ | `bytes=0-99,200-299`、后缀区间混合 |
| `multipart/byteranges` 响应 | ✅ | 每段带 `Content-Type` 与 `Content-Range`，流式拼接 |
| 部分越界 | ✅ | 丢弃越界段，保留可满足段；全部越界返回 416 |
| 段数上限 | ✅ | 超过 8 段按 RFC 忽略 Range，回落 200 全量 |
| `If-Range` 协商 | ✅ | 与单段一致：ETag 不匹配时不返回任何区间 |
| 多重段合并/排序 | ❌ | 保持请求顺序；见待做项 |
| 多段 Range 预签名 | ❌ | 预签名仍由对象存储直连处理 |
| 数据库变更 | ❌ | 无 |

**安全边界**：段数上限 8，避免单请求放大读取；仍受鉴权、文件归属与下载权限保护。

## 二、设计

`FileContentResponses` 的 Range 解析从「单段」升级为「区间集合」：

1. `If-Range` 校验失败、单位不是 `bytes`、语法非法或段数超过 8 → 忽略 Range，200 全量；
2. 逐段解析：可满足段保留，越界段丢弃并记录；全部越界 → 416 + `Content-Range: bytes */size`；
3. 仅 1 段可满足 → 维持现有单段 206 行为；
4. 多段可满足 → 206，`Content-Type: multipart/byteranges; boundary=...`，
   响应体按请求顺序拼接：每段 `--boundary` + `Content-Type` + `Content-Range` + 空行 + 区间流 + CRLF，
   最后 `--boundary--`；不设置 `Content-Length`（走分块传输）。

区间流仍由 `ContentSource#openRange` 提供，下载、预览、分享链接与内网接口共用同一实现。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 控制器 | 多段命中返回 206 + `multipart/byteranges`，响应体包含两段 `Content-Range` 与内容；部分越界只返回可满足段；全部越界返回 416；超过 8 段回落 200 全量；多段 + 失效 `If-Range` 回落 200 |
| 回归 | 单段、后缀区间、无 Range、416 与既有下载/预览用例保持通过 |

## 四、完成情况

- `FileContentResponses` 的 Range 解析升级为区间集合：`resolveRanges` + `parseRange`，
  支持单段、多段、后缀区间与部分越界；超过 8 段、语法非法或 `If-Range` 失败时回落 200 全量；
  全部越界返回 416。
- 多段可满足时返回 206 + `multipart/byteranges; boundary=...`，`multiPartStream` 按请求顺序
  拼接每段边界、`Content-Type`、`Content-Range` 与区间流，末尾追加结束边界；不设置
  `Content-Length`。
- 下载、预览、分享链接与内网内容接口共用同一实现，无需改动调用方。
- 测试：控制器新增多段命中、部分越界、全部越界、段数超限、多段 + 失效 `If-Range` 共 5 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
342 项单测零失败（批次 14 为 337，本轮新增 5 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- `RangeRequest` 的静态工厂 `refused()` 与 record 组件访问器 `refused()` 同名冲突，导致
  「记录中的存取方法无效」编译失败 → 静态工厂改名为 `refusedAll()`。

## 七、待做项

- 重叠/相邻区间合并与按偏移排序，减少响应体积。
- 多段响应总字节上限（当前仅限制段数）。
- 预签名直连场景的多段 Range 由对象存储实现，网关层不做干预。
- 客户端兼容性验证：部分旧客户端对 `multipart/byteranges` 支持有限，可按需增加开关。
