package com.bluewhale.erp.qc.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设备档案实体，对应表 {@code erp_qc_equipment}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("erp_qc_equipment")
public class EquipmentEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 设备编码 */
    private String equipmentNo;

    /** 设备名称 */
    private String name;

    /** 设备类型 */
    private String equipmentType;

    /** 安装位置 */
    private String location;

    /** 状态 */
    private String status;

    /** 购置日期 */
    private LocalDate purchaseDate;

    /** 备注 */
    private String remark;
}
