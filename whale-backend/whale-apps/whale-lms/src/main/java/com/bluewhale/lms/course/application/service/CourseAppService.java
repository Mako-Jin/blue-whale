package com.bluewhale.lms.course.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.course.application.command.ChapterSaveCommand;
import com.bluewhale.lms.course.application.command.CourseSaveCommand;
import com.bluewhale.lms.course.application.command.CourseStatusCommand;
import com.bluewhale.lms.course.application.command.WareSaveCommand;
import com.bluewhale.lms.course.application.view.CourseDetailView;
import com.bluewhale.lms.course.application.view.CourseView;
import com.bluewhale.lms.course.domain.model.CourseQuery;

/**
 * 课程管理应用服务。
 *
 * @author BlueWhale
 */
public interface CourseAppService {

    /**
     * 课程分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 课程分页
     */
    PageResult<CourseView> page(CourseQuery query, LmsActor actor);

    /**
     * 课程详情（含章节与课件）。
     *
     * @param id 课程 ID
     * @param actor 操作人
     * @return 课程详情
     */
    CourseDetailView detail(Long id, LmsActor actor);

    /**
     * 新建课程。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 课程 ID
     */
    Long create(CourseSaveCommand command, LmsActor actor);

    /**
     * 编辑课程（编码不可变更）。
     *
     * @param id 课程 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, CourseSaveCommand command, LmsActor actor);

    /**
     * 变更课程状态（发布 / 归档）。
     *
     * @param id 课程 ID
     * @param command 状态命令
     * @param actor 操作人
     */
    void changeStatus(Long id, CourseStatusCommand command, LmsActor actor);

    /**
     * 逻辑删除课程（发布态不可删）。
     *
     * @param id 课程 ID
     * @param actor 操作人
     */
    void delete(Long id, LmsActor actor);

    /**
     * 新增章节（发布态不可变更）。
     *
     * @param courseId 课程 ID
     * @param command 章节命令
     * @param actor 操作人
     * @return 章节 ID
     */
    Long addChapter(Long courseId, ChapterSaveCommand command, LmsActor actor);

    /**
     * 删除章节及其课件（发布态不可变更）。
     *
     * @param chapterId 章节 ID
     * @param actor 操作人
     */
    void removeChapter(Long chapterId, LmsActor actor);

    /**
     * 新增课件（发布态不可变更）。
     *
     * @param chapterId 章节 ID
     * @param command 课件命令
     * @param actor 操作人
     * @return 课件 ID
     */
    Long addWare(Long chapterId, WareSaveCommand command, LmsActor actor);

    /**
     * 删除课件（发布态不可变更）。
     *
     * @param wareId 课件 ID
     * @param actor 操作人
     */
    void removeWare(Long wareId, LmsActor actor);
}
