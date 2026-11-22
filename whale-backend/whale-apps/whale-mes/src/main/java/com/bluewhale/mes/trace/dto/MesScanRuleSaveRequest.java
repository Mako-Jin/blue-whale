package com.bluewhale.mes.trace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 扫码防错规则保存请求（无 id 新增，有 id 编辑）
 *
 * @author BlueWhale
 */
@Data
public class MesScanRuleSaveRequest {

    /** 规则 ID（编辑时传入） */
    private Long id;

    /** 规则编码 */
    @NotBlank(message = "{validation.mes.mes-scan-rule.rule-code-required}")
    @Size(max = 64, message = "{validation.mes.mes-scan-rule.rule-code-size}")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "{validation.mes.mes-scan-rule.rule-name-required}")
    @Size(max = 128, message = "{validation.mes.mes-scan-rule.rule-name-size}")
    private String ruleName;

    /** 适用工位编码（空表示不限） */
    @Size(max = 64, message = "{validation.mes.mes-scan-rule.station-code-size}")
    private String stationCode;

    /** 适用工序编码（空表示不限） */
    @Size(max = 64, message = "{validation.mes.mes-scan-rule.operation-code-size}")
    private String operationCode;

    /** 期望物料编码（空表示仅校验条码有效性） */
    @Size(max = 64, message = "{validation.mes.mes-scan-rule.expected-item-code-size}")
    private String expectedItemCode;

    /** 命中处理：BLOCK / WARN */
    @Pattern(regexp = "BLOCK|WARN", message = "{validation.mes.mes-scan-rule.action-pattern}")
    private String action;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "{validation.mes.mes-scan-rule.status-pattern}")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-scan-rule.remark-size}")
    private String remark;
}
