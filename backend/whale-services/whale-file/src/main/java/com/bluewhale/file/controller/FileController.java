package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.file.dto.ChunkUploadCompleteRequest;
import com.bluewhale.file.dto.ChunkUploadInitRequest;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.vo.ChunkUploadInitVO;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

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

    /** 禁止浏览器嗅探响应内容类型的安全响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

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
                .header(HttpHeaders.CONTENT_DISPOSITION, buildContentDisposition(info.getOriginalName(), false))
                .contentType(resolveMediaType(info.getContentType()))
                .contentLength(info.getFileSize() == null ? 0L : info.getFileSize())
                .body(resource);
    }

    /**
     * 在线预览文件内容：安全类型以 {@code inline} 呈现，其余类型回落 {@code attachment} 下载。
     *
     * @param id 文件ID
     * @return 文件内容流式响应
     */
    @GetMapping("/{id}/preview")
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> preview(@PathVariable Long id) {
        FileInfoVO info = fileInfoService.getDetail(id);
        Resource resource = fileInfoService.loadResource(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        buildContentDisposition(info.getOriginalName(), isPreviewable(info.getContentType())))
                .header(NOSNIFF_HEADER, NOSNIFF_VALUE)
                .contentType(resolveMediaType(info.getContentType()))
                .contentLength(info.getFileSize() == null ? 0L : info.getFileSize())
                .body(resource);
    }

    /**
     * 生成预签名下载 URL（仅 MinIO 存储支持；local 模式返回参数错误）。
     *
     * @param id 文件ID
     * @param expiresSeconds 有效期（秒），默认 600、上限 3600
     * @return 预签名 URL 与生效有效期
     */
    @GetMapping("/{id}/presigned")
    @RequirePermission("sys:file:download")
    public Result<FilePresignedVO> presigned(@PathVariable Long id,
                                             @RequestParam(value = "expiresSeconds", required = false)
                                             Integer expiresSeconds) {
        return Result.ok(fileInfoService.presignedUrl(id, expiresSeconds));
    }

    /**
     * 初始化分片上传会话（支持传入既有 uploadId 续传）。
     *
     * @param request 初始化请求：fileName、contentType、size、bucket（可选）、uploadId（可选，续传）
     * @return uploadId、分片大小与已上传分片索引
     */
    @PostMapping("/upload/chunk/init")
    @RequirePermission("sys:file:upload")
    public Result<ChunkUploadInitVO> initChunkUpload(@Valid @RequestBody ChunkUploadInitRequest request) {
        return Result.ok(fileInfoService.initChunkUpload(request));
    }

    /**
     * 上传单个分片（同一索引重复上传覆盖）。
     *
     * @param file 分片内容，必填
     * @param uploadId 上传会话 ID
     * @param index 分片索引（从 0 开始）
     * @return 已上传分片索引（升序）
     */
    @PostMapping("/upload/chunk/part")
    @RequirePermission("sys:file:upload")
    public Result<List<Integer>> uploadChunkPart(@RequestParam("file") MultipartFile file,
                                                 @RequestParam("uploadId") String uploadId,
                                                 @RequestParam("index") int index) {
        return Result.ok(fileInfoService.uploadChunkPart(uploadId, index, file));
    }

    /**
     * 查询分片上传会话的已上传分片索引。
     *
     * @param uploadId 上传会话 ID
     * @return 已上传分片索引（升序）
     */
    @GetMapping("/upload/chunk/{uploadId}/status")
    @RequirePermission("sys:file:upload")
    public Result<List<Integer>> chunkUploadStatus(@PathVariable String uploadId) {
        return Result.ok(fileInfoService.chunkUploadStatus(uploadId));
    }

    /**
     * 完成分片上传：校验完整性后合并落存储并登记元数据。
     *
     * @param request 完成请求：uploadId、fileName、size、bucket（可选）
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 回填上传人）
     * @return 上传结果，与普通上传接口一致
     */
    @PostMapping("/upload/chunk/complete")
    @RequirePermission("sys:file:upload")
    public Result<FileUploadVO> completeChunkUpload(@Valid @RequestBody ChunkUploadCompleteRequest request,
                                                    HttpServletRequest httpRequest) {
        return Result.ok(fileInfoService.completeChunkUpload(request, resolveUploaderId(httpRequest)));
    }

    /**
     * 秒传（哈希去重）：命中已有物理对象时仅登记新元数据，不重复上传内容。
     *
     * @param request 秒传请求：fileName、md5（32 位十六进制）、size、contentType（可选）
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 回填上传人）
     * @return 秒传结果：命中时携带新登记的元数据，未命中时 {@code hit=false}
     */
    @PostMapping("/upload/rapid")
    @RequirePermission("sys:file:upload")
    public Result<FileRapidUploadVO> rapidUpload(@Valid @RequestBody FileRapidUploadRequest request,
                                                 HttpServletRequest httpRequest) {
        return Result.ok(fileInfoService.rapidUpload(request, resolveUploaderId(httpRequest)));
    }

    /**
     * 查询当前用户的个人存储配额与用量。
     *
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id}）
     * @return 配额视图对象；无用户态时返回不限配额
     */
    @GetMapping("/quota")
    @RequirePermission("sys:file:list")
    public Result<FileQuotaVO> quota(HttpServletRequest httpRequest) {
        return Result.ok(fileInfoService.quota(resolveUploaderId(httpRequest)));
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
     * 构建响应头中的 {@code Content-Disposition}（RFC 5987，UTF-8 文件名）
     *
     * @param originalName 原始文件名
     * @param inline 是否以 {@code inline} 方式呈现（预览）；否则 {@code attachment} 下载
     * @return Content-Disposition 头值
     */
    private static String buildContentDisposition(String originalName, boolean inline) {
        String name = StringUtils.hasText(originalName) ? originalName : FALLBACK_FILENAME;
        ContentDisposition.Builder disposition = inline
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        return disposition.filename(name, StandardCharsets.UTF_8).build().toString();
    }

    /**
     * 判断 MIME 类型是否可在线预览：图片、音视频、文本与 PDF 为安全类型（不区分大小写）
     *
     * @param contentType 存储的 MIME 类型
     * @return 可安全内联预览时返回 {@code true}
     */
    private static boolean isPreviewable(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        String type = contentType.trim().toLowerCase(Locale.ROOT);
        return type.startsWith("image/")
                || type.startsWith("video/")
                || type.startsWith("audio/")
                || type.startsWith("text/")
                || type.equals("application/pdf");
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
