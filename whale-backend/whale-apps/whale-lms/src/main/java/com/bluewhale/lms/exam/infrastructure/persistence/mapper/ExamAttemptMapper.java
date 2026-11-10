package com.bluewhale.lms.exam.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.ExamAttemptEntity;
import org.apache.ibatis.annotations.Select;

/**
 * 作答记录 Mapper。
 *
 * <p>看板的两个「去重到人」计数走原生 SQL：参考与通过都要按人去重
 * （一人补考三次只算一人一次），Wrapper 只能数记录条数，会把补考人数算成多人。</p>
 *
 * @author BlueWhale
 */
public interface ExamAttemptMapper extends BaseMapper<ExamAttemptEntity> {

    /**
     * 参考人数：已交卷（含已判分）的去重学员数。
     *
     * <p>不含 {@code IN_PROGRESS}（还在答题，成绩未定）与 {@code EXPIRED}
     * （超时未交，没有卷面可判）。</p>
     *
     * @return 去重学员数
     */
    @Select("""
            SELECT COUNT(DISTINCT user_id) FROM lr_exam_attempt
             WHERE deleted = 0 AND status IN ('SUBMITTED', 'GRADED')
            """)
    long countDistinctTakers();

    /**
     * 通过人数：存在及格成绩的去重学员数。
     *
     * <p>不取「最新一版成绩」而取「任意一版及格」：补考通过就是通过，
     * 若按最新版统计，重判一次把及格改不及格的人会从通过名单里消失。</p>
     *
     * @return 去重学员数
     */
    @Select("""
            SELECT COUNT(DISTINCT a.user_id) FROM lr_score s
              JOIN lr_exam_attempt a ON a.id = s.attempt_id
             WHERE s.deleted = 0 AND s.passed = 1
               AND a.deleted = 0 AND a.status IN ('SUBMITTED', 'GRADED')
            """)
    long countDistinctPassed();
}
