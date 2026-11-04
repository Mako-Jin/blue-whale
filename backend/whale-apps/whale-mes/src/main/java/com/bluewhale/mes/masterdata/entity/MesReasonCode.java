package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 异常/停机原因主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_reason_code")
public class MesReasonCode extends MesMasterDataEntity {

    /** 原因分类 */
    private String category;

    /** 是否影响 OEE 可用率：0 否，1 是 */
    private Integer affectAvailability;
}
