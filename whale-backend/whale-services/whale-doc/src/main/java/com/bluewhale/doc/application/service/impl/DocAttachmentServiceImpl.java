package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocAttachmentContent;
import com.bluewhale.doc.application.service.DocAttachmentService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.infrastructure.client.DocFileClient;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAttachment;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAttachmentMapper;
import com.bluewhale.doc.interfaces.vo.DocPageAttachmentVO;
import com.bluewhale.doc.support.DocAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 页面附件应用服务实现。
 *
 * <p>文件实体存放在 whale-file（桶 {@code whale-doc}），本服务只登记引用并通过内网通道
 * 代理内容读取；下载前校验页面可见性与附件归属。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocAttachmentServiceImpl implements DocAttachmentService {

    /** 文档附件桶 */
    private static final String BUCKET = "whale-doc";

    /** 默认用途 */
    private static final String USAGE_ATTACHMENT = "ATTACHMENT";

    /** 行内图片用途 */
    private static final String USAGE_INLINE = "INLINE";

    /** 默认 MIME 类型 */
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    /** 默认文件名 */
    private static final String DEFAULT_FILENAME = "file";

    /** 附件下载地址模板 */
    private static final String CONTENT_URL = "/doc/v1/page/%d/attachment/%d/content";

    private final DocPageAttachmentMapper attachmentMapper;

    private final DocAccessGuard accessGuard;

    private final DocFileClient fileClient;

    private final DocProperties docProperties;

    @Override
    public List<DocPageAttachmentVO> list(Long pageId, Long userId) {
        accessGuard.requirePageRead(pageId, userId);
        return attachmentMapper.selectList(Wrappers.<DocPageAttachment>lambdaQuery()
                        .eq(DocPageAttachment::getPageId, pageId)
                        .orderByAsc(DocPageAttachment::getSortNo)
                        .orderByAsc(DocPageAttachment::getCreateTime))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageAttachmentVO upload(Long pageId, MultipartFile file, String usageType, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        validateFile(file);
        Long tenantId = TenantContext.requireId();
        DocFileClient.UploadedFile uploaded = fileClient.upload(file, BUCKET, userId, tenantId);
        if (uploaded.fileId() == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "doc.attachment.file-id-missing", "文件服务未返回文件ID");
        }
        String name = StringUtils.hasText(uploaded.originalName())
                ? uploaded.originalName() : resolveFilename(file);
        String contentType = StringUtils.hasText(uploaded.contentType())
                ? uploaded.contentType() : DEFAULT_CONTENT_TYPE;
        String usage = resolveUsage(usageType);
        DocPageAttachment attachment = saveReference(tenantId, page, uploaded, name, contentType, usage);
        log.info("页面附件已上传: pageId={}, fileId={}, operator={}", pageId, uploaded.fileId(), userId);
        return toVO(attachment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long pageId, Long attachmentId, Long userId) {
        requireEditablePage(pageId, userId);
        DocPageAttachment attachment = requireAttachment(pageId, attachmentId);
        attachmentMapper.deleteById(attachment.getId());
        log.info("页面附件已移除: pageId={}, attachmentId={}, operator={}", pageId, attachmentId, userId);
    }

    @Override
    public DocAttachmentContent content(Long pageId, Long attachmentId, Long userId) {
        accessGuard.requirePageRead(pageId, userId);
        DocPageAttachment attachment = requireAttachment(pageId, attachmentId);
        byte[] bytes = fileClient.content(attachment.getFileId());
        String contentType = StringUtils.hasText(attachment.getContentType())
                ? attachment.getContentType() : DEFAULT_CONTENT_TYPE;
        return new DocAttachmentContent(bytes, contentType, attachment.getOriginalName());
    }

    /**
     * 读取页面并校验编辑权限。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 页面
     */
    private DocPage requireEditablePage(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.attachment.archived-not-editable", "已归档页面不可编辑附件");
        }
        return page;
    }

    /**
     * 读取并校验附件归属页面。
     *
     * @param pageId       页面ID
     * @param attachmentId 附件引用ID
     * @return 附件引用
     */
    private DocPageAttachment requireAttachment(Long pageId, Long attachmentId) {
        DocPageAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !pageId.equals(attachment.getPageId())) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        return attachment;
    }

    /**
     * 校验上传文件体积与内容。
     *
     * @param file 上传文件
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.attachment.file-empty", "附件不能为空");
        }
        if (file.getSize() > docProperties.getAttachmentMaxBytes()) {
            throw DocErrorCode.exception(DocErrorCode.ATTACHMENT_TOO_LARGE);
        }
    }

    /**
     * 登记附件引用；已解除引用时复用原行。
     *
     * @param tenantId    租户ID
     * @param page        页面
     * @param uploaded    文件服务上传结果
     * @param name        文件名
     * @param contentType MIME 类型
     * @param usage       用途
     * @return 附件引用
     */
    private DocPageAttachment saveReference(Long tenantId, DocPage page, DocFileClient.UploadedFile uploaded,
                                            String name, String contentType, String usage) {
        DocPageAttachment existing = attachmentMapper.selectAny(tenantId, page.getId(), uploaded.fileId());
        if (existing != null) {
            attachmentMapper.reactivate(tenantId, existing.getId(), name, contentType,
                    uploaded.fileSize(), usage);
            return attachmentMapper.selectById(existing.getId());
        }
        DocPageAttachment attachment = new DocPageAttachment();
        attachment.setPageId(page.getId());
        attachment.setFileId(uploaded.fileId());
        attachment.setOriginalName(name);
        attachment.setContentType(contentType);
        attachment.setFileSize(uploaded.fileSize());
        attachment.setUsageType(usage);
        attachment.setSortNo(0);
        attachmentMapper.insert(attachment);
        return attachment;
    }

    /**
     * 解析附件用途。
     *
     * @param usageType 原始用途
     * @return 规范化用途
     */
    private String resolveUsage(String usageType) {
        return USAGE_INLINE.equalsIgnoreCase(usageType) ? USAGE_INLINE : USAGE_ATTACHMENT;
    }

    /**
     * 解析文件名，缺省时回落占位名。
     *
     * @param file 上传文件
     * @return 文件名
     */
    private String resolveFilename(MultipartFile file) {
        return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : DEFAULT_FILENAME;
    }

    /**
     * 附件实体转视图。
     *
     * @param attachment 附件引用
     * @return 附件视图
     */
    private DocPageAttachmentVO toVO(DocPageAttachment attachment) {
        DocPageAttachmentVO vo = new DocPageAttachmentVO();
        vo.setId(attachment.getId());
        vo.setFileId(attachment.getFileId());
        vo.setOriginalName(attachment.getOriginalName());
        vo.setContentType(attachment.getContentType());
        vo.setFileSize(attachment.getFileSize());
        vo.setUsageType(attachment.getUsageType());
        vo.setContentUrl(String.format(CONTENT_URL, attachment.getPageId(), attachment.getId()));
        vo.setCreateTime(attachment.getCreateTime());
        return vo;
    }
}
