package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 返工单视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesReworkOrderVO {

    /** 返工单 ID */
    private Long id;

    /** 返工单号 */
    private String reworkNo;

    /** NCR ID */
    private Long ncrId;

    /** NCR 编号 */
    private String ncrNo;

    /** 处置 ID */
    private Long dispositionId;

    /** 工单 ID */
    private Long workOrderId;

    /** 工单编码 */
    private String workOrderCode;

    /** 工序任务 ID */
    private Long execTaskId;

    /** 工序任务号 */
    private String execTaskNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 批次号 */
    private String batchNo;

    /** 返工数量 */
    private BigDecimal quantity;

    /** 状态 */
    private String status;

    /** 返工原因 */
    private String reason;

    /** 返工人 */
    private String operator;

    /** 开工时间 */
    private LocalDateTime startedAt;

    /** 完工时间 */
    private LocalDateTime completedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
