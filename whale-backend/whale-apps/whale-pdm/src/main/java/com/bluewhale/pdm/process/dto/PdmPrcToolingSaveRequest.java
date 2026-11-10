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
    @NotBlank(message = "工装类型不能为空")
    @Pattern(regexp = "TOOL|FIXTURE|MOLD|GAUGE", message = "工装类型仅支持 TOOL/FIXTURE/MOLD/GAUGE")
    private String toolingType;

    /** 工装编码 */
    @NotBlank(message = "工装编码不能为空")
    @Size(max = 64, message = "工装编码长度不能超过 64")
    private String toolingCode;

    /** 规格 */
    @Size(max = 128, message = "规格长度不能超过 128")
    private String spec;

    /** 数量 */
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
