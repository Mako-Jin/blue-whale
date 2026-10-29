package com.bluewhale.doc.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.doc.application.service.DocAttachmentContent;
import com.bluewhale.doc.application.service.DocAttachmentService;
import com.bluewhale.doc.interfaces.vo.DocPageAttachmentVO;
import com.bluewhale.doc.support.web.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 页面附件接口。
 *
 * <p>上传复用 whale-file 内网通道；读取经本服务校验页面可见性与附件归属后代理返回。
 * 仅常见位图类型允许 {@code inline} 预览，其余强制 {@code attachment} 下载并禁止嗅探。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/doc/page/{pageId}/attachment")
@RequiredArgsConstructor
public class DocAttachmentController {

    /** 禁止浏览器嗅探响应内容类型的响应头 */
    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";

    /** 禁止嗅探的响应头取值 */
    private static final String NOSNIFF_VALUE = "nosniff";

    /** 允许行内预览的位图类型（不含 SVG，避免脚本执行） */
    private static final Set<String> INLINE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp");

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "doc_attachment";

    private final DocAttachmentService attachmentService;

    /**
     * 查询页面附件。
     *
     * @param pageId      页面ID
     * @param httpRequest 当前请求
     * @return 附件列表
     */
    @GetMapping
    @RequirePermission("sys:doc:page:read")
    public Result<List<DocPageAttachmentVO>> list(@PathVariable Long pageId,
                                                  HttpServletRequest httpRequest) {
        return Result.ok(attachmentService.list(pageId, UserContext.requireUserId(httpRequest)));
    }

    /**
     * 上传附件。
     *
     * @param pageId      页面ID
     * @param file        文件
     * @param usageType   用途：ATTACHMENT / INLINE
     * @param httpRequest 当前请求
     * @return 附件视图
     */
    @PostMapping
    @RequirePermission("sys:doc:attachment:manage")
    @AuditLog(operation = "上传文档附件", type = OperateType.CREATE, targetType = TARGET_TYPE,
            recordParams = false)
    public Result<DocPageAttachmentVO> upload(@PathVariable Long pageId,
                                              @RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "usageType", required = false) String usageType,
                                              HttpServletRequest httpRequest) {
        return Result.ok(attachmentService.upload(pageId, file, usageType,
                UserContext.requireUserId(httpRequest)));
    }

    /**
     * 解除附件引用。
     *
     * @param pageId       页面ID
     * @param attachmentId 附件引用ID
     * @param httpRequest  当前请求
     * @return 空响应体
     */
    @DeleteMapping("/{attachmentId}")
    @RequirePermission("sys:doc:attachment:manage")
    @AuditLog(operation = "移除文档附件", type = OperateType.DELETE, targetType = TARGET_TYPE,
            targetId = "#attachmentId")
    public Result<Void> delete(@PathVariable Long pageId, @PathVariable Long attachmentId,
                               HttpServletRequest httpRequest) {
        attachmentService.delete(pageId, attachmentId, UserContext.requireUserId(httpRequest));
        return Result.ok();
    }

    /**
     * 读取附件内容。
     *
     * @param pageId       页面ID
     * @param attachmentId 附件引用ID
     * @param httpRequest  当前请求
     * @return 附件内容响应
     */
    @GetMapping("/{attachmentId}/content")
    @RequirePermission("sys:doc:page:read")
    public ResponseEntity<byte[]> content(@PathVariable Long pageId, @PathVariable Long attachmentId,
                                          HttpServletRequest httpRequest) {
        DocAttachmentContent content = attachmentService.content(pageId, attachmentId,
                UserContext.requireUserId(httpRequest));
        return buildResponse(content);
    }

    /**
     * 构建附件下载/预览响应。
     *
     * @param content 附件内容
     * @return 响应实体
     */
    private ResponseEntity<byte[]> buildResponse(DocAttachmentContent content) {
        MediaType mediaType = resolveMediaType(content.contentType());
        boolean inline = INLINE_TYPES.contains(mediaType.getType() + "/" + mediaType.getSubtype());
        ContentDisposition.Builder disposition = inline
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        if (content.filename() != null && !content.filename().isBlank()) {
            disposition.filename(content.filename(), StandardCharsets.UTF_8);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.build().toString())
                .header(NOSNIFF_HEADER, NOSNIFF_VALUE)
                .contentType(mediaType)
                .contentLength(content.content().length)
                .body(content.content());
    }

    /**
     * 解析 MIME 类型，非法值回落二进制流类型。
     *
     * @param contentType MIME 类型
     * @return 媒体类型
     */
    private MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
