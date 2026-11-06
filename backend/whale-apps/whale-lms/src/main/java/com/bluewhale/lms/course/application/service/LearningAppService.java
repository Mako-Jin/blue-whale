package com.bluewhale.lms.course.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.course.application.command.ProgressReportCommand;
import com.bluewhale.lms.course.application.view.EnrollmentView;

/**
 * 学习应用服务（报名 / 进度）。
 *
 * @author BlueWhale
 */
public interface LearningAppService {

    /**
     * 报名课程（幂等：重复报名返回既有记录）。
     *
     * @param courseId 课程 ID
     * @param actor 操作人
     * @return 学习记录视图
     */
    EnrollmentView enroll(Long courseId, LmsActor actor);

    /**
     * 我的学习分页。
     *
     * @param current 页码
     * @param size 条数
     * @param actor 操作人
     * @return 学习记录分页
     */
    PageResult<EnrollmentView> myPage(long current, long size, LmsActor actor);

    /**
     * 上报章节学习进度（只增不减）。
     *
     * @param courseId 课程 ID
     * @param command 进度命令
     * @param actor 操作人
     * @return 学习记录视图（含整体进度）
     */
    EnrollmentView reportProgress(Long courseId, ProgressReportCommand command, LmsActor actor);
}
