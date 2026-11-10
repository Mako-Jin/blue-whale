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
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码长度不能超过 64")
    private String ruleCode;

    /** 规则名称 */
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "规则名称长度不能超过 128")
    private String ruleName;

    /** 适用工位编码（空表示不限） */
    @Size(max = 64, message = "工位编码长度不能超过 64")
    private String stationCode;

    /** 适用工序编码（空表示不限） */
    @Size(max = 64, message = "工序编码长度不能超过 64")
    private String operationCode;

    /** 期望物料编码（空表示仅校验条码有效性） */
    @Size(max = 64, message = "物料编码长度不能超过 64")
    private String expectedItemCode;

    /** 命中处理：BLOCK / WARN */
    @Pattern(regexp = "BLOCK|WARN", message = "处理方式仅支持 BLOCK 或 WARN")
    private String action;

    /** 状态：ENABLED / DISABLED */
    @Pattern(regexp = "ENABLED|DISABLED", message = "状态仅支持 ENABLED 或 DISABLED")
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
