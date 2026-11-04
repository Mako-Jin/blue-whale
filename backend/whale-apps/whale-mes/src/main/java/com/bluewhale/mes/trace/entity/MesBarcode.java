package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 条码实体，对应表 {@code mes_tr_barcode}。
 *
 * <p>条码值租户内唯一，且一个条码只绑定一个批次；{@code lastScanNo} 记录最近一次扫码幂等键，
 * 相同幂等键重复扫码不再产生副作用。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_barcode")
public class MesBarcode extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 条码值（租户内唯一） */
    private String barcodeValue;

    /** 条码规则 ID */
    private Long ruleId;

    /** 绑定批次 ID */
    private Long batchId;

    /** 绑定批次号快照 */
    private String batchNo;

    /** 绑定对象：BATCH */
    private String objectType;

    /** 物料编码快照 */
    private String itemCode;

    /** 状态：ACTIVE / VOID */
    private String status;

    /** 打印载荷（标签系统消费） */
    private String printPayload;

    /** 最近一次扫码幂等键 */
    private String lastScanNo;

    /** 最近扫码时间 */
    private LocalDateTime lastScanTime;

    /** 累计扫码次数 */
    private Integer scanCount;

    /** 备注 */
    private String remark;
}
