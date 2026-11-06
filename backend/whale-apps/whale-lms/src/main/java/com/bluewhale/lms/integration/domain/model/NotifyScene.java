package com.bluewhale.lms.integration.domain.model;

/**
 * 通知场景。
 *
 * <p>每个场景对应一套固定的模板编码与幂等键口径。把「场景」做成枚举而不是
 * 让调用方自由传字符串，是为了让幂等键的拼装规则只有一处实现——
 * 幂等键一旦各处各拼，同一条通知换个入口就会重复发。</p>
 *
 * <p>幂等键口径：</p>
 * <ul>
 *   <li>{@link #ENROLL}：{@code ENROLL:{courseId}} —— 报名一次性事件，课程内唯一</li>
 *   <li>{@link #CLASS_START}：{@code CLASS_START:{classId}} —— 开班提醒一次性</li>
 *   <li>{@link #URGE}：{@code AUTO_URGE:{classId}:{yyyyMMdd}} —— 催学按天，一天最多一条</li>
 *   <li>{@link #CERT_ISSUE}：{@code CERT_ISSUE:{certificateId}} —— 发证一次性</li>
 * </ul>
 *
 * @author BlueWhale
 */
public enum NotifyScene {

    /** 报名成功 */
    ENROLL("LMS_ENROLL", "报名成功通知"),

    /** 开班提醒 */
    CLASS_START("LMS_CLASS_START", "开班提醒"),

    /** 催学 / 催办（含未完成必修） */
    URGE("LMS_URGE", "学习催办"),

    /** 发证通知 */
    CERT_ISSUE("LMS_CERT_ISSUE", "证书发放通知");

    /** 消息模板编码 */
    private final String templateCode;

    /** 默认标题 */
    private final String defaultTitle;

    /**
     * 构造场景。
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
     * 解析场景。
     *
     * @param value 原始值
     * @return 场景；无法识别返回 {@code null}
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
}
