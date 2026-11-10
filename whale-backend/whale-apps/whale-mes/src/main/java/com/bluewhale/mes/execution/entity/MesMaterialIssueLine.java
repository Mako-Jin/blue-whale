package com.bluewhale.mes.execution.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 领料明细实体，对应表 {@code mes_ex_material_issue_line}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_ex_material_issue_line")
public class MesMaterialIssueLine extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 领料单 ID */
    private Long issueId;

    /** 物料编码 */
    private String itemCode;

    /** 物料名称 */
    private String itemName;

    /** 领用数量 */
    private BigDecimal quantity;

    /** 计量单位 */
    private String uom;

    /** 批次号（M2 条码增强） */
    private String batchNo;
}
