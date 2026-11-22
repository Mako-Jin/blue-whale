package com.bluewhale.oa.drive.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.oa.drive.application.command.DriveCommands;
import com.bluewhale.oa.drive.application.service.DriveAppService;
import com.bluewhale.oa.drive.application.view.DriveViews;
import com.bluewhale.oa.drive.domain.model.DriveQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OA 网盘接口。
 *
 * <p>服务内路径 {@code /oa/drive/**}，受 {@code sys:oa:drive:*} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/oa/v{version}/drive", version = "1")
@RequiredArgsConstructor
public class DriveController {

    private static final String TARGET_TYPE = "oa_drive";

    private final DriveAppService driveAppService;

    /**
     * 查询目录下的可见文件夹。
     *
     * @param parentId 父文件夹ID（0 表示根）
     * @param request  请求上下文
     * @return 文件夹
     */
    @GetMapping("/folders")
    @RequirePermission("sys:oa:drive:list")
    public Result<List<DriveViews.FolderView>> listFolders(
            @RequestParam(defaultValue = "0") Long parentId, HttpServletRequest request) {
        return Result.ok(driveAppService.listFolders(parentId,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 新建文件夹。
     *
     * @param command 创建命令
     * @param request 请求上下文
     * @return 文件夹
     */
    @PostMapping("/folders")
    @RequirePermission("sys:oa:drive:manage")
    @AuditLog(operation = "新建网盘文件夹", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DriveViews.FolderView> createFolder(
            @Valid @RequestBody DriveCommands.FolderCreateCommand command, HttpServletRequest request) {
        return Result.ok(driveAppService.createFolder(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 修改文件夹。
     *
     * @param id      文件夹ID
     * @param command 修改命令
     * @param request 请求上下文
     * @return 文件夹
     */
    @PutMapping("/folders/{id}")
    @RequirePermission("sys:oa:drive:manage")
    @AuditLog(operation = "修改网盘文件夹", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<DriveViews.FolderView> updateFolder(
            @PathVariable Long id, @Valid @RequestBody DriveCommands.FolderUpdateCommand command,
            HttpServletRequest request) {
        return Result.ok(driveAppService.updateFolder(id, command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 删除文件夹。
     *
     * @param id      文件夹ID
     * @param request 请求上下文
     * @return 空结果
     */
    @DeleteMapping("/folders/{id}")
    @RequirePermission("sys:oa:drive:manage")
    @AuditLog(operation = "删除网盘文件夹", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> deleteFolder(@PathVariable Long id, HttpServletRequest request) {
        driveAppService.deleteFolder(id, UserContext.requireUserId(request), WebHeaders.username(request));
        return Result.ok();
    }

    /**
     * 查询文件夹授权。
     *
     * @param id      文件夹ID
     * @param request 请求上下文
     * @return 授权
     */
    @GetMapping("/folders/{id}/acls")
    @RequirePermission("sys:oa:drive:list")
    public Result<List<DriveViews.AclView>> listAcls(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(driveAppService.listAcls(id,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 整体替换文件夹授权。
     *
     * @param id      文件夹ID
     * @param command 授权命令
     * @param request 请求上下文
     * @return 最新授权
     */
    @PutMapping("/folders/{id}/acls")
    @RequirePermission("sys:oa:drive:manage")
    @AuditLog(operation = "维护网盘授权", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<List<DriveViews.AclView>> replaceAcls(
            @PathVariable Long id, @Valid @RequestBody DriveCommands.AclSaveCommand command,
            HttpServletRequest request) {
        return Result.ok(driveAppService.replaceAcls(id, command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 分页查询文件夹内文件。
     *
     * @param query   查询条件
     * @param request 请求上下文
     * @return 分页结果
     */
    @GetMapping("/files")
    @RequirePermission("sys:oa:drive:list")
    public Result<PageResult<DriveViews.FileView>> pageFiles(DriveQuery query, HttpServletRequest request) {
        return Result.ok(driveAppService.pageFiles(query,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 登记已上传文件。
     *
     * @param command 登记命令
     * @param request 请求上下文
     * @return 文件
     */
    @PostMapping("/files")
    @RequirePermission("sys:oa:drive:manage")
    @AuditLog(operation = "登记网盘文件", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<DriveViews.FileView> registerFile(
            @Valid @RequestBody DriveCommands.FileRegisterCommand command, HttpServletRequest request) {
        return Result.ok(driveAppService.registerFile(command,
                UserContext.requireUserId(request), WebHeaders.username(request)));
    }

    /**
     * 删除文件登记。
     *
     * @param id      记录ID
     * @param request 请求上下文
     * @return 空结果
     */
    @DeleteMapping("/files/{id}")
    @RequirePermission("sys:oa:drive:manage")
    @AuditLog(operation = "删除网盘文件", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> deleteFile(@PathVariable Long id, HttpServletRequest request) {
        driveAppService.deleteFile(id, UserContext.requireUserId(request), WebHeaders.username(request));
        return Result.ok();
    }

    /**
     * 下载文件。
     *
     * @param id      记录ID
     * @param request 请求上下文
     * @return 文件内容
     */
    @GetMapping("/files/{id}/download")
    @RequirePermission("sys:oa:drive:download")
    public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request) {
        DriveViews.DownloadView view = driveAppService.download(id,
                UserContext.requireUserId(request), WebHeaders.username(request));
        MediaType mediaType = resolveMediaType(view.getContentType());
        long contentLength = view.getFileSize() == null ? -1L : view.getFileSize();
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(view.getFileName()));
        if (contentLength >= 0) {
            builder.contentLength(contentLength);
        }
        return builder.body(view.getResource());
    }

    /**
     * 查询当前用户网盘配额。
     *
     * @param request 请求上下文
     * @return 配额
     */
    @GetMapping("/quota")
    @RequirePermission("sys:oa:drive:list")
    public Result<DriveViews.QuotaView> quota(HttpServletRequest request) {
        return Result.ok(driveAppService.quota(UserContext.requireUserId(request)));
    }

    /**
     * 解析 MIME 类型，缺失或非法回落二进制流。
     *
     * @param contentType 原始类型
     * @return 媒体类型
     */
    private static MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 构造附件下载响应头（RFC 5987 编码）。
     *
     * @param fileName 文件名
     * @return Content-Disposition 值
     */
    private static String contentDisposition(String fileName) {
        String name = StringUtils.hasText(fileName) ? fileName : "download";
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
