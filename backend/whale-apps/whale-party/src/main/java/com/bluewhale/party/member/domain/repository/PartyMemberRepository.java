package com.bluewhale.party.member.domain.repository;

import com.bluewhale.party.member.domain.model.MemberStatus;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.model.PartyMemberQuery;

import java.util.List;
import java.util.Optional;

/**
 * 党员档案仓储端口。
 *
 * @author BlueWhale
 */
public interface PartyMemberRepository {

    /**
     * 分页查询党员档案。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 档案列表
     */
    List<PartyMember> page(PartyMemberQuery query, long offset, int size);

    /**
     * 统计党员档案总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(PartyMemberQuery query);

    /**
     * 按主键查询。
     *
     * @param id 档案 ID
     * @return 档案
     */
    Optional<PartyMember> findById(Long id);

    /**
     * 按员工编号查询（一人一档）。
     *
     * @param employeeNo 员工编号
     * @return 档案
     */
    Optional<PartyMember> findByEmployeeNo(String employeeNo);

    /**
     * 按平台用户 ID 查询（书记登录后定位本人支部）。
     *
     * @param userId 用户 ID
     * @return 档案
     */
    Optional<PartyMember> findByUserId(Long userId);

    /**
     * 查询支部下所有未转出的党员（生成党费账单用）。
     *
     * <p>只排除已转出，不在这里过滤停籍：停籍是否缴费由调用方按
     * {@link MemberStatus#shouldPayDues()} 判定，把业务判断留在领域层而不是查询条件里。</p>
     *
     * @param branchId 支部 ID（为空表示全租户）
     * @return 档案列表
     */
    List<PartyMember> listPayable(Long branchId);

    /**
     * 统计支部下在册党员数（支部停用前的前置校验）。
     *
     * @param branchId 支部 ID
     * @return 在册党员数
     */
    long countActiveByBranch(Long branchId);

    /**
     * 保存档案（主键为空时插入，否则更新）。
     *
     * @param member 档案
     * @return 主键
     */
    Long save(PartyMember member);

    /**
     * 统计在册党员数（工作台与看板用）。
     *
     * @return 在册党员数
     */
    long countActive();

    /**
     * 统计待转正人数（预备党员且未填转正日期）。
     *
     * @return 待转正人数
     */
    long countProbationaryPending();
}
