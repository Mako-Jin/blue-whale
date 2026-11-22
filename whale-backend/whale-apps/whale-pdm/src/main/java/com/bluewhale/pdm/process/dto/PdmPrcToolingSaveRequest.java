package com.bluewhale.pdm.process.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 工艺工装保存请求。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcToolingSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序ID（为空表示路线级工装） */
    private Long stepId;

    /** 类型：TOOL/FIXTURE/MOLD/GAUGE */
    @NotBlank(message = "{validation.pdm.pdm-prc-tooling.tooling-type-required}")
    @Pattern(regexp = "TOOL|FIXTURE|MOLD|GAUGE", message = "{validation.pdm.pdm-prc-tooling.tooling-type-pattern}")
    private String toolingType;

    /** 工装编码 */
    @NotBlank(message = "{validation.pdm.pdm-prc-tooling.tooling-code-required}")
    @Size(max = 64, message = "{validation.pdm.pdm-prc-tooling.tooling-code-size}")
    private String toolingCode;

    /** 规格 */
    @Size(max = 128, message = "{validation.pdm.pdm-prc-tooling.spec-size}")
    private String spec;

    /** 数量 */
    @Min(value = 1, message = "{validation.pdm.pdm-prc-tooling.quantity-min}")
    private Integer quantity;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-prc-tooling.remark-size}")
    private String remark;
}
