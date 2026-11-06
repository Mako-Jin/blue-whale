package com.bluewhale.lms.course.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.course.infrastructure.persistence.entity.EnrollmentEntity;
import org.apache.ibatis.annotations.Select;

/**
 * 学习报名 Mapper。
 *
 * <p>看板取数用的两个去重计数走原生 SQL：MyBatis-Plus 的 Wrapper 拼不出
 * 「该报名的每个章节都已学满」这类集合语义，硬用内存比对就得把全量进度拉回来自己算。</p>
 *
 * @author BlueWhale
 */
public interface EnrollmentMapper extends BaseMapper<EnrollmentEntity> {

    /**
     * 学习人数（有报名记录的去重学员数）。
     *
     * @return 去重学员数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM lr_enrollment WHERE deleted = 0")
    long countDistinctLearners();

    /**
     * 完成人数：名下每个报名涉及的课程章节都已学满的去重学员数。
     *
     * <p>口径要点：</p>
     * <ol>
     *   <li><b>按章节集合判定，不看整体百分比</b>：只看平均进度会把「第八章 100%、其余 0%」
     *       也算成一半完成，而结业要求是章章学满；</li>
     *   <li><b>没有章节的课程不计入分母</b>：草稿期课程无章节，若把它算作「已满足」，
     *       任何人报名一个空课程都会变成「已完成」；</li>
     *   <li><b>去重到人</b>：一人报多门课且都学完只算一个人，否则完成率会超过覆盖率。</li>
     * </ol>
     *
     * @return 完成人数
     */
    @Select("""
            SELECT COUNT(DISTINCT e.user_id) FROM lr_enrollment e
             WHERE e.deleted = 0
               AND EXISTS (SELECT 1 FROM lr_course_chapter c
                            WHERE c.course_id = e.course_id AND c.deleted = 0)
               AND NOT EXISTS (
                   SELECT 1 FROM lr_course_chapter c
                    WHERE c.course_id = e.course_id AND c.deleted = 0
                      AND NOT EXISTS (SELECT 1 FROM lr_progress p
                                       WHERE p.enrollment_id = e.id AND p.chapter_id = c.id
                                         AND p.deleted = 0 AND p.percent >= 100))
            """)
    long countDistinctCompletedLearners();
}
