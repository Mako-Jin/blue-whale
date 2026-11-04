package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 物料批次实体，对应表 {@code mes_tr_batch}。
 *
 * <p>批次是正反追溯的节点：批次号租户内唯一，谱系链接只能追加且不成环，消耗量不得超过投入量。
 * 批次 {@code CLOSED} 后不再允许作为谱系父节点被继续消耗。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_batch")
public class MesMaterialBatch extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 批次号（租户内唯一） */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称快照 */
    private String itemName;

    /** 批次类型：RAW 来料 / SEMI 半成品 / FINISHED 成品 */
    private String batchType;

    /** 来源：PURCHASE 采购 / PRODUCTION 生产 / RETURN 退料 */
    private String sourceType;

    /** 来源工单 ID */
    private Long workOrderId;

    /** 来源工单编码快照 */
    private String workOrderCode;

    /** 供应商编码（来料批次） */
    private String supplierCode;

    /** 批次初始数量 */
    private BigDecimal quantity;

    /** 已被下游消耗数量 */
    private BigDecimal consumedQuantity;

    /** 计量单位 */
    private String uom;

    /** 生产/到货日期 */
    private LocalDate productionDate;

    /** 有效期至 */
    private LocalDate expiryDate;

    /** 状态：ACTIVE / CLOSED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
