package com.bluewhale.oa.document.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.domain.gateway.DocumentFileGateway;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 公文附件下载接口。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/document/attachments")
@RequiredArgsConstructor
public class DocAttachmentController {

    private final DocumentRepository repository;

    private final DocumentFileGateway fileGateway;

    /**
     * 下载公文附件。
     *
     * @param docType      单据类型
     * @param docId        单据ID
     * @param attachmentId 附件ID
     * @return 文件流
     */
    @GetMapping("/{docType}/{docId}/{attachmentId}/download")
    @RequirePermission("sys:oa:document:download")
    public ResponseEntity<byte[]> download(@PathVariable String docType,
                                           @PathVariable Long docId,
                                           @PathVariable Long attachmentId) {
        DocAttachment attachment = repository.findAttachment(attachmentId);
        if (attachment == null || !attachment.getDocType().equalsIgnoreCase(docType)
                || !attachment.getDocId().equals(docId)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_NOT_FOUND);
        }
        byte[] content = fileGateway.content(attachment.getFileId());
        String fileName = attachment.getFileName() == null ? "attachment" : attachment.getFileName();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType;
        try {
            mediaType = attachment.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(attachment.getContentType());
        } catch (IllegalArgumentException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName.replace("\"", "") + "\"; filename*=UTF-8''" + encoded)
                .body(content);
    }
}
