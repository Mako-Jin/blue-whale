package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 线边叫料/补货请求实体，对应表 {@code mes_tr_replenishment}。
 *
 * <p>请求号即幂等键：重复提交同一请求号返回已有请求；MES 只记请求，实际调拨由 ERP
 * {@code /erp/scm/transfer} 承接，回填 {@code erpTransferNo}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_replenishment")
public class MesReplenishment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 补货请求号（租户内唯一，幂等键） */
    private String requestNo;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码快照 */
    private String lineCode;

    /** 线边仓编码 */
    private String warehouseCode;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 叫料数量 */
    private BigDecimal quantity;

    /** 计量单位 */
    private String uom;

    /** 状态：REQUESTED / CONFIRMED / CANCELLED */
    private String status;

    /** 叫料原因 */
    private String reason;

    /** 叫料人 */
    private String requestedBy;

    /** 叫料时间 */
    private LocalDateTime requestedAt;

    /** ERP 调拨单号（回传后回填） */
    private String erpTransferNo;

    /** 备注 */
    private String remark;
}
