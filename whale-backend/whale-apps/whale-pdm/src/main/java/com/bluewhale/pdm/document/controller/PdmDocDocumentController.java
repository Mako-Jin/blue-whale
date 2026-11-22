package com.bluewhale.pdm.document.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.document.dto.PdmDocDocumentQueryRequest;
import com.bluewhale.pdm.document.dto.PdmDocDocumentSaveRequest;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import com.bluewhale.pdm.document.support.PdmDocExport;
import com.bluewhale.pdm.document.support.PdmDocRequestContext;
import com.bluewhale.pdm.document.support.PdmDocUploadFile;
import com.bluewhale.pdm.document.support.PdmDocViewer;
import com.bluewhale.pdm.document.vo.PdmDocDocumentVO;
import com.bluewhale.pdm.document.vo.PdmDocVersionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDM 图文档接口
 *
 * <p>版本上传、检入、发布与回滚均走动作端点，不做通用状态更新；
 * 下载经 PDM 代理并写审计，不暴露文件服务内部 ID。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/document", version = "1")
@RequiredArgsConstructor
public class PdmDocDocumentController {

    /** CSV 内容类型 */
    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");

    /** 导出文件名日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PdmDocDocumentService documentService;

    /**
     * 分页查询文档
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:doc:list")
    public Result<PageResult<PdmDocDocumentVO>> page(PdmDocDocumentQueryRequest request,
                                                     HttpServletRequest servletRequest) {
        return Result.ok(documentService.page(request, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 导出文档列表
     *
     * <p>查询条件与分页一致，最多导出 5000 行；超限时响应头 {@code X-Export-Truncated=true}。</p>
     *
     * @param request 查询条件
     * @param servletRequest 当前请求
     * @return CSV 文件流
     */
    @GetMapping("/export")
    @RequirePermission("sys:pdm:doc:export")
    @AuditLog(module = "PDM 图文档", type = OperateType.EXPORT, operation = "导出图文档列表")
    public ResponseEntity<byte[]> export(PdmDocDocumentQueryRequest request, HttpServletRequest servletRequest) {
        PdmDocExport export = documentService.export(request, PdmDocViewer.of(servletRequest));
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("pdm-documents-" + DATE_FORMAT.format(LocalDate.now()) + ".csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Export-Truncated", String.valueOf(export.truncated()))
                .body(export.content());
    }

