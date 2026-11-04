package com.bluewhale.mes.execution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务动作请求（开工 / 暂停 / 恢复 / 完工 / 报废）。
 *
 * <p>报废必须填写原因与数量；其余动作用于备注说明。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesTaskActionRequest {

    /** 动作数量（报废时必填） */
    @DecimalMin(value = "0.0001", message = "动作数量必须大于 0")
    private BigDecimal quantity;

    /** 原因 / 备注 */
    @Size(max = 255, message = "原因长度不能超过 255")
    private String remark;
}
