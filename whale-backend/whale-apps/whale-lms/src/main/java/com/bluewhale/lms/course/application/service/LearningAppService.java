package com.bluewhale.lms.course.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.course.application.command.ProgressReportCommand;
import com.bluewhale.lms.course.application.view.EnrollmentView;
import com.bluewhale.lms.course.application.view.LearningMetricsView;

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

    /**
     * 学习域指标（看板取数用）。
     *
     * <p>刻意不带操作人参数：这是供 analytics 上下文内部调用的取数方法，不直接对外暴露；
     * 对外可见性由看板接口自己的权限码控制，避免「同一个数在两个入口有两套口径」。</p>
     *
     * @return 学习域指标
     */
    LearningMetricsView metrics();
}
