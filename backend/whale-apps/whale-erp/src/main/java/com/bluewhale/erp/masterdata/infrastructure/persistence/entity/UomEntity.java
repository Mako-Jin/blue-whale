package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计量单位实体，对应表 {@code erp_uom}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_uom")
public class UomEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 单位编码 */
    private String code;

    /** 单位名称 */
    private String name;

    /** 数量小数位 */
    private Integer precisionNo;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
