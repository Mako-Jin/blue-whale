package com.bluewhale.lms.course.domain.repository;

import com.bluewhale.lms.course.domain.model.LearningProgress;

import java.util.List;
import java.util.Optional;

/**
 * 学习进度仓储。
 *
 * @author BlueWhale
 */
public interface ProgressRepository {

    /**
     * 查询某学习记录下某章节的进度。
     *
     * @param enrollmentId 学习记录 ID
     * @param chapterId 章节 ID
     * @return 进度
     */
    Optional<LearningProgress> find(Long enrollmentId, Long chapterId);

    /**
     * 保存进度（ID 为空插入，否则更新）。
     *
     * @param progress 进度
     * @return 主键
     */
    Long save(LearningProgress progress);

    /**
     * 查询某学习记录的全部章节进度。
     *
     * @param enrollmentId 学习记录 ID
     * @return 进度列表
     */
    List<LearningProgress> listByEnrollment(Long enrollmentId);
}
