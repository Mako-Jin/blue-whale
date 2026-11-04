package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 检验任务详情（任务 + 结果 + 不良）
 *
 * @author BlueWhale
 */
@Data
public class MesInspectionTaskDetailVO {

    /** 检验任务 */
    private MesInspectionTaskVO task;

    /** 检验结果 */
    private List<MesInspectionResultVO> results;

    /** 检验不良 */
    private List<MesInspectionDefectVO> defects;

    /** 计划检验项数 */
    private Integer planItemCount;

    /** 已录入检验项数 */
    private Integer completedItemCount;

    /** 是否具备判定条件 */
    private Boolean judgeReady;

    /** 查询时间 */
    private LocalDateTime queryTime;
}
