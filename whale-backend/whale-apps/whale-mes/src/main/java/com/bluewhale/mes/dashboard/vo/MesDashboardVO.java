package com.bluewhale.mes.dashboard.vo;

import com.bluewhale.mes.dispatch.vo.MesBoardVO;
import com.bluewhale.mes.quality.vo.MesQualityBoardVO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MES 综合看板聚合视图
 *
 * <p>生产域直接复用生产看板服务、质量域复用质量看板服务，保证与各自独立看板口径一致；
 * 设备域与人员域按各自模块口径聚合（设备当前状态 + 当日 OEE 快照、人员当日工时与当月绩效）。</p>
 *
 * @author BlueWhale
 */
@Data
public class MesDashboardVO {

    /** 统计日期 */
    private LocalDate boardDate;

    /** 产线 ID（可选筛选） */
    private Long lineId;

    /** 班组 ID（可选筛选） */
    private Long teamId;

    /** 快照生成时间 */
    private LocalDateTime generatedAt;

    /** 生产域 */
    private MesBoardVO production;

    /** 质量域 */
    private MesQualityBoardVO quality;

    /** 设备域 */
    private MesEquipmentBoardVO equipment;

    /** 人员域 */
    private MesWorkforceBoardVO workforce;
}
