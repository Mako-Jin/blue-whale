package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.application.service.DocPageActivityService;
import com.bluewhale.doc.application.service.DocPageLinkService;
import com.bluewhale.doc.application.service.DocTransferService;
import com.bluewhale.doc.application.service.support.DocPageMutationSupport;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.domain.model.DocPageType;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.interfaces.vo.DocExportVO;
import com.bluewhale.doc.interfaces.vo.DocImportResultVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocContentSupport;
import com.bluewhale.doc.support.DocMarkupSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文档导入导出应用服务实现。
 *
 * <p>导入只接受 Markdown / HTML 文本，统一转换为服务端权威的 TipTap JSON；
 * 导出按页面可见性校验后返回文本，不暴露内部存储结构。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocTransferServiceImpl implements DocTransferService {

    /** Markdown 格式 */
    private static final String FORMAT_MD = "md";

    /** HTML 格式 */
    private static final String FORMAT_HTML = "html";

    /** Markdown MIME 类型 */
    private static final String MD_CONTENT_TYPE = "text/markdown; charset=UTF-8";

    /** HTML MIME 类型 */
    private static final String HTML_CONTENT_TYPE = "text/html; charset=UTF-8";

    private final DocPageMapper pageMapper;

    private final DocAccessGuard accessGuard;

    private final DocContentSupport contentSupport;

    private final DocMarkupSupport markupSupport;

    private final DocPageMutationSupport mutationSupport;

    private final DocCollabService collabService;

    private final DocPageLinkService linkService;

    private final DocPageActivityService activityService;

    private final DocProperties docProperties;

    @Override
    public DocExportVO export(Long pageId, String format, Long userId) {
        DocPage page = accessGuard.requirePageRead(pageId, userId);
        boolean html = FORMAT_HTML.equals(normalizeFormat(format, null));
        String body = html ? markupSupport.toHtml(page.getContentJson())
                : markupSupport.toMarkdown(page.getContentJson());
        DocExportVO vo = new DocExportVO();
        vo.setFileName(fileName(page, html ? FORMAT_HTML : FORMAT_MD));
        vo.setContentType(html ? HTML_CONTENT_TYPE : MD_CONTENT_TYPE);
        vo.setContent(body.getBytes(StandardCharsets.UTF_8));
        log.info("页面已导出: id={}, format={}, operator={}", pageId, html ? FORMAT_HTML : FORMAT_MD, userId);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocImportResultVO importContent(Long pageId, MultipartFile file, String format, Long userId) {
        DocPage page = requireEditablePage(pageId, userId);
        String normalized = normalizeFormat(format, file == null ? null : file.getOriginalFilename());
        String text = readText(file);
        String contentJson = FORMAT_HTML.equals(normalized)
                ? markupSupport.fromHtml(text) : markupSupport.fromMarkdown(text);
        contentSupport.requireWithinLimit(contentJson);
        DocPage updated = mutationSupport.apply(page, page.getRevision(), null, contentJson,
                contentSupport.extractText(contentJson), userId);
        linkService.refresh(updated.getId(), updated.getContentJson());
        activityService.record(updated.getId(), "IMPORT", userId, "导入 " + normalized.toUpperCase(Locale.ROOT));
        log.info("页面已导入: id={}, format={}, revision={}, operator={}",
                pageId, normalized, updated.getRevision(), userId);
        DocImportResultVO vo = new DocImportResultVO();
        vo.setPageId(updated.getId());
        vo.setRevision(updated.getRevision());
        vo.setStatus(updated.getStatus());
        vo.setFormat(normalized.toUpperCase(Locale.ROOT));
        return vo;
    }

    /**
     * 读取页面并校验导入权限。
     *
     * @param pageId 页面ID
     * @param userId 当前用户ID
     * @return 页面
     */
    private DocPage requireEditablePage(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.transfer.archived-not-importable", "已归档页面不可导入");
        }
        if (DocPageType.FOLDER.name().equals(page.getPageType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "doc.transfer.folder-not-importable", "目录节点不支持导入正文");
        }
        if (collabService.isActive(pageId)) {
            throw DocErrorCode.exception(DocErrorCode.COLLAB_ACTIVE);
        }
        return page;
    }

    /**
     * 读取上传文本并校验体积。
     *
     * @param file 上传文件
     * @return 文本内容
     */
    private String readText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw DocErrorCode.exception(DocErrorCode.IMPORT_FILE_INVALID);
        }
        if (file.getSize() > docProperties.getContentMaxBytes()) {
            throw DocErrorCode.exception(DocErrorCode.IMPORT_FILE_INVALID);
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw DocErrorCode.exception(DocErrorCode.IMPORT_FILE_INVALID);
        }
    }

    /**
     * 规范化导入导出格式。
     *
     * @param format   显式格式
     * @param filename 文件名
     * @return md / html
     */
    private String normalizeFormat(String format, String filename) {
        String value = StringUtils.hasText(format) ? format.trim().toLowerCase(Locale.ROOT) : null;
        if (value == null && StringUtils.hasText(filename)) {
            String name = filename.toLowerCase(Locale.ROOT);
            value = name.endsWith(".html") || name.endsWith(".htm") ? FORMAT_HTML : FORMAT_MD;
        }
        if (value == null) {
            return FORMAT_MD;
        }
        if (value.equals(FORMAT_HTML) || value.equals("htm")) {
            return FORMAT_HTML;
        }
        if (value.equals(FORMAT_MD) || value.equals("markdown") || value.equals("txt")) {
            return FORMAT_MD;
        }
        throw DocErrorCode.exception(DocErrorCode.IMPORT_FILE_INVALID);
    }

    /**
     * 生成导出文件名。
     *
     * @param page   页面
     * @param suffix 扩展名
     * @return 文件名
     */
    private String fileName(DocPage page, String suffix) {
        String base = StringUtils.hasText(page.getSlug()) ? page.getSlug() : "doc-" + page.getId();
        return base + "." + suffix;
    }
}
