package com.bluewhale.erp.hr.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织单元实体，对应表 {@code erp_hr_org_unit}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_hr_org_unit")
public class OrgUnitEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 组织编码 */
    private String code;

    /** 组织名称 */
    private String name;

    /** 上级组织编码 */
    private String parentCode;

    /** 负责人 */
    private String manager;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
