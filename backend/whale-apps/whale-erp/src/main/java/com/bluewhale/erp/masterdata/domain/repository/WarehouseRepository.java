package com.bluewhale.erp.masterdata.domain.repository;

import com.bluewhale.erp.masterdata.domain.model.MasterDataQuery;
import com.bluewhale.erp.masterdata.domain.model.Warehouse;

import java.util.List;
import java.util.Optional;

/**
 * 仓库仓储。
 *
 * @author BlueWhale
 */
public interface WarehouseRepository {

    /**
     * 分页查询仓库。
     *
     * @param query 查询条件
     * @return 仓库列表
     */
    List<Warehouse> page(MasterDataQuery query);

    /**
     * 统计仓库数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MasterDataQuery query);

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 仓库
     */
    Optional<Warehouse> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param code 仓库编码
     * @return 仓库
     */
    Optional<Warehouse> findByCode(String code);

    /**
     * 新增仓库。
     *
     * @param warehouse 仓库
     */
    void insert(Warehouse warehouse);

    /**
     * 更新仓库。
     *
     * @param warehouse 仓库
     */
    void update(Warehouse warehouse);
}
