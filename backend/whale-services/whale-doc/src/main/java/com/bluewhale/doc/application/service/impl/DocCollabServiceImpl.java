package com.bluewhale.doc.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocCollabService;
import com.bluewhale.doc.application.service.DocPageActivityService;
import com.bluewhale.doc.application.service.DocPageLinkService;
import com.bluewhale.doc.application.service.DocVersionService;
import com.bluewhale.doc.application.service.support.DocPageMutationSupport;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.domain.model.DocPageType;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.interfaces.vo.DocWsTicketVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocContentSupport;
import com.bluewhale.doc.support.collab.DocCollabInit;
import com.bluewhale.doc.support.collab.DocCollabPayloads;
import com.bluewhale.doc.support.collab.DocCollabSnapshot;
import com.bluewhale.doc.support.collab.DocCollabStore;
import com.bluewhale.doc.support.collab.DocWsTicket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * 文档实时协作应用服务实现（M2）。
 *
 * <p>协作期间正文以 Yjs 更新流实时同步，持久化由领袖客户端每 {@code collab-snapshot-seconds}
 * 秒提交完整正文快照；快照以页面当前 revision 写入，正文纯文本与标题都未变化时跳过，
 * 版本历史沿用 M1 的自动快照间隔，避免每 12 秒产生一条版本。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocCollabServiceImpl implements DocCollabService {

    /** 标题最大长度 */
    private static final int TITLE_MAX_LENGTH = 200;

    /** 协作快照变更说明 */
    private static final String SNAPSHOT_SUMMARY = "实时协作快照";

    private final DocPageMapper pageMapper;

    private final DocAccessGuard accessGuard;

    private final DocContentSupport contentSupport;

    private final DocPageMutationSupport mutationSupport;

    private final DocVersionService versionService;

    private final DocPageLinkService linkService;

    private final DocPageActivityService activityService;

    private final DocCollabStore collabStore;

    private final DocProperties docProperties;

    private final ObjectMapper objectMapper;

    @Override
    public DocWsTicketVO issueTicket(Long pageId, Long userId, String username) {
        requireCollabEnabled();
        DocPage page = accessGuard.requirePage(pageId);
        if (DocPageType.FOLDER.name().equals(page.getPageType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "目录不支持实时协作");
        }
        accessGuard.requirePageEdit(page, userId);
        assertNotArchived(page);
        String ticket = UUID.randomUUID().toString().replace("-", "");
        String displayName = username == null || username.isBlank() ? "用户" + userId : username.trim();
        try {
            collabStore.saveTicket(ticket, new DocWsTicket(TenantContext.requireId(), userId, pageId, displayName));
        } catch (RuntimeException ex) {
            log.warn("协作 ticket 写入失败: pageId={}, reason={}", pageId, ex.getMessage());
            throw DocErrorCode.exception(DocErrorCode.COLLAB_UNAVAILABLE);
        }
        DocWsTicketVO vo = new DocWsTicketVO();
        vo.setTicket(ticket);
        vo.setExpiresIn(docProperties.getCollabTicketTtlSeconds());
        return vo;
    }

    @Override
    public DocWsTicket consumeTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            throw DocErrorCode.exception(DocErrorCode.COLLAB_TICKET_INVALID);
        }
        try {
            DocWsTicket value = collabStore.consumeTicket(ticket.trim());
            if (value == null) {
                throw DocErrorCode.exception(DocErrorCode.COLLAB_TICKET_INVALID);
            }
            return value;
        } catch (BizException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("协作 ticket 消费失败: {}", ex.getMessage());
            throw DocErrorCode.exception(DocErrorCode.COLLAB_UNAVAILABLE);
        }
    }

    @Override
    public DocCollabInit load(DocWsTicket ticket) {
        Long tenantId = ticket.tenantId();
        DocPage page = pageMapper.selectById(ticket.pageId());
        if (page == null) {
            throw DocErrorCode.exception(DocErrorCode.PAGE_NOT_FOUND);
        }
        accessGuard.requirePageEdit(page, ticket.userId());
        assertNotArchived(page);
        try {
            List<String> updates = collabStore.loadUpdates(tenantId, page.getId());
            String contentJson = updates.isEmpty() ? page.getContentJson() : null;
            return new DocCollabInit(page.getId(), page.getTitle(), page.getRevision(), page.getStatus(),
                    contentJson, updates);
        } catch (RuntimeException ex) {
            log.warn("协作更新日志读取失败: pageId={}, reason={}", page.getId(), ex.getMessage());
            throw DocErrorCode.exception(DocErrorCode.COLLAB_UNAVAILABLE);
        }
    }

    @Override
    public boolean isActive(Long pageId) {
        if (!docProperties.isCollabEnabled()) {
            return false;
        }
        try {
            return collabStore.isActive(TenantContext.requireId(), pageId);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public void recordUpdate(DocWsTicket ticket, String update) {
        DocCollabPayloads.decode(update, docProperties.getCollabMaxUpdateBytes());
        try {
            collabStore.appendUpdate(ticket.tenantId(), ticket.pageId(), update);
        } catch (RuntimeException ex) {
            log.warn("协作更新写入失败: pageId={}, reason={}", ticket.pageId(), ex.getMessage());
            throw DocErrorCode.exception(DocErrorCode.COLLAB_UNAVAILABLE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocCollabSnapshot snapshot(DocWsTicket ticket, String title, JsonNode contentJson) {
        DocPage page = accessGuard.requirePage(ticket.pageId());
        accessGuard.requirePageEdit(page, ticket.userId());
        assertNotArchived(page);
        String nextTitle = normalizeTitle(title, page.getTitle());
        String json = contentJson == null || contentJson.isNull()
                ? page.getContentJson() : objectMapper.writeValueAsString(contentJson);
        contentSupport.requireWithinLimit(json);
        String text = contentSupport.extractText(json);
        if (nextTitle.equals(page.getTitle()) && text != null && text.equals(page.getContentText())) {
            return new DocCollabSnapshot(page.getRevision(), page.getUpdateTime(), true);
        }
        DocPage updated = mutationSupport.apply(page, page.getRevision(), nextTitle, json, text, ticket.userId());
        linkService.refresh(updated.getId(), updated.getContentJson());
        versionService.maybeAutoSnapshot(updated, ticket.userId());
        activityService.record(updated.getId(), "COLLAB", ticket.userId(), "实时协作快照");
        log.info("协作快照已落库: pageId={}, revision={}, operator={}",
                updated.getId(), updated.getRevision(), ticket.userId());
        return new DocCollabSnapshot(updated.getRevision(), updated.getUpdateTime(), false);
    }

    @Override
    public long activeTtlSeconds() {
        return docProperties.getCollabActiveTtlSeconds();
    }

    @Override
    public int snapshotSeconds() {
        return docProperties.getCollabSnapshotSeconds();
    }

    /**
     * 校验实时协作开关。
     */
    private void requireCollabEnabled() {
        if (!docProperties.isCollabEnabled()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "实时协作未开启");
        }
    }

    /**
     * 归档页面不允许协作。
     *
     * @param page 页面
     */
    private void assertNotArchived(DocPage page) {
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已归档页面不可编辑");
        }
    }

    /**
     * 规范化协作快照标题。
     *
     * @param title       提交标题
     * @param defaultTitle 默认标题
     * @return 规范化标题
     */
    private String normalizeTitle(String title, String defaultTitle) {
        if (title == null || title.isBlank()) {
            return defaultTitle;
        }
        String value = title.trim();
        if (value.length() > TITLE_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_ERROR, "页面标题不能超过 200 字");
        }
        return value;
    }
}
