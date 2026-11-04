package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 质量检验项实体，对应表 {@code erp_qc_inspection_item}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_inspection_item")
public class InspectionItemEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 检验单主键 */
    private Long orderId;

    /** 行号 */
    private Integer lineNo;

    /** 检验项名称 */
    private String itemName;

    /** 标准值 */
    private String standardValue;

    /** 实测值 */
    private String actualValue;

    /** 结论 */
    private String result;

    /** 备注 */
    private String remark;
}
