package com.bluewhale.mes.collect.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 看板配置实体，对应表 {@code mes_cl_board_config}。
 *
 * <p>看板只读：配置产线、刷新间隔、指标编码与布局；数据由采集样本聚合返回。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_cl_board_config")
public class MesCollectBoardConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 看板编码（租户内唯一） */
    private String boardCode;

    /** 看板名称 */
    private String boardName;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码快照 */
    private String lineCode;

    /** 刷新间隔（秒） */
    private Integer refreshSeconds;

    /** 展示指标编码列表（逗号分隔） */
    private String metricCodes;

    /** 布局配置 */
    private String layout;

    /** 状态：ENABLED / DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
