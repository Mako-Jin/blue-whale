package com.bluewhale.lms.credit.domain.repository;

import com.bluewhale.lms.credit.domain.model.CreditLedger;
import com.bluewhale.lms.credit.domain.model.CreditLedgerQuery;
import com.bluewhale.lms.credit.domain.model.CreditSource;

import java.util.List;
import java.util.Optional;

/**
 * 学分台账仓储。
 *
 * @author BlueWhale
 */
public interface CreditLedgerRepository {

    /**
     * 分页查询台账。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 台账列表
     */
    List<CreditLedger> page(CreditLedgerQuery query, long offset, int size);

    /**
     * 统计台账总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(CreditLedgerQuery query);

    /**
     * 查询某学员在某业务事件上的记账记录（幂等判定用）。
     *
     * @param userId 学员用户 ID
     * @param sourceType 来源类型
     * @param sourceId 来源业务 ID
     * @return 台账记录
     */
    Optional<CreditLedger> findBySource(Long userId, CreditSource sourceType, Long sourceId);

    /**
     * 查询某学员某年度的全部台账（年度汇总用）。
     *
     * @param userId 学员用户 ID
     * @param periodYear 归属年度，null 表示不限
     * @return 台账列表
     */
    List<CreditLedger> listByUser(Long userId, Integer periodYear);

    /**
     * 保存台账。
     *
     * @param ledger 台账记录
     * @return 主键
     */
    Long save(CreditLedger ledger);
}
