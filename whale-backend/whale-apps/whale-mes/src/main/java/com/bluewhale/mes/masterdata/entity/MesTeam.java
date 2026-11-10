package com.bluewhale.mes.masterdata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MES 班组主数据
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_md_team")
public class MesTeam extends MesMasterDataEntity {

    /** 所属车间 ID */
    private Long workshopId;

    /** 班组长 */
    private String leaderName;
}
