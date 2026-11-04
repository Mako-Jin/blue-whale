package com.bluewhale.mes.trace.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 批次谱系链接实体，对应表 {@code mes_tr_trace_link}。
 *
 * <p>链接表达“父批次被消耗 → 产出子批次”的关系，只追加不修改；冲销以 {@code REVERSED} 状态标记。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_tr_trace_link")
public class MesTraceLink extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上级批次 ID（被消耗） */
    private Long parentBatchId;

    /** 上级批次号快照 */
    private String parentBatchNo;

    /** 下级批次 ID（产出） */
    private Long childBatchId;

    /** 下级批次号快照 */
    private String childBatchNo;

    /** 链接类型：CONSUME */
    private String linkType;

    /** 消耗数量 */
    private BigDecimal quantity;

    /** 计量单位 */
    private String uom;

    /** 关联工单 ID */
    private Long workOrderId;

    /** 状态：ACTIVE / REVERSED */
    private String status;

    /** 备注 */
    private String remark;
}
