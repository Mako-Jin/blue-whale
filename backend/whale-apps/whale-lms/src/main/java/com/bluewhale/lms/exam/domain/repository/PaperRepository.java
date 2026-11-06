package com.bluewhale.lms.exam.domain.repository;

import com.bluewhale.lms.exam.domain.model.Paper;
import com.bluewhale.lms.exam.domain.model.PaperQuery;
import com.bluewhale.lms.exam.domain.model.PaperQuestion;

import java.util.List;
import java.util.Optional;

/**
 * 试卷仓储（试卷 + 试卷题目聚合）。
 *
 * @author BlueWhale
 */
public interface PaperRepository {

    /**
     * 分页查询试卷。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 试卷列表
     */
    List<Paper> page(PaperQuery query, long offset, int size);

    /**
     * 统计试卷总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(PaperQuery query);

    /**
     * 按 ID 查询试卷。
     *
     * @param id 试卷 ID
     * @return 试卷
     */
    Optional<Paper> findById(Long id);

    /**
     * 按编码查询试卷。
     *
     * @param paperCode 规范化编码
     * @return 试卷
     */
    Optional<Paper> findByCode(String paperCode);

    /**
     * 保存试卷。
     *
     * @param paper 试卷
     * @return 主键
     */
    Long save(Paper paper);

    /**
     * 逻辑删除试卷。
     *
     * @param id 试卷 ID
     */
    void deleteById(Long id);

    /**
     * 查询试卷题目（按题序升序）。
     *
     * @param paperId 试卷 ID
     * @return 试卷题目列表
     */
    List<PaperQuestion> listQuestions(Long paperId);

    /**
     * 保存试卷题目。
     *
     * @param paperQuestion 试卷题目
     * @return 主键
     */
    Long saveQuestion(PaperQuestion paperQuestion);

    /**
     * 按来源清理试卷题目（重新抽题前清掉上次的 RULE 题）。
     *
     * @param paperId 试卷 ID
     * @param source 来源
     */
    void deleteQuestionsBySource(Long paperId, String source);

    /**
     * 清理试卷全部题目。
     *
     * @param paperId 试卷 ID
     */
    void deleteAllQuestions(Long paperId);

    /**
     * 移出试卷内的某道题。
     *
     * @param paperId 试卷 ID
     * @param questionId 题目 ID
     */
    void deleteQuestion(Long paperId, Long questionId);

    /**
     * 统计引用某题目的试卷数（题目删除前的引用校验）。
     *
     * @param questionId 题目 ID
     * @return 引用数
     */
    long countByQuestionId(Long questionId);
}
