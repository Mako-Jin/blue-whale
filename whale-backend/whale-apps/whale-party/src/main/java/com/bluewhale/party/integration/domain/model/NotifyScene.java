package com.bluewhale.party.integration.domain.model;

/**
 * 通知场景（M-P4）。
 *
 * <p>幂等键口径集中在这里说明，避免各处各拼：</p>
 * <ul>
 *   <li>{@link #PARTY_PROBATION}：{@code PARTY_PROBATION:{memberId}:{yyyyMM}} —— 转正提醒按月提醒</li>
 *   <li>{@link #PARTY_FEE}：{@code PARTY_FEE:{billId}} —— 每张欠缴账单只催一次</li>
 *   <li>{@link #PARTY_ACTIVITY}：{@code PARTY_ACTIVITY:{activityId}} —— 每场组织生活只通知一次</li>
 *   <li>{@link #UNION_ACTIVITY}：{@code UNION_ACTIVITY:{activityId}} —— 每场工会活动只通知一次</li>
 *   <li>{@link #UNION_PROPOSAL_FEEDBACK}：{@code UNION_PROPOSAL_FEEDBACK:{proposalId}} —— 反馈一次性</li>
 *   <li>{@link #PARTY_BRANCH_TERM}：{@code PARTY_BRANCH_TERM:{branchId}:{deadlineYear}} —— 每届只提醒一次</li>
 * </ul>
 *
 * @author BlueWhale
 */
public enum NotifyScene {

    /** 预备党员转正提醒 */
    PARTY_PROBATION("PARTY_PROBATION", "预备党员转正提醒"),

    /** 党费催缴 */
    PARTY_FEE("PARTY_FEE", "党费缴纳提醒"),

    /** 组织生活通知 */
    PARTY_ACTIVITY("PARTY_ACTIVITY", "组织生活通知"),

    /** 支部换届提醒 */
    PARTY_BRANCH_TERM("PARTY_BRANCH_TERM", "支部换届提醒"),

    /** 工会活动通知 */
    UNION_ACTIVITY("UNION_ACTIVITY", "工会活动通知"),

    /** 提案反馈通知 */
    UNION_PROPOSAL_FEEDBACK("UNION_PROPOSAL_FEEDBACK", "提案反馈通知");

    /** 消息模板编码 */
    private final String templateCode;

    /** 默认标题 */
    private final String defaultTitle;

    /**
     * 构造通知场景。
     *
     * @param templateCode 消息模板编码
     * @param defaultTitle 默认标题
     */
    NotifyScene(String templateCode, String defaultTitle) {
        this.templateCode = templateCode;
        this.defaultTitle = defaultTitle;
    }

    /**
     * 消息模板编码。
     *
     * @return 模板编码
     */
    public String getTemplateCode() {
        return templateCode;
    }

    /**
     * 默认标题。
     *
     * @return 标题
     */
    public String getDefaultTitle() {
        return defaultTitle;
    }

    /**
     * 解析场景（未知返回 null）。
     *
     * @param value 原始值
     * @return 场景
     */
    public static NotifyScene parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (NotifyScene scene : values()) {
            if (scene.name().equalsIgnoreCase(value.trim())) {
                return scene;
            }
        }
        return null;
    }

    /**
     * 是否工会场景（权限与文案分域用）。
     *
     * @return 工会场景返回 true
     */
    public boolean isUnion() {
        return this == UNION_ACTIVITY || this == UNION_PROPOSAL_FEEDBACK;
    }
}
