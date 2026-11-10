package com.bluewhale.party.fee.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 党费标准保存命令。
 *
 * @author BlueWhale
 */
@Data
public class FeeRuleSaveCommand {

    /** 档位名称 */
    @NotBlank(message = "档位名称不能为空")
    @Size(max = 64, message = "档位名称不得超过 64 字符")
    private String ruleName;

    /** 基数下限（含） */
    @NotNull(message = "基数下限不能为空")
    private BigDecimal minBase;

    /** 基数上限（不含，空表示无上限） */
    private BigDecimal maxBase;

    /** 缴费比例 */
    @NotNull(message = "缴费比例不能为空")
    private BigDecimal rate;

    /** 状态：ACTIVE / INACTIVE */
    private String status;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
