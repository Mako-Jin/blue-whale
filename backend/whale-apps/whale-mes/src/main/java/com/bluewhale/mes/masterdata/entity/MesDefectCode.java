package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 缺陷代码主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_defect_code")
public class MesDefectCode extends MesMasterDataEntity {

    /** 缺陷分类 */
    private String category;
}
