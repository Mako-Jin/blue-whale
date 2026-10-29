# Spec：文件服务应用层去 Web 化 + 架构守护强化

## 1. 背景与问题

当前 `whale-service-file-application` 的应用层接口直接暴露框架类型：

- [FileInfoService.java](file:///f:/blue-whale/backend/whale-services/whale-service-file/whale-service-file-application/src/main/java/com/bluewhale/file/service/FileInfoService.java)
  - `FileUploadVO upload(MultipartFile file, ...)` → 依赖 `org.springframework.web.multipart.MultipartFile`
  - `List<Integer> uploadChunkPart(String uploadId, int index, MultipartFile file)` → 同上
  - `Resource loadResource(Long id)` → 依赖 `org.springframework.core.io.Resource`
- [FileInfoServiceImpl.java](file:///f:/blue-whale/backend/whale-services/whale-service-file/whale-service-file-application/src/main/java/com/bluewhale/file/service/impl/FileInfoServiceImpl.java)
  - 直接 `file.getBytes()` / `file.getInputStream()` / `new InputStreamResource(...)`

这使应用层反向依赖 Web 与 IO 框架细节，违反 `interfaces → application → domain ← infrastructure` 的依赖方向，也让应用层单测必须构造 `MultipartFile` 桩。

## 2. 目标与非目标

### 目标
1. 应用层只接收/返回中立类型（JDK 类型或应用层自有类型），Web/IO 适配下沉到 `whale-service-file-interfaces`。
2. 用 ArchUnit 规则固化：`file.service..` 禁止依赖 `org.springframework.web..`、`org.springframework.core.io..`、`org.springframework.data.redis..`、`org.springframework.security..`；auth 应用层补充 redis/security 禁令。
3. 保持 HTTP 契约不变（路径、参数、响应体、下载响应头语义均不变）。
4. 全部既有测试继续通过，Checkstyle 0 违规。

### 非目标（明确排除）
- **不**把 auth 的 record 领域事件改造为 `AggregateDomainEvent`：
  - `User` 聚合 id 为 `private final Long id`，`create` 时传入 `null`，`CREATED` 事件无法携带聚合 id；
  - 大量测试通过 `verify(eventPublisher).publish(new XxxDomainEvent(...))` 依赖 record `equals`；
  - 事件消费方（缓存失效/审计监听）按 `aggregateId()` 取值，rename 为 `getAggregateId()` 会扩散改动。
- 不新增 `AggregateDomainEvent` 基类（无消费方，属于未被要求的抽象）。
- 不改数据库 schema、不改网关配置、不改 MQ/代理配置。
- 不处理应用层对 `org.springframework.util.*`（`StringUtils`/`DigestUtils`）与 `org.springframework.stereotype`/`transaction` 的既有依赖（属于 Spring 容器/工具，非 Web 细节）。

## 3. 设计

### 3.1 新增应用层中立类型
文件：`whale-service-file-application/src/main/java/com/bluewhale/file/service/model/UploadContent.java`

```java
package com.bluewhale.file.service.model;

/**
 * 上传内容（应用层中立类型）：屏蔽 Web 上传对象，仅暴露文件名、MIME 类型与字节内容。
 */
public record UploadContent(String originalName, String contentType, byte[] content) {

    /** 内容字节数 */
    public long size() {
        return content == null ? 0L : content.length;
    }

    /** 内容是否为空 */
    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
```

仅依赖 JDK，位于 application 模块。

### 3.2 `FileInfoService` 接口变更
文件：`.../file/service/FileInfoService.java`

| 旧签名 | 新签名 |
| --- | --- |
| `FileUploadVO upload(MultipartFile file, String bucket, Long uploaderId)` | `FileUploadVO upload(UploadContent content, String bucket, Long uploaderId)` |
| `List<Integer> uploadChunkPart(String uploadId, int index, MultipartFile file)` | `List<Integer> uploadChunkPart(String uploadId, int index, UploadContent part)` |
| `Resource loadResource(Long id)` | `InputStream openContent(Long id)` |

同步删除 `import org.springframework.core.io.Resource;`、`import org.springframework.web.multipart.MultipartFile;`，新增 `java.io.InputStream`、`UploadContent` 导入；Javadoc 同步改写（参数名 `file` → `content`/`part`）。

### 3.3 `FileInfoServiceImpl` 变更
文件：`.../file/service/impl/FileInfoServiceImpl.java`

- `upload(UploadContent content, String bucket, Long uploaderId)`：
  - 校验 `content == null || content.isEmpty()` → `BizException(PARAM_ERROR)`
  - `content.size() > fileProperties.getMaxSizeBytes()` → `PARAM_ERROR`
  - `validateQuota(content.size(), uploaderId)`
  - `persistFile(new ByteArrayInputStream(content.content()), content.originalName(), content.contentType(), content.size(), DigestUtils.md5DigestAsHex(content.content()), bucket, uploaderId)`
- `uploadChunkPart(String uploadId, int index, UploadContent part)`：
  - 校验 `part == null || part.isEmpty() || index < 0`
  - `part.size() > resolveChunkSize()` → 分片超限
  - `chunkUploadStore.storePart(sessionId, index, new ByteArrayInputStream(part.content()), part.size())`
  - 原 `try (InputStream in = file.getInputStream())` 与 `catch (IOException)` 移除（`ByteArrayInputStream` 不再抛 IO）
- `openContent(Long id)`：`FileAsset asset = requireFile(id); return fileStorage.load(asset.getBucket(), asset.getObjectKey());`
- 删除 `import org.springframework.core.io.InputStreamResource;`、`import org.springframework.core.io.Resource;`、`import org.springframework.web.multipart.MultipartFile;`
- 删除私有方法 `validateFile(MultipartFile)`、`readBytes(MultipartFile)`，其逻辑内联进 `upload`（不再需要 `IOException` 包装，同步移除 `java.io.IOException` 导入，若其他位置仍使用则保留）

### 3.4 interfaces 层适配（Web 类型只保留在此层）
文件：`.../file/controller/FileController.java`
- `upload` / `internalUpload`：`fileInfoService.upload(toUploadContent(file), bucket, uploaderId)`
- 新增私有方法：
  ```java
  private static UploadContent toUploadContent(MultipartFile file) {
      try {
          return new UploadContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
      } catch (IOException e) {
          throw new BizException(ErrorCode.SYSTEM_ERROR);
      }
  }
  ```
- `uploadChunkPart`：`fileInfoService.uploadChunkPart(uploadId, index, toUploadContent(file))`
- `download` / `preview`：`Resource resource = new InputStreamResource(fileInfoService.openContent(id));`
- 新增导入 `com.bluewhale.file.service.model.UploadContent`、`org.springframework.core.io.InputStreamResource`、`com.bluewhale.common.core.exception.BizException`、`com.bluewhale.common.core.exception.ErrorCode`、`java.io.IOException`

文件：`.../file/controller/FileInternalController.java`
- `content`：`Resource resource = new InputStreamResource(fileInfoService.openContent(id));`
- 新增 `InputStreamResource` 导入

### 3.5 测试调整
- `.../whale-service-file-application/src/test/java/.../FileInfoServiceImplTest.java`
  - 删除 `multipartFile(...)` / `chunkPart(...)` MultipartFile 桩，改为 `UploadContent` 构造
  - `loadResource(...)` → `openContent(...)`
  - 删除 `MultipartFile`、`IOException`（若仍被辅助方法使用则保留）、`ByteArrayInputStream`（若 `InMemoryChunkUploadStore` 仍用则保留）相关冗余导入
- `.../whale-service-file-interfaces/src/test/java/.../FileControllerTest.java`、`FileInternalControllerTest.java`
  - `when(fileInfoService.loadResource(FILE_ID))` → `when(fileInfoService.openContent(FILE_ID))`，返回 `new ByteArrayInputStream(bytes)`
  - 上传类用例仍可使用 `MockMultipartFile`（interfaces 层允许 Web 类型）
- 断言点保持不变（响应体、响应头、状态码）。

### 3.6 ArchUnit 规则扩展
文件：`.../whale-service-auth-architecture-test/src/test/java/.../AuthArchitectureTest.java`
- `application_must_not_depend_on_infrastructure_or_within_interfaces` 的禁用包追加：
  - `org.springframework.data.redis..`
  - `org.springframework.security..`

文件：`.../whale-service-file-architecture-test/src/test/java/.../FileArchitectureTest.java`
- `application_must_not_depend_on_infrastructure_or_web` 的禁用包追加：
  - `org.springframework.web..`
  - `org.springframework.core.io..`
  - `org.springframework.data.redis..`
  - `org.springframework.security..`
- 保留既有 `application_must_not_handle_file_system_details`（`java.nio.file..`）规则不变。

## 4. 验证步骤

1. 全仓编译：
   `mvn -f backend/pom.xml -DskipTests test-compile`
2. 目标模块测试（含 ArchUnit）：
   `mvn -f backend/pom.xml -pl backend/whale-services/whale-service-file/whale-service-file-application,backend/whale-services/whale-service-file/whale-service-file-interfaces,backend/whale-services/whale-service-file/whale-service-file-architecture-test,backend/whale-services/whale-service-auth/whale-service-auth-architecture-test -am test`
3. 期望：`BUILD SUCCESS`；`FileInfoServiceImplTest`、`FileControllerTest`、`FileInternalControllerTest`、`FileArchitectureTest`、`AuthArchitectureTest` 全绿；Checkstyle 0 违规。

## 5. 风险与回滚

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 内存占用 | `UploadContent` 持有 `byte[]`，与现状一致（`upload` 原本就 `getBytes()`） | 无回归；分片最大受 `chunk-size-bytes` 限制 |
| 下载流关闭 | `openContent` 返回裸 `InputStream` | interfaces 层用 `InputStreamResource` 包装，由 Spring MVC 负责关闭 |
| 大文件字节化 | 现状已是字节化，本次不改变 | 不在本轮范围 |

回滚：本次仅涉及 file 模块内 6 个生产类/接口 + 3 个测试类 + 2 个 ArchUnit 测试，`git revert` 即可。

## 6. 待用户批准
请确认按本 Spec 执行。批准后我将用 TodoWrite 建任务并逐项实施、跑验证。
