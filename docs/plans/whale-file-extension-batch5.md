# whale-file 能力扩展（批次 5：批量删除与打包下载）

## 一、范围与决策

批次 1~4 已完成存储类型路由、回收站生命周期、重命名/移动、指标与健康检查、孤儿对象对账、分享链接。
本批次落地路线图阶段 2 第二项：批量删除与打包下载。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| 批量逻辑删除 | ✅ | 单次上限可配置，逐项返回成功/失败原因，不因单项失败回滚整批 |
| 打包 ZIP 下载 | ✅ | 流式写出，不落本地临时文件，不占用堆内存 |
| ZIP 条目名去重 | ✅ | 同名文件插入 ` (n)` 后缀，保留扩展名 |
| 打包数量与体积上限 | ✅ | 条目数复用批量上限，未压缩总字节数单独设上限 |
| 权限复用 | ✅ | 删除复用 `sys:file:delete`，打包复用 `sys:file:download`，不新增权限码 |
| 租户可见性 | ✅ | 逐项复用 `FileAssetAggregate#assertVisibleTo`，跨租户与越权按不存在处理 |
| ZIP64 分卷/加密 | ❌ | 超 4GB 由 JDK 自动切换 ZIP64 单卷；分卷与加密不在本轮范围 |
| 断点续传打包 | ❌ | 响应为一次性流，不支持 Range 续传 |

**安全原则**：打包条目名只取原始文件名片段，剥离目录与 `..`，防解压时目录穿越；
越权与跨租户统一按「不存在」返回，不泄露文件是否存在；单项失败只记原因，不中断整批。

## 二、设计

### 2.1 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/file/v1/batch/delete` | `sys:file:delete` | 批量逻辑删除，返回逐项结果 |
| GET | `/file/v1/batch/download?ids=1,2,3` | `sys:file:download` | 打包 ZIP 流式下载 |

请求体 `{"ids":[1,2,3]}`；删除与打包共用同一批 ID 上限。`ids` 支持重复参数与逗号分隔两种写法。

### 2.2 批量删除语义

- 去重后按请求顺序处理，单次不超过 `whale.file.batch.max-files`（默认 100），超限整体拒绝 `PARAM_ERROR`。
- 逐项独立处理，**方法不声明事务**：每项读取 + 条件更新各自提交，单项失败不影响其余项。
- 单项失败原因：不存在/越权/跨租户 → `NOT_FOUND` 口径提示；归档锁定 → 「文件已归档锁定，禁止删除」。
- 响应含 `total` / `successCount` / `failureCount` 与 `items[{id, success, message}]`，前端据此提示。

### 2.3 打包下载语义

- **两阶段**：先 `prepare` 校验全部 ID（可见性、数量上限、未压缩总字节上限）并生成去重条目名，
  错误在响应提交前以正常异常返回；再 `writeZip` 流式写出，避免先提交 200 再中途失败。
- **流式**：`StreamingResponseBody` + `ZipOutputStream` 直接写响应输出流，逐文件 8KB 缓冲复制，
  不创建本地临时文件，内存占用与文件大小无关。
