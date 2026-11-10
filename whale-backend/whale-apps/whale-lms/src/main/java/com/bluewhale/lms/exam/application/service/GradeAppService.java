package com.bluewhale.lms.exam.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.exam.application.command.GradeCommand;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.GradeView;
import com.bluewhale.lms.exam.application.view.ScoreView;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;

/**
 * 人工判分应用服务（M3-2）：主观题待判队列、判分明细、提交判分。
 *
 * @author BlueWhale
 */
public interface GradeAppService {

    /**
     * 待判队列分页（已交卷且存在未判题目）。
     *
     * @param query 查询条件（可按考试过滤）
     * @param actor 判分人
     * @return 分页结果
     */
    PageResult<AttemptView> pending(AttemptQuery query, LmsActor actor);

    /**
     * 判分明细（含标准答案，仅判分人可见）。
     *
     * @param attemptId 作答记录 ID
     * @param actor 判分人
     * @return 判分视图
     */
    GradeView detail(Long attemptId, LmsActor actor);

    /**
     * 提交判分（仅主观题，重判生成新成绩版本）。
     *
     * @param attemptId 作答记录 ID
     * @param command 判分命令
     * @param actor 判分人
     * @return 新的成绩版本
     */
    ScoreView grade(Long attemptId, GradeCommand command, LmsActor actor);
}
