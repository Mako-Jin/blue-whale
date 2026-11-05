package com.bluewhale.pdm.document.controller;

import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.pdm.document.service.PdmDocDocumentService;
import com.bluewhale.pdm.document.support.PdmDocDownload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * PDM 图文档内网契约接口
 *
 * <p>供 MES 等内网系统按 {@code documentId + versionNo} 读取 SOP 内容；
 * 仅已发布版本可读。网关已将 {@code /pdm/internal/**} 加入 internal-deny，
 * 外部访问 403；服务内由内网 token 过滤器校验，因此不叠加功能权限。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/internal/document")
@RequiredArgsConstructor
public class PdmInternalDocController {

    private final PdmDocDocumentService documentService;

    /**
     * 读取已发布文档版本内容
     *
     * @param documentId 文档ID
     * @param versionNo  版本号
     * @return 文件内容
     */
    @GetMapping("/{documentId}/versions/{versionNo}/content")
    @AuditLog(module = "PDM 图文档", type = OperateType.OTHER, operation = "内网读取 SOP 内容",
            targetType = "pdm_doc_document", targetId = "#documentId")
    public ResponseEntity<byte[]> content(@PathVariable Long documentId, @PathVariable String versionNo) {
        PdmDocDownload download = documentService.contentOfReleasedVersion(documentId, versionNo);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName() == null ? "document" : download.fileName(),
                        StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    /**
     * 解析内容类型，无法识别时回落二进制
     *
     * @param contentType 内容类型
     * @return 媒体类型
     */
    private MediaType parseMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
