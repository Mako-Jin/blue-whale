# Tasks：文件服务应用层去 Web 化 + 架构守护强化

> 依据 `spec.md`，按顺序执行；每项完成后立即标记完成。

## [x] T1 新增应用层中立类型
- 新建 `backend/whale-services/whale-service-file/whale-service-file-application/src/main/java/com/bluewhale/file/service/model/UploadContent.java`
- record：`originalName`、`contentType`、`content(byte[])`；方法 `size()`、`isEmpty()`
- 仅 JDK 依赖，无框架 import

## [x] T2 改造应用层接口
- 修改 `.../file/service/FileInfoService.java`
  - `upload(UploadContent content, String bucket, Long uploaderId)`
  - `uploadChunkPart(String uploadId, int index, UploadContent part)`
  - `Resource loadResource(Long)` → `InputStream openContent(Long)`
  - 移除 `MultipartFile` / `Resource` 导入与 Javadoc 中对应表述

## [x] T3 改造应用层实现
- 修改 `.../file/service/impl/FileInfoServiceImpl.java`
  - `upload` 使用 `UploadContent`（isEmpty/size/content/originalName/contentType）
  - `uploadChunkPart` 使用 `ByteArrayInputStream(part.content())`，移除 `file.getInputStream()` 与 IO catch
  - `openContent` 直接返回 `fileStorage.load(...)`
  - 删除 `InputStreamResource` / `Resource` / `MultipartFile` 导入与 `validateFile`、`readBytes` 私有方法

## [x] T4 改造 interfaces 层
- 修改 `.../file/controller/FileController.java`
  - 新增 `toUploadContent(MultipartFile)`：`file.getBytes()`，`IOException` → `BizException(SYSTEM_ERROR)`
  - `upload` / `internalUpload` / `uploadChunkPart` 传入 `UploadContent`
  - `download` / `preview` 用 `new InputStreamResource(fileInfoService.openContent(id))`
- 修改 `.../file/controller/FileInternalController.java`
  - `content` 用 `new InputStreamResource(fileInfoService.openContent(id))`

## [x] T5 调整测试
- 修改 `.../file/service/impl/FileInfoServiceImplTest.java`
  - MultipartFile 桩 → `UploadContent`
  - `loadResource` → `openContent`
  - 清理无用导入
- 修改 `.../file/controller/FileControllerTest.java`、`FileInternalControllerTest.java`
  - `fileInfoService.loadResource(...)` → `fileInfoService.openContent(...)`，桩返回 `ByteArrayInputStream`
  - 保持所有断言与响应头断言不变

## [x] T6 扩展 ArchUnit 规则
- 修改 `.../whale-service-auth-architecture-test/.../AuthArchitectureTest.java`
  - 应用层禁用包追加 `org.springframework.data.redis..`、`org.springframework.security..`
- 修改 `.../whale-service-file-architecture-test/.../FileArchitectureTest.java`
  - 应用层禁用包追加 `org.springframework.web..`、`org.springframework.core.io..`、`org.springframework.data.redis..`、`org.springframework.security..`

## [x] T7 验证
- `mvn -f backend/pom.xml -DskipTests test-compile`
- `mvn -f backend/pom.xml -pl <file-application>,<file-interfaces>,<file-architecture-test>,<auth-architecture-test> -am test`
- 期望 `BUILD SUCCESS`，Checkstyle 0 违规
- 若失败：定位编译/断言问题并修复后重跑，不跳过测试
