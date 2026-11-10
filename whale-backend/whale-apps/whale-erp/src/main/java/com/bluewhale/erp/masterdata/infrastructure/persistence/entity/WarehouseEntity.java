package com.bluewhale.erp.masterdata.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 仓库实体，对应表 {@code erp_warehouse}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_warehouse")
public class WarehouseEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 仓库编码 */
    private String code;

    /** 仓库名称 */
    private String name;

    /** 负责人 */
    private String manager;

    /** 地址 */
    private String address;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
