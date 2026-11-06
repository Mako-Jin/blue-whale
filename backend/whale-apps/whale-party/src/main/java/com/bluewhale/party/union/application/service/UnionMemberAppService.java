package com.bluewhale.party.union.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.union.application.command.UnionMemberSaveCommand;
import com.bluewhale.party.union.application.command.UnionMemberWithdrawCommand;
import com.bluewhale.party.union.application.view.UnionMemberView;
import com.bluewhale.party.union.domain.model.UnionMemberQuery;

/**
 * 工会会员应用服务（M-P3）。
 *
 * @author BlueWhale
 */
public interface UnionMemberAppService {

    /**
     * 会员台账分页。
     *
     * @param query 查询条件（关键字 / 状态）
     * @param current 页码
     * @param size 每页条数
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<UnionMemberView> page(UnionMemberQuery query, Long current, Long size, PartyActor actor);

    /**
     * 会员详情。
     *
     * @param id 会员 ID
     * @param actor 操作人
     * @return 视图
     */
    UnionMemberView detail(Long id, PartyActor actor);

    /**
     * 办理入会（取号新建档案）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionMemberView create(UnionMemberSaveCommand command, PartyActor actor);

    /**
     * 编辑会员（保留编号与状态）。
     *
     * @param id 会员 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 视图
     */
    UnionMemberView update(Long id, UnionMemberSaveCommand command, PartyActor actor);

    /**
     * 办理退会（终态，保留历史档案）。
     *
     * @param id 会员 ID
     * @param command 退会命令（可空）
     * @param actor 操作人
     * @return 视图
     */
    UnionMemberView withdraw(Long id, UnionMemberWithdrawCommand command, PartyActor actor);
}
