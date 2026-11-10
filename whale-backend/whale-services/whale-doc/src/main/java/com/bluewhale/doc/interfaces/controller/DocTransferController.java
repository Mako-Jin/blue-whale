package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocTransferService;
import com.bluewhale.doc.interfaces.vo.DocExportVO;
import com.bluewhale.doc.interfaces.vo.DocImportResultVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 文档导入导出接口（M3）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/page/{id}")
@RequiredArgsConstructor
public class DocTransferController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_page";

    private final DocTransferService transferService;

    /**
     * 导出页面正文。
     *
     * @param id          页面ID
     * @param format      导出格式：md / html
     * @param httpRequest 当前请求
     * @return 文件响应
     */
    @GetMapping("/export")
    @RequirePermission("sys:doc:export")
    @AuditLog(operation = "导出文档页面", type = OperateType.EXPORT, targetType = TARGET_TYPE, targetId = "#id")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(required = false, defaultValue = "md") String format,
                                         HttpServletRequest httpRequest) {
        DocExportVO result = transferService.export(id, format, UserContext.requireUserId(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.getFileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(result.getContentType()))
                .contentLength(result.getContent().length)
                .body(result.getContent());
    }

    /**
     * 导入 Markdown / HTML 覆盖页面正文。
     *
     * @param id          页面ID
     * @param file        上传文件
     * @param format      格式，缺省按扩展名识别
     * @param httpRequest 当前请求
     * @return 导入结果
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("sys:doc:import")
    @AuditLog(operation = "导入文档页面", type = OperateType.IMPORT, targetType = TARGET_TYPE, targetId = "#id",
            recordParams = false)
    public Result<DocImportResultVO> importContent(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) String format,
                                                   HttpServletRequest httpRequest) {
        return Result.ok(transferService.importContent(id, file, format,
                UserContext.requireUserId(httpRequest)));
    }
}
