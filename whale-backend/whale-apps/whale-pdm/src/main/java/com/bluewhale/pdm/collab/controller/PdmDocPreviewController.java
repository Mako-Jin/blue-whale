package com.bluewhale.pdm.collab.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.collab.dto.PdmDocPreviewRequest;
import com.bluewhale.pdm.collab.service.PdmDocPreviewService;
import com.bluewhale.pdm.collab.vo.PdmDocPreviewJobVO;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * PDM 在线预览接口
 *
 * <p>创建任务按文档版本与类型幂等；内容读取复用图文档 ACL，返回 inline 便于查看器加载。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/collab/preview")
@RequiredArgsConstructor
public class PdmDocPreviewController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_doc_preview_job";

    /** 审计模块 */
    private static final String MODULE = "PDM 协同办公";

    /** 默认内容类型 */
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private final PdmDocPreviewService previewService;

    /**
     * 分页查询预览任务
     *
     * @param documentId 文档 ID
     * @param versionId  版本 ID
     * @param current    页码
     * @param size       每页条数
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:collab:preview:list")
    public Result<PageResult<PdmDocPreviewJobVO>> page(@RequestParam(required = false) Long documentId,
                                                       @RequestParam(required = false) Long versionId,
                                                       @RequestParam(defaultValue = "1") long current,
                                                       @RequestParam(defaultValue = "20") long size) {
        return Result.ok(previewService.page(documentId, versionId, current, size));
    }

    /**
     * 创建（或复用）预览任务
     *
     * @param documentId     文档 ID
     * @param versionId      版本 ID
     * @param request        预览请求
     * @param servletRequest 当前请求
     * @return 预览任务视图
     */
    @PostMapping("/{documentId}/versions/{versionId}")
    @RequirePermission("sys:pdm:collab:preview:create")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "创建在线预览", targetType = TARGET_TYPE)
    public Result<PdmDocPreviewJobVO> create(@PathVariable Long documentId,
                                             @PathVariable Long versionId,
                                             @Valid @RequestBody PdmDocPreviewRequest request,
                                             HttpServletRequest servletRequest) {
        return Result.ok(previewService.request(documentId, versionId, request.getPreviewType(),
                PdmDocViewer.of(servletRequest)));
    }

    /**
     * 重试预览任务
     *
     * @param jobId          任务 ID
     * @param servletRequest 当前请求
     * @return 预览任务视图
     */
    @PostMapping("/{jobId}/retry")
    @RequirePermission("sys:pdm:collab:preview:retry")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "重试在线预览", targetType = TARGET_TYPE)
    public Result<PdmDocPreviewJobVO> retry(@PathVariable Long jobId,
                                            HttpServletRequest servletRequest) {
        return Result.ok(previewService.retry(jobId, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 读取预览内容
     *
     * @param jobId          任务 ID
     * @param servletRequest 当前请求
     * @return 预览内容
     */
    @GetMapping("/{jobId}/content")
    @RequirePermission("sys:pdm:collab:preview:list")
    @AuditLog(module = MODULE, type = OperateType.QUERY, operation = "读取在线预览内容", targetType = TARGET_TYPE)
    public ResponseEntity<byte[]> content(@PathVariable Long jobId, HttpServletRequest servletRequest) {
        PdmDocDownload download = previewService.content(jobId, PdmDocViewer.of(servletRequest));
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        MediaType mediaType = MediaType.parseMediaType(
                download.contentType() == null ? DEFAULT_MEDIA_TYPE.toString() : download.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }
}
