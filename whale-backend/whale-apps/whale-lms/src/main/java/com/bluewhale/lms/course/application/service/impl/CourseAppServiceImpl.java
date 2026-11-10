package com.bluewhale.lms.course.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.course.application.command.ChapterSaveCommand;
import com.bluewhale.lms.course.application.command.CourseSaveCommand;
import com.bluewhale.lms.course.application.command.CourseStatusCommand;
import com.bluewhale.lms.course.application.command.WareSaveCommand;
import com.bluewhale.lms.course.application.service.CourseAppService;
import com.bluewhale.lms.course.application.view.CourseDetailView;
import com.bluewhale.lms.course.application.view.CourseView;
import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.CourseQuery;
import com.bluewhale.lms.course.domain.model.CourseStatus;
import com.bluewhale.lms.course.domain.model.Courseware;
import com.bluewhale.lms.course.domain.model.WareType;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 课程管理应用服务实现。
 *
 * <p>发布状态机（REQ-FUNC-087）：DRAFT → PUBLISHED → ARCHIVED；发布前至少 1 个章节；
 * 发布态课程的内容（章节 / 课件 / 头信息）不可变更，需先归档下架；
 * 课件字节归 {@code whale-file}，本服务只保存引用。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CourseAppServiceImpl implements CourseAppService {

    /** 发布动作 */
    private static final String ACTION_PUBLISH = "PUBLISH";

    /** 归档动作 */
    private static final String ACTION_ARCHIVE = "ARCHIVE";

    private final CourseRepository courseRepository;

    private final LmsProperties properties;

    @Override
    public PageResult<CourseView> page(CourseQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize(), 1L), properties.getCoursePageLimit());
        query.setCurrent(current);
        query.setSize(size);
        long total = courseRepository.count(query);
        List<CourseView> views = courseRepository.page(query).stream().map(CourseView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public CourseDetailView detail(Long id, LmsActor actor) {
        assertGate();
        Course course = requireCourse(id);
        List<CourseChapter> chapters = courseRepository.listChapters(id);
        List<Courseware> wares = courseRepository.listWares(id);
        return CourseDetailView.from(course, chapters, wares);
    }

    @Override
    @Transactional
    public Long create(CourseSaveCommand command, LmsActor actor) {
        assertGate();
        String code = Course.normalizeCode(command.getCourseCode());
        courseRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_CODE_CONFLICT);
        });
        Course course = new Course();
        course.setCourseCode(code);
        applyHeader(course, command);
        course.setStatus(CourseStatus.DRAFT);
        return courseRepository.save(course);
    }

    @Override
    @Transactional
    public void update(Long id, CourseSaveCommand command, LmsActor actor) {
        assertGate();
        Course course = requireCourse(id);
        if (!course.getStatus().canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "发布态课程不可编辑，请先归档下架");
        }
        applyHeader(course, command);
        courseRepository.save(course);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, CourseStatusCommand command, LmsActor actor) {
        assertGate();
        Course course = requireCourse(id);
        String action = command.getAction() == null ? "" : command.getAction().trim().toUpperCase(Locale.ROOT);
        switch (action) {
            case ACTION_PUBLISH -> course.publish(courseRepository.countChapters(id));
            case ACTION_ARCHIVE -> course.archive();
            default -> throw LmsErrorCode.exception(LmsErrorCode.COURSE_STATE_CONFLICT,
                    "状态动作仅支持 PUBLISH / ARCHIVE");
        }
        courseRepository.save(course);
    }

    @Override
    @Transactional
    public void delete(Long id, LmsActor actor) {
        assertGate();
        Course course = requireCourse(id);
        if (!course.getStatus().canDelete()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSE_STATE_CONFLICT,
                    "发布态课程不可删除，请先归档下架");
        }
        for (CourseChapter chapter : courseRepository.listChapters(id)) {
            courseRepository.listWaresByChapter(chapter.getId()).forEach(ware ->
                    courseRepository.deleteWare(ware.getId()));
            courseRepository.deleteChapter(chapter.getId());
        }
        courseRepository.deleteById(id);
    }

    @Override
    @Transactional
    public Long addChapter(Long courseId, ChapterSaveCommand command, LmsActor actor) {
        assertGate();
        Course course = requireCourse(courseId);
        if (!course.getStatus().canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "发布态课程不可变更章节，请先归档下架");
        }
        if (courseRepository.countChapters(courseId) >= properties.getChapterLimit()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "章节数已达上限：" + properties.getChapterLimit());
        }
        CourseChapter chapter = new CourseChapter();
        chapter.setCourseId(courseId);
        chapter.setTitle(command.getTitle().trim());
        chapter.setRemark(command.getRemark());
        chapter.setSortNo(command.getSortNo() != null ? command.getSortNo()
                : (int) courseRepository.countChapters(courseId) + 1);
        return courseRepository.saveChapter(chapter);
    }

    @Override
    @Transactional
    public void removeChapter(Long chapterId, LmsActor actor) {
        assertGate();
        CourseChapter chapter = requireChapter(chapterId);
        Course course = requireCourse(chapter.getCourseId());
        if (!course.getStatus().canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "发布态课程不可删除章节，请先归档下架");
        }
        courseRepository.listWaresByChapter(chapterId).forEach(ware ->
                courseRepository.deleteWare(ware.getId()));
        courseRepository.deleteChapter(chapterId);
    }

    @Override
    @Transactional
    public Long addWare(Long chapterId, WareSaveCommand command, LmsActor actor) {
        assertGate();
        CourseChapter chapter = requireChapter(chapterId);
        Course course = requireCourse(chapter.getCourseId());
        if (!course.getStatus().canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "发布态课程不可变更课件，请先归档下架");
        }
        if (courseRepository.listWaresByChapter(chapterId).size() >= properties.getWareLimitPerChapter()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "单章节课件数已达上限：" + properties.getWareLimitPerChapter());
        }
        Courseware ware = new Courseware();
        ware.setCourseId(chapter.getCourseId());
        ware.setChapterId(chapterId);
        ware.setName(command.getName().trim());
        ware.setWareType(WareType.of(command.getWareType()));
        ware.setFileRefId(StringUtils.hasText(command.getFileRefId()) ? command.getFileRefId().trim() : null);
        ware.setLinkUrl(StringUtils.hasText(command.getLinkUrl()) ? command.getLinkUrl().trim() : null);
        ware.validate();
        ware.setSortNo(command.getSortNo() != null ? command.getSortNo()
                : courseRepository.listWaresByChapter(chapterId).size() + 1);
        return courseRepository.saveWare(ware);
    }

    @Override
    @Transactional
    public void removeWare(Long wareId, LmsActor actor) {
        assertGate();
        Courseware ware = courseRepository.findWareById(wareId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.COURSE_NOT_FOUND, "课件不存在"));
        Course course = requireCourse(ware.getCourseId());
        if (!course.getStatus().canEditContent()) {
            throw LmsErrorCode.exception(LmsErrorCode.COURSEWARE_STATE_CONFLICT,
                    "发布态课程不可删除课件，请先归档下架");
        }
        courseRepository.deleteWare(wareId);
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载课程，不存在按 404 处理。
     *
     * @param id 课程 ID
     * @return 课程
     */
    private Course requireCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.COURSE_NOT_FOUND));
    }

    /**
     * 加载章节，不存在按 404 处理。
     *
     * @param chapterId 章节 ID
     * @return 章节
     */
    private CourseChapter requireChapter(Long chapterId) {
        return courseRepository.findChapterById(chapterId)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.COURSE_NOT_FOUND, "章节不存在"));
    }

    /**
     * 应用课程头信息。
     *
     * @param course 课程
     * @param command 保存命令
     */
    private void applyHeader(Course course, CourseSaveCommand command) {
        course.setTitle(command.getTitle().trim());
        course.setCategory(StringUtils.hasText(command.getCategory()) ? command.getCategory().trim() : null);
        course.setDescription(command.getDescription());
        course.setLecturer(StringUtils.hasText(command.getLecturer()) ? command.getLecturer().trim() : null);
    }
}
