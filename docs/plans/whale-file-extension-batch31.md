# whale-file 能力扩展（批次 31：批量打包下载）

## 一、范围与决策

阶段 2 路线图中的「批量删除/打包下载」只完成了批量删除，用户批量取回多个文件仍需逐个下载。
本批次新增 ZIP 流式打包下载：一次请求按文件 ID 列表把多个文件打成 ZIP 返回，复用既有可见性
校验、落盘介质路由与下载指标，不新建存储对象、不引入异步任务。

| 事项 | 本轮 | 说明 |
| --- | --- | --- |
| `GET /file/v1/batch/download` | ✅ | `ids` 重复参数，ZIP 流式响应 |
| 可见性校验 | ✅ 复用 | 逐个按「上传者本人 / 管理权限 / 同租户」校验 |
| 数量上限 | ✅ | 单次最多 50 个文件，超限按参数错误拒绝 |
| 条目名安全 | ✅ | 去除路径分隔符，同名自动追加序号，避免解压越权覆盖 |
| 审计 | ✅ | `EXPORT`，targetId 记录文件 ID 列表 |
| 下载指标 | ✅ 复用 | 每个文件按实际读取耗时与字节数计入 `file.download` |
| 权限码 / 数据库 / 配置 | ✅ 不变 | 复用 `sys:file:download`，无迁移 |

**不做**：异步打包任务与打包结果留存。单次 50 个文件以内同步流式输出即可满足「批量取回」，
引入异步任务需要额外的结果文件存储、过期清理与轮询接口，超出本批次价值。

## 二、设计

### 2.1 应用层

- `FileInfoService#downloadBatch(ids, requesterId, canManage, out)`：
  1. 校验 `ids` 非空且不超过 50 个；
  2. 逐个 `requireAccessibleFile` 完成可见性校验——**全部通过后才开始写 ZIP**，避免响应已开始
     输出后因越权/不存在中断，产生半个压缩包；
  3. 按每个文件记录 `storageType` 路由读取，写入 UTF-8 ZIP 条目；
  4. 只调用 `ZipOutputStream.finish()`，不关闭底层输出流（生命周期归 Web 容器）。
- 条目名：把 `/`、`\` 替换为 `_`（防止解压时写出目录），空名回落 `unknown`，重名按
  `name(2).ext` 递增；
- 读取异常（IO）转换为 `SYSTEM_ERROR`；存储插件异常按原样上抛。

### 2.2 Web 层

- 响应头：`Content-Type: application/zip`、`Content-Disposition: attachment; filename="files.zip"`；
- 权限：`sys:file:download`（与单文件下载一致）；
- 审计：`@AuditLog(operation="批量打包下载", type=EXPORT, targetType="file_info", targetId="#ids")`；
- 不接受 Range：打包结果是动态生成的流，分段下载无意义。

## 三、测试计划

| 范围 | 用例 |
| --- | --- |
| 应用层 | 两个文件打包成功，条目内容正确、同名条目去重 |
| 应用层 | 空列表 / 超过 50 个按参数错误拒绝 |
| 应用层 | 任一个文件不存在或越权时整体失败，且输出流未写入任何字节 |
| Web 层 | 接口返回 ZIP 头，透传文件 ID 列表、调用者与管理权限 |
| 审计 | 反射断言 `batchDownload` 标注 `@AuditLog(EXPORT, file_info, #ids)` |
| 回归 | file 模块全量单测通过 |

## 四、完成情况

- `FileInfoService#downloadBatch`：校验 `ids` 非空且不超过 50 个，逐个 `requireAccessibleFile`
  完成可见性校验后才开始写 ZIP；按 `storageType` 路由读取，UTF-8 条目名，`ZipOutputStream.finish()`
  后不关闭底层流；每个文件按实际读取耗时与字节数计入 `file.download`；
- 条目名安全：`/`、`\` 替换为 `_`，空名回落 `unknown`，重名按 `name(2).ext` 递增；
- Web：`GET /file/v1/batch/download?ids=...`，`sys:file:download`，返回
  `application/zip` + `attachment; filename="files.zip"`；
- 审计：`@AuditLog(operation="批量打包下载", type=EXPORT, targetType="file_info", targetId="#ids")`；
- 无新增表、迁移、配置项与权限码。

## 五、验证结果

- `mvn -o test`（工作目录 `whale-backend/whale-services/whale-service-file`）→
  13 个模块 BUILD SUCCESS，390 项单测零失败，checkstyle 0 violation；
- 单测分布：domain 44、infrastructure 65、application 165、plugins-local 12、
  plugins-minio 10、architecture 4、web 90；
- 本轮新增 5 项：应用层 3（ZIP 打包与同名去重、空/超限拒绝、不可见快速失败不写字节）、
  Web 1（ZIP 头与参数透传）、审计注解 1（`EXPORT` + `#ids`）。

## 六、已修正问题项

- 未按「边校验边写出」实现：响应一旦开始输出就无法撤回，改为先完成全部可见性校验再出包，
  保证越权或不存在时返回标准错误而不是损坏的压缩包。
- 条目名直接使用原始文件名存在解压越权风险（`../` 路径穿越），增加分隔符替换与重名去重。

## 七、待做项

- 打包下载联调：网关响应超时与下载审计字段取值（`target_id` 为 ID 列表，按 64 字符截断）；
- 既有候选：存储迁移工具、存储加密与密钥轮换、上传策略热更新、防盗链/限流。

