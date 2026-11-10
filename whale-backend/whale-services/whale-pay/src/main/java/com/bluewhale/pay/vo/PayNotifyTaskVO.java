package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务通知任务 VO。
 *
 * @author BlueWhale
 */
@Data
public class PayNotifyTaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private Long id;

    /** 业务方类型 */
    private String bizType;

    /** 业务单号 */
    private String bizOrderNo;

    /** 事件 */
    private String event;

    /** 回调地址 */
    private String notifyUrl;

    /** 状态 */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryAt;

    /** 最近一次失败原因 */
    private String lastError;

    /** 创建时间 */
    private LocalDateTime createTime;
}
