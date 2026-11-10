package com.bluewhale.erp.masterdata.domain.repository;

import com.bluewhale.erp.masterdata.domain.model.Account;
import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;

import java.util.List;
import java.util.Optional;

/**
 * 会计科目仓储。
 *
 * @author BlueWhale
 */
public interface AccountRepository {

    /**
     * 分页查询科目。
     *
     * @param query 查询条件
     * @return 科目列表
     */
    List<Account> page(MasterDataQuery query);

    /**
     * 统计科目数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MasterDataQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 科目
     */
    Optional<Account> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param code 科目编码
     * @return 科目
     */
    Optional<Account> findByCode(String code);

    /**
     * 查询全部启用科目。
     *
     * @return 科目列表
     */
    List<Account> listEnabled();

    /**
     * 新增科目。
     *
     * @param account 科目
     */
    void insert(Account account);

    /**
     * 更新科目。
     *
     * @param account 科目
     */
    void update(Account account);
}
