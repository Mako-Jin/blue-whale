package com.bluewhale.oa.hr.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出差预支记录。
 *
 * <p>一笔出差单最多一条预支记录，审批通过且预支金额大于 0 时幂等生成。</p>
 *
 * @author BlueWhale
 */
@Data
public class TripAdvance {

    /** 预支状态：待发放 */
    public static final String STATUS_PENDING = "PENDING";

    /** 主键 */
    private Long id;

    /** 出差申请ID */
    private Long tripId;

    /** 申请人用户ID */
    private Long applicantId;

    /** 预支金额 */
    private BigDecimal amount;

    /** 预支状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
