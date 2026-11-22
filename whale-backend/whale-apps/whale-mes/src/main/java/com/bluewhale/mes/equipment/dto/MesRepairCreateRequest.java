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
    @NotNull(message = "{validation.mes.mes-repair.equipment-id-required}")
    private Long equipmentId;

    /** 故障描述 */
    @NotBlank(message = "{validation.mes.mes-repair.fault-desc-required}")
    @Size(max = 255, message = "{validation.mes.mes-repair.fault-desc-size}")
    private String faultDesc;

    /** 严重度：MINOR / MAJOR / CRITICAL */
    @Pattern(regexp = "MINOR|MAJOR|CRITICAL", message = "{validation.mes.mes-repair.severity-pattern}")
    private String severity;

    /** 报修人 */
    @Size(max = 64, message = "{validation.mes.mes-repair.reported-by-size}")
    private String reportedBy;

    /** 报修时间（空则当前时间） */
    private LocalDateTime reportedAt;

    /** 备注 */
    @Size(max = 255, message = "{validation.mes.mes-repair.remark-size}")
    private String remark;
}
