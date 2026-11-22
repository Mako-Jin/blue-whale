package com.bluewhale.mail.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.mail.service.MailAttachmentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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
 * 邮件附件接口。
 *
 * <p>下载走本服务代理而不是直接暴露 {@code whale-file} 的下载地址：
 * 附件归属由「邮件 → 账号 → 当前用户」这条链判定，前端拿不到可绕过校验的裸 fileId。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mail/v{version}/attachment", version = "1")
@RequiredArgsConstructor
public class MailAttachmentController {

    /** Content-Disposition 的 RFC 5987 编码前缀 */
    private static final String DISPOSITION_PREFIX = "attachment; filename*=UTF-8''";

    private final MailAttachmentService attachmentService;

    /**
     * 下载附件
     *
     * @param id 附件ID
     * @param request 当前请求
     * @return 附件字节流
     */
    @GetMapping("/{id}/download")
    @RequirePermission("sys:mail:message:list")
    public ResponseEntity<Resource> download(@PathVariable Long id, HttpServletRequest request) {
        Long userId = UserContext.requireUserId(request);
        MailAttachmentService.AttachmentContent content = attachmentService.download(id, userId);
        byte[] bytes = content.content();
        String encodedName = URLEncoder.encode(content.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, DISPOSITION_PREFIX + encodedName)
                .contentType(resolveMediaType(content.contentType()))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    /**
     * 解析 MIME 类型；非法类型回落二进制流，避免下载直接 500。
     *
     * @param contentType 类型字符串
     * @return 媒体类型
     */
    private static MediaType resolveMediaType(String contentType) {
        try {
            return contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
