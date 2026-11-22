package com.bluewhale.archive.controller;

import com.bluewhale.archive.dto.ArchiveItemEditRequest;
import com.bluewhale.archive.dto.ArchiveItemQueryRequest;
import com.bluewhale.archive.service.ArchiveItemService;
import com.bluewhale.archive.support.ArchiveConstants;
import com.bluewhale.archive.support.ArchiveFileResponses;
import com.bluewhale.archive.vo.ArchiveItemDetailVO;
import com.bluewhale.archive.vo.ArchiveItemVO;
import com.bluewhale.archive.vo.ArchivePreviewVO;
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
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 档案条目接口：检索、详情、整理、确认归档、销毁、下载、在线预览与统计。
 *
 * <p>路径前缀 {@code /archive}，权限码 {@code sys:archive:item:*}；
 * 下载与在线预览均经文件服务内网流式代理（不整体缓冲、透传 {@code Range}），
 * 差异仅 {@code Content-Disposition}。整理、确认、销毁与下载落审计；
 * 预览按 Range 分段高频请求，不落审计，避免播放过程刷屏；
 * 敏感内容访问仍受条目密级与下载权限约束。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/archive/v{version}", version = "1")
@RequiredArgsConstructor
public class ArchiveItemController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "archive_item";

    /** 支持的范围单位声明 */
    private static final String ACCEPT_RANGES_VALUE = "bytes";

    /** 禁止浏览器嗅探响应内容类型的安全响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

    /** 私有且不允许缓存：防止代理缓存涉密档案内容 */
    private static final String CACHE_CONTROL_VALUE = "private, no-store";

    /** 越界响应 Content-Range 格式 */
    private static final String CONTENT_RANGE_REFUSED_FORMAT = "bytes */%d";

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
     * 下载档案文件（经档案权限校验后由文件服务内网流式代理读取）。
     *
     * <p>始终以 {@code attachment} 返回；同时透传 {@code Range} / {@code If-Range}，
     * 支持浏览器断点续传，且不整体缓冲大文件。</p>
     *
     * @param id 条目ID
     * @param fileId 文件引用ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @param httpRequest 当前请求（用于读取权限码）
     * @return 下载响应：200 全量、206 分段或 416 越界
     */
    @GetMapping("/item/{id}/files/{fileId}/download")
    @RequirePermission("sys:archive:item:download")
    @AuditLog(operation = "下载档案文件", type = OperateType.EXPORT,
            targetType = TARGET_TYPE, targetId = "#id")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId,
                                             @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                             @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                             HttpServletRequest httpRequest) {
        ArchivePreviewVO payload = itemService.download(id, fileId, canReadSecret(httpRequest), range, ifRange);
        return buildContentResponse(payload, false);
    }

    /**
     * 在线预览 / 播放档案文件：透传 {@code Range} 与 {@code If-Range}，返回 200 / 206 / 416。
     *
     * <p>图片、音频、视频、文本、PDF 等可内联渲染的类型按 {@code inline} 返回，
     * 其余类型回落为 {@code attachment}；响应带 {@code Accept-Ranges: bytes}
     * 与 {@code private, no-store}，不暴露文件服务内部 ID。</p>
     *
     * @param id 条目ID
     * @param fileId 文件引用ID
     * @param range 请求的 {@code Range} 头
     * @param ifRange 请求的 {@code If-Range} 头
     * @param httpRequest 当前请求（用于读取权限码）
     * @return 预览响应：200 全量、206 分段或 416 越界
     */
    @GetMapping("/item/{id}/files/{fileId}/preview")
    @RequirePermission("sys:archive:item:download")
    public ResponseEntity<Resource> preview(@PathVariable Long id, @PathVariable Long fileId,
                                            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
                                            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
                                            HttpServletRequest httpRequest) {
        ArchivePreviewVO payload = itemService.preview(id, fileId, canReadSecret(httpRequest), range, ifRange);
        return buildContentResponse(payload, ArchiveFileResponses.isInlinePreviewable(payload.getContentType()));
    }

    /**
     * 构造文件内容响应：下载与预览共用 Range、缓存与安全响应头，仅 {@code Content-Disposition} 不同。
     *
     * @param payload 内容载荷（下游状态、长度、Range 头与内容流）
     * @param inline {@code true} 按内联返回（在线预览），{@code false} 按附件下载
     * @return 200 / 206 / 416 响应
     */
    private static ResponseEntity<Resource> buildContentResponse(ArchivePreviewVO payload, boolean inline) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, ACCEPT_RANGES_VALUE);
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(NOSNIFF_HEADER, NOSNIFF_VALUE);
        headers.setContentType(ArchiveFileResponses.mediaType(payload.getContentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                ArchiveFileResponses.contentDisposition(payload.getFileName(), inline));
        if (payload.getEtag() != null) {
            headers.setETag(payload.getEtag());
        }
        if (payload.getStatus() == HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value()) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    String.format(CONTENT_RANGE_REFUSED_FORMAT, payload.getContentRangeTotal()));
            headers.setContentLength(0L);
            return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        }
        if (payload.getContentRange() != null) {
            headers.set(HttpHeaders.CONTENT_RANGE, payload.getContentRange());
        }
        if (payload.getContentLength() >= 0) {
            headers.setContentLength(payload.getContentLength());
        }
        HttpStatus status = payload.getStatus() == HttpStatus.PARTIAL_CONTENT.value()
                ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        return new ResponseEntity<>(new InputStreamResource(payload.getBody()), headers, status);
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
}
