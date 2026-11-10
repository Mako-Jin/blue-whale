package com.bluewhale.lms.training.application.view;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 培训完成认定汇总（班级维度）。
 *
 * <p>认定口径按「所有适用条件同时满足」计算：班级有场次则要求全部场次已签到，
 * 关联课程则要求课程全部章节完成；两者都无时不可认定。结果按请求实时计算，
 * 不做落库快照，避免签到补录或进度追加后认定结果过期。</p>
 *
 * @author BlueWhale
 */
@Data
public class TrainingCompletionView {

    /** 培训班 ID */
    private Long classId;

    /** 已分配学员总数 */
    private int total;

    /** 已认定完成人数 */
    private int completed;

    /** 完成率（0–100，保留 1 位小数） */
    private BigDecimal completionRate;

    /** 明细 */
    private List<CompletionItemView> items;
}
