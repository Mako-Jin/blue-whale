package com.bluewhale.lms.credit.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.credit.application.command.CreditRuleSaveCommand;
import com.bluewhale.lms.credit.application.view.CreditRuleView;
import com.bluewhale.lms.credit.domain.model.CreditRuleQuery;

/**
 * 学分规则应用服务。
 *
 * @author BlueWhale
 */
public interface CreditRuleAppService {

    /**
     * 分页查询规则。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<CreditRuleView> page(CreditRuleQuery query, LmsActor actor);

    /**
     * 规则详情。
     *
     * @param id 规则 ID
     * @param actor 操作人
     * @return 详情
     */
    CreditRuleView detail(Long id, LmsActor actor);

    /**
     * 新建规则。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 规则 ID
     */
    Long create(CreditRuleSaveCommand command, LmsActor actor);

    /**
     * 编辑规则。
     *
     * @param id 规则 ID
     * @param command 保存命令
     * @param actor 操作人
     */
    void update(Long id, CreditRuleSaveCommand command, LmsActor actor);

    /**
     * 删除规则（台账保留规则编码快照）。
     *
     * @param id 规则 ID
     * @param actor 操作人
     */
    void delete(Long id, LmsActor actor);

    /**
     * 启用 / 停用规则。
     *
     * @param id 规则 ID
     * @param enabled 是否启用
     * @param actor 操作人
     */
    void changeStatus(Long id, boolean enabled, LmsActor actor);
}
