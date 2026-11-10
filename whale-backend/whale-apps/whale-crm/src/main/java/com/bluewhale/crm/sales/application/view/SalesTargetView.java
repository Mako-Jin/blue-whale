package com.bluewhale.crm.sales.application.view;

import com.bluewhale.crm.sales.domain.model.SalesTarget;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售目标视图。
 *
 * @author BlueWhale
 */
@Data
public class SalesTargetView {

    /** 目标 ID */
    private Long id;

    /** 周期 */
    private String period;

    /** 周期类型 */
    private String periodType;

    /** 范围类型 */
    private String scopeType;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
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
     * 领域模型转视图。
     *
     * @param target 目标
     * @return 视图
     */
    public static SalesTargetView from(SalesTarget target) {
        SalesTargetView view = new SalesTargetView();
        view.setId(target.getId());
        view.setPeriod(target.getPeriod());
        view.setPeriodType(target.getPeriodType() == null ? null : target.getPeriodType().name());
        view.setScopeType(target.getScopeType() == null ? null : target.getScopeType().name());
        view.setOwnerId(target.getOwnerId());
        view.setDeptId(target.getDeptId());
        view.setTargetAmount(target.getTargetAmount());
        view.setRemark(target.getRemark());
        view.setCreatedBy(target.getCreatedBy());
        view.setLockVersion(target.getLockVersion());
        view.setCreateTime(target.getCreateTime());
        return view;
    }
}
