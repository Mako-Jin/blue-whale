package com.bluewhale.lms.course.application.view;

import lombok.Data;

/**
 * 学习域指标（看板取数用）。
 *
 * <p>只回计数，不回明细：看板要的是「多少人学过、多少人学完」，
 * 明细仍归学习记录接口，避免看板顺手把全量学习数据复制一份出去。</p>
 *
 * @author BlueWhale
 */
@Data
public class LearningMetricsView {

    /** 报名记录总数 */
    private long enrollTotal;

    /** 学习人数（去重学员数） */
    private long learnerTotal;

    /** 完成人数（名下报名涉及的章节全部学完的去重学员数） */
    private long learnerCompleted;
}
