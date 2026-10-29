package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.doc.application.service.DocArchiveService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.model.DocPageType;
import com.bluewhale.doc.infrastructure.client.DocArchiveClient;
import com.bluewhale.doc.infrastructure.client.DocFileClient;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.support.DocMarkupSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 页面归档快照应用服务实现。
 *
 * <p>快照文件上传 whale-file 后，以 {@code DOC_PAGE} 来源推送给档案服务；
 * 档案服务按 {@code (sourceType, sourceId)} 幂等，文档侧以 {@code archiveItemId} 做过期去重。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocArchiveServiceImpl implements DocArchiveService {

    /** 归档来源类型 */
    private static final String SOURCE_DOC_PAGE = "DOC_PAGE";

    /** 文档桶 */
    private static final String BUCKET = "whale-doc";

    /** 快照文件 MIME 类型 */
    private static final String MARKDOWN_CONTENT_TYPE = "text/markdown";

    /** 摘要最大长度 */
    private static final int SUMMARY_MAX_LENGTH = 2000;

    /** 快照正文最大长度（字符） */
    private static final int SNAPSHOT_TEXT_MAX_CHARS = 200_000;

    /** 密级：内部 */
    private static final int SECURITY_INTERNAL = 2;

    private final DocPageMapper pageMapper;

    private final DocFileClient fileClient;

    private final DocArchiveClient archiveClient;

    private final DocMarkupSupport markupSupport;

    private final DocProperties docProperties;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean archiveSnapshot(Long tenantId, Long pageId, Long userId) {
        DocPage page = pageMapper.selectById(pageId);
        if (page == null || DocPageType.FOLDER.name().equals(page.getPageType())) {
            return false;
        }
        if (page.getArchiveItemId() != null) {
            log.info("页面已入档，跳过重复快照: pageId={}, itemId={}", pageId, page.getArchiveItemId());
            return false;
        }
        String markdown = truncate(markupSupport.toMarkdown(page.getContentJson()), SNAPSHOT_TEXT_MAX_CHARS);
        DocFileClient.UploadedFile uploaded = fileClient.uploadBytes(
                markdown.getBytes(StandardCharsets.UTF_8), fileName(page), MARKDOWN_CONTENT_TYPE,
                BUCKET, userId, tenantId);
        DocArchiveClient.IngestOutcome outcome = archiveClient.ingest(tenantId, payload(page, markdown, uploaded));
        markArchived(pageId, outcome, uploaded.fileId());
        log.info("页面快照已入档: pageId={}, itemId={}, fileId={}", pageId, outcome.itemId(), uploaded.fileId());
        return true;
    }

    /**
     * 构建档案请求体。
     *
     * @param page     页面
     * @param markdown 快照正文
     * @param uploaded 快照文件
     * @return 请求体
     */
    private Map<String, Object> payload(DocPage page, String markdown, DocFileClient.UploadedFile uploaded) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sourceType", SOURCE_DOC_PAGE);
        body.put("sourceId", String.valueOf(page.getId()));
        body.put("title", page.getTitle());
        body.put("summary", truncate(markdown.replaceAll("\\s+", " ").trim(), SUMMARY_MAX_LENGTH));
        body.put("keywords", page.getSlug());
        body.put("securityLevel", SECURITY_INTERNAL);
        body.put("definitionCode", docProperties.getArchiveDefinitionCode());
        body.put("businessKey", "doc:" + page.getId());
        body.put("finishedAt", finishedAt(page));
        body.put("snapshotJson", snapshotJson(page, markdown));
        body.put("fileName", fileName(page));
        body.put("originalFileId", uploaded.fileId());
        body.put("originalFileName", uploaded.originalName());
        body.put("originalContentType", MARKDOWN_CONTENT_TYPE);
        body.put("originalFileSize", uploaded.fileSize());
        return body;
    }

    /**
     * 构建快照元数据 JSON（含截断正文，体积不超过配置上限）。
     *
     * @param page     页面
     * @param markdown 快照正文
     * @return JSON 文本
     */
    private String snapshotJson(DocPage page, String markdown) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pageId", String.valueOf(page.getId()));
        snapshot.put("spaceId", String.valueOf(page.getSpaceId()));
        snapshot.put("title", page.getTitle());
        snapshot.put("slug", page.getSlug());
        snapshot.put("status", page.getStatus());
        snapshot.put("revision", page.getRevision());
        snapshot.put("archivedAt", finishedAt(page));
        snapshot.put("content", markdown);
        String json = serialize(snapshot);
        while (json.getBytes(StandardCharsets.UTF_8).length > docProperties.getArchiveSnapshotMaxBytes()
                && markdown.length() > 1000) {
            markdown = markdown.substring(0, markdown.length() / 2);
            snapshot.put("content", markdown);
            snapshot.put("truncated", true);
            json = serialize(snapshot);
        }
        return json;
    }

    /**
     * 写入页面归档追溯字段。
     *
     * @param pageId   页面ID
     * @param outcome  入档结果
     * @param fileId   快照文件ID
     */
    private void markArchived(Long pageId, DocArchiveClient.IngestOutcome outcome, Long fileId) {
        LambdaUpdateWrapper<DocPage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocPage::getId, pageId)
                .set(DocPage::getArchiveItemId, outcome.itemId())
                .set(DocPage::getArchiveFileId, fileId)
                .set(DocPage::getArchiveAt, LocalDateTime.now());
        pageMapper.update(null, wrapper);
    }

    /**
     * 生成快照文件名。
     *
     * @param page 页面
     * @return 文件名
     */
    private String fileName(DocPage page) {
        return "DOC_PAGE-" + page.getId() + "-r" + page.getRevision() + ".md";
    }

    /**
     * 取页面归档时间或当前时间。
     *
     * @param page 页面
     * @return ISO 时间文本
     */
    private String finishedAt(DocPage page) {
        LocalDateTime time = page.getArchivedAt() == null ? LocalDateTime.now() : page.getArchivedAt();
        return time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 序列化 JSON。
     *
     * @param value 对象
     * @return JSON 文本
     */
    private String serialize(Map<String, Object> value) {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 按字符数截断文本。
     *
     * @param text 文本
     * @param max  最大字符数
     * @return 截断文本
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
