package com.bluewhale.oa.integration.application.view;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Outbox 事件视图。
 *
 * @author BlueWhale
 */
@Data
public class OutboxEventView implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 事件键 */
    private String eventKey;

    /** 事件类型 */
    private String eventType;

    /** 聚合类型 */
    private String aggregateType;

    /** 聚合ID */
    private String aggregateId;

    /** 状态 */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 发送成功时间 */
    private LocalDateTime sentAt;

    /** 失败原因 */
    private String errorMsg;
}
