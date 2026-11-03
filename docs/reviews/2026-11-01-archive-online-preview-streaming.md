# 检查报告：档案在线预览 / 播放与流式代理下载

- 日期：2026-11-01
- 范围：`whale-archive`（8093）文件内容读取链路 + `frontend/admin` 档案详情预览 UI
- 关联计划：[wave5.53-archive-file-knowledge.md](../plans/wave5.53-archive-file-knowledge.md)
- 关联日志：[devlog 2026-11-01](../devlog/2026-11-01.md)（「追加：档案服务在线预览 / 播放」与「追加：档案下载切换为流式代理」两节）
- 前序报告：[2026-10-25-wave5.53-m5-archive-knowledge.md](./2026-10-25-wave5.53-m5-archive-knowledge.md)
- 结论：**在线预览 / 播放与流式下载代理已完成**（归档模块单测 **73/73**、Checkstyle **0 违规**、ArchUnit **4/4**；前端 `tsc -b` / `vite build` / `eslint --max-warnings 0` 全 0）。**实机项**（200 / 206 / 416、`If-Range`、音视频拖拽、秘密级 404、跨租户 404）因无 MySQL / Nacos / `whale-file` 环境未执行。

---

## 1. 交付内容

| 任务 | 交付物 | 文件 |
|---|---|---|
| 预览端点 | `GET /archive/item/{id}/files/{fileId}/preview`：透传 `Range` / `If-Range`，200 / 206 / 416 | `whale-archive/.../controller/ArchiveItemController.java` |
| 流式客户端 | `openContent(id, range, ifRange)`：JDK `HttpClient`（HTTP/1.1）+ `BodyHandlers.ofInputStream`，不整体缓冲 | `whale-archive/.../client/FileArchiveClient.java` |
| 流式载荷 | 状态码、`Content-Type`、长度、`Content-Range`、总字节数、`ETag`、内容流 | `.../remote/RemoteContentStream.java`、`.../vo/ArchivePreviewVO.java` |
| 响应头工具 | MIME 解析、内联白名单、`Content-Disposition`（ASCII 兜底 + RFC 5987） | `.../support/ArchiveFileResponses.java` |
| 服务层 | 下载 / 预览共用 `loadContent`：条目存在 → 密级可见 → 文件归属三重校验 | `.../service/ArchiveItemService.java` |
| 前端 | 文件清单「预览」按钮 + Modal（`img` / `video` / `audio` / `iframe`）、blob 对象 URL 回收、200MB 音视频提示 | `frontend/admin/src/api/archive.ts`、`frontend/admin/src/pages/archive/ItemList.tsx` |
| 单测 | 内联白名单 / `Content-Range` 解析 / 下载与预览用例 | `.../support/ArchiveFileResponsesTest.java`、`.../client/FileArchiveClientTest.java`、`.../service/ArchiveItemServiceTest.java` |

**清理项**：`ArchiveDownloadVO`（旧的全量 `byte[]` 下载载荷）已随流式改造删除，无残留引用。

## 2. 逐项核对

| 项 | 要求 | 结果 | 证据 |
|---|---|---|---|
| 端点分离 | 预览与下载各自独立路径、同一权限码 | 通过 | 两条 `@GetMapping` 均标 `@RequirePermission("sys:archive:item:download")` |
| Range 语义 | 200 全量 / 206 分段 / 416 越界 | 通过（单测） | `buildContentResponse` 按下游状态分支；416 置 `Content-Range: bytes */<total>` + `Content-Length: 0`；`previewShouldPassThroughRangeHeaders` 断言 206 头 |
| 越界不泄露内容 | 416 不返回内容流 | 通过 | `FileArchiveClient.openContent` 416 分支 `closeQuietly` 后 `body=null`；`previewShouldCarryRangeTotalOnRefused` 断言 `body==null` |
| 内联白名单 | 仅安全类型 `inline`，其余 `attachment` | 通过 | `ArchiveFileResponses.isInlinePreviewable`：`image/ video/ audio/ text/` 前缀 + `application/pdf|json|xml|xhtml+xml`；`ArchiveFileResponsesTest` 覆盖大小写、带 `charset`、zip / octet-stream / null |
| 下载语义不变 | 始终 `attachment`，支持断点续传 | 通过 | 下载走同一响应构造但 `inline=false`；同时透传 `Range` / `If-Range` |
| 权限与密级 | 秘密级无 `sys:archive:secret:read` → 404 | 通过（单测） | `loadContent` 先 `ensureSecurityVisible`；`previewShouldBlockSecretItemWithoutPermission` / `downloadShouldBlockSecretItemWithoutPermission` 断言不调用 `openContent` |
| 文件归属 | 文件不属于该条目 → 404 | 通过（单测） | `requireFileRef` 校验 `itemId`；`previewRejectsForeignFile` / `downloadRejectsForeignFile` |
| 审计口径 | 下载留痕、预览不留痕 | 通过 | 下载保留 `@AuditLog(EXPORT)`；预览无注解（Range 分段高频） |
| 不整体缓冲 | 下载与预览均流式 | 通过 | `content(Long)`（`RestClient` + `byte[].class`）已删除；仅剩 `openContent` |
| 缓存与嗅探 | 私有、不缓存、禁嗅探 | 通过 | `Cache-Control: private, no-store`、`X-Content-Type-Options: nosniff`、`Accept-Ranges: bytes` |
| 前端对象 URL | 关闭 / 卸载即回收 | 通过 | `ItemList.tsx` 中 `useEffect` 返回 `URL.revokeObjectURL(preview.url)` |
| 门禁 | 单测全绿 + Checkstyle 0 + ArchUnit 通过 + 前端构建通过 | 通过 | 见 §4 |

