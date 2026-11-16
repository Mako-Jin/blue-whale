package com.bluewhale.file.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.file.api.FileVersionApi;
import com.bluewhale.file.assemble.FileAssembler;
import com.bluewhale.file.model.UploadContent;
import com.bluewhale.file.model.UploadLimits;
import com.bluewhale.file.model.response.FileVersionResponse;
import com.bluewhale.file.model.result.FileInfoResult;
import com.bluewhale.file.model.result.FileVersionResult;
import com.bluewhale.file.service.FileInfoService;
import com.bluewhale.file.service.support.FileUploadPolicy;
import com.bluewhale.file.support.FileContentResponses;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文件版本接口实现（Web 适配层）：协议适配 + multipart 转中立上传内容，业务编排在应用层。
 *
 * <p>列表要求 {@code sys:file:list}，上传新版本要求 {@code sys:file:upload}；
 * 普通上传通道与 {@code /file/v1/upload} 一致：先按生效上限预检声明大小再全量读入。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class FileVersionController implements FileVersionApi {

    /** 文件管理权限码：持有者可查看/操作全租户文件版本 */
    private static final String PERM_FILE_MANAGE = "sys:file:manage";

    private final FileInfoService fileInfoService;

    private final FileUploadPolicy fileUploadPolicy;

    private final FileAssembler fileAssembler;

    @Override
    @RequirePermission("sys:file:list")
    public Result<List<FileVersionResponse>> list(@PathVariable Long id, HttpServletRequest httpRequest) {
        List<FileVersionResponse> versions = fileInfoService
                .versions(id, UserContext.requireUserId(httpRequest), canManage(httpRequest)).stream()
                .map(fileAssembler::toVersionResponse)
                .toList();
        return Result.ok(versions);
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<FileVersionResponse> upload(@PathVariable Long id,
                                              @RequestParam("file") MultipartFile file,
                                              HttpServletRequest httpRequest) {
        UploadContent content = toUploadContent(file, fileUploadPolicy.defaults().maxSizeBytes());
        return Result.ok(fileAssembler.toVersionResponse(fileInfoService.uploadVersion(id, content,
                UserContext.requireUserId(httpRequest), canManage(httpRequest), uploadLimits(httpRequest))));
    }

    @Override
    @RequirePermission("sys:file:upload")
    public Result<FileVersionResponse> rollback(@PathVariable Long id,
                                                @PathVariable int targetVersion,
                                                HttpServletRequest httpRequest) {
        return Result.ok(fileAssembler.toVersionResponse(fileInfoService.rollback(id, targetVersion,
                UserContext.requireUserId(httpRequest), canManage(httpRequest), uploadLimits(httpRequest))));
    }

    @Override
    @RequirePermission("sys:file:delete")
    public Result<Void> delete(@PathVariable Long id, @PathVariable int targetVersion,
                               HttpServletRequest httpRequest) {
        fileInfoService.deleteVersion(id, targetVersion, UserContext.requireUserId(httpRequest),
                canManage(httpRequest));
        return Result.ok();
    }

    @Override
    @RequirePermission("sys:file:list")
    public Result<List<FileVersionResponse>> deleted(@PathVariable Long id, HttpServletRequest httpRequest) {
        List<FileVersionResult> versions = fileInfoService.deletedVersions(id,
                UserContext.requireUserId(httpRequest), canManage(httpRequest));
        return Result.ok(versions.stream().map(fileAssembler::toVersionResponse).toList());
    }

    @Override
    @RequirePermission("sys:file:delete")
    public Result<FileVersionResponse> restore(@PathVariable Long id, @PathVariable int targetVersion,
                                               HttpServletRequest httpRequest) {
        FileVersionResult version = fileInfoService.restoreVersion(id, targetVersion,
                UserContext.requireUserId(httpRequest), canManage(httpRequest), uploadLimits(httpRequest));
        return Result.ok(fileAssembler.toVersionResponse(version));
    }

    @Override
    @RequirePermission("sys:file:download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @PathVariable int targetVersion,
                                             @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                             @RequestHeader(value = HttpHeaders.IF_RANGE, required = false)
                                             String ifRange,
                                             HttpServletRequest httpRequest) {
        Long requesterId = UserContext.requireUserId(httpRequest);
        boolean canManage = canManage(httpRequest);
        FileInfoResult info = fileInfoService.versionDetail(id, targetVersion, requesterId, canManage);
        return FileContentResponses.build(info, false, range, ifRange, new FileContentResponses.ContentSource() {
            @Override
            public InputStream openAll() {
                return fileInfoService.openVersionContent(id, targetVersion, requesterId, canManage);
            }

            @Override
            public InputStream openRange(long offset, long length) {
                return fileInfoService.openVersionContent(id, targetVersion, requesterId, canManage, offset, length);
            }
        });
    }

    /**
     * 解析当前请求生效的上传上限与配额
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
     * 将 Web 上传对象适配为应用层中立上传内容
     *
     * <p>先按 {@code MultipartFile.getSize()} 预检，避免大文件全量进入堆内存导致 OOM。</p>
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
