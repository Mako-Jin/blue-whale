package com.bluewhale.oa.approval.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批业务副作用视图。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalEffectView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 副作用类型 */
    private String effectType;

    /** 执行状态 */
    private String effectStatus;

    /** 执行时间 */
    private LocalDateTime executedAt;

    /** 重试次数 */
    private Integer retryCount;

    /** 失败原因 */
    private String errorMsg;
}
