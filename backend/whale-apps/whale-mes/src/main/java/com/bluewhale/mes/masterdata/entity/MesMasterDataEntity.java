package com.bluewhale.mes.masterdata.entity;

import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 车间主数据实体基类
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class MesMasterDataEntity extends BaseEntity {

    /** 编码 */
    private String code;

    /** 名称 */
    private String name;

    /** 状态：ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}
