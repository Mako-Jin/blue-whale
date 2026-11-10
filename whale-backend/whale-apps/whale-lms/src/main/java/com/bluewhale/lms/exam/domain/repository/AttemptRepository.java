package com.bluewhale.lms.exam.domain.repository;

import com.bluewhale.lms.exam.domain.model.AttemptQuery;
import com.bluewhale.lms.exam.domain.model.ExamAnswer;
import com.bluewhale.lms.exam.domain.model.ExamAttempt;

import java.util.List;
import java.util.Optional;

/**
 * 作答记录仓储（作答记录 + 作答明细聚合）。
 *
 * @author BlueWhale
 */
public interface AttemptRepository {

    /**
     * 保存作答记录。
     *
     * @param attempt 作答记录
     * @return 主键
     */
    Long save(ExamAttempt attempt);

    /**
     * 按 ID 查询作答记录。
     *
     * @param id 作答记录 ID
     * @return 作答记录
     */
    Optional<ExamAttempt> findById(Long id);

    /**
     * 查询某人某场考试仍在作答中的记录（继续作答复用）。
     *
     * @param examId 考试 ID
     * @param userId 学员用户 ID
     * @return 作答记录
     */
    Optional<ExamAttempt> findInProgress(Long examId, Long userId);

    /**
     * 统计某人某场考试已用作答次数。
     *
     * @param examId 考试 ID
     * @param userId 学员用户 ID
     * @return 已用次数
     */
    long countByExamAndUser(Long examId, Long userId);

    /**
     * 分页查询作答记录。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 作答记录列表
     */
    List<ExamAttempt> page(AttemptQuery query, long offset, int size);

    /**
     * 统计作答记录总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(AttemptQuery query);

    /**
     * 查询作答明细（按呈现次序升序）。
     *
     * @param attemptId 作答记录 ID
     * @return 作答明细
     */
    List<ExamAnswer> listAnswers(Long attemptId);

    /**
     * 查询单题作答明细。
     *
     * @param attemptId 作答记录 ID
     * @param questionId 题目 ID
     * @return 作答明细
     */
    Optional<ExamAnswer> findAnswer(Long attemptId, Long questionId);

    /**
     * 保存作答明细（ID 为空插入）。
     *
     * @param answer 作答明细
     * @return 主键
     */
    Long saveAnswer(ExamAnswer answer);

    /**
     * 统计某次作答中待判题目数。
     *
     * @param attemptId 作答记录 ID
     * @return 待判数
     */
    long countPendingGrade(Long attemptId);

    /**
     * 参考人数（已交卷的去重学员数，看板用）。
     *
     * @return 去重学员数
     */
    long countDistinctTakers();

    /**
     * 通过人数（存在及格成绩的去重学员数，看板用）。
     *
     * @return 去重学员数
     */
    long countDistinctPassed();
}
