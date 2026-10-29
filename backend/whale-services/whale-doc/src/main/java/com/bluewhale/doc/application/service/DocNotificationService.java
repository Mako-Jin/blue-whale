package com.bluewhale.doc.application.service;

import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;

import java.util.List;

/**
 * 文档站内消息通知应用服务（M3）。
 *
 * <p>关闭开关时不发布任何事件；事件统一在事务提交后发送，失败只告警不阻断主流程。</p>
 *
 * @author BlueWhale
 */
public interface DocNotificationService {

    /**
     * 评论 @ 提醒。
     *
     * @param tenantId        租户ID
     * @param page            页面
     * @param commentId       评论ID
     * @param authorId        评论人ID
     * @param mentionedUserIds 被提及用户ID
     */
    void commentMention(Long tenantId, DocPage page, Long commentId, Long authorId,
                        List<Long> mentionedUserIds);

    /**
     * 审批提交提醒。
     *
     * @param tenantId   租户ID
     * @param page       页面
     * @param instanceId 流程实例ID
     * @param operatorId 提交人ID
     */
    void approvalSubmitted(Long tenantId, DocPage page, Long instanceId, Long operatorId);

    /**
     * 审批结果提醒。
     *
     * @param tenantId   租户ID
     * @param page       页面
     * @param instanceId 流程实例ID
     * @param approved   是否通过
     */
    void approvalResult(Long tenantId, DocPage page, Long instanceId, boolean approved);

    /**
     * 空间成员变更提醒。
     *
     * @param tenantId    租户ID
     * @param spaceId     空间ID
     * @param spaceName   空间名称
     * @param operatorId  操作人ID
     * @param memberIds   受影响成员ID
     * @param action      变更动作描述
     */
    void memberChanged(Long tenantId, Long spaceId, String spaceName, Long operatorId,
                       List<Long> memberIds, String action);
}