- **条目名**：取原始文件名最后一段，替换控制字符；`\` 归一为 `/`；空名、`.`、`..` 回落占位名。
  同名（忽略大小写）插入 ` (1)`、` (2)`…，扩展名保留。
- **体积上限**：`whale.file.batch.max-zip-bytes`（默认 4GB）限制未压缩总字节数；
  超过 4GB 的响应由 JDK 自动使用 ZIP64 记录，仍是单卷流。
- **响应头**：`Content-Type: application/zip`、`Content-Disposition: attachment; filename="files-<yyyyMMddHHmmss>.zip"`、
  `X-Content-Type-Options: nosniff`；无 `Content-Length`（分块传输）。

### 2.4 配置（`FileProperties.Batch`）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `whale.file.batch.max-files` | 100 | 单次批量删除/打包的最大文件数 |
| `whale.file.batch.max-zip-bytes` | 4294967296 | 打包未压缩总字节上限 |

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用（删除） | 全成功计数、单项不存在/越权失败、归档锁定失败、空列表与超上限拒绝、重复 ID 去重 |
| 应用（打包） | 条目名去重与扩展名保留、目录穿越名剥离、数量超限、总字节超限、不可见文件拒绝 |
| 打包内容 | 解压后条目名与内容与源文件一致、同名条目均存在 |
| 接口 | 删除请求映射与逐项响应、打包响应头与 ZIP 内容、权限注解 |

## 四、完成情况

- 配置：`FileProperties.Batch`（`max-files` / `max-zip-bytes`）与 `application.yml` 的
  `whale.file.batch.*` 环境变量绑定。
- 应用（删除）：`FileInfoService#batchDelete` 去重、空值与数量上限校验，逐项调用
  `requireAccessibleFile` + `assertDeletable` + `deleteById`，单项 `BizException` 收敛为失败原因；
  方法不声明事务，每项条件更新独立提交。新增 `FileBatchDeleteCommand`、
  `FileBatchDeleteResult`、`FileBatchDeleteItemResult`。
- 应用（打包）：`FileArchiveService` 两阶段——`prepare` 完成 ID 去重、文件数/总字节上限、
  逐文件可见性校验与条目名生成；`writeZip` 用 `ZipOutputStream` + 8KB 缓冲直接写响应流，
  不创建临时文件。条目名剥离目录、剔除控制字符、忽略大小写去重并在扩展名前插入 ` (n)`。
- 接口：`POST /file/v1/batch/delete`（`sys:file:delete`）、
  `GET /file/v1/batch/download`（`sys:file:download`）；新增 `FileBatchApi`、
  `FileBatchDeleteRequest`/`FileBatchDeleteResponse`。
- Web：`FileBatchController` 先 `prepare` 再返回 `StreamingResponseBody`，
  响应头为 `application/zip` + `attachment; filename="files-<yyyyMMddHHmmss>.zip"` + `nosniff`。

## 五、验证结果

`mvn -o test`（`whale-backend/whale-services/whale-service-file`）→ 13 个模块 BUILD SUCCESS，
249 项单测零失败（批次 4 为 235，本轮新增 14 项），全部模块 checkstyle 0 violation。
新增用例分布：`FileInfoServiceImplTest` +4（去重、逐项失败、上限、空 ID）、
`FileArchiveServiceImplTest` 7 项（命名、剥离路径、上限、不可见、空列表、ZIP 内容）、
`FileBatchControllerTest` 3 项（逐项响应、空列表校验、打包响应头与委派）。

## 六、已修正问题项

- 打包错误可见性：若在 `StreamingResponseBody` 内做校验，响应已提交后无法再返回 4xx；
  拆为 `prepare`（校验 + 命名）+ `writeZip`（纯流式写出）两阶段。
- 请求 DTO 的 `@Size(max = 100)` 与 `whale.file.batch.max-files` 重复硬编码，配置调大后会误拒；
  移除注解中的数量约束，统一由应用层按配置校验。
- `DomainException` 继承 `BizException`，批量删除单项失败只需捕获 `BizException`，
  避免重复分支；系统级异常（如数据库不可用）不吞掉，直接中止批次。
- 控制器测试直接 `unzip` 响应体需构造异步输出流；改为控制器只断言响应头与委派、
  ZIP 条目内容由 `FileArchiveServiceImplTest` 覆盖，避免测试依赖 MockMvc 异步调度细节。

## 七、待做项

- 打包任务异步化：大集合改为后台任务 + 下载凭据，避免长连接占用。
- 打包进度与取消、ZIP 分卷与加密。
- 批量恢复（回收站多选）与批量移动。
- 批次 1 路线图其余项：病毒扫描与魔数校验、租户/桶级配额、文件版本、存储迁移、CDN、领域事件、多段 Range、加密、策略热更新。
