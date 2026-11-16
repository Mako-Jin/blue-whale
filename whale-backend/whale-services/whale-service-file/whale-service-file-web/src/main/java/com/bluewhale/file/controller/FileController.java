package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.api.FileApi;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.command.RapidUploadCommand;
import com.bluewhale.file.model.command.UploadFileCommand;
import com.bluewhale.file.model.query.FilePageQuery;
import com.bluewhale.file.model.query.FileRecyclePageQuery;
import com.bluewhale.file.model.request.FileMoveRequest;
import com.bluewhale.file.model.request.FilePageRequest;
import com.bluewhale.file.model.request.FileRapidUploadRequest;
import com.bluewhale.file.model.request.FileRecyclePageRequest;
import com.bluewhale.file.model.request.FileRenameRequest;
import com.bluewhale.file.model.response.FileInfoResponse;
import com.bluewhale.file.model.response.FilePresignedResponse;
import com.bluewhale.file.model.response.FileQuotaResponse;
import com.bluewhale.file.model.response.FileRapidUploadResponse;
import com.bluewhale.file.model.response.FileRecycleResponse;
import com.bluewhale.file.model.response.FileUploadResponse;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileUploadResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.support.FileContentResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文件接口实现（Web 适配层）：协议适配 + Request ↔ Command/Query 转换，业务编排在应用层。
 *
 * <p>服务内路径 {@code /file/v1/**}，全部接口受 {@code @RequirePermission("sys:file:*")}
 * 保护（AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter
 * {@code RequirePermissionInterceptor} 判定）。内网服务间接口见
 * {@link com.bluewhale.file.api.FileInternalApi}。</p>
 *
 * <p>G1 大文件上传策略：分片上传按网关可信注入的 {@code X-Permission-Codes} 解析生效上限与配额
 * （视频 2GB / 8GB、电影 4GB / 不限、音乐 100MB），未命中规则仍按全局默认 50MB / 1GB 拒绝，
 * 避免为了视频上传而全局放宽全平台上限。普通上传接口为全量读入通道，
 * 单文件硬顶全局默认上限（{@code whale.file.max-size-bytes}），更大的文件必须走分片上传；
 * 权限码放宽仍作用于配额口径。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileController implements FileApi {

    /** 文件管理权限码：持有者可查看/下载/删除全租户文件 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    /** 审计操作对象类型 */
    private static final String AUDIT_TARGET_TYPE = "file_info";

    private final FileInfoService fileInfoService;

    private final FileUploadPolicy fileUploadPolicy;

    private final FileAssembler fileAssembler;

    @Override
    @RequirePermission("sys:file:upload")
    public Result<FileUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "bucket", required = false) String bucket,
                                             HttpServletRequest httpRequest) {
        FileUploadResult result = doUpload(file, bucket, UserContext.requireUserId(httpRequest),
                uploadLimits(httpRequest));
        return Result.ok(fileAssembler.toUploadResponse(result));
    }

    @Override
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                             String range,
                                             @RequestHeader(value = HttpHeaders.IF_RANGE, required = false)
                                             String ifRange,
                                             HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        FileInfoResult info = fileInfoService.getDetail(id, requesterId, canManage);
        return FileContentResponses.build(fileInfoService, info, requesterId, canManage, false, range, ifRange);
    }

    @Override
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> preview(@PathVariable Long id,
                                            @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                            String range,
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false)
                                            String ifRange,
                                            HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        FileInfoResult info = fileInfoService.getDetail(id, requesterId, canManage);
        return FileContentResponses.build(fileInfoService, info, requesterId, canManage,
                FileContentResponses.isPreviewable(info.contentType()), range, ifRange);
    }

    @Override
    @RequirePermission("sys:file:download")
    @AuditLog(operation = "批量打包下载", type = OperateType.EXPORT,
            targetType = AUDIT_TARGET_TYPE, targetId = "#ids")
    public void batchDownload(@RequestParam("ids") List<Long> ids, HttpServletRequest httpRequest,
                              HttpServletResponse response) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"files.zip\"");
        try {
            fileInfoService.downloadBatch(ids, requesterId, canManage, response.getOutputStream());
        } catch (IOException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Override
    @RequirePermission("sys:file:download")
    public Result<FilePresignedResponse> presigned(@PathVariable Long id,
                                                   @RequestParam(value = "expiresSeconds", required = false)
                                                   Integer expiresSeconds,
                                                   HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        return Result.ok(fileAssembler.toPresignedResponse(
                fileInfoService.presignedUrl(id, expiresSeconds, requesterId, canManage(httpRequest))));
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<FileRapidUploadResponse> rapidUpload(@Valid @RequestBody FileRapidUploadRequest request,
                                                       HttpServletRequest httpRequest) {
        RapidUploadCommand command = new RapidUploadCommand(request.getFileName(), request.getMd5(),
                request.getSize(), request.getContentType(), UserContext.requireUserId(httpRequest),
                uploadLimits(httpRequest));
        return Result.ok(fileAssembler.toRapidResponse(fileInfoService.rapidUpload(command)));
    }

    @Override
    @RequirePermission("sys:file:list")
    public Result<FileQuotaResponse> quota(HttpServletRequest httpRequest) {
        return Result.ok(fileAssembler.toQuotaResponse(
                fileInfoService.quota(UserContext.requireUserId(httpRequest), uploadLimits(httpRequest))));
    }

    @Override
    @RequirePermission("sys:file:list")
    public Result<PageResult<FileInfoResponse>> page(FilePageRequest request, HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        FilePageQuery query = new FilePageQuery(request.getCurrent(), request.getSize(),
                request.getOriginalName(), request.getBucket(), request.getCreateTimeStart(),
                request.getCreateTimeEnd(), requesterId, canManage(httpRequest),
                Boolean.TRUE.equals(request.getOnlyMine()));
        return Result.ok(fileAssembler.toInfoPage(fileInfoService.page(query)));
    }

    @Override
    @RequirePermission("sys:file:list")
    public Result<PageResult<FileRecycleResponse>> recyclePage(FileRecyclePageRequest request,
                                                               HttpServletRequest httpRequest) {
        FileRecyclePageQuery query = new FileRecyclePageQuery(request.getOriginalName(), request.getBucket(),
                UserContext.requireUserId(httpRequest), canManage(httpRequest),
                request.getCurrent(), request.getSize());
        return Result.ok(fileAssembler.toRecyclePage(fileInfoService.recyclePage(query)));
    }

    @Override
    @RequirePermission("sys:file:delete")
    @AuditLog(operation = "回收站找回文件", type = OperateType.UPDATE,
            targetType = AUDIT_TARGET_TYPE, targetId = "#id")
    public Result<Void> restore(@PathVariable Long id, HttpServletRequest httpRequest) {
        fileInfoService.restore(id, UserContext.requireUserId(httpRequest), canManage(httpRequest),
                uploadLimits(httpRequest));
        return Result.ok();
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<Void> rename(@PathVariable Long id, FileRenameRequest request, HttpServletRequest httpRequest) {
        fileInfoService.rename(id, request.getOriginalName(), UserContext.requireUserId(httpRequest),
                canManage(httpRequest));
        return Result.ok();
    }

    @Override
    @RequirePermission("sys:file:manage")
    public Result<Void> move(@PathVariable Long id, FileMoveRequest request, HttpServletRequest httpRequest) {
        fileInfoService.move(id, request.getBucket(), UserContext.requireUserId(httpRequest),
                canManage(httpRequest));
        return Result.ok();
    }

    @Override
    @RequirePermission("sys:file:delete")
    @AuditLog(operation = "删除文件（移入回收站）", type = OperateType.DELETE,
            targetType = AUDIT_TARGET_TYPE, targetId = "#id")
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
    private FileUploadResult doUpload(MultipartFile file, String bucket, Long uploaderId, UploadLimits limits) {
        UploadContent content = toUploadContent(file, fileUploadPolicy.defaults().maxSizeBytes());
        return fileInfoService.upload(new UploadFileCommand(content, bucket, uploaderId, limits));
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
