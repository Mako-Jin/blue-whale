package com.bluewhale.lms.course.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.course.application.command.ChapterSaveCommand;
import com.bluewhale.lms.course.application.command.CourseSaveCommand;
import com.bluewhale.lms.course.application.command.CourseStatusCommand;
import com.bluewhale.lms.course.application.command.WareSaveCommand;
import com.bluewhale.lms.course.domain.model.Course;
import com.bluewhale.lms.course.domain.model.CourseChapter;
import com.bluewhale.lms.course.domain.model.CourseStatus;
import com.bluewhale.lms.course.domain.model.Courseware;
import com.bluewhale.lms.course.domain.model.WareType;
import com.bluewhale.lms.course.domain.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CourseAppServiceImpl} 单元测试：门禁、发布状态机、内容冻结与编码唯一性。
 *
 * @author BlueWhale
 */
class CourseAppServiceImplTest {

    private CourseRepository courseRepository;

    private LmsProperties properties;

    private CourseAppServiceImpl service;

    private final LmsActor admin = new LmsActor(1L, "admin", Set.of("lms_admin"), Set.of());

    @BeforeEach
    void setUp() {
        courseRepository = mock(CourseRepository.class);
        properties = new LmsProperties();
        service = new CourseAppServiceImpl(courseRepository, properties);
    }

    @Test
    @DisplayName("门禁关闭时业务操作全部拒绝")
    void gateClosedShouldReject() {
        properties.setDemandGatePassed(false);
        CourseSaveCommand command = new CourseSaveCommand();
        command.setTitle("安全培训");
        BizException exception = assertThrows(BizException.class, () -> service.create(command, admin));
        assertEquals(LmsErrorCode.GATE_CLOSED, exception.getCode());
    }

    @Test
    @DisplayName("新建课程：编码冲突拒绝，正常创建为草稿态")
    void createShouldCheckCodeConflict() {
        CourseSaveCommand command = new CourseSaveCommand();
        command.setCourseCode("safe-01");
        command.setTitle("安全生产培训");

        when(courseRepository.findByCode("SAFE-01")).thenReturn(Optional.of(new Course()));
        BizException conflict = assertThrows(BizException.class, () -> service.create(command, admin));
        assertEquals(LmsErrorCode.COURSE_CODE_CONFLICT, conflict.getCode());
        verify(courseRepository, never()).save(any(Course.class));

        when(courseRepository.findByCode("SAFE-01")).thenReturn(Optional.empty());
        Long id = service.create(command, admin);
        assertEquals(0L, id);
        verify(courseRepository).save(any(Course.class));
    }

    @Test
    @DisplayName("发布：无章节拒绝；有章节成功进入发布态")
    void publishShouldRequireChapter() {
        Course course = draft();
        CourseStatusCommand command = new CourseStatusCommand();
        command.setAction("PUBLISH");

        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.countChapters(1L)).thenReturn(0L);
        BizException noChapter = assertThrows(BizException.class,
                () -> service.changeStatus(1L, command, admin));
        assertEquals(LmsErrorCode.COURSE_STATE_CONFLICT, noChapter.getCode());

        when(courseRepository.countChapters(1L)).thenReturn(2L);
        service.changeStatus(1L, command, admin);
        assertEquals(CourseStatus.PUBLISHED, course.getStatus());
        verify(courseRepository).save(course);
    }

    @Test
    @DisplayName("发布态课程：编辑头信息 / 加章节 / 删课件均拒绝，提示先归档")
    void publishedCourseShouldFreezeContent() {
        Course course = draft();
        course.publish(1);
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));

        CourseSaveCommand update = new CourseSaveCommand();
        update.setTitle("改名");
        BizException headerEdit = assertThrows(BizException.class, () -> service.update(1L, update, admin));
        assertEquals(LmsErrorCode.COURSEWARE_STATE_CONFLICT, headerEdit.getCode());

        ChapterSaveCommand chapter = new ChapterSaveCommand();
        chapter.setTitle("新章节");
        BizException addChapter = assertThrows(BizException.class,
                () -> service.addChapter(1L, chapter, admin));
        assertEquals(LmsErrorCode.COURSEWARE_STATE_CONFLICT, addChapter.getCode());

        Courseware ware = ware(course.getId());
        when(courseRepository.findWareById(10L)).thenReturn(Optional.of(ware));
        BizException removeWare = assertThrows(BizException.class, () -> service.removeWare(10L, admin));
        assertEquals(LmsErrorCode.COURSEWARE_STATE_CONFLICT, removeWare.getCode());

        BizException deleteCourse = assertThrows(BizException.class, () -> service.delete(1L, admin));
        assertEquals(LmsErrorCode.COURSE_STATE_CONFLICT, deleteCourse.getCode());
    }

    @Test
    @DisplayName("归档后可删除：级联删除章节课件")
    void archivedCourseShouldBeDeletable() {
        Course course = draft();
        course.publish(1);
        course.archive();
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        CourseChapter chapter = new CourseChapter();
        chapter.setId(100L);
        chapter.setCourseId(1L);
        when(courseRepository.listChapters(1L)).thenReturn(List.of(chapter));
        Courseware ware = ware(course.getId());
        when(courseRepository.listWaresByChapter(100L)).thenReturn(List.of(ware));

        service.delete(1L, admin);
        verify(courseRepository).deleteWare(ware.getId());
        verify(courseRepository).deleteChapter(100L);
        verify(courseRepository).deleteById(1L);
    }

    @Test
    @DisplayName("新增课件：类型校验由领域承担，VIDEO 缺文件引用拒绝")
    void addWareShouldValidateType() {
        Course course = draft();
        CourseChapter chapter = new CourseChapter();
        chapter.setId(100L);
        chapter.setCourseId(1L);
        when(courseRepository.findChapterById(100L)).thenReturn(Optional.of(chapter));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
        when(courseRepository.listWaresByChapter(100L)).thenReturn(List.of());

        WareSaveCommand command = new WareSaveCommand();
        command.setName("操作演示");
        command.setWareType("VIDEO");
        BizException invalid = assertThrows(BizException.class, () -> service.addWare(100L, command, admin));
        assertEquals(LmsErrorCode.COURSEWARE_STATE_CONFLICT, invalid.getCode());

        command.setFileRefId("file-1");
        Long wareId = service.addWare(100L, command, admin);
        assertEquals(0L, wareId);
        verify(courseRepository).saveWare(any(Courseware.class));
    }

    /**
     * 构造草稿课程。
     *
     * @return 草稿课程
     */
    private Course draft() {
        Course course = new Course();
        course.setId(1L);
        course.setCourseCode("SAFE-01");
        course.setTitle("安全生产培训");
        course.setStatus(CourseStatus.DRAFT);
        return course;
    }

    /**
     * 构造课件。
     *
     * @param courseId 课程 ID
     * @return 课件
     */
    private Courseware ware(Long courseId) {
        Courseware ware = new Courseware();
        ware.setId(10L);
        ware.setCourseId(courseId);
        ware.setChapterId(100L);
        ware.setName("操作演示");
        ware.setWareType(WareType.VIDEO);
        ware.setFileRefId("file-1");
        return ware;
    }
}
