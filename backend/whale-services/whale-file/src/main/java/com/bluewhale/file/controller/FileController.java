package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FileUploadVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 文件服务接口。
 *
 * <p>服务内路径 {@code /file/**}；经网关按 {@code Path=/file/**} 规则转发，外部路径与本文档一致。
 * 除 {@code POST /file/internal/upload} 外，全部接口受 {@code @RequirePermission("sys:file:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。
 * {@code POST /file/internal/upload} 为内网服务间接口，外部经网关不可达（网关侧对该路径 403 屏蔽）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    /** 下载文件名缺失时的占位名 */
    private static final String FALLBACK_FILENAME = "download";

    private final FileInfoService fileInfoService;

    /**
     * 上传文件（外部接口）。
     *
     * @param file 上传的文件，必填
     * @param bucket 目标桶名称，可选；为空时取配置的默认桶
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 回填上传人）
     * @return 上传结果
     */
    @PostMapping("/upload")
    @RequirePermission("sys:file:upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "bucket", required = false) String bucket,
                                       HttpServletRequest httpRequest) {
        Long uploaderId = resolveUploaderId(httpRequest);
        return Result.ok(fileInfoService.upload(file, bucket, uploaderId));
    }

    /**
     * 上传文件（内网服务间接口，外部经网关不可达）。
     *
     * <p>仅供服务间内网调用（whale-job 归档导出、whale-audit 审计导出等），
     * 语义与 {@code POST /file/upload} 一致，但不加 {@code @RequirePermission}；
     * 网关侧对该路径做 403 屏蔽，外部请求不可达。</p>
     *
     * @param file 上传的文件，必填
     * @param bucket 目标桶名称，可选；为空时取配置的默认桶
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 回填上传人）
     * @return 上传结果
     */
    @PostMapping("/internal/upload")
    public Result<FileUploadVO> internalUpload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "bucket", required = false) String bucket,
                                               HttpServletRequest httpRequest) {
        Long uploaderId = resolveUploaderId(httpRequest);
        return Result.ok(fileInfoService.upload(file, bucket, uploaderId));
    }

    /**
     * 流式下载文件内容。
     *
     * @param id 文件ID
     * @return 文件内容流式响应（{@code Content-Disposition: attachment}）
     */
    @GetMapping("/{id}/download")
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        FileInfoVO info = fileInfoService.getDetail(id);
        Resource resource = fileInfoService.loadResource(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(info.getOriginalName()))
                .contentType(resolveMediaType(info.getContentType()))
                .contentLength(info.getFileSize() == null ? 0L : info.getFileSize())
                .body(resource);
    }

    /**
     * 分页查询文件元数据。
     *
     * @param request 查询条件：originalName 模糊、bucket 等值、createTime 起止
     * @return 文件分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:file:list")
    public Result<PageResult<FileInfoVO>> page(FileQueryRequest request) {
        return Result.ok(fileInfoService.page(request));
    }

    /**
     * 删除文件元数据（逻辑删除，物理对象保留）。
     *
     * @param id 文件ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:file:delete")
    public Result<Void> delete(@PathVariable Long id) {
        fileInfoService.delete(id);
        return Result.ok();
    }

    /**
     * 解析上传用户 ID：以网关 {@code X-User-Id} 为准，缺失或不可解析时返回 {@code null}
     *
     * @param request 当前请求
     * @return 上传用户ID；无有效值时返回 {@code null}
     */
    private static Long resolveUploaderId(HttpServletRequest request) {
        String userId = WebHeaders.userId(request);
        if (userId == null) {
            return null;
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建下载响应头中的 {@code Content-Disposition}（RFC 5987，UTF-8 文件名）
     *
     * @param originalName 原始文件名
     * @return Content-Disposition 头值
     */
    private static String buildContentDisposition(String originalName) {
        String name = StringUtils.hasText(originalName) ? originalName : FALLBACK_FILENAME;
        return ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString();
    }

    /**
     * 解析响应媒体类型，非法或缺失时回落 {@code application/octet-stream}
     *
     * @param contentType 存储的 MIME 类型
     * @return 响应媒体类型
     */
    private static MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
