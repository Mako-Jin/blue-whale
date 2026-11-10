package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.time.LocalDate;

/**
 * 绩效周期聚合。
 *
 * <p>不变量：编号与名称非空；结束日期不早于开始日期；关闭后不可再录入考核。</p>
 *
 * @author BlueWhale
 */
@Data
public class PerformanceCycle {

    /** 主键 */
    private Long id;

    /** 周期编号 */
    private String cycleNo;

    /** 周期名称 */
    private String name;

    /** 考核期间（YYYY-MM） */
    private String period;

    /** 考核开始日期 */
    private LocalDate startDate;

    /** 考核结束日期 */
    private LocalDate endDate;

    /** 状态：DRAFT / IN_PROGRESS / CLOSED */
    private String status;

    /** 备注 */
    private String remark;

    /**
     * 校验周期信息。
     */
    public void validate() {
        if (cycleNo == null || cycleNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "周期编号不能为空");
        }
        if (name == null || name.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "周期名称不能为空");
        }
        if (period == null || period.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考核期间不能为空");
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考核结束日期不能早于开始日期");
        }
    }

    /**
     * 启动考核（草稿 → 进行中）。
     */
    public void start() {
        if (!HrStatus.CYCLE_DRAFT.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "只有草稿绩效周期可以启动");
        }
        status = HrStatus.CYCLE_IN_PROGRESS;
    }

    /**
     * 关闭考核。
     */
    public void close() {
        if (HrStatus.CYCLE_CLOSED.equals(status)) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_STATE_CONFLICT, "绩效周期已关闭");
        }
        status = HrStatus.CYCLE_CLOSED;
    }

    /**
     * 判断是否可录入考核结果。
     *
     * @return 是否允许录入
     */
    public boolean appraisable() {
        return !HrStatus.CYCLE_CLOSED.equals(status);
    }
}
