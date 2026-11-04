package com.bluewhale.mes.trace.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 追溯节点视图对象（正/反向追溯逐层返回）
 *
 * @author BlueWhale
 */
@Data
public class MesTraceNodeVO {

    /** 层级（起点为 0） */
    private Integer level;

    /** 批次 ID */
    private Long batchId;

    /** 批次号 */
    private String batchNo;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 批次类型 */
    private String batchType;

    /** 批次状态 */
    private String status;

    /** 与上一层级的消耗/产出数量 */
    private BigDecimal quantity;

    /** 计量单位 */
    private String uom;

    /** 关系方向：PARENT 上游 / CHILD 下游 */
    private String relation;
}
