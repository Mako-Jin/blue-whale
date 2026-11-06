package com.bluewhale.lms.course.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.lms.common.LmsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 课程发布状态机与进度只增不减的领域规则测试（REQ-FUNC-087 验收口径）。
 *
 * @author BlueWhale
 */
class CourseDomainRuleTest {

    @Test
    @DisplayName("仅 DRAFT 可发布，且发布前至少 1 个章节")
    void publishShouldRequireDraftAndChapter() {
        Course course = draftCourse();
        BizException noChapter = assertThrows(BizException.class, () -> course.publish(0));
        assertEquals(LmsErrorCode.COURSE_STATE_CONFLICT, noChapter.getCode());

        course.publish(1);
        assertEquals(CourseStatus.PUBLISHED, course.getStatus());

        BizException republish = assertThrows(BizException.class, () -> course.publish(1));
        assertEquals(LmsErrorCode.COURSE_STATE_CONFLICT, republish.getCode());
    }

    @Test
    @DisplayName("PUBLISHED 可归档，ARCHIVED 为终态")
    void archiveShouldTerminate() {
        Course course = draftCourse();
        course.archive();
        assertEquals(CourseStatus.ARCHIVED, course.getStatus());
        assertFalse(course.getStatus().canEditContent());

        Course published = draftCourse();
        published.publish(1);
        published.archive();
        assertEquals(CourseStatus.ARCHIVED, published.getStatus());
        BizException reArchive = assertThrows(BizException.class, published::archive);
        assertEquals(LmsErrorCode.COURSE_STATE_CONFLICT, reArchive.getCode());
    }

    @Test
    @DisplayName("发布态课程不可编辑内容、不可删除；草稿与归档可以")
    void contentFreezeRules() {
        Course draft = draftCourse();
        assertTrue(draft.getStatus().canEditContent());
        assertTrue(draft.getStatus().canDelete());

        draft.publish(1);
        assertFalse(draft.getStatus().canEditContent());
        assertFalse(draft.getStatus().canDelete());

        draft.archive();
        assertFalse(draft.getStatus().canEditContent());
        assertTrue(draft.getStatus().canDelete());
    }

    @Test
    @DisplayName("进度合并只增不减")
    void progressMergeShouldKeepMax() {
        LearningProgress progress = new LearningProgress();
        progress.setPercent(40);
        progress.merge(30);
        assertEquals(40, progress.getPercent());
        progress.merge(80);
        assertEquals(80, progress.getPercent());
        progress.merge(80);
        assertEquals(80, progress.getPercent());
    }

    @Test
    @DisplayName("进度越界拒绝")
    void progressOutOfRangeShouldReject() {
        LearningProgress progress = new LearningProgress();
        progress.setPercent(0);
        BizException low = assertThrows(BizException.class, () -> progress.merge(-1));
        assertEquals(LmsErrorCode.PROGRESS_INVALID, low.getCode());
        BizException high = assertThrows(BizException.class, () -> progress.merge(101));
        assertEquals(LmsErrorCode.PROGRESS_INVALID, high.getCode());
    }

    @Test
    @DisplayName("课件类型约束：DOCUMENT/VIDEO 必须携带文件引用，LINK 必须携带 URL")
    void wareValidation() {
        Courseware video = new Courseware();
        video.setName("操作演示");
        video.setWareType(WareType.VIDEO);
        assertThrows(BizException.class, video::validate);

        video.setFileRefId("file-1");
        video.validate();

        Courseware link = new Courseware();
        link.setName("外部资料");
        link.setWareType(WareType.LINK);
        link.setFileRefId(null);
        assertThrows(BizException.class, link::validate);
        link.setLinkUrl("https://example.com");
        link.validate();
    }

    @Test
    @DisplayName("课程编码归一化：去空格并大写")
    void codeShouldBeNormalized() {
        assertEquals("SAFE-01", Course.normalizeCode(" safe-01 "));
        assertThrows(BizException.class, () -> Course.normalizeCode("  "));
    }

    /**
     * 构造草稿课程。
     *
     * @return 草稿课程
     */
    private Course draftCourse() {
        Course course = new Course();
        course.setId(1L);
        course.setCourseCode("SAFE-01");
        course.setTitle("安全生产培训");
        course.setStatus(CourseStatus.DRAFT);
        return course;
    }
}
