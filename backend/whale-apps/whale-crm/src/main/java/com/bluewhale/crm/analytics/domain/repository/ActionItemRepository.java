package com.bluewhale.crm.analytics.domain.repository;

import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemQuery;

import java.util.List;

/**
 * 行动项仓储。
 *
 * @author BlueWhale
 */
public interface ActionItemRepository {

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 行动项，未找到返回 null
     */
    ActionItem findById(Long id);

    /**
     * 按去重键查询（含已关闭）。
     *
     * @param dedupKey 去重键
     * @return 行动项，未找到返回 null
     */
    ActionItem findByDedupKey(String dedupKey);

    /**
     * 按去重键查询未关闭行动项。
     *
     * @param dedupKey 去重键
     * @return 行动项，未找到返回 null
     */
    ActionItem findOpenByDedupKey(String dedupKey);

    /**
     * 按规则编码查询全部行动项（含已关闭）。
     *
     * @param ruleCode 规则编码
     * @return 行动项列表
     */
    List<ActionItem> findByRuleCode(String ruleCode);

    /**
     * 新增行动项。
     *
     * @param item 行动项
     */
    void insert(ActionItem item);

    /**
     * 更新行动项。
     *
     * @param item 行动项
     */
    void update(ActionItem item);

    /**
     * 分页查询行动项。
     *
     * @param query 查询条件
     * @return 行动项列表
     */
    List<ActionItem> page(ActionItemQuery query);

    /**
     * 查询行动项总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(ActionItemQuery query);

    /**
     * 统计未关闭行动项数量。
     *
     * @return 未关闭数量
     */
    long countOpen();
}
