package com.bluewhale.mes.dashboard.dto;

import lombok.Data;

/**
 * 综合看板 SSE 订阅请求
 *
 * <p>推送间隔缺省取服务端配置（默认 10 秒，D14 口径），入参限制 5–60 秒。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesDashboardStreamRequest {

    /** 产线 ID（可选筛选） */
    private Long lineId;

    /** 班组 ID（可选筛选） */
    private Long teamId;

    /** 统计日期（yyyy-MM-dd，空则当天） */
    private String boardDate;

    /** 推送间隔（秒，5–60，空则取服务端默认） */
    private Long intervalSeconds;
}
