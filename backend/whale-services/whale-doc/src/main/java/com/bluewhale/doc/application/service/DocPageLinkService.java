package com.bluewhale.doc.application.service;

import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageLink;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageLinkMapper;
import com.bluewhale.doc.infrastructure.persistence.query.DocBacklinkRow;
import com.bluewhale.doc.interfaces.vo.DocBacklinkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 页面引用（反向链接）应用服务。
 *
 * <p>保存页面时从 TipTap JSON 的链接 mark / href / attrs.pageId 中提取目标页面ID，
 * 重建 {@code doc_page_link}。反向链接查询只返回当前用户可读的引用方页面。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocPageLinkService {

    /** 从 href / docpage: 标识中提取页面ID */
    private static final Pattern PAGE_ID_PATTERN =
            Pattern.compile("(?i)(?:pageId=|/doc/page/|docpage:)(\\d{1,19})");

    /** 单页最多登记的引用数 */
    private static final int MAX_LINKS = 200;

    /** 反向链接查询上限 */
    private static final int BACKLINK_LIMIT = 50;

    /** 链接文本最大长度 */
    private static final int MAX_LINK_TEXT = 200;

    private final DocPageLinkMapper linkMapper;

    private final ObjectMapper objectMapper;

    /**
     * 重建页面的引用关系；正文无法解析时清空该页引用。
     *
     * @param sourcePageId 引用方页面ID
     * @param contentJson  TipTap 正文 JSON
     */
    @Transactional(rollbackFor = Exception.class)
    public void refresh(Long sourcePageId, String contentJson) {
        if (sourcePageId == null) {
            return;
        }
        long tenantId = TenantContext.requireId();
        linkMapper.deleteBySource(tenantId, sourcePageId);
        Map<Long, String> targets = extract(contentJson, sourcePageId);
        for (Map.Entry<Long, String> entry : targets.entrySet()) {
            DocPageLink link = new DocPageLink();
            link.setSourcePageId(sourcePageId);
            link.setTargetPageId(entry.getKey());
            link.setLinkText(truncate(entry.getValue()));
            linkMapper.insert(link);
        }
    }

    /**
     * 查询反向链接。
     *
     * @param pageId 目标页面ID
     * @param userId 当前用户ID
     * @return 反向链接列表
     */
    public List<DocBacklinkVO> backlinks(Long pageId, Long userId) {
        List<DocBacklinkRow> rows = linkMapper.selectBacklinks(
                TenantContext.requireId(), userId, pageId, BACKLINK_LIMIT);
        return rows.stream().map(row -> {
            DocBacklinkVO vo = new DocBacklinkVO();
            vo.setPageId(row.getPageId());
            vo.setTitle(row.getTitle());
            vo.setSpaceId(row.getSpaceId());
            vo.setSpaceName(row.getSpaceName());
            vo.setSlug(row.getSlug());
            vo.setLinkText(row.getLinkText());
            vo.setUpdateTime(row.getUpdateTime());
            return vo;
        }).toList();
    }

    /**
     * 从 TipTap JSON 中提取引用目标与链接文本。
     *
     * @param contentJson  正文 JSON
     * @param sourcePageId 引用方页面ID（用于排除自引用）
     * @return 目标页面ID → 链接文本
     */
    private Map<Long, String> extract(String contentJson, Long sourcePageId) {
        Map<Long, String> targets = new LinkedHashMap<>();
        if (contentJson == null || contentJson.isBlank()) {
            return targets;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            collect(root, targets, sourcePageId);
        } catch (RuntimeException ex) {
            log.debug("解析页面引用失败: pageId={}, reason={}", sourcePageId, ex.getMessage());
        }
        return targets;
    }

    /**
     * 递归遍历 JSON 节点，提取页面引用。
     *
     * @param node         当前节点
     * @param targets      收集结果
     * @param sourcePageId 引用方页面ID
     */
    private void collect(JsonNode node, Map<Long, String> targets, Long sourcePageId) {
        if (node == null || targets.size() >= MAX_LINKS) {
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collect(child, targets, sourcePageId));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        String text = node.hasNonNull("text") ? node.get("text").asText() : null;
        JsonNode marks = node.get("marks");
        if (marks != null && marks.isArray()) {
            for (JsonNode mark : marks) {
                if ("link".equals(mark.path("type").asText())) {
                    addTarget(textOrNull(mark.path("attrs").path("href")), mark.path("attrs"),
                            text, targets, sourcePageId);
                }
            }
        }
        JsonNode attrs = node.get("attrs");
        if (attrs != null && attrs.isObject()) {
            addTarget(textOrNull(attrs.path("href")), attrs, text, targets, sourcePageId);
            addTarget(textOrNull(attrs.path("pageId")), attrs, text, targets, sourcePageId);
        }
        JsonNode content = node.get("content");
        if (content != null) {
            collect(content, targets, sourcePageId);
        }
    }

    /**
     * 解析并登记一个引用目标。
     *
     * @param raw          原始 href / pageId
     * @param attrs        属性节点
     * @param text         节点文本
     * @param targets      收集结果
     * @param sourcePageId 引用方页面ID
     */
    private void addTarget(String raw, JsonNode attrs, String text,
                           Map<Long, String> targets, Long sourcePageId) {
        if (raw == null || raw.isBlank() || targets.size() >= MAX_LINKS) {
            return;
        }
        Long targetId = parsePageId(raw);
        if (targetId == null || targetId == 0 || targetId.equals(sourcePageId)) {
            return;
        }
        String label = text;
        if (label == null || label.isBlank()) {
            label = textOrNull(attrs.path("label"));
        }
        if (label == null || label.isBlank()) {
            label = textOrNull(attrs.path("text"));
        }
        targets.putIfAbsent(targetId, label);
    }

    /**
     * 读取节点文本，缺失或 null 节点返回 {@code null}。
     *
     * @param node JSON 节点
     * @return 文本
     */
    private String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /**
     * 从原始标识中解析页面ID。
     *
     * @param raw 原始标识
     * @return 页面ID；无法解析返回 {@code null}
     */
    private Long parsePageId(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.matches("\\d{1,19}")) {
            try {
                return Long.valueOf(raw);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        Matcher matcher = PAGE_ID_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 截断链接文本。
     *
     * @param text 文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.length() <= MAX_LINK_TEXT ? trimmed : trimmed.substring(0, MAX_LINK_TEXT);
    }
}
