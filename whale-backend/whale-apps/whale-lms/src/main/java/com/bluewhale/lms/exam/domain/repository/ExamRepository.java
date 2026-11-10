package com.bluewhale.lms.exam.domain.repository;

import com.bluewhale.lms.exam.domain.model.Exam;
import com.bluewhale.lms.exam.domain.model.ExamQuery;

import java.util.List;
import java.util.Optional;

/**
 * 考试仓储。
 *
 * @author BlueWhale
 */
public interface ExamRepository {

    /**
     * 分页查询考试。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 考试列表
     */
    List<Exam> page(ExamQuery query, long offset, int size);

    /**
     * 统计考试总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ExamQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 考试 ID
     * @return 考试
     */
    Optional<Exam> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param examCode 规范化编码
     * @return 考试
     */
    Optional<Exam> findByCode(String examCode);

    /**
     * 保存考试。
     *
     * @param exam 考试
     * @return 主键
     */
    Long save(Exam exam);

    /**
     * 逻辑删除考试。
     *
     * @param id 考试 ID
     */
    void deleteById(Long id);

    /**
     * 统计引用某试卷的考试数（试卷归档前的引用校验）。
     *
     * @param paperId 试卷 ID
     * @return 引用数
     */
    long countByPaperId(Long paperId);
}
