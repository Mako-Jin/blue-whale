package com.bluewhale.pdm.process.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 工艺工序保存请求。
 *
 * @author BlueWhale
 */
@Data
public class PdmPrcStepSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工序序号（缺省取当前最大序号 +1） */
    @Min(value = 1, message = "工序序号必须大于 0")
    private Integer stepNo;

    /** 工序名称 */
    @NotBlank(message = "工序名称不能为空")
    @Size(max = 128, message = "工序名称长度不能超过 128")
    private String name;

    /** 工作中心 */
    @Size(max = 128, message = "工作中心长度不能超过 128")
    private String workCenter;

    /** 标准工时（小时） */
    @DecimalMin(value = "0", message = "标准工时不能为负")
    private BigDecimal standardHours;

    /** 准备工时（小时） */
    @DecimalMin(value = "0", message = "准备工时不能为负")
    private BigDecimal setupHours;

    /** 工序说明 */
    @Size(max = 500, message = "工序说明长度不能超过 500")
    private String description;
}
