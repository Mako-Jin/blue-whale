package com.bluewhale.lms.exam.application.view;

import lombok.Data;

/**
 * 考试域指标（看板取数用）。
 *
 * @author BlueWhale
 */
@Data
public class ExamMetricsView {

    /** 参考人数（已交卷的去重学员数） */
    private long takerTotal;

    /** 通过人数（存在及格成绩的去重学员数） */
    private long passedTotal;
}
