package com.bluewhale.party.member.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.member.application.command.MemberSaveCommand;
import com.bluewhale.party.member.application.command.MemberTransferCommand;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.domain.model.PartyMemberQuery;

import java.time.LocalDate;

/**
 * 党员台账应用服务（M-P1）。
 *
 * @author BlueWhale
 */
public interface MemberAppService {

    /**
     * 党员档案分页。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<MemberView> page(PartyMemberQuery query, PartyActor actor);

    /**
     * 党员档案详情。
     *
     * @param id 档案 ID
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView detail(Long id, PartyActor actor);

    /**
     * 建档（一人一档，编号由流水生成）。
     *
     * @param command 保存命令
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView create(MemberSaveCommand command, PartyActor actor);

    /**
     * 编辑档案（已转出档案只读）。
     *
     * @param id 档案 ID
     * @param command 保存命令
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView update(Long id, MemberSaveCommand command, PartyActor actor);

    /**
     * 办理组织关系转出（终态，档案保留只读）。
     *
     * @param id 档案 ID
     * @param command 转出命令
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView transfer(Long id, MemberTransferCommand command, PartyActor actor);

    /**
     * 办理预备党员转正。
     *
     * @param id 档案 ID
     * @param fullMemberDate 转正日期（空表示当天）
     * @param actor 操作人
     * @return 档案视图
     */
    MemberView promote(Long id, LocalDate fullMemberDate, PartyActor actor);
}
