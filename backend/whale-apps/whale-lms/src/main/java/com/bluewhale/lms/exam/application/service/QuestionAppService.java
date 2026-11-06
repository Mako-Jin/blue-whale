package com.bluewhale.lms.exam.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.exam.application.command.QuestionSaveCommand;
import com.bluewhale.lms.exam.application.command.QuestionStatusCommand;
import com.bluewhale.lms.exam.application.view.QuestionView;
import com.bluewhale.lms.exam.domain.model.QuestionQuery;

/**
 * 题库应用服务（M3-1）。
 *
 * @author BlueWhale
 */
public interface QuestionAppService {

    /**
     * 题目分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<QuestionView> page(QuestionQuery query, LmsActor actor);

    /**
     * 题目详情。
     *
     * @param id 题目 ID
     * @param actor 操作人
     * @return 题目
     */
    QuestionView detail(Long id, LmsActor actor);

    /**
     * 新建题目（默认草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 题目 ID
     */
    Long create(QuestionSaveCommand command, LmsActor actor);

    /**
     * 编辑题目。
     *
     * @param id 题目 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, QuestionSaveCommand command, LmsActor actor);

    /**
     * 启用 / 停用题目（启用前做完整性校验）。
     *
     * @param id 题目 ID
     * @param command 状态命令
     * @param actor 操作人
     */
    void changeStatus(Long id, QuestionStatusCommand command, LmsActor actor);

    /**
     * 逻辑删除题目（被试卷引用时拒绝）。
     *
     * @param id 题目 ID
     * @param actor 操作人
     */
    void delete(Long id, LmsActor actor);
}
