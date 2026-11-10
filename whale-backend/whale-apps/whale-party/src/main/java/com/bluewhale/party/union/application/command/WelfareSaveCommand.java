package com.bluewhale.party.union.application.command;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 节日福利批次新建 / 编辑命令。
 *
 * <p>只允许改名称、年度、标准与备注：批次编号与状态由取号器与状态机维护，
 * 放进命令会让编辑接口有机会绕过「发放后冻结」。</p>
 *
 * @author BlueWhale
 */
@Data
public class WelfareSaveCommand {

    /** 福利名称 */
    @NotBlank(message = "福利名称不能为空")
    @Size(max = 128, message = "福利名称不得超过 128 字符")
    private String title;

    /** 福利年度 */
    @NotNull(message = "福利年度不能为空")
    private Integer welfareYear;

    /** 发放标准 */
    @NotNull(message = "发放标准不能为空")
    @DecimalMin(value = "0.00", message = "发放标准不能为负数")
    private BigDecimal standardAmount;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
