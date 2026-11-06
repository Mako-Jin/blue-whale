package com.bluewhale.lms.exam.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.exam.application.command.AttemptSaveCommand;
import com.bluewhale.lms.exam.application.view.AttemptPaperView;
import com.bluewhale.lms.exam.application.view.AttemptResultView;
import com.bluewhale.lms.exam.application.view.AttemptView;
import com.bluewhale.lms.exam.application.view.ExamMetricsView;
import com.bluewhale.lms.exam.application.view.ScoreView;
import com.bluewhale.lms.exam.domain.model.AttemptQuery;

/**
 * 作答应用服务（M3-2）：开始作答、保存答案、交卷判分、查成绩。
 *
 * @author BlueWhale
 */
public interface AttemptAppService {

    /**
     * 开始作答（同一场考试存在未交卷记录时直接复用）。
     *
     * @param examId 考试 ID
     * @param actor 学员
     * @return 作答记录 ID
     */
    Long start(Long examId, LmsActor actor);

    /**
     * 获取作答卷面（不含标准答案与选项正确标记）。
     *
     * @param attemptId 作答记录 ID
     * @param actor 学员
     * @return 卷面
     */
    AttemptPaperView paper(Long attemptId, LmsActor actor);

    /**
     * 保存答案（未提交的题保持原答案）。
     *
     * @param attemptId 作答记录 ID
     * @param command 保存命令
     * @param actor 学员
     * @return 本次落库的题数
     */
    int save(Long attemptId, AttemptSaveCommand command, LmsActor actor);

    /**
     * 交卷：自动判客观题、生成成绩版本，主观题进入待判队列。
     *
     * @param attemptId 作答记录 ID
     * @param actor 学员
     * @return 交卷结果
     */
    AttemptResultView submit(Long attemptId, LmsActor actor);

    /**
     * 我的作答记录分页。
     *
     * @param query 查询条件（学员维度由服务端强制覆盖）
     * @param actor 学员
     * @return 分页结果
     */
    PageResult<AttemptView> mine(AttemptQuery query, LmsActor actor);

    /**
     * 查询某次作答的结果（仅本人）。
     *
     * @param attemptId 作答记录 ID
     * @param actor 学员
     * @return 结果
     */
    AttemptResultView result(Long attemptId, LmsActor actor);

    /**
     * 我的成绩分页（按判分时间倒序）。
     *
     * @param current 页码
     * @param size 条数
     * @param actor 学员
     * @return 分页结果
     */
    PageResult<ScoreView> myScores(long current, long size, LmsActor actor);

    /**
     * 考试域指标（看板取数用）。
     *
     * <p>不带操作人参数：供 analytics 上下文内部调用，对外可见性由看板接口的权限码控制。</p>
     *
     * @return 考试域指标
     */
    ExamMetricsView metrics();
}
