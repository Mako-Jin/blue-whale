package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 车间主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_workshop")
public class MesWorkshop extends MesMasterDataEntity {

    /** 车间位置 */
    private String address;

    /** 车间负责人 */
    private String managerName;
}
