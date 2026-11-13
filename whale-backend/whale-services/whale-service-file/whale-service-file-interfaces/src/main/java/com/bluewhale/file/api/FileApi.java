package com.bluewhale.file.api;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.file.model.request.FilePageRequest;
import com.bluewhale.file.model.request.FileRapidUploadRequest;
import com.bluewhale.file.model.response.FileInfoResponse;
import com.bluewhale.file.model.response.FilePresignedResponse;
import com.bluewhale.file.model.response.FileQuotaResponse;
import com.bluewhale.file.model.response.FileRapidUploadResponse;
import com.bluewhale.file.model.response.FileUploadResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务 API 契约（上传/下载/预览/查询/删除；分片上传见 {@link FileChunkUploadApi}）。
 *
 * <p>本接口只声明 HTTP 契约（路径、方法、参数与返回 DTO），业务实现位于 web 适配层。
 * 路径统一版本化为 {@code /file/v1/**}，内网服务间接口见 {@link FileInternalApi}。</p>
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/file/v{version}", version = "1")
public interface FileApi {

    /**
     * 上传文件（普通全量上传）。
     *
     * <p>普通上传为全量读入通道，单文件上限取全局默认值（不随权限码放宽）；
     * 更大的文件请使用分片上传接口。</p>
     *
     * @param file 上传的文件，必填
     * @param bucket 目标桶名称，可选；为空时取配置的默认桶
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 与权限码）
     * @return 上传结果
     */
    @PostMapping("/upload")
    Result<FileUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "bucket", required = false) String bucket,
                                      HttpServletRequest httpRequest);

    /**
     * 上传文件（内网服务间接口，外部经网关不可达）。
     *
     * @param file 上传的文件，必填
     * @param bucket 目标桶名称，可选；为空时取配置的默认桶
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id}）
     * @return 上传结果
     */
    @PostMapping("/internal/upload")
    Result<FileUploadResponse> internalUpload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "bucket", required = false) String bucket,
                                              HttpServletRequest httpRequest);

    /**
     * 流式下载文件内容（支持 HTTP Range 分段）。
     *
     * @param id 文件ID
     * @param range 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRange 请求的 {@code If-Range} 头，可为 {@code null}
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 文件内容流式响应
     */
    @GetMapping("/{id}/download")
    ResponseEntity<Resource> download(@PathVariable Long id,
                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                      @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                      HttpServletRequest httpRequest);

    /**
     * 在线预览文件内容：安全类型以 {@code inline} 呈现，其余类型回落 {@code attachment} 下载。
     *
     * @param id 文件ID
     * @param range 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRange 请求的 {@code If-Range} 头，可为 {@code null}
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 文件内容流式响应
     */
    @GetMapping("/{id}/preview")
    ResponseEntity<Resource> preview(@PathVariable Long id,
                                     @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                     @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                     HttpServletRequest httpRequest);

    /**
     * 生成预签名下载 URL。
     *
     * @param id 文件ID
     * @param expiresSeconds 有效期（秒），默认 600、上限 3600
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 预签名 URL 与生效有效期
     */
    @GetMapping("/{id}/presigned")
    Result<FilePresignedResponse> presigned(@PathVariable Long id,
                                             @RequestParam(value = "expiresSeconds", required = false)
                                             Integer expiresSeconds,
                                             HttpServletRequest httpRequest);

    /**
     * 秒传（哈希去重）：命中已有物理对象时仅登记新元数据，不重复上传内容。
     *
     * @param request 秒传请求：fileName、md5（32 位十六进制）、size、contentType（可选）
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 与权限码）
     * @return 秒传结果
     */
    @PostMapping("/upload/rapid")
    Result<FileRapidUploadResponse> rapidUpload(@Valid @RequestBody FileRapidUploadRequest request,
                                                HttpServletRequest httpRequest);

    /**
     * 查询当前用户的个人存储配额与用量。
     *
     * @param httpRequest 当前请求（用于读取 {@code X-User-Id} 与 {@code X-Permission-Codes}）
     * @return 配额响应；无用户态时返回不限配额
     */
    @GetMapping("/quota")
    Result<FileQuotaResponse> quota(HttpServletRequest httpRequest);

    /**
     * 分页查询文件元数据。
     *
     * @param request 查询条件：originalName 模糊、bucket 等值、createTime 起止
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 文件分页结果
     */
    @GetMapping("/page")
    Result<PageResult<FileInfoResponse>> page(FilePageRequest request, HttpServletRequest httpRequest);

    /**
     * 删除文件元数据（逻辑删除，物理对象保留）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest);
}
