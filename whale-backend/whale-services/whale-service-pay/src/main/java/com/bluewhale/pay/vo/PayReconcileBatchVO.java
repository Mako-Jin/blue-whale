package com.bluewhale.pay.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账批次视图对象。
 *
 * @author BlueWhale
 */
@Data
public class PayReconcileBatchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 批次 ID */
    private Long id;

    /** 渠道编码 */
    private String channel;

    /** 对账业务日 */
    private LocalDate bizDate;

    /** 状态：RUNNING / SUCCESS / FAILED / PARTIAL */
    private String status;

    /** 本地记录数 */
    private Integer localCount;

    /** 渠道记录数（降级模式为 0） */
    private Integer channelCount;

    /** 差异数 */
    private Integer diffCount;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 失败原因 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;
}
