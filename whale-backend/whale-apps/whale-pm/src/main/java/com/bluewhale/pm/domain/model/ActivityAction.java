package com.bluewhale.pm.domain.model;

/**
 * 工作项动态动作类型。
 *
 * @author BlueWhale
 */
public enum ActivityAction {

    /** 创建 */
    CREATE,

    /** 更新 */
    UPDATE,

    /** 状态流转 */
    TRANSITION,

    /** 评论 */
    COMMENT,

    /** 附件 */
    ATTACH,

    /** 关联 */
    LINK,

    /** 删除 */
    DELETE
}
