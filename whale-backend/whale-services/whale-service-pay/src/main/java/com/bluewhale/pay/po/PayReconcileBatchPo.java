package com.bluewhale.pay.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账批次实体，对应表 {@code pay_reconcile_batch}。
 *
 * <p>唯一键 {@code (tenant_id, channel, biz_date)} 同时承担幂等与单实例抢占：
 * 同一业务日同一渠道只会有一个批次真正执行，其余实例读到已有批次直接返回。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_reconcile_batch")
public class PayReconcileBatchPo extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 渠道编码 */
    private String channel;

    /** 对账业务日 */
    private LocalDate bizDate;

    /** 状态：RUNNING / SUCCESS / FAILED / PARTIAL */
    private String status;

    /** 本地记录数 */
    private Integer localCount;

    /** 渠道记录数（降级模式下为 0） */
    private Integer channelCount;

    /** 差异数 */
    private Integer diffCount;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 失败原因（截断到列宽） */
    private String errorMessage;
}
