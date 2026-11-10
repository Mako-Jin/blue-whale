package com.bluewhale.lms.exam.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.PaperQuestionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 试卷题目 Mapper。
 *
 * <p>与选项同理，试卷题目是试卷聚合内的子实体：重新抽题要能清掉上次的 RULE 题后
 * 重新插入同一道题，因此用物理删除绕过 {@code uk_lr_paper_question} 的唯一键占用。</p>
 *
 * @author BlueWhale
 */
public interface PaperQuestionMapper extends BaseMapper<PaperQuestionEntity> {

    /**
     * 物理删除某试卷的全部题目。
     *
     * @param paperId 试卷 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM lr_paper_question WHERE paper_id = #{paperId}")
    int deleteByPaperId(@Param("paperId") Long paperId);

    /**
     * 物理删除某试卷指定来源的题目（重新抽题前清理）。
     *
     * @param paperId 试卷 ID
     * @param source 来源
     * @return 影响行数
     */
    @Delete("DELETE FROM lr_paper_question WHERE paper_id = #{paperId} AND source = #{source}")
    int deleteByPaperIdAndSource(@Param("paperId") Long paperId, @Param("source") String source);

    /**
     * 物理删除试卷内的某道题（手工移出题目用）。
     *
     * @param paperId 试卷 ID
     * @param questionId 题目 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM lr_paper_question WHERE paper_id = #{paperId} AND question_id = #{questionId}")
    int deleteByPaperIdAndQuestionId(@Param("paperId") Long paperId, @Param("questionId") Long questionId);
}
