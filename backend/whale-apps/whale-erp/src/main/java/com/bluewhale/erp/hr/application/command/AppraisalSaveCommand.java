package com.bluewhale.erp.hr.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 录入绩效考核结果命令。
 *
 * @author BlueWhale
 */
@Data
public class AppraisalSaveCommand {

    /** 员工编号 */
    @NotBlank
    private String employeeNo;

    /** 绩效指标主键 */
    @NotNull
    private Long kpiId;

    /** 得分（0–100） */
    @NotNull
    private BigDecimal score;

    /** 评语 */
    private String remark;
}
