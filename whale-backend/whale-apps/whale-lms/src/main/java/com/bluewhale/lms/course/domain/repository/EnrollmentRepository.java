package com.bluewhale.lms.course.domain.repository;

import com.bluewhale.lms.course.domain.model.Enrollment;

import java.util.List;
import java.util.Optional;

/**
 * 学习报名仓储。
 *
 * @author BlueWhale
 */
public interface EnrollmentRepository {

    /**
     * 按课程与学员查询报名（幂等判定用）。
     *
     * @param courseId 课程 ID
     * @param userId 学员用户 ID
     * @return 报名记录
     */
    Optional<Enrollment> findByCourseAndUser(Long courseId, Long userId);

    /**
     * 按 ID 查询。
     *
     * @param id 报名 ID
     * @return 报名记录
     */
    Optional<Enrollment> findById(Long id);

    /**
     * 保存报名。
     *
     * @param enrollment 报名记录
     * @return 主键
     */
    Long save(Enrollment enrollment);

    /**
     * 分页查询某学员的报名。
     *
     * @param userId 学员用户 ID
     * @param offset 偏移量
     * @param size 条数
     * @return 报名列表
     */
    List<Enrollment> pageByUser(Long userId, long offset, int size);

    /**
     * 统计某学员报名总数。
     *
     * @param userId 学员用户 ID
     * @return 总数
     */
    long countByUser(Long userId);

    /**
     * 统计全部报名记录数（看板完成率分母）。
     *
     * @return 报名总数
     */
    long countAll();

    /**
     * 统计学习人数（去重学员数）。
     *
     * @return 去重学员数
     */
    long countDistinctLearners();

    /**
     * 统计完成人数（名下报名涉及的章节全部学完的去重学员数）。
     *
     * @return 完成人数
     */
    long countDistinctCompletedLearners();
}
