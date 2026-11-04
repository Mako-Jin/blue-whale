package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退料单实体，对应表 {@code mes_ex_material_return}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_material_return")
public class MesMaterialReturn extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 退料单号（租户内唯一） */
    private String returnNo;

    /** 工单 ID */
    private Long workOrderId;

    /** 任务 ID */
    private Long taskId;

    /** 原领料单 ID */
    private Long issueId;

    /** 退料仓库编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 退料数量 */
    private BigDecimal quantity;

    /** 计量单位 */
    private String uom;

    /** 批次号 */
    private String batchNo;

    /** 退料原因 */
    private String reason;

    /** 状态：RETURNED */
    private String status;

    /** 退料人 */
    private String returnedBy;

    /** 退料时间 */
    private LocalDateTime returnedAt;
}
