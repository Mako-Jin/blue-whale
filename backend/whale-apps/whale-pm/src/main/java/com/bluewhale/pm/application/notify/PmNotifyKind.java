package com.bluewhale.pm.application.notify;

/**
 * 站内通知类型。
 *
 * @author BlueWhale
 */
public enum PmNotifyKind {

    /** 被指派 */
    ASSIGN("工作项指派"),

    /** 被评论 */
    COMMENT("工作项有新评论"),

    /** 状态变更 */
    TRANSITION("工作项状态变更");

    private final String title;

    /**
     * 构造通知类型。
     *
     * @param title 通知标题
     */
    PmNotifyKind(String title) {
        this.title = title;
    }

    /**
     * 通知标题。
     *
     * @return 标题文案
     */
    public String title() {
        return title;
    }
}
