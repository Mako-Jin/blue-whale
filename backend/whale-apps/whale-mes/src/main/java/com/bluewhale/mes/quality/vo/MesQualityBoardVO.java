package com.bluewhale.mes.quality.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 质量看板聚合视图
 *
 * @author BlueWhale
 */
@Data
public class MesQualityBoardVO {

    /** 统计日期 */
    private String boardDate;

    /** 当日检验任务数 */
    private Long inspectionTaskCount;

    /** 当日待检任务数 */
    private Long pendingTaskCount;

    /** 当日不合格任务数 */
    private Long failedTaskCount;

    /** 首件通过率（0–100） */
    private BigDecimal firstArticlePassRate;

    /** 未关闭 NCR 数 */
    private Long ncrOpenCount;

    /** 已关闭 NCR 数 */
    private Long ncrClosedCount;

    /** 处置单数 */
    private Long dispositionCount;

    /** 未完成返工单数 */
    private Long reworkOpenCount;

    /** 命中 SPC 规则的样本数 */
    private Long spcAlertCount;
}
