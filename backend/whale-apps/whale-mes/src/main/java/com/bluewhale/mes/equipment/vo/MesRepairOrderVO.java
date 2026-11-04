package com.bluewhale.mes.equipment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 维修工单视图
 *
 * @author BlueWhale
 */
@Data
public class MesRepairOrderVO {

    /** 工单 ID */
    private Long id;

    /** 维修单号 */
    private String repairNo;

    /** 设备 ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 故障描述 */
    private String faultDesc;

    /** 严重度 */
    private String severity;

    /** 状态 */
    private String status;

    /** 报修人 */
    private String reportedBy;

    /** 报修时间 */
    private LocalDateTime reportedAt;

    /** 维修人 */
    private String assignee;

    /** 派工时间 */
    private LocalDateTime assignedAt;

    /** 开始维修时间 */
    private LocalDateTime startedAt;

    /** 维修说明 */
    private String repairDesc;

    /** 验证人 */
    private String verifiedBy;

    /** 验证时间 */
    private LocalDateTime verifiedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 验证结论 */
    private String verifyConclusion;

    /** 停机时长（分钟） */
    private BigDecimal downtimeMinutes;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
