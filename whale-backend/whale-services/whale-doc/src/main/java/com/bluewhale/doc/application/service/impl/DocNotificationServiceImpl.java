package com.bluewhale.doc.application.service.impl;

import com.bluewhale.doc.application.service.DocNotificationService;
import com.bluewhale.doc.application.service.DocNotifyEvent;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文档站内消息通知实现。
 *
 * <p>只在开关开启时发布事件；接收人去重并剔除操作人，单次最多 100 人。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocNotificationServiceImpl implements DocNotificationService {

    /** 单次接收人上限（与消息服务一致） */
    private static final int MAX_RECIPIENTS = 100;

    /** 评论业务类型 */
    private static final String BIZ_COMMENT = "DOC_COMMENT";

    /** 审批业务类型 */
    private static final String BIZ_APPROVAL = "DOC_APPROVAL";

    /** 成员业务类型 */
    private static final String BIZ_MEMBER = "DOC_MEMBER";

    private final ApplicationEventPublisher eventPublisher;

    private final DocProperties docProperties;

    @Override
    public void commentMention(Long tenantId, DocPage page, Long commentId, Long authorId,
                               List<Long> mentionedUserIds) {
        if (!docProperties.isNotifyEnabled() || mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return;
        }
        List<Long> recipients = recipients(mentionedUserIds, authorId);
        if (recipients.isEmpty()) {
            return;
        }
        for (Long userId : recipients) {
            publish(new DocNotifyEvent(tenantId, BIZ_COMMENT, String.valueOf(page.getId()),
                    "doc:comment:" + commentId + ":" + userId,
                    "文档评论提到了你", "页面《" + page.getTitle() + "》的评论中提到了你", List.of(userId)));
        }
    }

    @Override
    public void approvalSubmitted(Long tenantId, DocPage page, Long instanceId, Long operatorId) {
        if (!docProperties.isNotifyEnabled()) {
            return;
        }
        List<Long> recipients = recipients(List.of(page.getAuthorId() == null ? operatorId : page.getAuthorId()),
                operatorId);
        for (Long userId : recipients) {
            publish(new DocNotifyEvent(tenantId, BIZ_APPROVAL, String.valueOf(page.getId()),
                    "doc:approval-submit:" + instanceId + ":" + userId,
                    "文档发布审批已提交", "页面《" + page.getTitle() + "》已提交发布审批", List.of(userId)));
        }
    }

    @Override
    public void approvalResult(Long tenantId, DocPage page, Long instanceId, boolean approved) {
        if (!docProperties.isNotifyEnabled()) {
            return;
        }
        List<Long> candidates = new ArrayList<>();
        if (page.getAuthorId() != null) {
            candidates.add(page.getAuthorId());
        }
        if (page.getLastEditorId() != null) {
            candidates.add(page.getLastEditorId());
        }
        List<Long> recipients = recipients(candidates, null);
        String title = approved ? "文档发布审批已通过" : "文档发布审批已驳回";
        for (Long userId : recipients) {
            publish(new DocNotifyEvent(tenantId, BIZ_APPROVAL, String.valueOf(page.getId()),
                    "doc:approval-result:" + instanceId + ":" + (approved ? "PASS" : "REJECT") + ":" + userId,
                    title, "页面《" + page.getTitle() + "》" + (approved ? "已发布" : "审批未通过"),
                    List.of(userId)));
        }
    }

    @Override
    public void memberChanged(Long tenantId, Long spaceId, String spaceName, Long operatorId,
                              List<Long> memberIds, String action) {
        if (!docProperties.isNotifyEnabled()) {
            return;
        }
        List<Long> recipients = recipients(memberIds, operatorId);
        for (Long userId : recipients) {
            publish(new DocNotifyEvent(tenantId, BIZ_MEMBER, String.valueOf(spaceId),
                    "doc:member:" + spaceId + ":" + action + ":" + userId,
                    "文档空间成员变更", "你在空间《" + spaceName + "》中的成员权限已变更", List.of(userId)));
        }
    }

    /**
     * 发布通知事件。
     *
     * @param event 通知事件
     */
    private void publish(DocNotifyEvent event) {
        if (event.tenantId() == null || event.recipientIds().isEmpty()) {
            return;
        }
        eventPublisher.publishEvent(event);
    }

    /**
     * 接收人去重、剔除操作人并截断上限。
     *
     * @param candidates 候选用户ID
     * @param excluded   排除的用户ID，可为 {@code null}
     * @return 接收人列表
     */
    private List<Long> recipients(List<Long> candidates, Long excluded) {
        if (candidates == null) {
            return List.of();
        }
        Set<Long> distinct = new LinkedHashSet<>();
        for (Long id : candidates) {
            if (id != null && !id.equals(excluded)) {
                distinct.add(id);
            }
        }
        List<Long> result = new ArrayList<>(distinct);
        return result.size() > MAX_RECIPIENTS ? result.subList(0, MAX_RECIPIENTS) : result;
    }
}
