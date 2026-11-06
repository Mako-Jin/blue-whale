package com.bluewhale.party.union.domain.repository;

import com.bluewhale.party.union.domain.model.UnionMember;
import com.bluewhale.party.union.domain.model.UnionMemberQuery;

import java.util.List;
import java.util.Optional;

/**
 * 工会会员仓储端口。
 *
 * @author BlueWhale
 */
public interface UnionMemberRepository {

    /**
     * 分页查询会员。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 会员列表
     */
    List<UnionMember> page(UnionMemberQuery query, long offset, int size);

    /**
     * 统计会员数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(UnionMemberQuery query);

    /**
     * 按主键查询。
     *
     * @param id 会员 ID
     * @return 会员
     */
    Optional<UnionMember> findById(Long id);

    /**
     * 按员工编号查询在册会员（用于「同时在册只能一份」校验）。
     *
     * @param employeeNo 员工编号
     * @return 在册会员
     */
    Optional<UnionMember> findActiveByEmployeeNo(String employeeNo);

    /**
     * 按用户 ID 查询在册会员。
     *
     * @param userId 用户 ID
     * @return 在册会员
     */
    Optional<UnionMember> findActiveByUserId(Long userId);

    /**
     * 列出全部在册会员（福利全量发放与活动范围校验用）。
     *
     * @return 在册会员列表
     */
    List<UnionMember> listActive();

    /**
     * 保存会员。
     *
     * @param member 会员
     * @return 主键
     */
    Long save(UnionMember member);
}
