# whale-file 能力扩展（批次 24：历史版本下载）

## 一、范围与决策

版本能力已支持上传、列表、回滚、删除、找回，但无法直接读取历史版本内容：如需旧版本内容，
只能先回滚再下载，会额外产生一次版本记录并改变当前内容。本批次增加历史版本下载。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 版本下载 | ✅ | `GET /file/v1/{id}/versions/{targetVersion}/download` |
| HTTP Range | ✅ | 复用统一响应构建：200/206/416、单段/多段、`If-Range` |
| 权限 | ✅ | 复用 `sys:file:download` |
| 归属校验 | ✅ | 与列表/回滚一致：上传者或 `sys:file:manage` |
| 已删除版本 | ❌ | 已逻辑删除版本不可下载，需先找回 |
| 下载指标 | ✅ | 复用 `recordDownload`，按实际读取字节数记录 |
| 数据库变更 | ❌ | 无 |

**语义**：下载目标是版本行自身的对象，不影响当前内容、不产生新版本、不计配额。

## 二、设计

- 应用层新增：
  - `versionDetail(id, targetVersion, requesterId, canManage)`：回溯根文件 → 在未删除版本中
    定位版本 → 转为 `FileInfoResult`（提供大小、类型、文件名等响应头信息）；
  - `openVersionContent(...)` 与区间重载：按版本自身桶/对象键/存储类型读取，记录下载指标；
- Web 层复用 `FileContentResponses.build(info, inline=false, range, ifRange, ContentSource)`，
  保持与普通下载一致的响应头、ETag、单段/多段 Range 语义；
- 版本不存在 → `NOT_FOUND`；非本人且无管理权限 → 既有归属校验错误。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用层 | 版本详情返回版本元数据；全量与区间读取命中版本对象并记录指标；版本不存在返回未找到 |
| Web | MockMvc 下载返回 200 与内容；权限注解为 `sys:file:download` |
| 回归 | 列表/上传/回滚/删除/找回/保留清理用例保持通过 |

## 四、完成情况

- 应用层新增 `versionDetail`、`openVersionContent`（全量/区间），按版本自身桶、对象键与
  存储类型读取，并记录下载指标；
- 抽出 `resolveVersion`：回溯根文件 → 定位未删除版本 → 不存在返回 `NOT_FOUND`；
- 接口新增 `GET /file/v1/{id}/versions/{targetVersion}/download`，权限复用 `sys:file:download`；
- Web 层复用 `FileContentResponses`，下载历史版本同样支持 ETag、`If-Range`、单段/多段 Range；
- 测试：版本详情与全量/区间读取、版本不存在、Web 下载与权限注解共 3 项。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
376 项单测零失败（批次 23 为 373，本轮新增 3 项），全部模块 checkstyle 0 violation。

## 六、已修正问题项

- 无。本轮实现与单测一次通过。

## 七、待做项

- 已删除版本下载（需先找回）与整组打包下载。
- 下载审计（记录下载的是哪个版本）。
- 大文件版本下载的 CDN/预签名直连（加密场景受限，见批次 20）。
