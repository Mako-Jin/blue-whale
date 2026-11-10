package com.bluewhale.crm.sales.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 销售目标（个人 / 部门，按周期唯一）。
 *
 * <p>目标口径固定为签约额（合同台账 M3 上线前以赢单商机金额近似），
 * 调整必须记录调整前 / 后金额与原因。</p>
 *
 * @author BlueWhale
 */
@Data
public class SalesTarget {

    /** 金额小数位 */
    private static final int AMOUNT_SCALE = 2;

    /** 主键 */
    private Long id;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private ForecastPeriodType periodType;

    /** 范围类型 */
    private ForecastScopeType scopeType;

    /** 负责人（USER 范围） */
    private Long ownerId;

    /** 归属部门（DEPT 范围） */
    private Long deptId;

    /** 目标金额 */
    private BigDecimal targetAmount;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    /** 乐观锁版本号 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 更新目标金额与备注。
     *
     * @param amount 目标金额
     * @param note 备注
     */
    public void updateAmount(BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw CrmErrorCode.exception(CrmErrorCode.TARGET_AMOUNT_INVALID);
        }
        this.targetAmount = amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        this.remark = note;
    }
}
