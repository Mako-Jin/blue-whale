package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 假期类型聚合。
 *
 * <p>编码在租户内唯一；启用类型才参与年度额度初始化。额度是否允许为负由
 * {@code allowNegative} 控制，供额度冻结时使用。</p>
 *
 * @author BlueWhale
 */
@Data
public class LeaveType {

    /** 主键 */
    private Long id;

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 计量单位 */
    private LeaveTypeUnit unit;

    /** 年度默认额度 */
    private BigDecimal annualQuota;

    /** 最小请假单位 */
    private BigDecimal minUnit;

    /** 是否必须上传附件 */
    private Boolean requiresAttachment;

    /** 是否允许负额度 */
    private Boolean allowNegative;

    /** 是否启用 */
    private Boolean enabled;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验类型基本约束。
     *
     * <p>编码与名称必填；年度额度不得为负；最小单位必须大于 0。</p>
     */
    public void ensureValid() {
        if (code == null || code.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID, "假期类型编码不能为空");
        }
        if (name == null || name.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID, "假期类型名称不能为空");
        }
        if (annualQuota != null && annualQuota.signum() < 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID, "年度额度不能为负数");
        }
        if (minUnit != null && minUnit.signum() <= 0) {
            throw OaErrorCode.exception(OaErrorCode.LEAVE_TYPE_INVALID, "最小请假单位必须大于 0");
        }
    }

    /**
     * 是否允许负额度。
     *
     * @return 允许返回 {@code true}
     */
    public boolean allowNegative() {
        return Boolean.TRUE.equals(allowNegative);
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 {@code true}
     */
    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 启用假期类型。
     */
    public void enable() {
        enabled = Boolean.TRUE;
    }

    /**
     * 停用假期类型。
     */
    public void disable() {
        enabled = Boolean.FALSE;
    }

    /**
     * 按目标状态启停。
     *
     * @param target 是否启用
     */
    public void toggle(boolean target) {
        if (target) {
            enable();
        } else {
            disable();
        }
    }
}
