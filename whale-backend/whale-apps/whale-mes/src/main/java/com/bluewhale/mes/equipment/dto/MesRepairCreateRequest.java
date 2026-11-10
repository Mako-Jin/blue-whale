package com.bluewhale.mes.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修工单创建请求
 *
 * @author BlueWhale
 */
@Data
public class MesRepairCreateRequest {

    /** 设备 ID */
    @NotNull(message = "设备不能为空")
    private Long equipmentId;

    /** 故障描述 */
    @NotBlank(message = "故障描述不能为空")
    @Size(max = 255, message = "故障描述长度不能超过 255")
    private String faultDesc;

    /** 严重度：MINOR / MAJOR / CRITICAL */
    @Pattern(regexp = "MINOR|MAJOR|CRITICAL", message = "严重度不合法")
    private String severity;

    /** 报修人 */
    @Size(max = 64, message = "报修人长度不能超过 64")
    private String reportedBy;

    /** 报修时间（空则当前时间） */
    private LocalDateTime reportedAt;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
