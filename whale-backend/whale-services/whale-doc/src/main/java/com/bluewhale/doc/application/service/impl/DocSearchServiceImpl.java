package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocSearchService;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocSearchRow;
import com.bluewhale.doc.interfaces.dto.DocSearchQueryRequest;
import com.bluewhale.doc.interfaces.vo.DocSearchItemVO;
import com.bluewhale.doc.support.DocMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 文档搜索应用服务实现。
 *
 * <p>只检索已发布页面，且限定公开空间或当前用户已加入的空间；摘要由服务端从纯文本投影生成。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DocSearchServiceImpl implements DocSearchService {

    /** 摘要命中前保留字符数 */
    private static final int SNIPPET_BEFORE = 40;

    /** 摘要命中后保留字符数 */
    private static final int SNIPPET_AFTER = 80;

    /** 无命中时的摘要长度 */
    private static final int SNIPPET_LENGTH = 120;

    /** 搜索最大条数 */
    private static final long MAX_PAGE_SIZE = 100L;

    /** ngram 全文索引最小关键词长度 */
    private static final int MIN_FULLTEXT_LENGTH = 2;

    private final DocPageMapper pageMapper;

    private final DocMetrics docMetrics;

    @Override
    public PageResult<DocSearchItemVO> search(DocSearchQueryRequest request, Long userId) {
        Long tenantId = TenantContext.requireId();
        String keyword = request.getKeyword().trim();
        boolean useFulltext = keyword.length() >= MIN_FULLTEXT_LENGTH;
        long start = System.nanoTime();
        try {
            long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
            long current = Math.max(request.getCurrent(), 1L);
            long total = pageMapper.countSearch(tenantId, userId, keyword, request.getSpaceId(),
                    request.getTagId(), useFulltext);
            if (total == 0) {
                return PageResult.of(List.of(), 0L, current, size);
            }
            long offset = (current - 1) * size;
            List<DocSearchRow> rows = pageMapper.search(tenantId, userId, keyword, request.getSpaceId(),
                    request.getTagId(), useFulltext, offset, (int) size);
            List<DocSearchItemVO> records = rows.stream()
                    .map(row -> toVO(row, keyword))
                    .toList();
            return PageResult.of(records, total, current, size);
        } finally {
            docMetrics.recordSearch(useFulltext, System.nanoTime() - start);
        }
    }

    /**
     * 命中行转搜索结果。
     *
     * @param row     命中行
     * @param keyword 关键词
     * @return 搜索结果
     */
    private DocSearchItemVO toVO(DocSearchRow row, String keyword) {
        DocSearchItemVO vo = new DocSearchItemVO();
        vo.setPageId(row.getPageId());
        vo.setSpaceId(row.getSpaceId());
        vo.setSpaceName(row.getSpaceName());
        vo.setTitle(row.getTitle());
        vo.setSlug(row.getSlug());
        vo.setStatus(row.getStatus());
        vo.setRevision(row.getRevision());
        vo.setSnippet(buildSnippet(row.getContentText(), keyword));
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    /**
     * 从纯文本投影生成命中摘要。
     *
     * @param text    纯文本
     * @param keyword 关键词
     * @return 摘要
     */
    private String buildSnippet(String text, String keyword) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        String lowerText = normalized.toLowerCase(Locale.ROOT);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        int index = lowerText.indexOf(lowerKeyword);
        if (index < 0) {
            return normalized.length() <= SNIPPET_LENGTH
                    ? normalized : normalized.substring(0, SNIPPET_LENGTH) + "…";
        }
        int start = Math.max(0, index - SNIPPET_BEFORE);
        int end = Math.min(normalized.length(), index + keyword.length() + SNIPPET_AFTER);
        String snippet = normalized.substring(start, end);
        return (start > 0 ? "…" : "") + snippet + (end < normalized.length() ? "…" : "");
    }
}
