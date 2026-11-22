package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 条码规则保存请求（无 id 新增，有 id 编辑）
 *
 * @author BlueWhale
 */
@Data
public class MesBarcodeRuleSaveRequest {

    /** 规则 ID（编辑时传入） */
    private Long id;

    /** 规则编码 */
    @NotBlank(message = "{validation.mes.mes-barcode-rule.rule-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-barcode-rule.rule-code-size}")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "{validation.mes.mes-barcode-rule.rule-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-barcode-rule.rule-name-size}")
    private String ruleName;

    /** 条码前缀 */
    @NotBlank(message = "{validation.mes.mes-barcode-rule.prefix-required}")
    @Size(max = 32, message = "{validation.mes.mes-barcode-rule.prefix-size}")
    private String prefix;

    /** 码制：CODE128 / QR */
    @Pattern(regexp = "CODE128|QR", message = "{validation.mes.mes-barcode-rule.code-type-pattern}")
    private String codeType;

    /** 流水号长度（4–12） */
    @Min(value = 4, message = "{validation.mes.mes-barcode-rule.serial-length-min}")
    @Max(value = 12, message = "{validation.mes.mes-barcode-rule.serial-length-max}")
    private Integer serialLength;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-barcode-rule.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-barcode-rule.remark-size}")
    private String remark;
}
