package com.bluewhale.pdm.change.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 变更追溯视图
 *
 * @author BlueWhale
 */
@Data
public class PdmChgTraceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 追溯ID */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private Long bizId;

    /** 业务编号 */
    private String bizNo;

    /** 对象类型 */
    private String objectType;

    /** 对象ID */
    private Long objectId;

    /** 对象编码 */
    private String objectCode;

    /** 动作 */
    private String action;

    /** 变更前版本 */
    private String fromVersion;

    /** 变更后版本 */
    private String toVersion;

    /** 内容摘要 */
    private String content;

    /** 操作人 */
    private Long operatorId;

    /** 追溯时间 */
    private LocalDateTime traceTime;
}
