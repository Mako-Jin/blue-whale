package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 不合格品记录 NCR 视图对象
 *
 * @author BlueWhale
 */
@Data
public class MesNcrVO {

    /** NCR ID */
    private Long id;

    /** NCR 编号 */
    private String ncrNo;

    /** 来源类型 */
    private String sourceType;

    /** 来源检验任务 ID */
    private Long inspectionTaskId;

    /** 来源检验任务号 */
    private String inspectionTaskNo;

    /** 来源报工记录 ID */
    private Long reportId;

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

    /** 工序编码 */
    private String operationCode;

    /** 缺陷代码 */
    private String defectCode;

    /** 缺陷名称 */
    private String defectName;

    /** 不良数量 */
    private BigDecimal defectQuantity;

    /** 状态 */
    private String status;

    /** 关闭结论 */
    private String conclusion;

    /** 关闭人 */
    private String closedBy;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
