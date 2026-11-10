package com.bluewhale.mes.collect.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 看板配置视图
 *
 * @author BlueWhale
 */
@Data
public class MesCollectBoardVO {

    /** 看板 ID */
    private Long id;

    /** 看板编码 */
    private String boardCode;

    /** 看板名称 */
    private String boardName;

    /** 产线 ID */
    private Long lineId;

    /** 产线编码 */
    private String lineCode;

    /** 刷新间隔（秒） */
    private Integer refreshSeconds;

    /** 指标编码列表 */
    private String metricCodes;

    /** 布局配置 */
    private String layout;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;
}
