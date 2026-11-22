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
    @NotBlank(message = "{validation.party.welfare.title-required}")
    @Size(max = 128, message = "{validation.party.welfare.title-size}")
    private String title;

    /** 福利年度 */
    @NotNull(message = "{validation.party.welfare.welfare-year-required}")
    private Integer welfareYear;

    /** 发放标准 */
    @NotNull(message = "{validation.party.welfare.standard-amount-required}")
    @DecimalMin(value = "0.00", message = "{validation.party.welfare.standard-amount-min}")
    private BigDecimal standardAmount;

    /** 备注 */
    @Size(max = 255, message = "{validation.party.welfare.remark-size}")
    private String remark;
}
