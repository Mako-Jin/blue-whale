package com.bluewhale.lms.exam.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.exam.application.command.ExamSaveCommand;
import com.bluewhale.lms.exam.application.view.ExamView;
import com.bluewhale.lms.exam.domain.model.ExamQuery;

/**
 * 考试应用服务（M3-2）。
 *
 * @author BlueWhale
 */
public interface ExamAppService {

    /**
     * 考试分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<ExamView> page(ExamQuery query, LmsActor actor);

    /**
     * 考试详情（含试卷名称与总分，便于核对及格分）。
     *
     * @param id 考试 ID
     * @param actor 操作人
     * @return 详情
     */
    ExamView detail(Long id, LmsActor actor);

    /**
     * 新建考试（草稿）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 考试 ID
     */
    Long create(ExamSaveCommand command, LmsActor actor);

    /**
     * 编辑考试（仅草稿）。
     *
     * @param id 考试 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, ExamSaveCommand command, LmsActor actor);

    /**
     * 逻辑删除考试（仅草稿）。
     *
     * @param id 考试 ID
     * @param actor 操作人
     */
    void delete(Long id, LmsActor actor);

    /**
     * 发布考试（须引用已发布试卷）。
     *
     * @param id 考试 ID
     * @param actor 操作人
     */
    void publish(Long id, LmsActor actor);

    /**
     * 结课（终态，不再接受作答）。
     *
     * @param id 考试 ID
     * @param actor 操作人
     */
    void close(Long id, LmsActor actor);
}