## 3. 关键设计取舍

1. **流式而非 `RestClient`**：`RestClient` 的 `byte[]` 转换会把响应体整体读入堆，归档文件可达 GB 级；改用 JDK `HttpClient` + `ofInputStream` 与 `whale-music` / `whale-video` 已有实现保持一致，并固定 HTTP/1.1 避免 h2c 探测往返。
2. **下载与预览共用一条读取路径**：两者差异只有 `Content-Disposition`，把差异收敛到 controller 的 `inline` 标志，避免校验逻辑（条目 / 密级 / 归属）出现两份实现而走偏。
3. **预览按最严格的权限码收敛**：沿用 `sys:archive:item:download`，而不是新增 `preview` 权限码——预览与下载在「能看到文件内容」这一事实层面等价，避免出现「能预览不能下载」的权限缝隙。
4. **预览不落审计**：音视频播放一秒钟可能多次 Range 请求，落审计会形成刷屏与存储放大；下载仍保留 `EXPORT` 类型审计，`recordResult` 默认 `false`，流式响应体不进入序列化。
5. **前端 blob + 对象 URL**：`<img>` / `<video>` / `<a download>` 无法携带 `Authorization`，改用 `fetch`（带 Bearer）+ `URL.createObjectURL`；大文件（>200MB 音视频）提示「下载后播放」，避免整块入内存。
6. **`Content-Disposition` 下沉为静态工具**：下载与预览共用同一实现，非 ASCII / 控制符 / 引号统一替换为 `_`，并附 RFC 5987 `filename*=UTF-8''`，避免早期 controller 私有方法重复。

## 4. 验证证据

| 检查 | 命令 | 结果 |
|---|---|---|
| 单测 | `mvn -o -q -pl whale-services/whale-archive test` | **73/73**（14 suites，0 failures / 0 errors） |
| Checkstyle | `mvn -o -q -pl whale-services/whale-archive checkstyle:check` | **EXIT=0**；`checkstyle-result.xml` 中 `severity="error"` 计数 **0**（仅 `info` 级 MagicNumber 提示） |
| ArchUnit | 同上（`ArchiveArchitectureTest`） | **4/4**，0 failure |
| 前端类型 | `tsc -b`（`frontend/admin`） | exit 0 |
| 前端构建 | `vite build` | 成功（2146 modules，dist 1,639.10 kB） |
| 前端 lint | `eslint src --max-warnings 0` | exit 0 |

新增用例：`ArchiveFileResponsesTest` 4 例、`ArchiveItemServiceTest` 预览 4 例 + 下载流式 1 例、`FileArchiveClientTest` `parseTotal` 1 例。

## 5. 遗留与后续

- **实机补测（缺环境）**：起服 `whale-archive`(8093) / `whale-file`(8085) 后验证 200 / 206 / 416、`If-Range`、音视频拖拽进度与首字节时间、秘密级无权限 404、跨租户 404、超大文件（1GB+）下载内存占用。
- **前端仍整块下载**：`downloadItemFile` 为 fetch blob + `<a download>`，大文件会整块入内存；如需边下边写，需部署层签名 URL / 短时票据（与渐进式播放同源）。
- **渐进式播放**：当前预览为 blob，不能边下边播；若要 `<video src>` 直连流式，同样依赖短时票据方案。
- **知识库侧未覆盖音视频**：`whale-agent` 的 `FileDocumentParser` 白名单不含 `video/` `audio/`（无 OCR / ASR），归档音视频仍不可检索，如需检索要另行接入转写。
- **权限码细化（可选）**：若后续要求「可预览不可下载」（如只读查阅场景），再引入独立预览权限码并同步种子与前端按钮。
