package com.bluewhale.erp.hr.domain.model;

import com.bluewhale.erp.common.ErpErrorCode;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 绩效考核结果。
 *
 * <p>不变量：得分在 0–100 之间；等级由得分推导（A ≥ 90，B ≥ 75，C ≥ 60，其余 D）。</p>
 *
 * @author BlueWhale
 */
@Data
public class Appraisal {

    /** 满分 */
    private static final BigDecimal FULL_SCORE = new BigDecimal("100");

    /** A 级门槛 */
    private static final BigDecimal GRADE_A = new BigDecimal("90");

    /** B 级门槛 */
    private static final BigDecimal GRADE_B = new BigDecimal("75");

    /** C 级门槛 */
    private static final BigDecimal GRADE_C = new BigDecimal("60");

    /** 主键 */
    private Long id;

    /** 绩效周期主键 */
    private Long cycleId;

    /** 绩效指标主键 */
    private Long kpiId;

    /** 指标名称快照 */
    private String kpiName;

    /** 员工编号 */
    private String employeeNo;

    /** 员工姓名快照 */
    private String employeeName;

    /** 得分 */
    private BigDecimal score;

    /** 等级：A / B / C / D */
    private String grade;

    /** 评语 */
    private String remark;

    /**
     * 校验并推导等级。
     */
    public void validateAndGrade() {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考核员工不能为空");
        }
        if (kpiId == null) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考核指标不能为空");
        }
        if (score == null || score.signum() < 0 || score.compareTo(FULL_SCORE) > 0) {
            throw ErpErrorCode.exception(ErpErrorCode.HR_INVALID, "考核得分必须在 0–100 之间");
        }
        grade = gradeOf(score);
    }

    /**
     * 按得分推导等级。
     *
     * @param value 得分
     * @return 等级
     */
    public static String gradeOf(BigDecimal value) {
        BigDecimal score = value.setScale(2, RoundingMode.HALF_UP);
        if (score.compareTo(GRADE_A) >= 0) {
            return "A";
        }
        if (score.compareTo(GRADE_B) >= 0) {
            return "B";
        }
        if (score.compareTo(GRADE_C) >= 0) {
            return "C";
        }
        return "D";
    }
}
