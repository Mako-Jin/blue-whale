package com.bluewhale.party.union.domain.repository;

import com.bluewhale.party.union.domain.model.ReliefType;
import com.bluewhale.party.union.domain.model.UnionRelief;
import com.bluewhale.party.union.domain.model.UnionReliefQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 慰问帮扶仓储端口。
 *
 * @author BlueWhale
 */
public interface UnionReliefRepository {

    /**
     * 分页查询申请。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 每页条数
     * @return 申请列表
     */
    List<UnionRelief> page(UnionReliefQuery query, long offset, int size);

    /**
     * 统计申请数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(UnionReliefQuery query);

    /**
     * 按主键查询。
     *
     * @param id 申请 ID
     * @return 申请
     */
    Optional<UnionRelief> findById(Long id);

    /**
     * 保存申请。
     *
     * @param relief 申请
     * @return 主键
     */
    Long save(UnionRelief relief);

    /**
     * 统计已批（已通过 / 已发放）次数。
     *
     * @param unionMemberId 工会会员 ID
     * @param type 慰问类型
     * @param year 申请年度
     * @return 已批次数
     */
    long countApproved(Long unionMemberId, ReliefType type, Integer year);

    /**
     * 汇总已批（已通过 / 已发放）金额。
     *
     * @param unionMemberId 工会会员 ID
     * @param type 慰问类型
     * @param year 申请年度
     * @return 已批金额合计（无记录返回 0）
     */
    BigDecimal sumApprovedAmount(Long unionMemberId, ReliefType type, Integer year);
}
