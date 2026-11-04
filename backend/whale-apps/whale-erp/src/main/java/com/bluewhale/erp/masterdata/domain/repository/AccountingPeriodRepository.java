package com.bluewhale.erp.masterdata.domain.repository;

import com.bluewhale.erp.masterdata.domain.model.AccountingPeriod;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;

import java.util.List;
import java.util.Optional;

/**
 * 会计期间仓储。
 *
 * @author BlueWhale
 */
public interface AccountingPeriodRepository {

    /**
     * 分页查询期间。
     *
     * @param query 查询条件
     * @return 期间列表
     */
    List<AccountingPeriod> page(MasterDataQuery query);

    /**
     * 统计期间数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MasterDataQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 期间
     */
    Optional<AccountingPeriod> findById(Long id);

    /**
     * 按期间编码查询。
     *
     * @param periodCode 期间编码
     * @return 期间
     */
    Optional<AccountingPeriod> findByCode(String periodCode);

    /**
     * 更新期间（状态、关账信息）。
     *
     * @param period 期间
     */
    void update(AccountingPeriod period);
}
