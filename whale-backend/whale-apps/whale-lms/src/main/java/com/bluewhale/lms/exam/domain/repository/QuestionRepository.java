package com.bluewhale.lms.exam.domain.repository;

import com.bluewhale.lms.exam.domain.model.Question;
import com.bluewhale.lms.exam.domain.model.QuestionQuery;

import java.util.List;
import java.util.Optional;

/**
 * 题库仓储（题目 + 选项聚合）。
 *
 * @author BlueWhale
 */
public interface QuestionRepository {

    /**
     * 分页查询题目。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 题目列表
     */
    List<Question> page(QuestionQuery query, long offset, int size);

    /**
     * 统计题目总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(QuestionQuery query);

    /**
     * 按 ID 查询（含选项）。
     *
     * @param id 题目 ID
     * @return 题目
     */
    Optional<Question> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param questionCode 规范化编码
     * @return 题目
     */
    Optional<Question> findByCode(String questionCode);

    /**
     * 保存题目（含选项整体替换）。
     *
     * @param question 题目
     * @return 主键
     */
    Long save(Question question);

    /**
     * 逻辑删除题目。
     *
     * @param id 题目 ID
     */
    void deleteById(Long id);

    /**
     * 抽取候选题目（仅启用状态，条件为空表示不限）。
     *
     * @param category 分类
     * @param type 题型
     * @param difficulty 难度
     * @return 候选题目（不含选项）
     */
    List<Question> listPickCandidates(String category, String type, String difficulty);
}
