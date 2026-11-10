package com.bluewhale.lms.exam.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.lms.exam.infrastructure.persistence.entity.QuestionOptionEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 题目选项 Mapper。
 *
 * <p>选项是题目聚合内的子实体，保存题目时整体替换，因此这里用物理删除：
 * 若沿用逻辑删除，{@code uk_lr_question_option(tenant_id, question_id, option_key)}
 * 会被已删行占用，导致「删掉 B 选项后再加回 B」直接撞唯一键。</p>
 *
 * @author BlueWhale
 */
public interface QuestionOptionMapper extends BaseMapper<QuestionOptionEntity> {

    /**
     * 物理删除某题的全部选项（保存题目时整体替换用）。
     *
     * @param questionId 题目 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM lr_question_option WHERE question_id = #{questionId}")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
