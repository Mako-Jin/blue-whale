package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.dto.FileQueryRequest;
import com.bluewhale.file.dto.FileRapidUploadRequest;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.model.UploadContent;
import com.bluewhale.file.service.model.UploadLimits;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.support.FileContentResponses;
import com.bluewhale.file.vo.FileInfoVO;
import com.bluewhale.file.vo.FilePresignedVO;
import com.bluewhale.file.vo.FileQuotaVO;
import com.bluewhale.file.vo.FileRapidUploadVO;
import com.bluewhale.file.vo.FileUploadVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件服务接口（上传/下载/查询/删除；分片上传见 {@link FileChunkUploadController}）。
 *
 * <p>服务内路径 {@code /file/**}；经网关按 {@code Path=/file/**} 规则转发，外部路径与本文档一致。
 * 除 {@code POST /file/internal/upload} 外，全部接口受 {@code @RequirePermission("sys:file:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。
 * {@code POST /file/internal/upload} 为内网服务间接口，外部经网关不可达（网关侧对该路径 403 屏蔽）。</p>
 *
 * <p>G1 大文件上传策略：分片上传按网关可信注入的 {@code X-Permission-Codes} 解析生效上限与配额
 * （视频 2GB / 8GB、电影 4GB / 不限、音乐 100MB），未命中规则仍按全局默认 50MB / 1GB 拒绝，
 * 避免为了视频上传而全局放宽全平台上限。普通上传接口为全量读入通道，
 * 单文件硬顶全局默认上限（{@code whale.file.max-size-bytes}），更大的文件必须走分片上传；
 * 权限码放宽仍作用于配额口径。</p>
 *
 * <p>用户语义约定：带 {@code @RequirePermission} 的接口必有登录态，统一用
 * {@code UserContext.requireUserId}；{@code /internal/upload} 为系统内网调用（无用户态），
 * {@code /quota} 面向"无用户态返回不限配额"的语义，二者用 {@code UserContext.findUserId}，
 * 上传人缺失时记 {@code null}、归属为系统（配额不归属个人）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    /** 文件管理权限码：持有者可查看/下载/删除全租户文件 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    private final FileInfoService fileInfoService;

    private final FileUploadPolicy fileUploadPolicy;

    /**
     * 上传文件（外部接口）。
     *
     * <p>普通上传为全量读入通道，单文件上限取全局默认值（不随权限码放宽）；
     * 更大的文件请使用分片上传接口。</p>
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
        return Result.ok(doUpload(file, bucket, UserContext.requireUserId(httpRequest),
                uploadLimits(httpRequest)));
    }

    /**
     * 上传文件（内网服务间接口，外部经网关不可达）。
     *
     * <p>仅供服务间内网调用（whale-job 归档导出、whale-audit 审计导出等），
     * 语义与 {@code POST /file/upload} 一致，但不加 {@code @RequirePermission}；
     * 网关侧对该路径做 403 屏蔽，外部请求不可达。内网调用不携带用户权限码，
     * 固定使用全局默认上限与配额，不参与 G1 按权限码放宽。</p>
     *
     * <p>内网调用通常无用户态：请求头缺失 {@code X-User-Id} 时上传人记 {@code null}，
     * 文件归属为系统（分页按人过滤不可见、配额不归属个人）。</p>
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
        return Result.ok(doUpload(file, bucket, UserContext.findUserId(httpRequest),
                fileUploadPolicy.defaults()));
    }

    /**
     * 流式下载文件内容（支持 HTTP Range 分段，浏览器可拖动音频进度）。
     *
     * @param id 文件ID
     * @param range 请求的 {@code Range} 头，可为 {@code null}
     * @param ifRange 请求的 {@code If-Range} 头，可为 {@code null}
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 文件内容流式响应（{@code Content-Disposition: attachment}）
     */
    @GetMapping("/{id}/download")
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                             @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                             HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        FileInfoVO info = fileInfoService.getDetail(id, requesterId, canManage);
        return FileContentResponses.build(fileInfoService, info, requesterId, canManage, false, range, ifRange);
    }

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
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> preview(@PathVariable Long id,
                                            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                            HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        FileInfoVO info = fileInfoService.getDetail(id, requesterId, canManage);
        return FileContentResponses.build(fileInfoService, info, requesterId, canManage,
                FileContentResponses.isPreviewable(info.getContentType()), range, ifRange);
    }

    /**
     * 生成预签名下载 URL（仅支持预签名能力的对象存储插件可用；local 模式返回参数错误）。
     *
     * @param id 文件ID
     * @param expiresSeconds 有效期（秒），默认 600、上限 3600
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 预签名 URL 与生效有效期
     */
    @GetMapping("/{id}/presigned")
    @RequirePermission("sys:file:download")
    public Result<FilePresignedVO> presigned(@PathVariable Long id,
                                             @RequestParam(value = "expiresSeconds", required = false)
                                             Integer expiresSeconds,
                                             HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        return Result.ok(fileInfoService.presignedUrl(id, expiresSeconds, requesterId, canManage(httpRequest)));
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
        return Result.ok(fileInfoService.rapidUpload(request, UserContext.requireUserId(httpRequest),
                uploadLimits(httpRequest)));
    }

    /**
     * 查询当前用户的个人存储配额与用量。
     *
     * <p>配额随当前请求权限码生效（如视频用户 8GB、电影管理员不限），与上传/秒传校验口径一致；
     * 无用户态时返回不限配额。</p>
     *
     * @param httpRequest 当前请求（用于读取网关 {@code X-User-Id} 与 {@code X-Permission-Codes}）
     * @return 配额视图对象；无用户态时返回不限配额
     */
    @GetMapping("/quota")
    @RequirePermission("sys:file:list")
    public Result<FileQuotaVO> quota(HttpServletRequest httpRequest) {
        return Result.ok(fileInfoService.quota(UserContext.findUserId(httpRequest), uploadLimits(httpRequest)));
    }

    /**
     * 分页查询文件元数据。
     *
     * <p>默认仅返回调用者本人上传的文件；持有 {@code sys:file:manage} 时可查看全租户文件
     * （服务间上传的上传人为空的文件保持租户可见）。</p>
     *
     * @param request 查询条件：originalName 模糊、bucket 等值、createTime 起止
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 文件分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:file:list")
    public Result<PageResult<FileInfoVO>> page(FileQueryRequest request, HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        return Result.ok(fileInfoService.page(request, requesterId, canManage(httpRequest)));
    }

    /**
     * 删除文件元数据（逻辑删除，物理对象保留）。
     *
     * @param id 文件ID
     * @param httpRequest 当前请求（用于判定调用者归属与文件管理权限）
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:file:delete")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        fileInfoService.delete(id, UserContext.requireUserId(httpRequest), canManage(httpRequest));
        return Result.ok();
    }

    /**
     * 上传公共流程：全量读入预检（防大文件进入堆内存）后委托应用服务。
     *
     * @param file 上传的文件
     * @param bucket 目标桶名称，可为 {@code null}
     * @param uploaderId 上传用户 ID；内网系统调用可为 {@code null}
     * @param limits 本次上传生效的上限与配额
     * @return 上传结果
     */
    private FileUploadVO doUpload(MultipartFile file, String bucket, Long uploaderId, UploadLimits limits) {
        UploadContent content = toUploadContent(file, fileUploadPolicy.defaults().maxSizeBytes());
        return fileInfoService.upload(content, bucket, uploaderId, limits);
    }

    /**
     * 解析当前请求生效的上传上限与配额（G1 大文件上传策略）
     *
     * @param request 当前请求
     * @return 生效上传上限；无权限码时返回全局默认值
     */
    private UploadLimits uploadLimits(HttpServletRequest request) {
        return fileUploadPolicy.resolve(UserContext.permissionCodes(request));
    }

    /**
     * 判定当前请求是否持有文件管理权限（{@code sys:file:manage}，超管天然具备）
     *
     * @param request 当前请求
     * @return 持有管理权限时返回 {@code true}
     */
    private static boolean canManage(HttpServletRequest request) {
        return UserContext.hasPermission(request, PERM_FILE_MANAGE);
    }

    /**
     * 将 Web 上传对象适配为应用层中立上传内容（Web 类型仅停留在接口层）。
     *
     * <p>先按 {@code MultipartFile.getSize()}（框架已解析的声明大小）预检，
     * 超限时直接拒绝并提示走分片上传，避免大文件 {@code getBytes()} 全量进入堆内存导致 OOM。</p>
     *
     * @param file 上传的文件
     * @param maxBytes 允许全量读入的最大字节数
     * @return 上传内容
     * @throws BizException 超出全量读入上限或读取内容失败时抛出
     */
    private static UploadContent toUploadContent(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "文件大小超出普通上传上限，请使用分片上传接口");
        }
        try {
            return new UploadContent(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
