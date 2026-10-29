package com.bluewhale.archive.controller;

import com.bluewhale.archive.dto.ArchiveItemEditRequest;
import com.bluewhale.archive.dto.ArchiveItemQueryRequest;
import com.bluewhale.archive.service.ArchiveItemService;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.vo.ArchiveDownloadVO;
import com.bluewhale.archive.vo.ArchiveItemDetailVO;
import com.bluewhale.archive.vo.ArchiveItemVO;
import com.bluewhale.archive.vo.ArchiveStatsVO;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 档案条目接口：检索、详情、整理、确认归档、销毁、下载与统计。
 *
 * <p>路径前缀 {@code /archive}，权限码 {@code sys:archive:item:*}；
 * 整理、确认、销毁与下载均落审计。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/archive")
@RequiredArgsConstructor
public class ArchiveItemController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "archive_item";

    /** 下载响应 MIME 兜底值 */
    private static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    /** 下载文件名兜底值 */
    private static final String FALLBACK_FILE_NAME = "archive-file";

    /** 非 ASCII 文件名的安全替换字符 */
    private static final char ASCII_PLACEHOLDER = '_';

    private final ArchiveItemService itemService;

    /**
     * 分页查询档案条目。
     *
     * <p>密级可见范围按当前用户权限码裁剪：无 {@code sys:archive:secret:read} 时仅返回公开/内部级。</p>
     *
     * @param request 查询条件
     * @param httpRequest 当前请求（用于读取权限码）
     * @return 条目分页结果
     */
    @GetMapping("/item/page")
    @RequirePermission("sys:archive:item:list")
    public Result<PageResult<ArchiveItemVO>> page(ArchiveItemQueryRequest request,
                                                  HttpServletRequest httpRequest) {
        return Result.ok(itemService.page(request, canReadSecret(httpRequest)));
    }

    /**
     * 查询条目详情。
     *
     * @param id 条目ID
     * @param httpRequest 当前请求（用于读取权限码）
     * @return 条目详情
     */
    @GetMapping("/item/{id}")
    @RequirePermission("sys:archive:item:read")
    public Result<ArchiveItemDetailVO> detail(@PathVariable Long id, HttpServletRequest httpRequest) {
        return Result.ok(itemService.detail(id, canReadSecret(httpRequest)));
    }

    /**
     * 整理待整理条目。
     *
     * @param id 条目ID
     * @param request 整理请求
     * @return 整理后的条目视图
     */
    @PutMapping("/item/{id}")
    @RequirePermission("sys:archive:item:edit")
    @AuditLog(operation = "整理档案条目", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<ArchiveItemVO> edit(@PathVariable Long id,
                                      @Valid @RequestBody ArchiveItemEditRequest request) {
        return Result.ok(itemService.edit(id, request));
    }

    /**
     * 确认归档：分配档号并置为已归档。
     *
     * @param id 条目ID
     * @param request 当前请求（用于解析操作人）
     * @return 归档后的条目视图
     */
    @PostMapping("/item/{id}/confirm")
    @RequirePermission("sys:archive:item:archive")
    @AuditLog(operation = "确认归档", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<ArchiveItemVO> confirm(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(itemService.confirm(id, UserContext.requireUserId(request)));
    }

    /**
     * 销毁登记：已归档条目置为已销毁。
     *
     * @param id 条目ID
     * @return 空结果
     */
    @PostMapping("/item/{id}/destroy")
    @RequirePermission("sys:archive:item:destroy")
    @AuditLog(operation = "销毁档案条目", type = OperateType.DELETE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> destroy(@PathVariable Long id) {
        itemService.destroy(id);
        return Result.ok();
    }

    /**
     * 下载档案文件（经档案权限校验后由文件服务内网代理读取）。
     *
     * @param id 条目ID
     * @param fileId 文件引用ID
     * @param httpRequest 当前请求（用于读取权限码）
     * @return 文件字节流
     */
    @GetMapping("/item/{id}/files/{fileId}/download")
    @RequirePermission("sys:archive:item:download")
    @AuditLog(operation = "下载档案文件", type = OperateType.EXPORT,
            targetType = TARGET_TYPE, targetId = "#id")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long fileId,
                                           HttpServletRequest httpRequest) {
        ArchiveDownloadVO payload = itemService.download(id, fileId, canReadSecret(httpRequest));
        return ResponseEntity.ok()
                .contentType(resolveMediaType(payload.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(payload.getFileName()))
                .header("X-Content-Type-Options", "nosniff")
                .body(payload.getContent());
    }

    /**
     * 档案统计：总量、状态分布与年度/密级/门类分布。
     *
     * @param httpRequest 当前请求（用于读取权限码）
     * @return 统计视图
     */
    @GetMapping("/stats")
    @RequirePermission("sys:archive:item:list")
    public Result<ArchiveStatsVO> stats(HttpServletRequest httpRequest) {
        return Result.ok(itemService.stats(canReadSecret(httpRequest)));
    }

    /**
     * 判断当前用户是否具备秘密级档案读取权限。
     *
     * @param request 当前请求
     * @return 具备 {@code sys:archive:secret:read} 时返回 {@code true}
     */
    private static boolean canReadSecret(HttpServletRequest request) {
        return UserContext.hasPermission(request, ArchiveConstants.PERM_SECRET_READ);
    }

    /**
     * 解析下载 MIME，非法时回落为二进制流。
     *
     * @param contentType 原始 MIME
     * @return 可用 MIME
     */
    private static MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /**
     * 构造下载响应头：ASCII 兜底文件名 + RFC 5987 编码文件名。
     *
     * @param fileName 原始文件名
     * @return Content-Disposition 值
     */
    private static String contentDisposition(String fileName) {
        String name = fileName == null || fileName.isBlank() ? FALLBACK_FILE_NAME : fileName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        StringBuilder ascii = new StringBuilder(name.length());
        for (char ch : name.toCharArray()) {
            ascii.append(ch < 0x20 || ch > 0x7E || ch == '"' ? ASCII_PLACEHOLDER : ch);
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }
}
