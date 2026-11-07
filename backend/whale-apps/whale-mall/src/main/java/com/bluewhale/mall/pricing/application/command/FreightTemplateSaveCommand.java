package com.bluewhale.mall.pricing.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 运费模板保存命令。
 *
 * @author BlueWhale
 */
@Data
public class FreightTemplateSaveCommand {

    /** 模板名称（租户内唯一） */
    @NotBlank(message = "模板名称不能为空")
    @Size(max = 64, message = "模板名称不得超过 64 字符")
    private String templateName;

    /** 计费方式：FREE / FIXED / THRESHOLD */
    @NotBlank(message = "必须指定计费方式")
    private String chargeType;

    /** 固定运费金额（FREE 时忽略） */
    private BigDecimal baseAmount;

    /** 包邮门槛（仅 THRESHOLD 必填） */
    private BigDecimal freeThreshold;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
