package com.bluewhale.lms.training.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.training.application.command.AssignmentBatchCommand;
import com.bluewhale.lms.training.application.command.SessionSaveCommand;
import com.bluewhale.lms.training.application.command.TrainingClassSaveCommand;
import com.bluewhale.lms.training.application.command.TrainingClassStatusCommand;
import com.bluewhale.lms.training.application.view.AssignmentResultView;
import com.bluewhale.lms.training.application.view.LecturerHoursView;
import com.bluewhale.lms.training.application.view.TrainingClassDetailView;
import com.bluewhale.lms.training.application.view.TrainingClassView;
import com.bluewhale.lms.training.application.view.TrainingCompletionView;
import com.bluewhale.lms.training.domain.model.TrainingClassQuery;

import java.util.List;

/**
 * 培训计划应用服务（M2）。
 *
 * @author BlueWhale
 */
public interface TrainingAppService {

    /**
     * 培训班分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<TrainingClassView> page(TrainingClassQuery query, LmsActor actor);

    /**
     * 培训班详情（含场次与统计）。
     *
     * @param classId 培训班 ID
     * @param actor 操作人
     * @return 详情
     */
    TrainingClassDetailView detail(Long classId, LmsActor actor);

    /**
     * 新建培训班。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 培训班 ID
     */
    Long create(TrainingClassSaveCommand command, LmsActor actor);

    /**
     * 编辑培训班（仅草稿可编辑）。
     *
     * @param classId 培训班 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long classId, TrainingClassSaveCommand command, LmsActor actor);

    /**
     * 发布 / 结课。
     *
     * @param classId 培训班 ID
     * @param command 状态命令
     * @param actor 操作人
     */
    void changeStatus(Long classId, TrainingClassStatusCommand command, LmsActor actor);

    /**
     * 逻辑删除培训班（仅草稿可删，级联场次与课时台账）。
     *
     * @param classId 培训班 ID
     * @param actor 操作人
     */
    void delete(Long classId, LmsActor actor);

    /**
     * 新增场次（仅草稿可加）。
     *
     * @param classId 培训班 ID
     * @param command 场次命令
     * @param actor 操作人
     * @return 场次 ID
     */
    Long addSession(Long classId, SessionSaveCommand command, LmsActor actor);

    /**
     * 删除场次（仅草稿可删）。
     *
     * @param sessionId 场次 ID
     * @param actor 操作人
     */
    void removeSession(Long sessionId, LmsActor actor);

    /**
     * 批量必修分配（幂等：已分配学员计入 skipped）。
     *
     * @param classId 培训班 ID
     * @param command 分配命令
     * @param actor 操作人
     * @return 执行结果
     */
    AssignmentResultView assign(Long classId, AssignmentBatchCommand command, LmsActor actor);

    /**
     * 讲师课时台账（按讲师聚合，合计等于各场次之和）。
     *
     * @param classId 培训班 ID
     * @param actor 操作人
     * @return 讲师课时列表
     */
    List<LecturerHoursView> lecturerHours(Long classId, LmsActor actor);

    /**
     * 完成认定（按签到与课程进度实时计算，不落库）。
     *
     * @param classId 培训班 ID
     * @param actor 操作人
     * @return 认定结果
     */
    TrainingCompletionView completion(Long classId, LmsActor actor);
}