    /**
     * 查询文档详情
     *
     * @param id 文档 ID
     * @param servletRequest 当前请求
     * @return 文档视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:doc:list")
    public Result<PdmDocDocumentVO> detail(@PathVariable Long id, HttpServletRequest servletRequest) {
        return Result.ok(documentService.detail(id, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 查询文档版本
     *
     * @param id 文档 ID
     * @param servletRequest 当前请求
     * @return 版本列表
     */
    @GetMapping("/{id}/versions")
    @RequirePermission("sys:pdm:doc:list")
    public Result<List<PdmDocVersionVO>> versions(@PathVariable Long id,
                                                  HttpServletRequest servletRequest) {
        return Result.ok(documentService.versions(id, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 新建文档
     *
     * @param request 保存请求
     * @param servletRequest 当前请求
     * @return 文档视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:doc:add")
    @AuditLog(module = "PDM 图文档", type = OperateType.CREATE, operation = "新建图文档")
    public Result<PdmDocDocumentVO> create(@Valid @RequestBody PdmDocDocumentSaveRequest request,
                                           HttpServletRequest servletRequest) {
        return Result.ok(documentService.create(request,
                PdmDocRequestContext.requireUserId(servletRequest),
                PdmDocRequestContext.username(servletRequest)));
    }

    /**
     * 更新文档元数据
     *
     * @param id      文档 ID
     * @param request 保存请求
     * @param servletRequest 当前请求
     * @return 文档视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:doc:edit")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "更新图文档")
    public Result<PdmDocDocumentVO> update(@PathVariable Long id,
                                           @Valid @RequestBody PdmDocDocumentSaveRequest request,
                                           HttpServletRequest servletRequest) {
        return Result.ok(documentService.update(id, request,
                PdmDocViewer.of(servletRequest),
                PdmDocRequestContext.canManage(servletRequest)));
    }

    /**
     * 删除文档
     *
     * @param id 文档 ID
     * @param servletRequest 当前请求
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:doc:delete")
    @AuditLog(module = "PDM 图文档", type = OperateType.DELETE, operation = "删除图文档")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest servletRequest) {
        documentService.delete(id,
                PdmDocViewer.of(servletRequest),
                PdmDocRequestContext.canManage(servletRequest));
        return Result.ok();
    }

    /**
     * 上传新草稿版本
     *
     * @param id             文档 ID
     * @param file           文件
     * @param changeNote     变更说明
     * @param servletRequest 当前请求
     * @return 版本视图
     */
    @PostMapping("/{id}/versions")
    @RequirePermission("sys:pdm:doc:version")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "上传图文档版本")
    public Result<PdmDocVersionVO> uploadVersion(@PathVariable Long id,
                                                 @RequestPart("file") MultipartFile file,
                                                 @RequestParam(required = false) String changeNote,
                                                 HttpServletRequest servletRequest) {
        return Result.ok(documentService.uploadVersion(id, toUploadFile(file), changeNote,
                PdmDocViewer.of(servletRequest),
                PdmDocRequestContext.canManage(servletRequest)));
    }

    /**
     * 检出文档
     *
     * @param id             文档 ID
     * @param servletRequest 当前请求
     * @return 文档视图
     */
    @PostMapping("/{id}/checkout")
    @RequirePermission("sys:pdm:doc:checkout")
    @AuditLog(module = "PDM 图文档", type = OperateType.OTHER, operation = "检出图文档")
    public Result<PdmDocDocumentVO> checkOut(@PathVariable Long id, HttpServletRequest servletRequest) {
        return Result.ok(documentService.checkOut(id, PdmDocViewer.of(servletRequest)));
    }

    /**
     * 检入文档
     *
     * @param id             文档 ID
     * @param file           文件
     * @param changeNote     变更说明
     * @param servletRequest 当前请求
     * @return 版本视图
     */
    @PostMapping("/{id}/checkin")
    @RequirePermission("sys:pdm:doc:version")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "检入图文档")
    public Result<PdmDocVersionVO> checkIn(@PathVariable Long id,
                                           @RequestPart("file") MultipartFile file,
                                           @RequestParam(required = false) String changeNote,
                                           HttpServletRequest servletRequest) {
        return Result.ok(documentService.checkIn(id, toUploadFile(file), changeNote,
                PdmDocViewer.of(servletRequest)));
    }

    /**
     * 发布草稿版本
     *
     * @param id             文档 ID
     * @param versionId      版本 ID
     * @param servletRequest 当前请求
     * @return 版本视图
     */
    @PostMapping("/{id}/versions/{versionId}/release")
    @RequirePermission("sys:pdm:doc:release")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "发布图文档版本")
    public Result<PdmDocVersionVO> release(@PathVariable Long id,
                                           @PathVariable Long versionId,
                                           HttpServletRequest servletRequest) {
        return Result.ok(documentService.release(id, versionId,
                PdmDocViewer.of(servletRequest),
                PdmDocRequestContext.canManage(servletRequest)));
    }

    /**
     * 以历史版本回滚生成新草稿
     *
     * @param id             文档 ID
     * @param versionId      版本 ID
     * @param servletRequest 当前请求
     * @return 新版本视图
     */
    @PostMapping("/{id}/versions/{versionId}/rollback")
    @RequirePermission("sys:pdm:doc:rollback")
    @AuditLog(module = "PDM 图文档", type = OperateType.UPDATE, operation = "回滚图文档版本")
    public Result<PdmDocVersionVO> rollback(@PathVariable Long id,
                                            @PathVariable Long versionId,
                                            HttpServletRequest servletRequest) {
        return Result.ok(documentService.rollback(id, versionId,
                PdmDocViewer.of(servletRequest),
                PdmDocRequestContext.canManage(servletRequest)));
    }

    /**
     * 下载指定版本
     *
     * @param id        文档 ID
     * @param versionId 版本 ID
     * @return 文件流
     */
    @GetMapping("/{id}/versions/{versionId}/content")
    @RequirePermission("sys:pdm:doc:download")
    @AuditLog(module = "PDM 图文档", type = OperateType.OTHER, operation = "下载图文档")
    public ResponseEntity<byte[]> download(@PathVariable Long id, @PathVariable Long versionId,
                                           HttpServletRequest servletRequest) {
        PdmDocDownload download = documentService.download(id, versionId,
                PdmDocViewer.of(servletRequest));
        MediaType mediaType;
        try {
            mediaType = download.contentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(download.contentType());
        } catch (IllegalArgumentException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    /**
     * Multipart 文件转上传对象
     *
     * @param file 上传文件
     * @return 上传对象
     */
    private PdmDocUploadFile toUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "上传文件不能为空");
        }
        try {
            return new PdmDocUploadFile(file.getBytes(), file.getOriginalFilename(),
                    file.getContentType(), file.getSize());
        } catch (IOException ex) {
            throw new BizException(ErrorCode.PARAM_ERROR, "读取上传文件失败");
        }
    }
}
