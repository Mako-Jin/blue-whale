package com.bluewhale.mes.common.outbox.entity;

import com.bluewhale.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MES 集成发件箱
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_common_outbox")
public class MesOutboxEvent extends BaseEntity {

    /** 源事件幂等键 */
    private String sourceEventId;

    /** 事件类型 */
    private String eventType;

    /** 聚合类型 */
    private String aggregateType;

    /** 聚合 ID */
    private String aggregateId;

    /** 事件报文 */
    private String payload;

    /** 状态：PENDING/SENT/FAILED */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 最后错误 */
    private String lastError;
}
