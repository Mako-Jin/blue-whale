package com.bluewhale.oa.administrative.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 司机实体，对应表 {@code oa_driver}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_driver")
public class DriverEntity extends BaseEntity {

    /** 姓名 */
    private String name;

    /** 联系电话 */
    private String phone;

    /** 驾驶证号 */
    private String licenseNo;

    /** 准驾车型 */
    private String licenseType;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;
}
