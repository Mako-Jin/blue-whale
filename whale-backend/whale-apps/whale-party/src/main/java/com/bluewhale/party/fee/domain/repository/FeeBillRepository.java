package com.bluewhale.party.fee.domain.repository;

import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeBillQuery;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 党费账单仓储端口。
 *
 * @author BlueWhale
 */
public interface FeeBillRepository {

    /**
     * 分页查询账单。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 账单列表
     */
    List<FeeBill> page(FeeBillQuery query, long offset, int size);

    /**
     * 统计账单总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(FeeBillQuery query);

    /**
     * 按主键查询。
     *
     * @param id 账单 ID
     * @return 账单
     */
    Optional<FeeBill> findById(Long id);

    /**
     * 查询某党员某月账单（生成时判重用）。
     *
     * @param memberId 党员档案 ID
     * @param periodMonth 所属月份 yyyyMM
     * @return 账单
     */
    Optional<FeeBill> findByMemberAndPeriod(Long memberId, String periodMonth);

    /**
     * 统计某月已生成账单人数。
     *
     * @param periodMonth 所属月份
     * @return 已生成条数
     */
    long countByPeriod(String periodMonth);

    /**
     * 统计某月应缴总额。
     *
     * @param periodMonth 所属月份
     * @return 应缴合计
     */
    BigDecimal sumDueByPeriod(String periodMonth);

    /**
     * 统计某月实缴总额。
     *
     * @param periodMonth 所属月份
     * @return 实缴合计
     */
    BigDecimal sumPaidByPeriod(String periodMonth);

    /**
     * 查询某月账单明细（收缴率统计用，排除免缴口径由调用方判定）。
     *
     * @param periodMonth 所属月份 yyyyMM
     * @param branchId 支部 ID（为空表示全租户）
     * @return 账单列表
     */
    List<FeeBill> listByPeriod(String periodMonth, Long branchId);

    /**
     * 保存账单。
     *
     * @param bill 账单
     * @return 主键
     */
    Long save(FeeBill bill);
}
