package com.bluewhale.party.branch.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.branch.application.command.BranchSaveCommand;
import com.bluewhale.party.branch.application.view.BranchScopeView;
import com.bluewhale.party.branch.application.view.BranchTermDueView;
import com.bluewhale.party.branch.application.view.BranchTreeView;
import com.bluewhale.party.branch.application.view.BranchView;
import com.bluewhale.party.branch.domain.model.BranchQuery;
import com.bluewhale.party.common.PartyActor;

import java.util.List;

/**
 * 支部台账应用服务（M-P1）。
 *
 * @author BlueWhale
 */
public interface BranchAppService {

    /**
     * 支部分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<BranchView> page(BranchQuery query, PartyActor actor);

    /**
     * 支部树（一次取全量在内存装配）。
     *
     * @param actor 操作人
     * @return 树根列表
     */
    List<BranchTreeView> tree(PartyActor actor);

    /**
     * 支部详情。
     *
     * @param id 支部 ID
     * @param actor 操作人
     * @return 支部视图
     */
    BranchView detail(Long id, PartyActor actor);

    /**
     * 新建支部（编码由编号流水生成）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 支部视图
     */
    BranchView create(BranchSaveCommand command, PartyActor actor);

    /**
     * 编辑支部（可调整上级，层级随之级联）。
     *
     * @param id 支部 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 支部视图
     */
    BranchView update(Long id, BranchSaveCommand command, PartyActor actor);

    /**
     * 启用 / 停用支部。
     *
     * <p>停用前要求「无下级支部且无在册党员」：否则会留下挂在停用支部下的党员，
     * 组织生活与党费都会失去归属。</p>
     *
     * @param id 支部 ID
     * @param active true 启用，false 停用
     * @param actor 操作人
     * @return 支部视图
     */
    BranchView changeStatus(Long id, boolean active, PartyActor actor);

    /**
     * 换届到期支部（M-P4，M-P1 遗留）。
     *
     * @param withinDays 提前天数（空表示取配置值）
     * @param actor 操作人
     * @return 到期支部列表（按剩余天数升序）
     */
    List<BranchTermDueView> termDue(Integer withinDays, PartyActor actor);

    /**
     * 在册支部范围（看板按支部刷新与范围收敛用）。
     *
     * @return 支部范围列表
     */
    List<BranchScopeView> activeScopes();
}
