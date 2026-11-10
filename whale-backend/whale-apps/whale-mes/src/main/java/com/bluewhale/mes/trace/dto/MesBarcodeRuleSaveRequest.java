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
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String ruleName;

    /** 条码前缀 */
    @NotBlank(message = "条码前缀不能为空")
    @Size(max = 32, message = "条码前缀长度不能超过 32")
    private String prefix;

    /** 码制：CODE128 / QR */
    @Pattern(regexp = "CODE128|QR", message = "码制仅支持 CODE128 或 QR")
    private String codeType;

    /** 流水号长度（4–12） */
    @Min(value = 4, message = "流水号长度不能小于 4")
    @Max(value = 12, message = "流水号长度不能大于 12")
    private Integer serialLength;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED 或 DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
