package com.bluewhale.lms.exam.domain.repository;

import com.bluewhale.lms.exam.domain.model.ExamScore;

import java.util.List;
import java.util.Optional;

/**
 * 成绩仓储（版本化留存）。
 *
 * @author BlueWhale
 */
public interface ScoreRepository {

    /**
     * 保存成绩版本。
     *
     * @param score 成绩
     * @return 主键
     */
    Long save(ExamScore score);

    /**
     * 查询某次作答的全部成绩版本（版本号升序）。
     *
     * @param attemptId 作答记录 ID
     * @return 成绩版本列表
     */
    List<ExamScore> listByAttempt(Long attemptId);

    /**
     * 查询某次作答的最新成绩版本。
     *
     * @param attemptId 作答记录 ID
     * @return 成绩
     */
    Optional<ExamScore> findLatest(Long attemptId);

    /**
     * 分页查询某学员的成绩（按判分时间倒序）。
     *
     * @param userId 学员用户 ID
     * @param offset 偏移量
     * @param size 条数
     * @return 成绩列表
     */
    List<ExamScore> pageByUser(Long userId, long offset, int size);

    /**
     * 统计某学员的成绩数。
     *
     * @param userId 学员用户 ID
     * @return 总数
     */
    long countByUser(Long userId);
}
