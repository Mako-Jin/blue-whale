package com.bluewhale.lms.credit.domain.repository;

import com.bluewhale.lms.credit.domain.model.CreditRule;
import com.bluewhale.lms.credit.domain.model.CreditRuleQuery;
import com.bluewhale.lms.credit.domain.model.CreditSource;

import java.util.List;
import java.util.Optional;

/**
 * 学分规则仓储。
 *
 * @author BlueWhale
 */
public interface CreditRuleRepository {

    /**
     * 分页查询规则。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 规则列表
     */
    List<CreditRule> page(CreditRuleQuery query, long offset, int size);

    /**
     * 统计规则总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CreditRuleQuery query);

    /**
     * 按 ID 查询。
     *
     * @param id 规则 ID
     * @return 规则
     */
    Optional<CreditRule> findById(Long id);

    /**
     * 按编码查询（编码冲突校验用）。
     *
     * @param ruleCode 归一后的规则编码
     * @return 规则
     */
    Optional<CreditRule> findByCode(String ruleCode);

    /**
     * 按来源与匹配对象查询（唯一性冲突校验用，不限状态）。
     *
     * @param sourceType 来源类型
     * @param matchId 匹配对象 ID（0 为通配）
     * @return 规则
     */
    Optional<CreditRule> findByMatch(CreditSource sourceType, Long matchId);

    /**
     * 查询记账应使用的规则：先精确匹配来源对象，再回落该来源的通配规则。
     *
     * <p>只返回启用状态的规则。停用的精确规则不会再往下落到通配规则——
     * 停用表达的意图是「这个对象暂不计分」，回落到通配等于把停用当没发生。</p>
     *
     * @param sourceType 来源类型
     * @param sourceId 来源业务 ID
     * @return 命中的规则
     */
    Optional<CreditRule> findEffective(CreditSource sourceType, Long sourceId);

    /**
     * 保存规则。
     *
     * @param rule 规则
     * @return 主键
     */
    Long save(CreditRule rule);

    /**
     * 逻辑删除规则。
     *
     * @param id 规则 ID
     */
    void deleteById(Long id);
}
