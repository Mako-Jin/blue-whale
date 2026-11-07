package com.bluewhale.crm.analytics.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemQuery;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.common.CrmActor;

import java.util.List;

/**
 * 行动计划应用服务。
 *
 * @author BlueWhale
 */
public interface ActionPlanAppService {

    /**
     * 分页查询行动项。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<ActionItem> page(ActionItemQuery query, CrmActor actor);

    /**
     * 按规则生成行动项；条件解除的未关闭行动项自动关闭。
     *
     * @param actor 操作人
     * @return 新增条数
     */
    int generate(CrmActor actor);

    /**
     * 关闭规则。
     *
     * @param id 规则主键
     * @param actor 操作人
     * @return 更新后的规则
     */
    ActionRule closeRule(Long id, CrmActor actor);

    /**
     * 手工关闭行动项。
     *
     * @param id 行动项主键
     * @param reason 关闭原因
     * @param actor 操作人
     * @return 更新后的行动项
     */
    ActionItem closeItem(Long id, String reason, CrmActor actor);

    /**
     * 查询全部行动规则。
     *
     * @param actor 操作人
     * @return 规则列表
     */
    List<ActionRule> listRules(CrmActor actor);
}
