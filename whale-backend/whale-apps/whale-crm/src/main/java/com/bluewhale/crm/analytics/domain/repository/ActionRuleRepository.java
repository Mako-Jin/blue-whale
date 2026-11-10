package com.bluewhale.crm.analytics.domain.repository;

import com.bluewhale.crm.analytics.domain.model.ActionRule;

import java.util.List;

/**
 * 行动计划规则仓储。
 *
 * @author BlueWhale
 */
public interface ActionRuleRepository {

    /**
     * 查询全部规则（含停用）。
     *
     * @return 规则列表
     */
    List<ActionRule> findAll();

    /**
     * 查询启用规则。
     *
     * @return 启用规则列表
     */
    List<ActionRule> findEnabled();

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 规则，未找到返回 null
     */
    ActionRule findById(Long id);

    /**
     * 按规则编码查询。
     *
     * @param ruleCode 规则编码
     * @return 规则，未找到返回 null
     */
    ActionRule findByCode(String ruleCode);

    /**
     * 新增规则。
     *
     * @param rule 规则
     */
    void insert(ActionRule rule);

    /**
     * 更新规则。
     *
     * @param rule 规则
     */
    void update(ActionRule rule);
}
