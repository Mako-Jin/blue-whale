package com.bluewhale.lms.exam.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.exam.application.command.PaperGenerateCommand;
import com.bluewhale.lms.exam.application.command.PaperQuestionAddCommand;
import com.bluewhale.lms.exam.application.command.PaperSaveCommand;
import com.bluewhale.lms.exam.application.view.PaperDetailView;
import com.bluewhale.lms.exam.application.view.PaperGenerateResultView;
import com.bluewhale.lms.exam.application.view.PaperView;
import com.bluewhale.lms.exam.domain.model.PaperQuery;

/**
 * 试卷应用服务（M3-1）。
 *
 * @author BlueWhale
 */
public interface PaperAppService {

    /**
     * 试卷分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<PaperView> page(PaperQuery query, LmsActor actor);

    /**
     * 试卷详情（含题目清单）。
     *
     * @param id 试卷 ID
     * @param actor 操作人
     * @return 详情
     */
    PaperDetailView detail(Long id, LmsActor actor);

    /**
     * 新建试卷（草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 试卷 ID
     */
    Long create(PaperSaveCommand command, LmsActor actor);

    /**
     * 编辑试卷（仅草稿）。
     *
     * @param id 试卷 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, PaperSaveCommand command, LmsActor actor);

    /**
     * 逻辑删除试卷（仅草稿，级联题目）。
     *
     * @param id 试卷 ID
     * @param actor 操作人
     */
    void delete(Long id, LmsActor actor);

    /**
     * 手工加题（已存在则更新分值）。
     *
     * @param paperId 试卷 ID
     * @param command 加题命令
     * @param actor 操作人
     */
    void addQuestion(Long paperId, PaperQuestionAddCommand command, LmsActor actor);

    /**
     * 移出题目。
     *
     * @param paperId 试卷 ID
     * @param questionId 题目 ID
     * @param actor 操作人
     */
    void removeQuestion(Long paperId, Long questionId, LmsActor actor);

    /**
     * 规则抽题（可复现：同一 seed + 同一题库 => 同一套题）。
     *
     * @param paperId 试卷 ID
     * @param command 抽题命令
     * @param actor 操作人
     * @return 抽题结果
     */
    PaperGenerateResultView generate(Long paperId, PaperGenerateCommand command, LmsActor actor);

    /**
     * 发布试卷（至少 1 题、总分 > 0、及格分不超总分）。
     *
     * @param paperId 试卷 ID
     * @param actor 操作人
     */
    void publish(Long paperId, LmsActor actor);

    /**
     * 归档试卷（终态只读）。
     *
     * @param paperId 试卷 ID
     * @param actor 操作人
     */
    void archive(Long paperId, LmsActor actor);
}
