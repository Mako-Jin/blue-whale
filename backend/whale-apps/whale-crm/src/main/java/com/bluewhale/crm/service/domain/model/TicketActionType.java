package com.bluewhale.crm.service.domain.model;

/**
 * 工单处理动作类型。
 *
 * @author BlueWhale
 */
public enum TicketActionType {

    /** 新建 */
    CREATE,

    /** 分派 */
    ASSIGN,

    /** 处理 */
    PROCESS,

    /** 备注 */
    COMMENT,

    /** 暂停（等待客户） */
    PAUSE,

    /** 恢复 */
    RESUME,

    /** 升级 */
    ESCALATE,

    /** 解决 */
    RESOLVE,

    /** 关闭 */
    CLOSE,

    /** 重开 */
    REOPEN,

    /** 取消 */
    CANCEL,

    /** 低分回访（M4 满意度联动） */
    FOLLOW_UP
}
