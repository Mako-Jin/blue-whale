package com.bluewhale.mes.dashboard.service;

import com.bluewhale.mes.dashboard.vo.MesDashboardVO;

/**
 * MES 综合看板应用服务
 *
 * <p>聚合生产 / 质量 / 设备 / 人员四域指标，作为综合看板、SSE 推送与导出的统一数据源。</p>
 *
 * @author BlueWhale
 */
public interface MesDashboardService {

    /**
     * 综合看板快照
     *
     * @param lineId    产线 ID（可选）
     * @param teamId    班组 ID（可选）
     * @param boardDate 统计日期（yyyy-MM-dd，空则当天）
     * @return 四域聚合视图
     */
    MesDashboardVO dashboard(Long lineId, Long teamId, String boardDate);

    /**
     * 综合看板 CSV 导出内容
     *
     * @param lineId    产线 ID（可选）
     * @param teamId    班组 ID（可选）
     * @param boardDate 统计日期（yyyy-MM-dd，空则当天）
     * @return CSV 文本（UTF-8，首行标题）
     */
    String exportCsv(Long lineId, Long teamId, String boardDate);
}
