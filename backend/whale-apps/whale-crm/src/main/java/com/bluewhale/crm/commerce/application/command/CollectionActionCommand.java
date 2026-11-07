package com.bluewhale.crm.commerce.application.command;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 催收记录命令。
 *
 * @author BlueWhale
 */
@Data
public class CollectionActionCommand {

    /** 催收动作：CALL / VISIT / EMAIL / REMINDER / PROMISE */
    @NotBlank
    private String actionType;

    /** 催收内容 */
    private String content;

    /** 承诺还款日期（动作类型为 PROMISE 时填写） */
    private LocalDate promiseDate;

    /** 承诺还款金额（动作类型为 PROMISE 时填写） */
    private BigDecimal promiseAmount;
}
